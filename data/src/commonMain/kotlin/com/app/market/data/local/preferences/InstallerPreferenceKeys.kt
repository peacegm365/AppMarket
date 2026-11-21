package com.app.market.data.local.preferences

import com.app.market.data.local.BooleanPreferenceKey
import com.app.market.data.local.StringPreferenceKey

private const val Namespace = "installer_prefs"

internal object InstallerPreferenceKeys {
    val Mode = StringPreferenceKey(Namespace, "mode")
    val ThirdPartyInstallerPackage = StringPreferenceKey(Namespace, "custom_pkg")
    /** Root 提权命令（默认 su，可改成 ksud/apatch 等）。 */
    val PrivilegeCommand = StringPreferenceKey(Namespace, "privilege_command")
    /** 安装完成后自动删除安装包（默认开）；安装包默认存放在 Download/AppMarket。 */
    val DeletePackageAfterInstall = BooleanPreferenceKey(Namespace, "delete_package_after_install", default = true)
    val SaveToDownloads = BooleanPreferenceKey(Namespace, "save_to_downloads", default = false)
    val SaveToDownloadsMigrated = BooleanPreferenceKey(Namespace, "save_to_downloads_migrated")
    /** 下载完成后另存安装包副本到指定文件夹（与「保存到 Download」相互独立）。 */
    val ApkCopyEnabled = BooleanPreferenceKey(Namespace, "apk_copy_enabled", default = false)
    /** 另存目标文件夹（绝对路径）；空值回退 Download/AppMarketPackages。 */
    val ApkCopyDirectory = StringPreferenceKey(Namespace, "apk_copy_directory")
    val PackageSaveDirectory = StringPreferenceKey(Namespace, "package_save_directory")
    val LegacyDeleteAfterInstall = BooleanPreferenceKey(Namespace, "delete_after_install")
    val UserActionNotRequired = BooleanPreferenceKey(Namespace, "user_action_not_required", default = false)
    val DeltaUpdate = BooleanPreferenceKey(Namespace, "delta_update", default = true)
    val DeltaFallbackNotice = BooleanPreferenceKey(Namespace, "delta_fallback_notice", default = true)
    val MultiThreadDownload = BooleanPreferenceKey(Namespace, "multi_thread_download", default = true)
    val DownloadConnections = StringPreferenceKey(Namespace, "download_connections")
    /** ab-dm 的动态分段：某片下完后把剩余最大片劈开给空闲连接。 */
    val DynamicPartCreation = BooleanPreferenceKey(Namespace, "dynamic_part_creation", default = true)
    /** 最小分片大小（字节），低于它不再劈分。 */
    val MinPartSize = StringPreferenceKey(Namespace, "min_part_size")
    /** 单个分片的最大重试次数。 */
    val PartMaxRetries = StringPreferenceKey(Namespace, "part_max_retries")
    val ConcurrentDownloads = StringPreferenceKey(Namespace, "concurrent_downloads")
    val XiaomiIslandOptimization = BooleanPreferenceKey(Namespace, "xiaomi_island_optimization")

    /** 下载通知样式四档：off（关闭）/ normal（普通）/ live（实时通知）/ island（小米超级岛）。 */
    val DownloadNotificationStyle = StringPreferenceKey(Namespace, "download_notification_style")
    val AutoCheckUpdates = BooleanPreferenceKey(Namespace, "auto_check_updates", default = true)
    /** 安装来源三档：default（按来源自动）/ google_play / custom；仅 Root/Shizuku 生效。 */
    val InstallerSourceMode = StringPreferenceKey(Namespace, "installer_source_mode")
    val CustomInstallerSource = StringPreferenceKey(Namespace, "custom_installer_source")
    /** 安装后手动触发 dex2oat（pm compile）；仅 Root/Shizuku 生效。 */
    val ManualDexopt = BooleanPreferenceKey(Namespace, "manual_dexopt", default = false)
    val DexoptMode = StringPreferenceKey(Namespace, "dexopt_mode")
    val DexoptForce = BooleanPreferenceKey(Namespace, "dexopt_force", default = false)
}