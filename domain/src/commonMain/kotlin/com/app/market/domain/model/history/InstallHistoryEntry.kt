package com.app.market.domain.model.history

import com.app.market.domain.model.market.AppSource

/** 历史记录里的操作类型。 */
enum class InstallHistoryAction { DOWNLOAD, INSTALL }

/**
 * 一条「下载 / 安装」记录。
 *
 * @param action 下载完成或安装结束。
 * @param success 该步骤是否成功（下载失败 / 安装失败都会留痕）。
 * @param detail 失败原因等补充说明，可空。
 */
data class InstallHistoryEntry(
    val packageName: String,
    val displayName: String,
    /** 本地真实应用名（显示层按「获取真实应用名」开关即时选用，不持久化）。 */
    val localLabel: String = "",
    val versionName: String,
    /** 安装包体积（字节）。老记录没有这个字段时为 0，界面就直接不显示。 */
    val apkSize: Long = 0L,
    val icon: String,
    val action: InstallHistoryAction,
    val success: Boolean,
    val timestamp: Long,
    val detail: String = "",
val source: AppSource? = null,
    /** 来源里的应用 id（详情页需要）；历史数据里没有时为 0。 */
    val appId: Long = 0L,
    /** 安装方式（STANDARD / ROOT / SHIZUKU / DHIZUKU / SYSTEM / THIRD_PARTY），旧记录为空。 */
    val installerMode: String = "",
)