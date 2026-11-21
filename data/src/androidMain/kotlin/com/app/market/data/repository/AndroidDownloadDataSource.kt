package com.app.market.data.repository

import android.content.pm.PackageInstaller
import com.app.market.data.download.PlatformDownloadDataSource
import com.app.market.data.install.DeltaFallbackBus
import com.app.market.data.install.RemotePackageDownloader
import com.app.market.data.install.DexoptRunner
import com.app.market.data.install.platform.InstallResultHandler
import com.app.market.data.install.platform.InstallTaskLauncher
import com.app.market.data.install.platform.InstallTaskProcessor
import com.app.market.data.install.platform.PackageChangeHandler
import com.app.market.data.install.platform.SavedPackageInstallLauncher
import com.app.market.data.install.storage.ArtifactStagingStore
import com.app.market.data.install.storage.MediaStorePackageStore
import com.app.market.data.install.task.InstallTaskRecord
import com.app.market.data.install.task.InstallTaskStore
import com.app.market.data.platform.debugLog
import com.app.market.domain.diagnostic.AppLog
import com.app.market.domain.model.download.DownloadMeta
import com.app.market.domain.model.download.DownloadPart
import com.app.market.domain.model.download.DownloadPhase
import com.app.market.domain.model.download.DownloadState
import com.app.market.domain.model.download.DownloadTaskKey
import com.app.market.domain.model.download.taskKey
import com.app.market.domain.model.install.DeltaFallback
import com.app.market.domain.model.install.InstallArtifact
import com.app.market.domain.model.install.InstallEvent
import com.app.market.domain.model.install.InstallFailureCode
import com.app.market.domain.model.install.InstallRequest
import com.app.market.domain.model.install.InstallSource
import com.app.market.domain.model.install.InstallUserAction
import com.app.market.domain.model.installer.InstallerMode
import com.app.market.domain.repository.InstallRepository
import com.app.market.domain.repository.InstallerPreferencesRepository
import com.app.market.domain.repository.PackageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class AndroidDownloadDataSource(
    private val tasks: InstallTaskStore,
    private val installer: InstallRepository,
    private val downloader: RemotePackageDownloader,
    private val packageStore: MediaStorePackageStore,
    private val staging: ArtifactStagingStore,
    private val preferences: InstallerPreferencesRepository,
    private val packages: PackageRepository,
    private val launcher: InstallTaskLauncher,
    private val packageChanges: PackageChangeHandler,
    private val deltaFallbackBus: DeltaFallbackBus,
    private val scope: CoroutineScope,
) : PlatformDownloadDataSource, InstallTaskProcessor, InstallResultHandler, SavedPackageInstallLauncher {
    private val dexoptRunner = DexoptRunner()
    private val mutableStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    override val states: StateFlow<Map<String, DownloadState>> = mutableStates.asStateFlow()

    /** 点击下载立即注册 QUEUED 占位：按钮先显示「下载中」，真任务创建后同 key 自动覆盖。 */
    override fun prepareDownload(app: com.app.market.domain.model.market.MarketAppInfo) {
        val cur = mutableStates.value
        if (!cur.containsKey(app.packageName)) {
            mutableStates.value = cur + (app.packageName to DownloadState(
                appId = app.appId,
                packageName = app.packageName,
                displayName = app.displayName,
                progress = null,
                phase = DownloadPhase.QUEUED,
                icon = app.icon,
                source = app.source,
            ))
        }
    }
    private val mutableTaskStates = MutableStateFlow<Map<DownloadTaskKey, DownloadState>>(emptyMap())
    override val taskStates: StateFlow<Map<DownloadTaskKey, DownloadState>> = mutableTaskStates.asStateFlow()
    /** 每个下载任务的差分测速表；任务结束/长时间无进度后由 [SpeedMeter] 自行归零。 */
    private val speedMeters = ConcurrentHashMap<DownloadTaskKey, SpeedMeter>()
    /** 每任务已传字节的单调峰值：写安装会话阶段 completed 从 0 重新累计，不许拉低已显示的进度。 */
    private val completedMeters = ConcurrentHashMap<DownloadTaskKey, Long>()

    private val mutableInstalledPackages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val installedPackages: SharedFlow<String> = mutableInstalledPackages.asSharedFlow()
    override val deltaFallbacks: SharedFlow<DeltaFallback> = deltaFallbackBus.events

    private val mutablePendingUserAction = MutableStateFlow<InstallUserAction?>(null)
    override val pendingUserAction: StateFlow<InstallUserAction?> = mutablePendingUserAction.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val lifecycleMutex = Mutex()
    private val restored = CompletableDeferred<Unit>()

    /**
     * 下载并发槽，容量可配（设置 → 下载与安装 → 同时下载任务数量）。
     * kotlinx 的 Semaphore permits 不可变，这里自己实现「可调容量」信号量：
     * 调小后不再放新任务进入，等在途任务释放完自然收敛到新容量。
     */
    private val downloadSlots = MutablePermits(MAX_CONCURRENT_DOWNLOADS)

    init {
        scope.launch {
            try {
                restoreTasks()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                // 恢复失败只降级为空任务列表，绝不能阻断后续下载/安装能力
                debugLog("InstallTask") { "Task restore failed: ${error.stackTraceToString()}" }
            } finally {
                restored.complete(Unit)
            }
        }
        // 并发上限跟随设置项；首次读取失败退回默认值
        scope.launch { syncDownloadSlotsCapacity() }
    }

    /** 把设置里的「同时下载任务数量」同步到下载并发槽；改设置后即时生效，无需重启。 */
    private suspend fun syncDownloadSlotsCapacity() {
        downloadSlots.setCapacity(
            runCatching { preferences.concurrentDownloads() }
                .getOrDefault(MAX_CONCURRENT_DOWNLOADS)
        )
    }

    override fun start(meta: DownloadMeta, installAfterDownload: Boolean, saveUri: String?) {
        scope.launch {
            restored.await()
            val saveToDownloads = !installAfterDownload ||
                    runCatching { preferences.saveToDownloads() }.getOrDefault(false)
            val deltaUpdateEnabled = runCatching {
                preferences.deltaUpdateEnabled()
            }.getOrDefault(false)
            val record = lifecycleMutex.withLock {
                val taskKey = meta.taskKey
                val existing = tasks.all().filter { it.taskKey == taskKey }
                if (existing.any { it.phase in ActivePhases }) return@withLock null
                // 暂停/失败的同版本任务保留原 id，复用暂存数据实现断点续传；其余记录清掉
                val resumable = existing.lastOrNull {
                    it.phase in ResumablePhases
                }
                existing.forEach { stale ->
                    if (stale.id == resumable?.id) return@forEach
                    tasks.remove(stale.id)
                    staging.clear(stale.id)
                    removePublishedState(stale)
                }

                val id = resumable?.id ?: UUID.randomUUID().toString()
                InstallTaskRecord(
                    id = id,
                    appId = meta.appId,
                    request = meta.toInstallRequest(id, saveToDownloads, deltaUpdateEnabled, saveUri),
                    installAfterDownload = installAfterDownload,
                    progress = resumable?.progress,
                ).also {
                    tasks.put(it)
                    publishState(it)
                }
            } ?: return@launch
            launchTask(record)
        }
    }

    override fun install(packageName: String) {
        scope.launch {
            restored.await()
            val current = mutableStates.value[packageName] ?: return@launch
            val savedId = current.savedPackageId ?: return@launch
            runCatching { enqueueSavedPackage(savedId, current.appId, propagateLaunchFailure = false) }
                .onFailure { error ->
                    val message = error.message ?: "Saved package is unavailable"
                    publishFailureAction(InstallFailureCode.STORAGE, message)
                    mutableStates.update { states ->
                        states + (packageName to current.copy(
                            phase = DownloadPhase.FAILED,
                            savedPackageId = null,
                            errorMessage = message,
                        ))
                    }
                    runCatching {
                        tasks.all()
                            .filter { it.request.packageName == packageName }
                            .forEach { record ->
                                tasks.update(record.id) {
                                    it.copy(
                                        phase = DownloadPhase.FAILED,
                                        errorMessage = message,
                                    )
                                }
                            }
                    }.onFailure { persistenceError ->
                        debugLog("InstallTask") { "Unable to persist saved package failure: $persistenceError" }
                    }
                }
        }
    }

    override suspend fun installSavedPackage(savedPackageId: String) {
        restored.await()
        try {
            enqueueSavedPackage(savedPackageId, appId = null, propagateLaunchFailure = true)
        } catch (error: Throwable) {
            publishFailureAction(
                InstallFailureCode.STORAGE,
                error.message ?: "Saved package is unavailable",
            )
            throw error
        }
    }

    override fun cancel(packageName: String) {
        scope.launch {
            restored.await()
            val record = tasks.all().lastOrNull {
                it.request.packageName == packageName && it.phase !in setOf(DownloadPhase.DOWNLOADED, DownloadPhase.FAILED)
            } ?: return@launch
            pauseTask(record.id)
        }
    }

    override fun cancel(packageName: String, versionCode: Long) {
        scope.launch {
            restored.await()
            val record = tasks.all().lastOrNull {
                it.request.packageName == packageName &&
                        it.request.versionCode == versionCode &&
                        it.phase !in setOf(DownloadPhase.DOWNLOADED, DownloadPhase.FAILED)
            } ?: return@launch
            pauseTask(record.id)
        }
    }

    override fun clear(packageName: String) {
        scope.launch {
            restored.await()
            val records = tasks.all().filter { it.request.packageName == packageName }
            records.forEach { record ->
                activeJobs[record.id]?.cancel()
                installer.cancel(record.id)
                tasks.remove(record.id)
                staging.clear(record.id)
            }
            val savedIds = buildSet {
                mutableStates.value[packageName]?.savedPackageId?.let(::add)
                records.mapNotNullTo(this) { it.savedPackageId }
            }
            savedIds.forEach { packageStore.delete(it) }
            mutableStates.update { it - packageName }
            mutableTaskStates.update { states -> states.filterKeys { it.packageName != packageName } }
        }
    }

    override fun consumePendingUserAction() {
        mutablePendingUserAction.value = null
    }

    override suspend fun process(taskId: String) {
        restored.await()
        val job = currentCoroutineContext().job
        // 暂停后立刻恢复时旧任务可能还在收尾：等它完全退出再接管，避免状态互相覆盖
        while (true) {
            val previous = activeJobs.putIfAbsent(taskId, job) ?: break
            if (previous === job) break
            previous.cancel()
            previous.join()
            activeJobs.remove(taskId, previous)
        }
        val record = tasks.get(taskId) ?: run {
            activeJobs.remove(taskId)
            return
        }
        try {
            // 「全部更新」会一次拉起几十个任务，不限并发会切碎带宽并塞满暂存区
            val needsSlot = record.request.artifacts.any { it.source !is InstallSource.Local }
            if (needsSlot) {
                updateRecord(taskId) { it.copy(phase = DownloadPhase.QUEUED, errorMessage = "") }
                downloadSlots.acquire(refreshCapacity = ::syncDownloadSlotsCapacity)
            }
            try {
                val localArtifacts = record.request.artifacts.all { a -> a.source is InstallSource.Local }
                val standardInstaller = isStandardInstaller()
                updateRecord(taskId) {
                    it.copy(
                        // 全本地 artifact（重装保存的安装包）点「安装」不再闪下载进度条；
                        // 普通安装要调起系统安装器确认（确认框可能被吞），不进「安装中」保持可点击
                        phase = when {
                            !localArtifacts -> DownloadPhase.DOWNLOADING
                            standardInstaller -> DownloadPhase.DOWNLOADED
                            else -> DownloadPhase.INSTALLING
                        },
                        errorMessage = "",
                    )
                }
                val events = if (record.installAfterDownload) {
                    installer.install(record.request)
                } else {
                    downloader.download(record.request)
                }
                events.collect { event ->
                    handleEvent(taskId, record, event)
                }
            } finally {
                if (needsSlot) downloadSlots.release()
            }
            confirmInstall(taskId)
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                updateRecord(taskId) { it.copy(phase = DownloadPhase.PAUSED) }
            }
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: "Task failed"
            recordFailure(taskId, InstallFailureCode.UNKNOWN, message) {
                it.copy(
                    phase = DownloadPhase.FAILED,
                    errorMessage = message,
                )
            }
        } finally {
            activeJobs.remove(taskId, job)
        }
    }

    override suspend fun onUnhandledFailure(taskId: String, error: Throwable) {
        if (error is CancellationException) return
        val message = error.message ?: "Installation failed"
        recordFailure(taskId, InstallFailureCode.UNKNOWN, message) {
            it.copy(
                phase = DownloadPhase.FAILED,
                errorMessage = message,
            )
        }
    }

    override fun cancelTask(taskId: String) {
        scope.launch {
            restored.await()
            pauseTask(taskId)
        }
    }

    override suspend fun onPendingUserAction(taskId: String) {
        restored.await()
        // 普通安装：确认页由 InstallResultReceiver 立即拉起，不再挂「等待安装完成」——
        // 否则确认页一旦被忽略/拉起失败，任务永远停在该状态
        val mode = runCatching { preferences.mode() }.getOrDefault(InstallerMode.STANDARD)
        if (mode == InstallerMode.STANDARD) return
        updateRecord(taskId) { it.copy(phase = DownloadPhase.AWAITING_USER_ACTION) }
    }

    override suspend fun onResult(taskId: String, status: Int, message: String?) {
        restored.await()
        val record = tasks.get(taskId) ?: return
        if (status == PackageInstaller.STATUS_SUCCESS) {
            // 回执成功 ≠ 装上了：部分 ROM 会给出伪成功的回执，应用其实没进系统。
            // 以 PackageManager 的实际可见性为准，避免「装完了但应用不存在」。
            val installedNow = runCatching {
                packages.freshInstalledVersionCode(record.request.packageName)
            }.getOrNull()
            if (installedNow == null) {
                val text = "安装未生效：系统中找不到该应用，请重试"
                AppLog.log("installResult: ${record.request.packageName} status=SUCCESS but package missing")
                recordFailure(taskId, InstallFailureCode.SESSION, text) {
                    it.copy(
                        phase = if (it.savedPackageId != null) DownloadPhase.DOWNLOADED else DownloadPhase.FAILED,
                        errorMessage = text,
                    )
                }
                return
            }
            // Normally PACKAGE_ADDED/REPLACED wins. This is a safety net for ROMs that suppress
            // package broadcasts: preserve the same cache-first ordering before completing.
            if (packageChanges.onPackageChanged(record.request.packageName, installed = true)) {
                onPackageChanged(record.request.packageName)
            }
            return
        }

        AppLog.log(
            "installResult: ${record.request.packageName} status=$status msg=${message.orEmpty()}"
        )
        val hasSavedPackage = record.savedPackageId != null
        val failureMessage = message ?: "Installation failed ($status)"
        val shouldShowFailure = status != PackageInstaller.STATUS_FAILURE_ABORTED
        recordFailure(
            taskId = taskId,
            code = if (shouldShowFailure) InstallFailureCode.SESSION else InstallFailureCode.CANCELLED,
            message = failureMessage,
        ) {
            it.copy(
                phase = if (hasSavedPackage) DownloadPhase.DOWNLOADED else DownloadPhase.FAILED,
                errorMessage = failureMessage,
            )
        }
    }

    override suspend fun onPackageChanged(packageName: String) {
        restored.await()
        val installedNow = runCatching { packages.freshInstalledVersionCode(packageName) }.getOrNull()
        val completed = tasks.all().filter { record ->
            record.request.packageName == packageName &&
                    (record.phase in setOf(DownloadPhase.INSTALLING, DownloadPhase.AWAITING_USER_ACTION) ||
                        // 目标版本已装上：清理任意相位的残留任务（含自更新进程被杀留下的 PAUSED）
                        (record.request.versionCode > 0L &&
                                installedNow != null && installedNow >= record.request.versionCode))
        }
        completed.forEach { record ->
            // 安装完成后是否删包由设置决定（默认删除；关闭则保留在 Download/AppMarket）
            val autoDelete = runCatching { preferences.deletePackageAfterInstall() }.getOrDefault(true)
            if (autoDelete) {
                record.savedPackageId?.let { savedId -> runCatching { packageStore.delete(savedId) } }
            }
            tasks.remove(record.id)
            staging.clear(record.id)
            removePublishedState(record)
        }
        if (completed.isNotEmpty()) runManualDexopt(packageName)
        // This broadcast is the authoritative signal that PackageManager now sees the package.
        // Emit it even when the install-result callback already removed our task record.
        AppLog.log("onPackageChanged: $packageName installedNow=$installedNow, removed ${completed.size} task(s)")
        mutableInstalledPackages.emit(packageName)
    }

    /** 手动 dex 优化（安装成功后 pm compile）；仅 Root/Shizuku，其余模式静默跳过。 */
    private fun runManualDexopt(packageName: String) {
        scope.launch {
            val enabled = runCatching { preferences.manualDexoptEnabled() }.getOrDefault(false)
            if (!enabled) return@launch
            val mode = runCatching { preferences.mode() }.getOrDefault(InstallerMode.STANDARD)
            if (mode != InstallerMode.SHIZUKU && mode != InstallerMode.ROOT) return@launch
            val filter = runCatching { preferences.dexoptMode() }.getOrDefault("speed-profile")
            val force = runCatching { preferences.dexoptForceEnabled() }.getOrDefault(false)
            runCatching { dexoptRunner.run(mode, packageName, filter, force) }
                .onFailure { error -> debugLog("Dexopt") { "Dexopt failed for $packageName: $error" } }
        }
    }

    /** 普通安装（无静默权限，需调起系统安装器确认）：确认框可能被 ROM 吞掉，不能让任务锁在「安装中」。 */
    private suspend fun isStandardInstaller(): Boolean =
        runCatching { preferences.mode() }.getOrDefault(InstallerMode.STANDARD) == InstallerMode.STANDARD

    /** commit 后主动确认：STATUS 回调与 PACKAGE_ADDED 广播都可能被 ROM 压制，丢一次就永远卡 INSTALLING。 */
    private suspend fun confirmInstall(taskId: String) {
        val record = tasks.get(taskId) ?: return
        if (record.phase !in PendingResultPhases) return
        val packageName = record.request.packageName
        val expected = record.request.versionCode
        // 特权模式无需用户交互，很快就该有结果；标准模式要留出系统确认弹窗的时间
        val timeout = if (preferences.mode() in PrivilegedModes) {
            PRIVILEGED_CONFIRM_TIMEOUT_MS
        } else {
            STANDARD_CONFIRM_TIMEOUT_MS
        }
        var elapsed = 0L
        while (elapsed < timeout) {
            // 转入 AWAITING_USER_ACTION 后进度由用户掌握，不能按超时判失败
            val current = tasks.get(taskId) ?: return
            if (current.phase != DownloadPhase.INSTALLING) return
            val installed = runCatching { packages.freshInstalledVersionCode(packageName) }.getOrNull()
            if (installed != null && (expected <= 0L || installed >= expected)) {
                onPackageChanged(packageName)
                return
            }
            delay(INSTALL_CONFIRM_INTERVAL_MS)
            elapsed += INSTALL_CONFIRM_INTERVAL_MS
        }
        timeOutInstall(taskId, "安装结果未回传，请重试")
    }

    /** 无回执的 INSTALLING / AWAITING_USER_ACTION 收敛回可操作相位：有本地包退回 DOWNLOADED，否则判失败。 */
    private suspend fun timeOutInstall(taskId: String, message: String) {
        val record = tasks.get(taskId) ?: return
        if (record.phase !in PendingResultPhases) return
        AppLog.log("timeOutInstall: ${record.request.packageName} phase=${record.phase} msg=$message")
        debugLog("InstallTask") { "No install result for ${record.request.packageName}, releasing $taskId" }
        updateRecord(taskId) {
            it.copy(
                phase = if (it.savedPackageId != null) DownloadPhase.DOWNLOADED else DownloadPhase.FAILED,
                externalInstallerPackage = null,
                errorMessage = message,
            )
        }
    }

    private suspend fun handleEvent(taskId: String, original: InstallTaskRecord, event: InstallEvent) {
        // 本地安装包的「下载进度」其实是写安装会话的拷贝进度，对用户是噪音：直接不报
        val localInstall = original.request.artifacts.isNotEmpty() &&
                original.request.artifacts.all { it.source is InstallSource.Local }
        val standardInstaller = isStandardInstaller()
        when (event) {
            InstallEvent.Preparing -> updateRecord(taskId) {
                // 普通安装不进「安装中」：确认框被吞时任务会永久卡死、按钮无法再点
                it.copy(
                    phase = when {
                        !localInstall -> DownloadPhase.QUEUED
                        standardInstaller -> DownloadPhase.DOWNLOADED
                        else -> DownloadPhase.INSTALLING
                    },
                )
            }
            is InstallEvent.Progress -> {
                if (localInstall) return
                // completed 单调取峰值：写会话阶段从 0 重新累计也不会造成进度回退
                val effCompleted = completedMeters.merge(original.taskKey, event.completed, ::maxOf) ?: event.completed
                val percent = if (event.total > 0L) {
                    ((effCompleted * 10000L) / event.total).coerceIn(0L, 10000L).toInt() / 100f
                } else {
                    null
                }
                // 速度必须用「单调递增的完成字节」算：写安装会话阶段会从 0 重新累计，
                // 用原始 event.completed 会让采样窗口里出现大幅回退/跳变，算出「一开始很快然后特别慢」的假速度。
                val speed = speedMeters.getOrPut(original.taskKey) { SpeedMeter() }
                    .onProgress(effCompleted, System.nanoTime())
                val current = mutableTaskStates.value[original.taskKey] ?: original.toDownloadState()
                // 拿不到应用大小时用下载接口回传的 Content-Length 补上（列表缓存缺失的兜底）
                val resolvedSize = current.apkSize.takeIf { it > 0L } ?: event.total
                // 安装器在解压/校验阶段仍会抛 Progress 事件，此时 percent 从 100 掉回小值，
                // 造成「进度满后回退再增长」的观感。一旦到过 100% 就锁住，不再回退。
                val reachedFull = (current.progress ?: 0f) >= 100f
                // 进度只增不减：增量降级为全量/分母确定会让瞬时值跳变（回退又猛涨）；
                // total 未定时（percent == null）保持原值不清空。
                val nextPercent = when {
                    reachedFull -> 100f
                    percent == null -> current.progress
                    // 真实值直出：增量降级为全量时进度会回退（224MB补丁→348MB整包），
                    // 靠 300ms 动画平滑回退；锁住不放才会「卡在 55% 不动」。
                    else -> percent
                }
                val keepPhase = reachedFull && current.phase != DownloadPhase.DOWNLOADING
                val nextPhase = if (keepPhase) current.phase else DownloadPhase.DOWNLOADING
                        if (current.progress != nextPercent ||
                            current.phase != nextPhase ||
                            current.speedBps != speed ||
                            current.apkSize != resolvedSize
                        ) {
                    publishTransientState(
                        if (keepPhase) {
                            // 已满 100%：只刷新速度，phase/progress 保持不变
                            current.copy(speedBps = speed)
                        } else {
                            current.copy(
                                progress = nextPercent,
                                phase = DownloadPhase.DOWNLOADING,
                                speedBps = speed,
                                apkSize = resolvedSize,
                            )
                        }
                    )
                }
            }

            is InstallEvent.Downloaded -> updateRecord(taskId) {
                it.copy(
                    phase = DownloadPhase.DOWNLOADED,
                    progress = 100f,
                    savedPackageId = event.savedPackageId,
                )
            }

            is InstallEvent.SessionReady -> {
                // 普通安装（调起系统安装器确认）不进「安装中」：并发安装时第二个确认框
                // 可能被系统吞掉，任务锁在「安装中」会让按钮永久不可点；保持 DOWNLOADED 可随时重装
                val readyPhase = if (standardInstaller) DownloadPhase.DOWNLOADED else DownloadPhase.INSTALLING
                AppLog.log("SessionReady: ${original.request.packageName} phase=$readyPhase")
                updateRecord(taskId) {
                    it.copy(
                        phase = readyPhase,
                        progress = 100f,
                        sessionId = event.sessionId,
                        savedPackageId = event.savedPackageId ?: it.savedPackageId,
                    )
                }
            }

            is InstallEvent.Committed -> Unit
            is InstallEvent.ExternalInstallerPrepared -> updateRecord(taskId) {
                it.copy(
                    phase = DownloadPhase.INSTALLING,
                    progress = 100f,
                    savedPackageId = event.savedPackageId,
                    externalInstallerPackage = event.installerPackageName,
                )
            }

            is InstallEvent.ExternalInstallerLaunched -> updateRecord(taskId) {
                it.copy(
                    phase = DownloadPhase.AWAITING_USER_ACTION,
                    progress = 100f,
                    savedPackageId = event.savedPackageId,
                    externalInstallerPackage = event.installerPackageName,
                )
            }

            is InstallEvent.Failed -> {
                AppLog.log("InstallEvent.Failed: ${original.request.packageName} code=${event.code} msg=${event.message}")
                recordFailure(taskId, event.code, event.message) {
                    it.copy(
                        phase = if (it.savedPackageId != null) DownloadPhase.DOWNLOADED else DownloadPhase.FAILED,
                        externalInstallerPackage = null,
                        errorMessage = event.message,
                    )
                }
            }
        }
    }

    private suspend fun updateRecord(
        taskId: String,
        transform: (InstallTaskRecord) -> InstallTaskRecord,
    ): InstallTaskRecord? {
        val updated = tasks.update(taskId, transform) ?: return null
        publishState(updated)
        return updated
    }

    private fun publishState(record: InstallTaskRecord) {
        publishTransientState(record.toDownloadState())
    }

    private fun publishTransientState(state: DownloadState) {
        mutableTaskStates.update { states -> states + (state.taskKey to state) }
        mutableStates.update { states ->
            states + (state.packageName to state)
        }
    }

    private fun removePublishedState(record: InstallTaskRecord) {
        val key = record.taskKey
        mutableTaskStates.update { it - key }
        val replacement = mutableTaskStates.value.values.lastOrNull { it.packageName == key.packageName }
        mutableStates.update { states ->
            if (replacement == null) states - key.packageName
            else states + (key.packageName to replacement)
        }
    }

    private suspend fun launchTask(record: InstallTaskRecord, propagateFailure: Boolean = false) {
        // 任务重启（含暂停续传）：清掉速度/峰值计数。断点字节会由 onBytes(existing) 立即接续，
        // 不清则旧峰值会把进度条锁在暂停时的百分比上（观感像"进度还在但重新下载了"）。
        speedMeters.remove(record.taskKey)
        completedMeters.remove(record.taskKey)
        try {
            launcher.start(record.id)
        } catch (error: Throwable) {
            val message = error.message ?: "Unable to start installer service"
            recordFailure(record.id, InstallFailureCode.UNKNOWN, message) {
                it.copy(
                    phase = DownloadPhase.FAILED,
                    errorMessage = message,
                )
            }
            if (propagateFailure) throw error
        }
    }

    private suspend fun enqueueSavedPackage(
        savedPackageId: String,
        appId: Long?,
        propagateLaunchFailure: Boolean,
    ) {
        val record = lifecycleMutex.withLock {
            val saved = packageStore.get(savedPackageId) ?: error("Saved package is unavailable")
            val existing = tasks.all().filter { it.request.packageName == saved.packageName }
            // 安装来源必须跟着安装包走：保存的安装包重装时丢了 marketSource，
            // installer 就会被写成默认的小米商店（Google Play 分发的应用直接拒跑跳商店）
            val priorSource = saved.source
                ?: existing.lastOrNull()?.request?.marketSource
                ?: mutableStates.value[saved.packageName]?.source
            check(existing.none { it.phase in ActivePhases }) {
                "An installation for ${saved.displayName} is already active"
            }
            existing.forEach {
                tasks.remove(it.id)
                staging.clear(it.id)
                removePublishedState(it)
            }

            val id = UUID.randomUUID().toString()
            val request = InstallRequest(
                id = id,
                packageName = saved.packageName,
                displayName = saved.displayName,
                versionName = saved.versionName,
                versionCode = saved.versionCode,
                icon = saved.icon,
                artifacts = saved.artifacts.mapIndexed { index, artifact ->
                    InstallArtifact(
                        name = sessionName(index, artifact.fileName, "split"),
                        source = InstallSource.Local(artifact.uri),
                        size = artifact.size,
                    )
                },
                saveToDownloads = if (preferences.mode() == InstallerMode.THIRD_PARTY) {
                    preferences.saveToDownloads()
                } else {
                    false
                },
                sourceSavedPackageId = savedPackageId,
                marketSource = priorSource,
                apkSize = saved.size,
            )
            InstallTaskRecord(
                id = id,
                appId = appId ?: mutableStates.value[saved.packageName]?.appId ?: 0L,
                request = request,
                installAfterDownload = true,
                // 直接从「安装中」起步：本地包没有下载阶段，起手 QUEUED 会闪一下进度条
                phase = DownloadPhase.INSTALLING,
                savedPackageId = savedPackageId,
            ).also {
                tasks.put(it)
                publishState(it)
            }
        }
        launchTask(record, propagateLaunchFailure)
    }

    private suspend fun pauseTask(taskId: String) {
        activeJobs[taskId]?.cancel()
        installer.cancel(taskId)
        updateRecord(taskId) { record ->
            if (record.phase == DownloadPhase.AWAITING_USER_ACTION && record.savedPackageId != null) {
                record.copy(
                    phase = DownloadPhase.DOWNLOADED,
                    externalInstallerPackage = null,
                    errorMessage = "",
                )
            } else {
                record.copy(phase = DownloadPhase.PAUSED)
            }
        }
    }

    private suspend fun restoreTasks() {
        val records = tasks.all()
        if (records.isEmpty()) {
            staging.retainOnly(emptyList())
            reclaimSessions(emptyList())
            return
        }
        val installed = runCatching {
            packages.installedVersionCodes(records.map { it.request.packageName })
        }.getOrDefault(emptyMap())
        records.forEach { original ->
            val installedVersion = installed[original.request.packageName]
            // 目标版本已装上就清理残留：自更新安装会杀死进程，任务可能以 PAUSED/DOWNLOADED
            // 等任意相位持久化，仅清 INSTALLING/AWAITING 会把「已暂停」的僵尸任务留在下载页
            val goalReached = original.request.versionCode > 0L &&
                    installedVersion != null && installedVersion >= original.request.versionCode
            if (goalReached ||
                (original.phase in setOf(DownloadPhase.INSTALLING, DownloadPhase.AWAITING_USER_ACTION) &&
                    original.request.versionCode > 0L &&
                    installedVersion != null && installedVersion >= original.request.versionCode)
            ) {
                if (original.externalInstallerPackage != null && !original.request.saveToDownloads) {
                    original.savedPackageId?.let { savedId -> runCatching { packageStore.delete(savedId) } }
                }
                tasks.remove(original.id)
                return@forEach
            }
            var record = if (original.phase in setOf(DownloadPhase.QUEUED, DownloadPhase.DOWNLOADING)) {
                tasks.update(original.id) { it.copy(phase = DownloadPhase.PAUSED) } ?: original
            } else {
                original
            }
            // 长期无进展说明回执已丢；留着会拦下用户的所有后续操作
            if (record.phase in PendingResultPhases &&
                System.currentTimeMillis() - record.modifiedAt > STALE_INSTALL_AGE_MS
            ) {
                timeOutInstall(record.id, "安装结果未回传，请重试")
                record = tasks.get(record.id) ?: record
            }
            if (record.phase == DownloadPhase.DOWNLOADED &&
                (record.savedPackageId == null || packageStore.get(record.savedPackageId) == null)
            ) {
                record = tasks.update(record.id) {
                    it.copy(
                        phase = DownloadPhase.FAILED,
                        savedPackageId = null,
                        errorMessage = "Saved package is unavailable",
                    )
                } ?: record
            }
            publishState(record)
        }
        val surviving = tasks.all()
        staging.retainOnly(surviving.map { it.id })
        reclaimSessions(surviving)
        runCatching { packageStore.sweepOrphans() }
            .onFailure { error -> debugLog("InstallTask") { "Saved package sweep failed: $error" } }
    }

    private suspend fun reclaimSessions(records: List<InstallTaskRecord>) {
        val retained = records.asSequence()
            .filter { it.phase in PendingResultPhases }
            .mapNotNull { it.sessionId }
            .toSet()
        runCatching { installer.reclaimSessions(retained) }
            .onFailure { error -> debugLog("InstallTask") { "Session reclaim failed: $error" } }
    }

    private fun publishFailureAction(code: InstallFailureCode, message: String) {
        if (code == InstallFailureCode.CANCELLED) return
        val action = userAction(code, message)
        mutablePendingUserAction.update { current ->
            when {
                action == InstallUserAction.GrantUnknownSourcesPermission -> action
                current == InstallUserAction.GrantUnknownSourcesPermission -> current
                else -> action
            }
        }
    }

    private suspend fun recordFailure(
        taskId: String,
        code: InstallFailureCode,
        message: String,
        transform: (InstallTaskRecord) -> InstallTaskRecord,
    ) {
        publishFailureAction(code, message)
        runCatching { updateRecord(taskId, transform) }
            .onFailure { error -> debugLog("InstallTask") { "Unable to persist failure for $taskId: $error" } }
    }

    private fun userAction(code: InstallFailureCode, message: String): InstallUserAction =
        if (
            code == InstallFailureCode.UNKNOWN_SOURCES_PERMISSION ||
            message.contains("install unknown apps", ignoreCase = true)
        ) {
            InstallUserAction.GrantUnknownSourcesPermission
        } else {
            InstallUserAction.InstallationFailed(message.ifBlank { "Installation failed" })
        }

    private fun InstallTaskRecord.toDownloadState(): DownloadState = DownloadState(
        appId = appId,
        packageName = request.packageName,
        displayName = request.displayName,
        progress = progress,
        phase = phase,
        savedPackageId = savedPackageId,
        errorMessage = errorMessage,
        icon = request.icon,
        versionName = request.versionName,
        versionCode = request.versionCode,
        source = request.marketSource,
        changeLog = request.changeLog,
        apkSize = request.apkSize,
        installedVersionName = request.installedVersionName,
        installAfterDownload = installAfterDownload,
    )

    private fun DownloadMeta.toInstallRequest(
        id: String,
        saveToDownloads: Boolean,
        deltaUpdateEnabled: Boolean,
        saveUri: String? = null,
    ): InstallRequest {
        val resolvedParts = parts.takeIf { it.isNotEmpty() }
            ?: listOf(DownloadPart("base", "base", url, size))
        val baseApk = File(installedBaseApkPath)
        val canUseDelta = deltaUpdateEnabled &&
                resolvedParts.size == 1 &&
                baseApk.isFile &&
                baseApk.canRead()
        return InstallRequest(
            id = id,
            packageName = packageName,
            displayName = displayName,
            versionName = versionName,
            versionCode = versionCode,
            icon = icon,
            marketSource = source,
            changeLog = changeLog,
            apkSize = size,
            installedVersionName = installedVersionName,
            saveUri = saveUri,
            artifacts = resolvedParts.mapIndexed { index, part ->
                val fullSource = InstallSource.Remote(part.url, requestHeaders)
                val patch = part.patch
                InstallArtifact(
                    name = sessionName(index, part.name, part.type),
                    source = if (canUseDelta && patch != null) {
                        InstallSource.Delta(
                            full = fullSource,
                            patch = InstallSource.Remote(
                                patch.url,
                                patch.requestHeaders.ifEmpty { requestHeaders },
                            ),
                            patchSize = patch.size,
                            patchChecksum = patch.hash,
                            patchVersion = patch.version,
                            baseApkPath = installedBaseApkPath,
                            patchProtocol = patch.protocol,
                        )
                    } else {
                        fullSource
                    },
                    size = part.size,
                    checksum = part.hash,
                )
            },
            saveToDownloads = saveToDownloads,
        )
    }

    private fun sessionName(index: Int, name: String, type: String): String {
        if (index == 0 && (name.equals("base", true) || type.equals("base", true))) return "base.apk"
        val safe = listOf(name, type)
            .filter { it.isNotBlank() && !it.equals("base", true) }
            .joinToString("_")
            .ifBlank { "split_$index" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "${index}_$safe".let { if (it.endsWith(".apk", true)) it else "$it.apk" }
    }

    private val InstallTaskRecord.taskKey: DownloadTaskKey
        get() = DownloadTaskKey(request.packageName, request.versionCode)

    private companion object {
        val ActivePhases = setOf(
            DownloadPhase.QUEUED,
            DownloadPhase.DOWNLOADING,
            DownloadPhase.INSTALLING,
            DownloadPhase.AWAITING_USER_ACTION,
        )
        val ResumablePhases = setOf(DownloadPhase.PAUSED, DownloadPhase.FAILED)
        val PendingResultPhases = setOf(DownloadPhase.INSTALLING, DownloadPhase.AWAITING_USER_ACTION)
        val PrivilegedModes = setOf(InstallerMode.ROOT, InstallerMode.SHIZUKU)
        const val PRIVILEGED_CONFIRM_TIMEOUT_MS = 30_000L
        const val STANDARD_CONFIRM_TIMEOUT_MS = 180_000L
        const val INSTALL_CONFIRM_INTERVAL_MS = 400L
        const val MAX_CONCURRENT_DOWNLOADS = 3
        /** 同时下载任务数量的可选值；3 为默认（与历史硬编码值一致）。 */
        val CONCURRENT_DOWNLOAD_OPTIONS = listOf(1, 2, 3, 4, 6, 8)
        const val STALE_INSTALL_AGE_MS = 30 * 60 * 1000L
    }
}

