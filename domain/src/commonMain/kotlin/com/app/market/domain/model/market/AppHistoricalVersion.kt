package com.app.market.domain.model.market

/** 历史版本目录条目（Exodus 版本记录）：版本名 / 版本号 / 日期。 */
data class AppHistoricalVersion(
    val versionCode: Long,
    val versionName: String,
    val date: String,
    val displayVersionCode: Long = versionCode,
)