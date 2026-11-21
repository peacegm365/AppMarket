package com.app.market.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.app.market.domain.repository.DownloadRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 处理进度通知上的用户动作：暂停（保留任务可续传）与取消（移除任务与暂存）。 */
class InstallNotificationActionReceiver : BroadcastReceiver(), KoinComponent {
    private val downloads: DownloadRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)?.takeIf { it.isNotBlank() } ?: return
        when (intent.action) {
            ACTION_PAUSE -> downloads.cancel(packageName)
            ACTION_CANCEL -> downloads.clear(packageName)
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.app.market.action.NOTIFICATION_PAUSE_DOWNLOAD"
const val ACTION_CANCEL = "com.app.market.action.NOTIFICATION_CANCEL_DOWNLOAD"
        const val EXTRA_PACKAGE_NAME = "package_name"
    }
}