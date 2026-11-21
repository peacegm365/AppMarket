package com.app.market.domain.model.installer

import com.app.market.domain.model.market.AppSource
data class SavedPackage(
    val id: String,
    val fileName: String,
    val packageName: String,
    val displayName: String,
    val versionName: String,
    val versionCode: Long,
    val size: Long,
    val modifiedAt: Long,
    val artifacts: List<SavedPackageArtifact>,
    val icon: String = "",
    // 下载时的来源：重装保存的安装包时要按它写回 installer（Google Play 分发的应用会校验）
    val source: AppSource? = null,
)

data class SavedPackageArtifact(
    val uri: String,
    val fileName: String,
    val size: Long,
)
