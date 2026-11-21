package com.app.market.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.market.domain.model.update.IgnoredUpdate
import com.app.market.domain.repository.UpdatePreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class IgnoredAppsUiState(
    val permanent: List<IgnoredUpdate> = emptyList(),
    val once: List<IgnoredUpdate> = emptyList(),
)

class IgnoredAppsViewModel(
    private val prefs: UpdatePreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<IgnoredAppsUiState> =
        combine(prefs.permanentIgnores, prefs.onceIgnores) { permanent, once ->
            IgnoredAppsUiState(permanent = permanent, once = once)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IgnoredAppsUiState())

    // ===== 多选模式（长按进入，批量恢复） =====
    private val _multiSelect = MutableStateFlow(false)
    val multiSelect: StateFlow<Boolean> = _multiSelect.asStateFlow()
    private val _multiSelected = MutableStateFlow<Set<String>>(emptySet())
    val multiSelected: StateFlow<Set<String>> = _multiSelected.asStateFlow()

    fun enterMultiSelect(packageName: String) {
        _multiSelect.value = true
        _multiSelected.value = setOf(packageName)
    }

    fun toggleMultiSelected(packageName: String) {
        _multiSelected.value = _multiSelected.value
            .let { current -> if (packageName in current) current - packageName else current + packageName }
    }

    /** 全选/取消全选切换（永久 + 一次性合并计算）。 */
    fun toggleMultiSelectAll() {
        val all = uiState.value.let { it.permanent + it.once }.map { it.packageName }
        _multiSelected.value = if (all.isNotEmpty() && _multiSelected.value.containsAll(all)) {
            emptySet()
        } else {
            all.toSet()
        }
    }

    fun exitMultiSelect() {
        _multiSelect.value = false
        _multiSelected.value = emptySet()
    }

    /** 批量恢复选中的忽略项（两类忽略各走各的恢复通道）。 */
    fun restoreSelected() {
        val selected = _multiSelected.value
        val state = uiState.value
        state.permanent.filter { it.packageName in selected }.forEach { removePermanent(it.packageName) }
        state.once.filter { it.packageName in selected }.forEach { removeOnce(it.packageName) }
        exitMultiSelect()
    }

    fun removePermanent(packageName: String) {
        viewModelScope.launch { prefs.removePermanent(packageName) }
    }

    fun removeOnce(packageName: String) {
        viewModelScope.launch { prefs.removeOnce(packageName) }
    }
}
