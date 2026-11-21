package com.app.market.data.download

import com.app.market.data.platform.debugLog
import com.app.market.domain.model.download.DownloadMeta
import com.app.market.domain.model.download.DownloadPhase
import com.app.market.domain.model.download.DownloadState
import com.app.market.domain.model.history.InstallHistoryAction
import com.app.market.domain.model.history.InstallHistoryEntry
import com.app.market.domain.model.update.UpdateHistoryEntry
import com.app.market.domain.repository.DownloadRepository
import com.app.market.domain.repository.InstallHistoryRepository
import com.app.market.domain.repository.InstallerPreferencesRepository
import com.app.market.domain.repository.PackageRepository
import com.app.market.domain.repository.SavedPackageRepository
import com.app.market.domain.repository.UpdateHistoryRepository
import com.app.market.domain.repository.UpdatePreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** 更新日志入库前的截断长度，控制单键 JSON 体积。 */
private const val MaxChangeLogLength = 500

/**
 * [DownloadRepository] 装饰器：在下载真正入队前暂存一条 pending 历史（旧版本名从设备实时读取），
 * 安装成功（[DownloadRepository.installedPackages] 发射）后写入 [UpdateHistoryRepository]。
 * cancel 不撤销 pending —— 底层取消是暂停语义，任务可从通知栏恢复安装；未安装的 pending 永不入历史。
 * clear 为真正移除，随之撤销 pending。
 */
