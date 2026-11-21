package com.app.market.domain.repository

import com.app.market.domain.model.history.InstallHistoryEntry
import kotlinx.coroutines.flow.StateFlow

/** 下载 / 安装历史。两种操作共用一张表，靠 [InstallHistoryEntry.action] 区分。 */
interface InstallHistoryRepository {
    val entries: StateFlow<List<InstallHistoryEntry>>

    suspend fun record(entry: InstallHistoryEntry)

    suspend fun clear()
}
