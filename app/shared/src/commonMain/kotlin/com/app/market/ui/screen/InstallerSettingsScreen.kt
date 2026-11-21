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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.market.domain.model.installer.InstallerMode
import com.app.market.resources.Res
import com.app.market.resources.cancel
import com.app.market.resources.installer_custom_source
import com.app.market.resources.installer_custom_source_custom
import com.app.market.resources.installer_custom_source_default
import com.app.market.resources.installer_custom_source_package
import com.app.market.resources.installer_custom_source_summary
import com.app.market.resources.installer_dexopt_force
import com.app.market.resources.installer_dexopt_force_summary
import com.app.market.resources.installer_dexopt_mode
import com.app.market.resources.installer_delete_after_install
import com.app.market.resources.installer_delete_after_install_summary
import com.app.market.resources.installer_manual_dexopt
import com.app.market.resources.installer_manual_dexopt_summary
import com.app.market.resources.installer_mode_dhizuku
import com.app.market.resources.installer_mode_dhizuku_summary
import com.app.market.resources.installer_mode_default
import com.app.market.resources.installer_mode_default_summary
import com.app.market.resources.installer_mode_root
import com.app.market.resources.installer_mode_root_summary
import com.app.market.resources.installer_mode_shizuku
import com.app.market.resources.installer_mode_shizuku_summary
import com.app.market.resources.installer_mode_third_party
import com.app.market.resources.installer_mode_third_party_summary
import com.app.market.resources.installer_no_user_action
import com.app.market.resources.installer_no_user_action_summary
import com.app.market.resources.installer_none
import com.app.market.resources.installer_pick
import com.app.market.resources.installer_save_to_downloads
import com.app.market.resources.installer_save_to_downloads_summary
import com.app.market.resources.installer_package_save_dir
import top.yukonga.miuix.kmp.basic.IconButton
import com.app.market.resources.installer_section
import com.app.market.ui.component.AppSpinnerPreference
import com.app.market.ui.component.AppTextButton
import com.app.market.ui.component.MarketScaffold
import com.app.market.ui.component.PageVerticalPadding
import com.app.market.viewmodel.InstallerSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import com.app.market.ui.theme.PopupTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.app.market.resources.installer_mode_system
import com.app.market.resources.installer_mode_system_summary

