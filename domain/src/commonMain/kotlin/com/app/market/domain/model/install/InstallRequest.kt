package com.app.market.domain.model.install

import com.app.market.domain.model.market.AppSource

data class InstallRequest(
    val id: String,
    val packageName: String,
    val displayName: String,
    val versionName: String,
    val versionCode: Long,
    val artifacts: List<InstallArtifact>,
    val saveToDownloads: Boolean = true,
    val sourceSavedPackageId: String? = null,
    val icon: String = "",
    val marketSource: AppSource? = null,
    val changeLog: String = "",
    val apkSize: Long = 0L,
    val installedVersionName: String = "",
    /** 「另存」目标文档 uri（ACTION_CREATE_DOCUMENT 返回）；非空时下载完成后把安装包写入该位置。 */
    val saveUri: String? = null,
)