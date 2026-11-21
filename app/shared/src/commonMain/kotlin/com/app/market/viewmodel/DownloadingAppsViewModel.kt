package com.app.market.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.market.domain.model.download.DownloadPhase
import com.app.market.domain.model.download.DownloadState
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.repository.DownloadRepository
import com.app.market.domain.repository.MarketSourceRepository
import com.app.market.domain.repository.PackageRepository
import com.app.market.domain.repository.UpdatePreferencesRepository
import com.app.market.platform.UiPlatform
import com.app.market.resources.Res
import com.app.market.resources.download_failed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class DownloadingAppsViewModel(
    private val sources: MarketSourceRepository,
    private val prefs: UpdatePreferencesRepository,
    private val downloads: DownloadRepository,
    private val packages: PackageRepository,
    private val uiPlatform: UiPlatform,
) : ViewModel() {
    val downloadStates: StateFlow<Map<String, DownloadState>> = downloads.states
    /** 本机已装版本（供下载页区分「更新」蓝 / 「安装」绿控件配色）。 */
    suspend fun installedVersions(packageNames: Collection<String>): Map<String, Long> =
        packages.installedVersionCodes(packageNames)
    private val pendingResumes = mutableSetOf<String>()

    /** 任务记录里的下载地址可能已过期，恢复前重新拉取元数据；同版本任务会复用暂存数据续传。 */
    fun resume(state: DownloadState) {
        if (!pendingResumes.add(state.packageName)) return
        viewModelScope.launch {
            try {
                val source = state.source
                    ?: prefs.searchSources.value.firstOrNull()
                    ?: AppSource.Default.first()
                runCatchingCancellable {
                    sources.downloadMeta(source, state.toAppInfo(source), state.displayName)
                }
                    // 保留原任务意图：「安装」两段式只续传下载，不改变安装策略
                    .onSuccess { downloads.start(it, installAfterDownload = state.installAfterDownload) }
                    .onFailure { error ->
                        uiPlatform.showToast(error.message ?: getString(Res.string.download_failed))
                    }
            } finally {
                pendingResumes.remove(state.packageName)
            }
        }
    }

    fun installDownloaded(packageName: String) = downloads.install(packageName)
    fun pause(packageName: String) = downloads.cancel(packageName)
    /** 取消下载：移除任务记录与暂存，列表里那一行直接消失。 */
    fun remove(packageName: String) = downloads.clear(packageName)

    /** 暂停全部：将排队中/下载中的任务全部转为已暂停。 */
    fun pauseAll() {
        downloadStates.value.values
            .filter { it.phase in setOf(DownloadPhase.QUEUED, DownloadPhase.DOWNLOADING) }
            .forEach { downloads.cancel(it.packageName) }
    }

    /** 开始全部：恢复所有已暂停的任务。 */
    fun resumeAll() {
        downloadStates.value.values
            .filter { it.phase == DownloadPhase.PAUSED }
            .forEach { resume(it) }
    }

    /** 取消全部：移除所有未完成的下载任务（UI 侧需二次确认）。 */
    fun removeAll() {
        downloadStates.value.values
            .filter {
                it.phase in setOf(
                    DownloadPhase.QUEUED,
                    DownloadPhase.DOWNLOADING,
                    DownloadPhase.PAUSED,
                    DownloadPhase.FAILED,
                )
            }
            .forEach { downloads.clear(it.packageName) }
    }

    private fun DownloadState.toAppInfo(source: AppSource) = MarketAppInfo(
        appId = appId,
        packageName = packageName,
        displayName = displayName,
        publisherName = "",
        // 版本信息必须带上：写死 0 会让「停止→继续」拿 versionCode=0 去拉下载地址，下载到错版本/直接失败
        versionName = versionName,
        versionCode = versionCode,
        icon = icon,
        apkSize = apkSize,
        ratingScore = 0.0,
        changeLog = changeLog,
        source = source,
    )
}
