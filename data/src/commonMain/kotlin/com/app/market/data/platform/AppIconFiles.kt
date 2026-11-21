package com.app.market.data.platform

/**
 * 已安装应用的本地图标（`file://` URL）。
 *
 * 用途：LSPosed 模块仓库/更新列表里，远端接口不给图标，但模块本身已装在设备上，
 * 直接读本地 `PackageManager` 的图标比远端猜一个准得多。
 *
 * @return 本地 PNG 的 `file://` 路径；未安装或平台不支持（桌面端）时返回 null。
 */
expect suspend fun installedAppIconUrl(packageName: String): String?

/**
 *已安装应用的用户可见名称（`PackageManager.getApplicationLabel`）。
 *
 * 安装历史里的老记录常常只留下包名，用它补回应用名（和 [installedAppIconUrl] 同一套思路：
 * 直接问系统，比猜远端数据可靠）。
 */
expect suspend fun installedAppLabel(packageName: String): String?
