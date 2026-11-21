package com.app.market.domain.model.market

/** LSPosed 模块的一条发布记录（对应仓库的一个 release）。 */
data class ModuleRelease(
    val name: String,
    val tagName: String,
    val versionCode: Long,
    val publishedAt: Long,
    val changeLog: String,
    val assets: List<ModuleAsset> = emptyList(),
)