package com.app.market.platform

interface UiPlatform {
    val packageInstallationSupported: Boolean
    fun showToast(message: String)
    fun openAppSettings(): Boolean
    fun openUnknownSourcesSettings(): Boolean
    fun openInOtherAppStore(packageName: String): Boolean = false
    fun requestInstalledAppsPermission(onResult: (Boolean) -> Unit): Boolean
    fun requestPostNotificationsPermission(onResult: () -> Unit): Boolean
    suspend fun saveImageToPictures(url: String, fileName: String): ImageSaveResult

    /** 分享一段文本：移动端拉起系统分享面板，桌面端复制到剪贴板。 */
    fun shareText(text: String): ShareResult = ShareResult.Unsupported

    /** 把文本导出成日志文件（Android 写进 Download/AppMarket/logs），返回路径；不支持时返回 null。 */
    suspend fun exportLogFile(fileName: String, content: String): String? = null

    /** 读取剪贴板文本；剪贴板自上次读取后没有变化时返回 null，避免重复读取（Android 每次读取会提示用户）。 */
    suspend fun readNewClipboardText(): String? = null
    /** 强制读取当前剪贴板文本（用户主动点「粘贴」时用）；无内容返回 null。 */
    suspend fun readClipboardText(): String? = null

    /** 弹系统文档创建器选保存位置，返回目标文档 uri；用户取消或不支持返回 null。 */
    suspend fun pickSaveUri(fileName: String, mimeType: String): String? = null

    /** 弹系统文件管理选目录，返回文件系统路径；用户取消或不支持返回 null。 */
    suspend fun pickDirectoryPath(): String? = null
}