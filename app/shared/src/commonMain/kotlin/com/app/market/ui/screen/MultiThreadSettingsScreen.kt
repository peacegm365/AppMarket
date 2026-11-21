package com.app.market.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.market.resources.Res
import com.app.market.resources.download_connections
import com.app.market.resources.download_connections_summary
import com.app.market.resources.download_multi_thread
import com.app.market.resources.download_multi_thread_summary
import com.app.market.ui.component.AppSpinnerPreference
import com.app.market.ui.component.MarketScaffold
import com.app.market.ui.component.PageVerticalPadding
import com.app.market.viewmodel.InstallerSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 多线程下载二级页：单文件分片并发与分段策略（与「安装方式」「安装历史」同级入口）。 */
@Composable
fun MultiThreadSettingsScreen(
    viewModel: InstallerSettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MarketScaffold(
        title = stringResource(Res.string.download_multi_thread),
        onBack = onBack,
    ) { innerPadding, backdropModifier, scrollBehavior ->
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(backdropModifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
                top = innerPadding.calculateTopPadding() + PageVerticalPadding,
                bottom = innerPadding.calculateBottomPadding() + PageVerticalPadding,
            ),
        ) {
            item(key = "multi-thread") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    if (state.multiThreadDownloadSupported) {
                        val downloadConnectionOptions = listOf(2, 4, 8, 16)
                        SwitchPreference(
                            title = stringResource(Res.string.download_multi_thread),
                            summary = stringResource(Res.string.download_multi_thread_summary),
                            checked = state.multiThreadDownloadEnabled,
                            onCheckedChange = viewModel::setMultiThreadDownloadEnabled,
                        )
                        // 挤压弹出动画：开启多线程时选项从开关下方压出，关闭压回
                        AnimatedVisibility(
                            visible = state.multiThreadDownloadEnabled,
                            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                            exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                AppSpinnerPreference(
                                    title = stringResource(Res.string.download_connections),
                                    summary = stringResource(Res.string.download_connections_summary),
                                    items = downloadConnectionOptions.map { it.toString() },
                                    selectedIndex = downloadConnectionOptions
                                        .indexOf(state.downloadConnections)
                                        .coerceAtLeast(0),
                                    onSelectedIndexChange = {
                                        viewModel.setDownloadConnections(downloadConnectionOptions[it])
                                    },
                                )
                                // 以下三项对应 AB Download Manager 的 dynamicPartCreationMode / minPartSize / PART_MAX_TRIES
                                SwitchPreference(
                                    title = "动态分段",
                                    summary = "分片下完后把最大的剩余分片对半劈开，交给空闲连接继续下载",
                                    checked = state.dynamicPartCreationEnabled,
                                    onCheckedChange = viewModel::setDynamicPartCreationEnabled,
                                )
                                val minPartSizeOptions = listOf(256L, 512L, 1024L, 2048L).map { it * 1024 }
                                AppSpinnerPreference(
                                    title = "最小分片大小",
                                    summary = "剩余量小于该值就不再继续劈分",
                                    items = minPartSizeOptions.map {
                                        if (it >= 1024L * 1024) "${it / (1024L * 1024)}MB" else "${it / 1024}KB"
                                    },
                                    selectedIndex = minPartSizeOptions
                                        .indexOf(state.minPartSizeBytes)
                                        .coerceAtLeast(0),
                                    onSelectedIndexChange = {
                                        viewModel.setMinPartSizeBytes(minPartSizeOptions[it])
                                    },
                                )
                                val partRetryOptions = listOf(3, 5, 10, 20)
                                AppSpinnerPreference(
                                    title = "分片重试次数",
                                    summary = "单个分片下载失败后的最大重试次数",
                                    items = partRetryOptions.map { it.toString() },
                                    selectedIndex = partRetryOptions
                                        .indexOf(state.partMaxRetries)
                                        .coerceAtLeast(0),
                                    onSelectedIndexChange = {
                                        viewModel.setPartMaxRetries(partRetryOptions[it])
                                    },
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "当前设备不支持多线程下载",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}