/**
 * 容量可调的信号量：kotlinx 的 [Semaphore] permits 构造后不可变，无法响应设置项修改。
 * 调小 [capacity] 后新任务不再获取到槽，等在途任务 [release] 完自然收敛到新容量。
 */
private class MutablePermits(initial: Int) {
    private var capacity = initial.coerceAtLeast(1)
    private var available = capacity
    private val lock = Mutex()
    suspend fun acquire(refreshCapacity: suspend () -> Unit = {}) = lock.withLock {
        var polls = 0
        while (available <= 0) {
            lock.unlock()
            // 排队期间持续吸收设置项修改，避免「改了并发数但排队任务不动」
            if (polls % 20 == 0) runCatching { refreshCapacity() }
            polls++
            delay(50)
            lock.lock()
        }
        available--
    }
    suspend fun release() = lock.withLock {
        if (available < capacity) available++
    }

    suspend fun setCapacity(value: Int) = lock.withLock {
        val target = value.coerceAtLeast(1)
        val delta = target - capacity
        capacity = target
        // 扩容必须补发新增槽位：否则 available 停留在旧值，提高设置等于没改
        if (delta > 0) available += delta
        if (available > capacity) available = capacity
    }
}

/**
 * 相邻进度事件差分测速：字节增量 / 时间增量，指数平滑避免跳动；
 * 超过 [IDLE_RESET_NANOS] 无进度（暂停/恢复、任务重启）时归零重新起步。
 */
