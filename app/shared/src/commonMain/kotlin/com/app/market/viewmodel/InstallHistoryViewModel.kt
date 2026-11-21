package com.app.market.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.market.domain.model.history.InstallHistoryEntry
import com.app.market.domain.repository.InstallHistoryRepository
import com.app.market.domain.repository.MarketSourceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 安装历史列表。
 *
 * 老记录里 `displayName` / `icon` 可能是空的（下载任务没带这些信息，或应用已卸/改名），
 * 直接渲染就会看到「包名 + 首字母头像」。这里用本机已安装信息兜底补齐：
 * 名称走 PackageManager，图标走 `installedAppIconUrl` 的本地 PNG 缓存。
 */
class InstallHistoryViewModel(
    private val store: InstallHistoryRepository,
    private val sources: MarketSourceRepository,
) : ViewModel() {

    /** packageName → (本机应用名, 本机图标 file:// URL) */
    private val fixes = MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap())
    val entries: StateFlow<List<InstallHistoryEntry>> =
        combine(store.entries, fixes) { list, resolved ->
            list.map { entry ->
                val fix = resolved[entry.packageName] ?: return@map entry
                val nameNeedsFix = entry.displayName.isBlank() || entry.displayName == entry.packageName
                entry.copy(
                    displayName = if (nameNeedsFix && fix.first.isNotBlank()) fix.first else entry.displayName,
                    localLabel = fix.first.ifBlank { entry.localLabel },
                    icon = entry.icon.ifBlank { fix.second },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    init {
        viewModelScope.launch {
            store.entries.collect { list ->
                list.map { it.packageName }
                    .distinct()
                    .filter { it !in fixes.value }
                    .forEach { pkg ->
                        launch {
                            val name = runCatching { sources.installedLabel(pkg) }.getOrNull().orEmpty()
                            val icon = runCatching { sources.installedIcon(pkg) }.getOrNull().orEmpty()
                            if (name.isNotBlank() || icon.isNotBlank()) {
                                fixes.update { it + (pkg to (name to icon)) }
                            }
                        }
                    }
            }
        }
    }

    fun clear() {
        viewModelScope.launch { store.clear() }
    }
}