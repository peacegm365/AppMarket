package com.app.market.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.market.domain.model.update.UpdateHistoryEntry
import com.app.market.domain.repository.MarketSourceRepository
import com.app.market.domain.repository.UpdateHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UpdateHistoryViewModel(
    private val store: UpdateHistoryRepository,
    private val sources: MarketSourceRepository,
) : ViewModel() {
    /** packageName → 本地真实应用名（显示层按「获取真实应用名」开关即时选用）。 */
    private val labels = MutableStateFlow<Map<String, String>>(emptyMap())
    val entries: StateFlow<List<UpdateHistoryEntry>> =
        combine(store.entries, labels) { list, resolved ->
            list.map { entry ->
                val label = resolved[entry.packageName] ?: return@map entry
                entry.copy(localLabel = label.ifBlank { entry.localLabel })
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    init {
        viewModelScope.launch {
            store.entries.collect { list ->
                list.map { it.packageName }
                    .distinct()
                    .filter { it !in labels.value }
                    .forEach { pkg ->
                        launch {
                            val name = runCatching { sources.installedLabel(pkg) }.getOrNull().orEmpty()
                            if (name.isNotBlank()) labels.update { it + (pkg to name) }
                        }
                    }
            }
        }
    }
    fun clear() {
        viewModelScope.launch { store.clear() }
    }
}