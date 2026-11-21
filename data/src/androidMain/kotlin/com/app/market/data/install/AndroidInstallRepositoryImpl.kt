package com.app.market.data.install

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.app.market.data.install.backend.InstallerBackendSelector
import com.app.market.data.install.backend.PackageInstallerAccess
import com.app.market.data.install.backend.XIAOMI_MARKET_PACKAGE_NAME
import com.app.market.data.install.network.ArtifactSourceReader
import com.app.market.data.install.platform.InstallStatusIntentFactory
import com.app.market.data.install.storage.ApkCopySaver
import com.app.market.data.install.storage.MediaStorePackageStore
import com.app.market.data.platform.debugLog
import com.app.market.domain.model.install.InstallEvent
import com.app.market.domain.model.install.InstallFailureCode
import com.app.market.domain.model.install.InstallRequest
import com.app.market.domain.model.install.InstallSource
import com.app.market.domain.model.installer.InstallerMode
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.repository.InstallRepository
import com.app.market.domain.repository.InstallerDiscoveryRepository
import com.app.market.domain.repository.InstallerPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

internal class AndroidInstallRepositoryImpl(
    private val context: Context,
    private val preferences: InstallerPreferencesRepository,
    private val backends: InstallerBackendSelector,
    private val sourceReader: ArtifactSourceReader,
    private val stagingDownloader: PackageStagingDownloader,
    private val packageStore: MediaStorePackageStore,
    private val statusIntentFactory: InstallStatusIntentFactory,
    private val discovery: InstallerDiscoveryRepository,
    private val thirdPartyInstaller: ThirdPartyInstallEngine,
) : InstallRepository {
    private val activeJobs = ConcurrentHashMap<String, Job>()

    @SuppressLint("RequestInstallPackagesPolicy")
    override fun install(request: InstallRequest): Flow<InstallEvent> = channelFlow {
        require(request.artifacts.isNotEmpty()) { "Install request has no artifacts" }
        val job = currentCoroutineContext().job
        check(activeJobs.putIfAbsent(request.id, job) == null) { "Install task ${request.id} is already active" }

        var pendingPackage: MediaStorePackageStore.PendingSavedPackage? = null
        var published = false
        var session: PackageInstaller.Session? = null
        // openSession / prepare 可能在 session 句柄拿到之前抛错，回收必须以 id 为准
        var sessionId: Int? = null
        var committed = false
        var access: PackageInstallerAccess? = null
        try {
            var mode = preferences.mode()
            // 每次安装前同步一次提权命令，避免重启后持有者回落成 su
            runCatching {
                com.app.market.data.install.RootPrivilegeCommand.command =
                    preferences.privilegeCommand()
            }
            if (mode == InstallerMode.THIRD_PARTY) {
                val packageName = preferences.thirdPartyInstallerPackage()
                val isAvailable = packageName.isNotBlank() && runCatching {
                    discovery.listCandidates().any { it.packageName == packageName }
                }.getOrDefault(false)
                if (isAvailable) {
                    thirdPartyInstaller.install(request, packageName).collect { send(it) }
                    return@channelFlow
                }
                preferences.setMode(InstallerMode.STANDARD)
                mode = InstallerMode.STANDARD
            }
            send(InstallEvent.Preparing)
            // 第一阶段：远端 artifact 断点续传暂存；取消保留数据，恢复后继续传
            val staged = stagingDownloader.stage(request) { completed, total ->
                send(InstallEvent.Progress(completed, total))
            }
            // 「另存应用安装包」：下载一完成就把副本写到用户指定文件夹
            ApkCopySaver.save(context, request, staged, preferences)
            // 64 位专用机型（abilist 只有 arm64-v8a）遇到只带 32 位原生库的包，系统会在
            // 会话里抛 INSTALL_FAILED_NO_MATCHING_ABIS，用户看到的就是一串看不懂的英文码。
            // 包已经下完了，先扫一遍 APK 的 lib/ 目录，提前给出人话说明，别白等一次安装。
            unsupportedAbiMessage(staged)?.let { message ->
                throw InstallPipelineException(InstallFailureCode.UNKNOWN, message)
            }
            // 第二阶段：本地数据写入安装会话；仅本地源（保存的安装包）在此阶段报进度
            // 把 installer 写成别的应用只有 Shizuku/Root/Dhizuku 做得到；
            // 普通安装没有 INSTALL_PACKAGES，冒充别人会被系统直接拒：
            // SecurityException: Package com.xiaomi.market does not belong to <uid>，整次安装失败
            val canImpersonate = mode != InstallerMode.STANDARD && mode != InstallerMode.SYSTEM
            val desiredInstaller = if (canImpersonate) desiredInstallerFor(request, mode) else null
            access = backends.get(mode).open(desiredInstaller)
            pendingPackage = request.takeIf {
                // 安装包总是落位到「安装包保存路径」；安装后是否删除由
                // 「安装完成后自动删除安装包」决定，与 saveToDownloads 无关
                it.sourceSavedPackageId == null
            }?.let { packageStore.createPending(it) }

            val params = createSessionParams(
                request = request,
                userActionNotRequired = shouldRequestUserActionNotRequired(mode),
                // 来源是 Google Play 的应用会把 installer 认成「不是 Play 装的」，打开就跳商店，
                // 所以按来源写回对应商店的包名（Shizuku/Root/Dhizuku 才有权限写成别的应用）。
                installerPackageName = desiredInstaller ?: access.installerPackageName,
            )
            val effectiveInstaller = desiredInstaller ?: access.installerPackageName
            val createdSessionId = try {
                access.packageInstaller.createSession(params)
            } catch (error: Throwable) {
                // 冒充安装器被系统拒绝时退回自身包名重建会话，不能连累整次安装
                if (effectiveInstaller == access.installerPackageName) throw error
                access.packageInstaller.createSession(
                    createSessionParams(
                        request = request,
                        userActionNotRequired = shouldRequestUserActionNotRequired(mode),
                        installerPackageName = access.installerPackageName,
                    )
                )
            }
            sessionId = createdSessionId
            session = access.packageInstaller.openSession(createdSessionId).also(access::prepare)

            // 写入会话 + 保存副本在大包上可达数十秒，不报进度用户会以为卡死
            val total = request.artifacts.map { it.transferSize }
                .takeIf { sizes -> sizes.all { it > 0L } }
                ?.sum()
                ?: -1L
            var completed = 0L
            var lastProgressAt = 0L
            request.artifacts.forEachIndexed { index, artifact ->
                session.openWrite(artifact.name, 0L, artifact.size.takeIf { it > 0L } ?: -1L).use { sessionOutput ->
                    val savedOutput = pendingPackage?.openOutput(index)
                    savedOutput.use { savedOutput ->
                        val outputs = buildList {
                            add(sessionOutput)
                            savedOutput?.let(::add)
                        }
                        sourceReader.copyTo(artifact, staged[index], outputs) { count ->
                            completed += count
                            // 时间节流：固定频率上报，写会话阶段也实时显示进度
                            val now = System.currentTimeMillis()
                            if (now - lastProgressAt >= 150L) {
                                lastProgressAt = now
                                send(InstallEvent.Progress(completed, total))
                            }
                        }
                        savedOutput?.flush()
                        session.fsync(sessionOutput)
                    }
                }
            }

            val savedPackage = pendingPackage?.publish()
            published = savedPackage != null
            send(InstallEvent.SessionReady(createdSessionId, savedPackage?.id))
            session.commit(
                statusIntentFactory.create(request.id, createdSessionId, request.packageName, request.displayName)
            )
            committed = true
            stagingDownloader.clear(request.id)
            send(InstallEvent.Committed(createdSessionId, savedPackage?.id))
        } finally {
            withContext(NonCancellable) {
                runCatching { session?.close() }
                if (!committed) {
                    sessionId?.let { id -> runCatching { access?.packageInstaller?.abandonSession(id) } }
                }
                if (!published) runCatching { pendingPackage?.rollback() }
                runCatching { access?.close() }
                activeJobs.remove(request.id, job)
            }
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        val failure = error.toInstallFailure()
        emit(InstallEvent.Failed(failure.code, failure.message ?: "Installation failed"))
    }.flowOn(Dispatchers.IO)

    override suspend fun cancel(id: String) {
        activeJobs[id]?.cancel()
    }

    override suspend fun reclaimSessions(retainedSessionIds: Set<Int>) = withContext(Dispatchers.IO) {
        val sessions = runCatching { context.packageManager.packageInstaller.mySessions }.getOrNull() ?: return@withContext
        sessions.asSequence()
            .map { it.sessionId }
            .filter { it !in retainedSessionIds }
            .forEach { id ->
                runCatching { context.packageManager.packageInstaller.abandonSession(id) }
                    .onSuccess { debugLog("Installer") { "Abandoned orphaned session $id" } }
            }
    }

    /**
     * staged 里的包只带本机不支持的原生库时，返回一句人话提示；能装就返回 null。
     * 纯 Java 包（没有 lib/ 目录）视为通过；多 split 只要有一份含匹配 ABI 也通过。
     */
    private fun unsupportedAbiMessage(staged: List<java.io.File?>): String? {
        val supported = Build.SUPPORTED_ABIS.toSet()
        val declared = mutableSetOf<String>()
        for (file in staged) {
            if (file == null || !file.isFile) continue
            val abis = runCatching {
                java.util.zip.ZipFile(file).use { zip ->
                    zip.entries().asSequence()
                        .map { it.name }
                        .filter { it.startsWith("lib/") }
                        .map { it.removePrefix("lib/").substringBefore('/') }
                        .filter { it.isNotBlank() }
                        .toSet()
                }
            }.getOrNull() ?: continue
            if (abis.isEmpty()) continue
            if (abis.any { it in supported }) return null
            declared += abis
        }
        if (declared.isEmpty()) return null
        val machine = Build.SUPPORTED_ABIS.joinToString("/")
        return "安装包只带 ${declared.sorted().joinToString("/")} 原生库，本机只支持 $machine，" +
            "架构不匹配装不上（系统错误 INSTALL_FAILED_NO_MATCHING_ABIS）。" +
            "换一个提供 ${Build.SUPPORTED_ABIS.first()} 安装包的来源再试。"
    }

    private suspend fun shouldRequestUserActionNotRequired(mode: InstallerMode): Boolean {
        // 只有「系统权限静默安装」这一档才声明无需用户操作：此时本应用自身持有
        // INSTALL_PACKAGES，系统会真的静默安装。普通安装声明会被部分 ROM 静默丢弃
        //（表现为「安装中」转一圈又回到「安装」，应用根本没进系统），所以其余模式一律不声明。
        return mode == InstallerMode.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    /**
     * 按安装来源决定 installer 包名。
     *
     * Google Play 分发的应用（Play Integrity / 安装来源校验）发现 `installingPackageName`
     * 不是 `com.android.vending` 时会拒绝运行并跳转商店，所以用 Shizuku / Root / Dhizuku
     * 安装来自 Play 的应用时，把 installer 写回 Play 商店；小米源同理写回小米商店。
     * 标准安装没有该权限，只能保持自身包名。
     */
    private suspend fun desiredInstallerFor(request: InstallRequest, mode: InstallerMode): String? {
        // 安装来源三档（InstallerX Revived 同款 setInstallerPackageName 逻辑）；
        // 仅 Root/Shizuku 支持：Dhizuku 会话归属固定为 Dhizuku 自身，普通安装无权限冒充
        if (mode == InstallerMode.ROOT || mode == InstallerMode.SHIZUKU) {
            when (runCatching { preferences.installerSourceMode() }.getOrDefault("google_play")) {
                "google_play" -> return PLAY_STORE_PACKAGE_NAME
                "custom" -> {
                    val custom = runCatching { preferences.customInstallerSource() }.getOrDefault("")
                    if (custom.isNotBlank()) return custom.trim()
                }
                // "default" → 走下面按来源自动推断
            }
        }
        return when (request.marketSource) {
            AppSource.GOOGLE -> PLAY_STORE_PACKAGE_NAME
            AppSource.XIAOMI -> XIAOMI_MARKET_PACKAGE_NAME
            else -> null
        }
    }


    private fun createSessionParams(
        request: InstallRequest,
        userActionNotRequired: Boolean,
        installerPackageName: String,
    ): PackageInstaller.SessionParams =
        PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(request.packageName)
            request.artifacts.map { it.size }
                .takeIf { sizes -> sizes.isNotEmpty() && sizes.all { it > 0L } }
                ?.sum()
                ?.let(::setSize)
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            request.artifacts.firstNotNullOfOrNull { artifact ->
                when (val source = artifact.source) {
                    is InstallSource.Remote -> source.url
                    is InstallSource.Delta -> source.patch.url
                    is InstallSource.Local -> null
                }
            }?.let { setOriginatingUri(Uri.parse(it)) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (userActionNotRequired) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // 普通安装没有权限冒充别的安装器，失败就退回自身包名，不能连累整次安装
                runCatching { setInstallerPackageName(installerPackageName) }
            }
            // MODE_FULL_INSTALL 本身已是替换语义，隐藏字段不可访问时不应连累整次安装
            runCatching { enableReplaceExisting() }
        }

    private companion object {
        const val PROGRESS_STEP_BYTES = 1024L * 1024L
        const val PLAY_STORE_PACKAGE_NAME = "com.android.vending"
    }
}

@SuppressLint("DiscouragedPrivateApi")
private fun PackageInstaller.SessionParams.enableReplaceExisting() {
    val installFlagsField = PackageInstaller.SessionParams::class.java
        .getDeclaredField("installFlags")
        .apply { isAccessible = true }
    installFlagsField.setInt(
        this,
        installFlagsField.getInt(this) or INSTALL_REPLACE_EXISTING,
    )
}

// PackageManager.INSTALL_REPLACE_EXISTING is hidden from the public SDK.
private const val INSTALL_REPLACE_EXISTING = 0x00000002