internal class RecordingDownloadRepositoryImpl(
    private val delegate: PlatformDownloadDataSource,
    private val packages: PackageRepository,
    private val history: UpdateHistoryRepository,
    private val savedPackages: SavedPackageRepository,
    private val updatePrefs: UpdatePreferencesRepository,
    private val installLog: InstallHistoryRepository,
    private val installerPrefs: InstallerPreferencesRepository,
    private val scope: CoroutineScope,
) : DownloadRepository by delegate {
    /** 记录当时的安装方式（ROOT / SHIZUKU / ...）：历史里只存 key，展示时再翻成中文。 */
    private suspend fun currentInstallerMode(): String =
        runCatching { installerPrefs.mode().key }.getOrDefault("")

    init {
        // 缓存最后一次看到的下载状态：安装广播到达时任务可能已被清理，
        // 靠它补回「应用名 / 图标 / 来源」，不再只能写包名且没有来源标签。
        val lastStates = mutableMapOf<String, DownloadState>()
        // 下载 / 安装历史的记录：相位跃迁时各记一条（进度刷新不重复记录）
        scope.launch {
            val lastPhases = mutableMapOf<String, DownloadPhase>()
            delegate.states.collect { states ->
                states.forEach { (pkg, st) ->
                    lastStates[pkg] = st
                    val prev = lastPhases[pkg]
                    if (prev == st.phase) return@forEach
                    lastPhases[pkg] = st.phase
                    when (st.phase) {
                        DownloadPhase.DOWNLOADED -> {
                            // 首次观察（prev == null，例如重启后恢复）不补记，避免重复
                            // 更新任务已经有「更新历史」记录，这里不再重复记到「下载与安装」
                            if (prev != null && st.installedVersionName.isBlank()) {
                                runCatching {
                                    installLog.record(
                                        InstallHistoryEntry(
                                            packageName = pkg,
                                            displayName = st.displayName,
                                            versionName = st.versionName,
                                            apkSize = st.apkSize,
                                            icon = st.icon,
                                            action = InstallHistoryAction.DOWNLOAD,
                                            success = true,
                                            timestamp = nowEpochMillis(),
                                            source = st.source,
                                            appId = st.appId,
                                            installerMode = currentInstallerMode(),
                                        ),
                                    )
                                }.onFailure { debugLog("InstallHistory") { "record download $pkg failed: $it" } }
                            }
                        }

                        DownloadPhase.FAILED -> {
                            // 从 INSTALLING 掉到 FAILED 是安装失败，其余算下载失败
                            val installing = prev == DownloadPhase.INSTALLING
                            val isUpdate = st.installedVersionName.isNotBlank()
                            // 失败原因写全（不截断成一句话），并同时落一份到运行日志，便于回溯
                            val detail = st.errorMessage.ifBlank { "未知错误（无错误信息）" }.take(600)
                            runCatching {
                                com.app.market.domain.diagnostic.AppLog.log(
                                    "InstallHistory: $pkg ${if (installing) "安装" else "下载"}失败 ${st.versionName} :: $detail",
                                )
                            }
                            if (!isUpdate) runCatching {
                                installLog.record(
                                    InstallHistoryEntry(
                                        packageName = pkg,
                                        displayName = st.displayName,
                                        versionName = st.versionName,
                                        icon = st.icon,
                                        action = if (installing) {
                                            InstallHistoryAction.INSTALL
                                        } else {
                                            InstallHistoryAction.DOWNLOAD
                                        },
                                        success = false,
                                        timestamp = nowEpochMillis(),
                                        detail = detail,
                                        source = st.source,
                                        appId = st.appId,
                                            installerMode = currentInstallerMode(),
                                    ),
                                )
                            }.onFailure { debugLog("InstallHistory") { "record failure $pkg failed: $it" } }
                        }

                        else -> Unit
                    }
                }
            }
        }
        // 安装成功
        scope.launch {
            delegate.installedPackages.collect { packageName ->
                val st = delegate.states.value[packageName] ?: lastStates[packageName]
                // 更新安装属于「更新历史」，不写进「下载与安装历史」
                if (st != null && st.installedVersionName.isNotBlank()) return@collect
                runCatching {
                    installLog.record(
                        InstallHistoryEntry(
                            packageName = packageName,
                            displayName = st?.displayName ?: packageName,
                            versionName = st?.versionName.orEmpty(),
                            apkSize = st?.apkSize ?: 0L,
                            icon = st?.icon.orEmpty(),
                            action = InstallHistoryAction.INSTALL,
                            success = true,
                            timestamp = nowEpochMillis(),
                            source = st?.source,
                            appId = st?.appId ?: 0L,
                            installerMode = currentInstallerMode(),
                        ),
                    )
                }.onFailure { debugLog("InstallHistory") { "record install $packageName failed: $it" } }
            }
        }
        scope.launch {
            delegate.installedPackages.collect { packageName ->
                runCatching { history.commitPending(packageName) }
                    .onFailure { debugLog("UpdateHistory") { "commit $packageName failed: $it" } }
                // 安装成功后按设置自动删除已保存的安装包（默认开启，不堆积在 Download 目录）
                runCatching {
                    if (updatePrefs.autoDeletePackage.value) {
                        savedPackages.list()
                            .filter { it.packageName == packageName }
                            .forEach { savedPackages.delete(it.id) }
                    }
                }.onFailure { debugLog("UpdateHistory") { "auto delete $packageName failed: $it" } }
            }
        }
        scope.launch {
            runCatching { reconcilePending() }
                .onFailure { debugLog("UpdateHistory") { "reconcile pending failed: $it" } }
        }
    }

    override fun start(meta: DownloadMeta, installAfterDownload: Boolean, saveUri: String?) {
        if (!installAfterDownload) {
            delegate.start(meta, installAfterDownload = false, saveUri = saveUri)
            return
        }
        scope.launch {
            // 已有进行中的任务时 DownloadCenter 会拒绝本次请求（暂停任务除外——恢复时改用新 meta），
            // 此时保留原 pending 才与最终真正安装的版本一致。
            val existing = delegate.states.value[meta.packageName]
            if (existing == null || existing.isPaused) {
                val previous = runCatching { packages.installedVersionName(meta.packageName) }.getOrNull()
                runCatching {
                    history.markPending(
                        UpdateHistoryEntry(
                            appId = meta.appId,
                            packageName = meta.packageName,
                            displayName = meta.displayName,
                            versionName = meta.versionName,
                            versionCode = meta.versionCode,
                            previousVersionName = previous.orEmpty(),
                            icon = meta.icon,
                            changeLog = meta.changeLog.take(MaxChangeLogLength),
                            installedAt = nowEpochMillis(),
                            source = meta.source,
                        )
                    )
                }.onFailure { debugLog("UpdateHistory") { "mark ${meta.packageName} pending failed: $it" } }
            }
            // 在存储可用时，pending 先于 start 落盘；历史故障不阻断下载主流程。
            delegate.start(meta, installAfterDownload, saveUri)
        }
    }

    override fun clear(packageName: String) {
        scope.launch {
            runCatching { history.removePending(packageName) }
                .onFailure { debugLog("UpdateHistory") { "remove $packageName pending failed: $it" } }
            delegate.clear(packageName)
        }
    }

    /** 进程死亡期间完成的安装（通知栏恢复、静默安装）没有发射可收；启动时按已装版本对账遗留 pending。 */
    private suspend fun reconcilePending() {
        val pending = history.pendingEntries()
        if (pending.isEmpty()) return
        val installed = packages.installedVersionCodes(pending.map { it.packageName })
        pending.forEach { entry ->
            val installedCode = installed[entry.packageName] ?: return@forEach
            // 自更新来自 GitLab tag，tag 里通常不带 versionCode，记录下来就是 0，
            // 拿 0 去比会被判定成「已装版本更高 → 陈旧 pending」直接删掉，于是更新历史里
            // 永远没有 AppMarket 自己的记录。这时改用版本名判断是否已装到目标版本。
            val installedName = if (entry.versionCode > 0L) {
                ""
            } else {
                runCatching { packages.installedVersionName(entry.packageName) }.getOrNull().orEmpty()
            }
            when {
                installedCode == entry.versionCode ->
                    // 同版本重下载型 pending 无法区分「死进程期间真装了」与「从未安装」，宁可不记也不造假条目。
                    if (entry.previousVersionName == entry.versionName) {
                        history.removePending(entry.packageName)
                    } else {
                        history.commitPending(entry.packageName)
                    }

                entry.versionCode <= 0L && installedName.isNotBlank() &&
                    installedName == entry.versionName -> history.commitPending(entry.packageName)

                installedCode > entry.versionCode -> history.removePending(entry.packageName)
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
private fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
