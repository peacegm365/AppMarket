package com.app.market.data.platform

/** 桌面端没有本地安装包，永远给不出本地图标。 */
actual suspend fun installedAppIconUrl(packageName: String): String? = null
/** 桌面端没有本地安装包，拿不到应用名。 */
actual suspend fun installedAppLabel(packageName: String): String? = null
