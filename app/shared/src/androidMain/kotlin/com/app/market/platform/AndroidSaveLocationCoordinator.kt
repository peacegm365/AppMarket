package com.app.market.platform

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.result.ActivityResultLauncher

/**
 * 「另存」系统文档创建器（ACTION_CREATE_DOCUMENT）桥：
 * MainActivity 注册 launcher，业务侧挂起等待用户选好的保存位置 uri。
 * 用户取消或未选时回调 null。
 */
class AndroidSaveLocationCoordinator {
    private var launcher: ActivityResultLauncher<Intent>? = null
    private var callback: ((String?) -> Unit)? = null

    fun register(launcher: ActivityResultLauncher<Intent>) {
        this.launcher = launcher
    }

    fun unregister(launcher: ActivityResultLauncher<Intent>) {
        if (this.launcher === launcher) {
            this.launcher = null
            callback = null
        }
    }

    /** 弹系统文档创建器选保存位置；返回目标 uri 字符串，用户取消/未注册返回 null。 */
    fun pick(mimeType: String, fileName: String, onResult: (String?) -> Unit): Boolean {
        val launcher = launcher ?: return false
        callback = onResult
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        return runCatching { launcher.launch(intent) }
            .onFailure { callback = null }
            .isSuccess
    }

    /** 弹系统文件管理选目录；回调文件系统路径（tree uri 反解），用户取消/未注册/反解失败回调 null。 */
    fun pickDirectory(onResult: (String?) -> Unit): Boolean {
        val launcher = launcher ?: return false
        callback = { uriString ->
            onResult(uriString?.let { treeUriToPath(it) })
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        return runCatching { launcher.launch(intent) }
            .onFailure { callback = null }
            .isSuccess
    }

    /** externalstorage.documents 的 tree uri → 文件系统路径（primary 卷精确，其他卷按 /storage/<vol> 近似）。 */
    private fun treeUriToPath(uriString: String): String? = runCatching {
        val uri = Uri.parse(uriString)
        if (uri.authority != "com.android.externalstorage.documents") return null
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":", limit = 2)
        val relative = parts.getOrElse(1) { "" }
        val base = when (parts[0]) {
            "primary" -> Environment.getExternalStorageDirectory().absolutePath
            else -> "/storage/${parts[0]}"
        }
        if (relative.isEmpty()) base else "$base/$relative"
    }.getOrNull()

    fun dispatchResult(uriString: String?) {
        val pending = callback
        callback = null
        pending?.invoke(uriString)
    }
}