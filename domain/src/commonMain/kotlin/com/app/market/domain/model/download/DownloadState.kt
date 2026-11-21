package com.app.market.domain.model.download

import com.app.market.domain.model.market.AppSource

/** 百分比文本（一位小数，如 45.3%）；progress 取值 0f..100f。 */
fun formatPercent(progress: Float): String = "${progress.toInt().coerceIn(0, 100)}%"

data class DownloadState(
    val appId: Long,
    val packageName: String,
    val displayName: String,
    /** 下载进度 0f..100f（精确到小数）；null 表示总量未定。 */
    val progress: Float?,
    /** 实时下载速度（字节/秒），仅在 [DownloadPhase.DOWNLOADING] 时有效。 */
    val speedBps: Long = 0L,
    val phase: DownloadPhase = DownloadPhase.QUEUED,
    val savedPackageId: String? = null,
    val errorMessage: String = "",
    val icon: String = "",
    val versionName: String = "",
    val versionCode: Long = 0L,
    val source: AppSource? = null,
    val changeLog: String = "",
    val apkSize: Long = 0L,
    val installedVersionName: String = "",
    /** 下载完成后是否自动安装；「安装」两段式（先下载后安装）为 false。 */
    val installAfterDownload: Boolean = true,
) {
    val isComplete: Boolean get() = phase == DownloadPhase.DOWNLOADED && savedPackageId != null
    val isPaused: Boolean get() = phase == DownloadPhase.PAUSED
}

enum class DownloadPhase {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    INSTALLING,
    AWAITING_USER_ACTION,
    DOWNLOADED,
    FAILED,
}
