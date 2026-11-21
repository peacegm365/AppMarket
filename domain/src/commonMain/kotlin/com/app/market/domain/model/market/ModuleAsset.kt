package com.app.market.domain.model.market

/**
 * LSPosed 模块仓库一个 Release 里的附件（同一版本可能同时有国内版/国际版、arm64/arm 等多份 APK）。
 */
data class ModuleAsset(
    val name: String,
    val url: String,
    val size: Long,
)