@Composable
fun InstallerSettingsScreen(
    viewModel: InstallerSettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val privileges by viewModel.privileges.collectAsStateWithLifecycle()

    MarketScaffold(
        title = stringResource(Res.string.installer_section),
        onBack = onBack
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
            item(key = "mode") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    InstallerModeRow(
                        label = stringResource(Res.string.installer_mode_default),
                        summary = stringResource(Res.string.installer_mode_default_summary),
                        selected = state.mode == InstallerMode.STANDARD,
                        onClick = { viewModel.setMode(InstallerMode.STANDARD) },
                    )
                    InstallerModeRow(
                        label = stringResource(Res.string.installer_mode_system),
                        summary = if (privileges.systemAvailable) {
                            "正在作为系统应用运行"
                        } else {
                            stringResource(Res.string.installer_mode_system_summary)
                        },
                        selected = state.mode == InstallerMode.SYSTEM,
                        onClick = { viewModel.setMode(InstallerMode.SYSTEM) },
                    )
                    InstallerModeRow(
                        label = stringResource(Res.string.installer_mode_root),
                        summary = if (privileges.rootAvailable) {
                            "可用（${privileges.rootManager}）"
                        } else {
                            stringResource(Res.string.installer_mode_root_summary)
                        },
                        selected = state.mode == InstallerMode.ROOT,
                        onClick = { viewModel.setMode(InstallerMode.ROOT) },
                    )
                    InstallerModeRow(
                        label = stringResource(Res.string.installer_mode_shizuku),
                        summary = if (privileges.shizukuAvailable) {
                            "可用（Shizuku）"
                        } else {
                            stringResource(Res.string.installer_mode_shizuku_summary)
                        },
                        selected = state.mode == InstallerMode.SHIZUKU,
                        onClick = { viewModel.setMode(InstallerMode.SHIZUKU) },
                    )
                    InstallerModeRow(
                        label = stringResource(Res.string.installer_mode_dhizuku),
                        summary = if (privileges.dhizukuAvailable) {
                            "可用（Dhizuku）"
                        } else {
                            stringResource(Res.string.installer_mode_dhizuku_summary)
                        },
                        selected = state.mode == InstallerMode.DHIZUKU,
                        onClick = { viewModel.setMode(InstallerMode.DHIZUKU) },
                    )
                    val selectedInstaller = state.installerCandidates.firstOrNull {
                        it.packageName == state.thirdPartyInstallerPackage
                    }
                    InstallerModeRow(
                        label = stringResource(Res.string.installer_mode_third_party),
                        summary = selectedInstaller?.label
                            ?: stringResource(Res.string.installer_mode_third_party_summary),
                        selected = state.mode == InstallerMode.THIRD_PARTY,
                        onClick = viewModel::showThirdPartyInstallerPicker,
                    )
                }
            }

            // 安装来源 / 手动 dex 优化：仅 Root/Shizuku 支持
            //（Dhizuku 会话归属固定、普通安装无 INSTALL_PACKAGES，均不支持）
            if (state.mode == InstallerMode.ROOT || state.mode == InstallerMode.SHIZUKU) {
                if (state.mode == InstallerMode.ROOT) {
                    item(key = "root-command") {
                        val commandDraft = remember { mutableStateOf("su") }
                        LaunchedEffect(Unit) {
                            commandDraft.value = viewModel.privilegeCommand()
                        }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            TextField(
                                value = commandDraft.value,
                                onValueChange = { value ->
                                    commandDraft.value = value
                                    viewModel.setPrivilegeCommand(value)
                                },
                                label = "自定义提权命令（默认 su）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item(key = "install-source") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        val sourceModes = listOf("default", "google_play", "custom")
                        AppSpinnerPreference(
                            title = stringResource(Res.string.installer_custom_source),
                            // 副标题只在 Google Play 档显示：解释它解决「并非来自 Google Play」检查
                            summary = if (state.installerSourceMode == "google_play") {
                                stringResource(Res.string.installer_custom_source_summary)
                            } else {
                                null
                            },
                            items = listOf(
                                stringResource(Res.string.installer_custom_source_default),
                                "Google Play",
                                stringResource(Res.string.installer_custom_source_custom),
                            ),
                            selectedIndex = sourceModes.indexOf(state.installerSourceMode).coerceAtLeast(0),
                            onSelectedIndexChange = { index ->
                                viewModel.setInstallerSourceMode(sourceModes[index])
                            },
                        )
                        // 挤压弹出动画：选中「自定义」时输入框从上方压出，收起时压回
                        AnimatedVisibility(
                            visible = state.installerSourceMode == "custom",
                            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                            exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
                        ) {
                            TextField(
                                value = state.customInstallerSource,
                                onValueChange = viewModel::setCustomInstallerSource,
                                label = stringResource(Res.string.installer_custom_source_package),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                item(key = "dexopt") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        SwitchPreference(
                            title = stringResource(Res.string.installer_manual_dexopt),
                            summary = stringResource(Res.string.installer_manual_dexopt_summary),
                            checked = state.manualDexoptEnabled,
                            onCheckedChange = viewModel::setManualDexoptEnabled,
                        )
                        // 挤压弹出动画：打开手动 dex 优化时选项从开关下方压出，关闭压回
                        AnimatedVisibility(
                            visible = state.manualDexoptEnabled,
                            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                            exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
                        ) {
                            Column {
                                val modes = listOf("verify", "speed-profile", "speed", "everything")
                                AppSpinnerPreference(
                                    title = stringResource(Res.string.installer_dexopt_mode),
                                    items = modes,
                                    selectedIndex = modes.indexOf(state.dexoptMode).coerceAtLeast(0),
                                    onSelectedIndexChange = { index -> viewModel.setDexoptMode(modes[index]) },
                                )
                                SwitchPreference(
                                    title = stringResource(Res.string.installer_dexopt_force),
                                    summary = stringResource(Res.string.installer_dexopt_force_summary),
                                    checked = state.dexoptForceEnabled,
                                    onCheckedChange = viewModel::setDexoptForceEnabled,
                                )
                            }
                        }
                    }
                }
            }
            item(key = "save") {
                val deleteAfterInstall = state.mode == InstallerMode.THIRD_PARTY
                Card(modifier = Modifier.fillMaxWidth()) {
                    // 「保存到 Download 目录」开关已移除（对系统安装器而言没有实际作用）；
                    // 只保留第三方安装器的「安装后删除安装包」
                    val autoDeleteState = remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        autoDeleteState.value = viewModel.deletePackageAfterInstall()
                    }
                    SwitchPreference(
                        title = "安装完成后自动删除安装包",
                        summary = "安装包默认保存在 Download/AppMarket；关闭后安装完仍会保留",
                        checked = autoDeleteState.value,
                        onCheckedChange = { checked ->
                            autoDeleteState.value = checked
                            viewModel.setDeletePackageAfterInstall(checked)
                        },
                    )
                    // 安装包默认保存路径：下载完成的安装包与 log 日志都落在这里；📂 弹系统文件管理选目录
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextField(
                            value = state.packageSaveDirectory,
                            onValueChange = viewModel::setPackageSaveDirectory,
                            label = stringResource(Res.string.installer_package_save_dir),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = viewModel::pickSaveDirectory,
                            cornerRadius = 100.dp,
                            minHeight = 48.dp,
                            minWidth = 48.dp,
                        ) {
                            Text("📂", fontSize = 22.sp)
                        }
                    }
                    if (deleteAfterInstall) {
                        SwitchPreference(
                            title = stringResource(Res.string.installer_delete_after_install),
                            summary = stringResource(Res.string.installer_delete_after_install_summary),
                            checked = !state.saveToDownloads,
                            onCheckedChange = { checked ->
                                viewModel.setSaveToDownloads(!checked)
                            },
                        )
                    }
                }
            }

        }
    }

    PopupTheme { WindowDialog(
        show = state.showInstallerPicker,
        title = stringResource(Res.string.installer_pick),
        onDismissRequest = viewModel::dismissThirdPartyInstallerPicker,
        insideMargin = DpSize(0.dp, 24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (state.installerCandidates.isEmpty()) {
                Text(
                    text = stringResource(Res.string.installer_none),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                ) {
                    items(
                        items = state.installerCandidates,
                        key = { it.packageName },
                    ) { candidate ->
                        RadioButtonPreference(
                            title = candidate.label,
                            summary = candidate.packageName,
                            selected = candidate.packageName == state.thirdPartyInstallerPackage,
                            radioButtonLocation = RadioButtonLocation.End,
                            insideMargin = PaddingValues(24.dp, 16.dp),
                            onClick = { viewModel.selectThirdPartyInstaller(candidate) },
                        )
                    }
                }
            }
            AppTextButton(
                text = stringResource(Res.string.cancel),
                onClick = viewModel::dismissThirdPartyInstallerPicker,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
        }
    } }
}

@Composable
private fun InstallerModeRow(
    label: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    RadioButtonPreference(
        title = label,
        summary = summary,
        selected = selected,
        onClick = onClick,
        radioButtonLocation = RadioButtonLocation.End,
    )
}