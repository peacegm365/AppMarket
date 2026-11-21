package com.app.market.domain.repository

import com.app.market.domain.model.installer.InstallerMode

/** Persisted installer settings and availability of platform-dependent installer options. */
interface InstallerPreferencesRepository {
    suspend fun mode(): InstallerMode
    suspend fun setMode(mode: InstallerMode)
    suspend fun thirdPartyInstallerPackage(): String
    suspend fun setThirdPartyInstallerPackage(packageName: String)
    /** Root 提权命令（默认 su）。 */
    suspend fun privilegeCommand(): String = "su"

    /** 探测当前可用特权（root / shizuku / dhizuku / 系统权限）。 */
    suspend fun installerPrivileges(): com.app.market.domain.model.installer.InstallerPrivileges =
        com.app.market.domain.model.installer.InstallerPrivileges()

    suspend fun setPrivilegeCommand(command: String) = Unit

    /** 安装完成后自动删除安装包（默认开）。 */
    suspend fun deletePackageAfterInstall(): Boolean = true

    suspend fun setDeletePackageAfterInstall(value: Boolean) = Unit
    suspend fun saveToDownloads(): Boolean
    suspend fun setSaveToDownloads(enabled: Boolean)
    /** 下载完成后另存安装包副本到指定文件夹（默认关；与「保存到 Download」独立）。 */
    suspend fun apkCopyEnabled(): Boolean = false
    suspend fun setApkCopyEnabled(value: Boolean) = Unit
    /** 另存目标文件夹（绝对路径）；空值回退 Download/AppMarketPackages。 */
    suspend fun apkCopyDirectory(): String = ""
    suspend fun setApkCopyDirectory(value: String) = Unit
    /** 安装包默认保存路径（下载完成的安装包与 log 日志的落位目录）。 */
    suspend fun packageSaveDirectory(): String = DEFAULT_PACKAGE_SAVE_DIRECTORY
    suspend fun setPackageSaveDirectory(value: String) = Unit

    companion object {
        const val DEFAULT_PACKAGE_SAVE_DIRECTORY = "/storage/emulated/0/Download/AppMarket/"
    }
    fun userActionNotRequiredConfigurable(): Boolean
    suspend fun userActionNotRequiredEnabled(): Boolean
    suspend fun setUserActionNotRequiredEnabled(enabled: Boolean)
    fun deltaUpdateSupported(): Boolean
    suspend fun deltaUpdateEnabled(): Boolean
    suspend fun setDeltaUpdateEnabled(enabled: Boolean)
    /** 大体积安装包按字节范围分片并发下载；关闭或单连接时按单连接下载。 */
    fun multiThreadDownloadSupported(): Boolean
    suspend fun multiThreadDownloadEnabled(): Boolean
    suspend fun setMultiThreadDownloadEnabled(enabled: Boolean)
    /** 分片并发连接数，取值 [1, 32]，1 表示单连接下载。 */
    suspend fun downloadConnections(): Int
    suspend fun setDownloadConnections(connections: Int)

    /** 动态分段：分片完成后把最大的剩余分片对半劈开，交给空闲连接（AB Download Manager 同名选项）。 */
    suspend fun dynamicPartCreationEnabled(): Boolean
    suspend fun setDynamicPartCreationEnabled(enabled: Boolean)

    /** 最小分片大小（字节）：低于它就停止继续劈分。 */
    suspend fun minPartSizeBytes(): Long
    suspend fun setMinPartSizeBytes(size: Long)

    /** 单个分片的最大重试次数。 */
    suspend fun partMaxRetries(): Int
    suspend fun setPartMaxRetries(count: Int)

    /** 同时下载的任务数量上限；超出的任务排队等待。 */
    suspend fun concurrentDownloads(): Int
    suspend fun setConcurrentDownloads(count: Int)

    /** 增量回退全量时弹提示，用于暴露尚未实现的补丁格式分支。 */
    suspend fun deltaFallbackNoticeEnabled(): Boolean
    suspend fun setDeltaFallbackNoticeEnabled(enabled: Boolean)

    /** 设备支持焦点通知（OS2）或超级岛（OS3），控制开关显隐。 */
    fun focusNotificationSupported(): Boolean

    /** 设备为超级岛（OS3 带岛胶囊）模式，控制开关的标题措辞。 */
    fun xiaomiIslandSupported(): Boolean
    suspend fun xiaomiIslandOptimizationEnabled(): Boolean
    suspend fun setXiaomiIslandOptimizationEnabled(enabled: Boolean)

    /** 下载通知样式：off / normal / live / island。 */
    suspend fun downloadNotificationStyle(): String
    suspend fun setDownloadNotificationStyle(style: String)

    /** 每次启动时自动检查新版本（关于页「检查更新」下方开关）。 */
    suspend fun autoCheckUpdatesEnabled(): Boolean
    suspend fun setAutoCheckUpdatesEnabled(enabled: Boolean)

    /** 安装来源（InstallerX Revived 同款 setInstallerPackageName）：default / google_play / custom。 */
    suspend fun installerSourceMode(): String
    suspend fun setInstallerSourceMode(mode: String)
    suspend fun customInstallerSource(): String
    suspend fun setCustomInstallerSource(packageName: String)
    /** 安装完成后手动触发 dex2oat（pm compile）；仅 Root/Shizuku 模式生效。 */
    suspend fun manualDexoptEnabled(): Boolean
    suspend fun setManualDexoptEnabled(enabled: Boolean)
    suspend fun dexoptMode(): String
    suspend fun setDexoptMode(mode: String)
    suspend fun dexoptForceEnabled(): Boolean
    suspend fun setDexoptForceEnabled(enabled: Boolean)
}