/**
 * 滑动窗口测速：窗口内（字节总增量 / 时间总增量）求平均，天然平滑、不受毫秒级回调
 * 间隔造成的瞬时尖峰影响；显示值每秒最多发布一次，UI 上的速度 1 秒刷新一次。
 */
private class SpeedMeter {
    /** (累计字节, 采样时间纳秒) 样本队列。 */
    private val samples = ArrayDeque<Pair<Long, Long>>()
    private var lastPublishedAtNanos = 0L
    private var lastPublishedBps = 0L

    @Synchronized
    fun onProgress(completed: Long, nowNanos: Long): Long {
        val last = samples.lastOrNull()
        // 完成字节取单调峰值：下层偶尔会把计数回退（降级为全量、写会话阶段重计），
        // 直接用原始值会算出远超实际带宽的假速度
        val monotonic = maxOf(completed, last?.first ?: 0L)
        // 样本间隔过小则丢弃，避免并发分片回调在毫秒级间隔里制造尖峰
        if (last == null || nowNanos - last.second >= MIN_SAMPLE_INTERVAL_NANOS) {
            samples.addLast(monotonic to nowNanos)
            val cutoff = nowNanos - WINDOW_NANOS
            while (samples.size > 2 && samples.first().second < cutoff) {
                samples.removeFirst()
            }
        }
        val first = samples.firstOrNull()
        val newest = samples.lastOrNull()
        // 窗口跨度不足 1 秒时不发布：刚起步那一两个样本间隔极短，算出来的瞬时值是几十倍的假峰值
        val windowBps = if (first != null && newest != null && newest.second - first.second >= MIN_WINDOW_NANOS) {
            val dt = newest.second - first.second
            val delta = newest.first - first.first
            if (dt > 0L && delta > 0L) delta * 1_000_000_000L / dt else 0L
        } else {
            lastPublishedBps
        }
        // 显示值每秒最多发布一次
        if (nowNanos - lastPublishedAtNanos >= PUBLISH_INTERVAL_NANOS) {
            lastPublishedAtNanos = nowNanos
            lastPublishedBps = windowBps
        }
        return lastPublishedBps
    }

    private companion object {
        /** 窗口长度 3 秒：太长会让显示速度滞后于真实速度。 */
        const val WINDOW_NANOS = 3_000_000_000L
        /** 样本最小间隔 200 毫秒。 */
        const val MIN_SAMPLE_INTERVAL_NANOS = 200_000_000L
        /** 计算速度所需的最小窗口跨度：不足 500 毫秒不发布，避免起步尖峰。 */
        const val MIN_WINDOW_NANOS = 500_000_000L
        /** 显示值发布节流：300 毫秒，降低「速度显示慢半拍」观感。 */
        const val PUBLISH_INTERVAL_NANOS = 300_000_000L
    }
}