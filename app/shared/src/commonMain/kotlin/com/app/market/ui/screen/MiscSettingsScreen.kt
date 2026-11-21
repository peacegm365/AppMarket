package com.app.market.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.market.resources.Res
import com.app.market.resources.clipboard_link_detect
import com.app.market.resources.clipboard_link_detect_summary
import com.app.market.resources.strip_name_subtitle
import com.app.market.resources.strip_name_subtitle_summary
import com.app.market.ui.component.MarketScaffold
import com.app.market.ui.component.AppSpinnerPreference
import com.app.market.ui.component.PageVerticalPadding
import com.app.market.viewmodel.UpdatesViewModel
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 杂项二级页 —— 「通用」分类里挪出来的开关：
 * 名称精简 / 剪贴板识别 / 日志（精确搜索在「搜索」分类）。
 */
@Composable
fun MiscSettingsScreen(
    onBack: () -> Unit,
    updatesViewModel: UpdatesViewModel,
) {
    val stripAppNameSubtitle by updatesViewModel.stripAppNameSubtitle.collectAsStateWithLifecycle()
    val detectClipboardLinks by updatesViewModel.detectClipboardLinks.collectAsStateWithLifecycle()
    val pageCacheEnabled by updatesViewModel.pageCacheEnabled.collectAsStateWithLifecycle()
    val logEnabled by updatesViewModel.logEnabled.collectAsStateWithLifecycle()
    val logMaxSize by updatesViewModel.logMaxSize.collectAsStateWithLifecycle()
    // 日志限制档位：字节数 → 显示文案（0 = 不限制）
    val logLimitValues = listOf(256 * 1024, 1024 * 1024, 4 * 1024 * 1024, 16 * 1024 * 1024, 0)
    val logLimitLabels = listOf("256 KB", "1 MB", "4 MB", "16 MB", "不限制")

    MarketScaffold(title = "杂项", onBack = onBack) { innerPadding, backdropModifier, scrollBehavior ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(backdropModifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = PageVerticalPadding,
                end = PageVerticalPadding,
                top = innerPadding.calculateTopPadding() + PageVerticalPadding,
                bottom = innerPadding.calculateBottomPadding() + PageVerticalPadding,
            ),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = stringResource(Res.string.strip_name_subtitle),
                        summary = stringResource(Res.string.strip_name_subtitle_summary),
                        checked = stripAppNameSubtitle,
                        onCheckedChange = updatesViewModel::setStripAppNameSubtitle,
                    )
                    SwitchPreference(
                        title = stringResource(Res.string.clipboard_link_detect),
                        summary = stringResource(Res.string.clipboard_link_detect_summary),
                        checked = detectClipboardLinks,
                        onCheckedChange = updatesViewModel::setDetectClipboardLinks,
                    )
                    SwitchPreference(
                        title = "页面缓存",
                        summary = "先显示缓存，再联网刷新；关闭后不读取或保存页面缓存",
                        checked = pageCacheEnabled,
                        onCheckedChange = updatesViewModel::setPageCacheEnabled,
                    )
                    ArrowPreference(
                        title = "清除缓存",
                        summary = "清除页面、更新列表及翻译缓存",
                        onClick = updatesViewModel::clearPageCaches,
                    )
                    SwitchPreference(
                        title = "日志",
                        summary = "关闭后不再生成日志，也不在 Download 目录创建文件",
                        checked = logEnabled,
                        onCheckedChange = updatesViewModel::setLogEnabled,
                    )
                    AppSpinnerPreference(
                        items = logLimitLabels,
                        selectedIndex = logLimitValues.indexOf(logMaxSize).let { if (it >= 0) it else 1 },
                        title = "日志限制",
                        summary = "超过上限时截断旧日志，只保留最新内容",
                        onSelectedIndexChange = { updatesViewModel.setLogMaxSize(logLimitValues[it]) },
                        enabled = logEnabled,
                    )
                }
            }
        }
    }
}