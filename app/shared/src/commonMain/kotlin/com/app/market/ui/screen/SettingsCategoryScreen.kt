package com.app.market.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.CommentSourceSetting
import com.app.market.domain.repository.ReleaseTokenRepository
import com.app.market.domain.repository.ThemePreferencesRepository
import com.app.market.platform.UiPlatform
import com.app.market.resources.Res
import com.app.market.resources.google_play_source
import com.app.market.resources.google_play_source_summary
import com.app.market.resources.google_account
import com.app.market.resources.google_account_none
import com.app.market.resources.check_timeout
import com.app.market.resources.check_timeout_summary
import com.app.market.resources.check_timeout_value
import com.app.market.resources.receive_prerelease
import com.app.market.resources.receive_prerelease_summary
import com.app.market.resources.about
import com.app.market.resources.about_summary
import com.app.market.resources.clipboard_link_detect
import com.app.market.resources.clipboard_link_detect_summary
import com.app.market.resources.device_profile
import com.app.market.resources.device_profile_summary
import com.app.market.resources.download_connections
import com.app.market.resources.download_connections_summary
import com.app.market.resources.concurrent_downloads
import com.app.market.resources.github_login
import com.app.market.resources.github_login_signed_in
import com.app.market.resources.github_token_hint
import com.app.market.resources.concurrent_downloads_summary
import com.app.market.resources.download_multi_thread
import com.app.market.resources.download_multi_thread_summary
import com.app.market.resources.downloading_apps
import com.app.market.resources.comment_source
import com.app.market.resources.comment_source_follow_app
import com.app.market.resources.comment_source_off
import com.app.market.resources.comment_source_summary
import com.app.market.resources.downloading_apps_summary
import com.app.market.resources.filter_quick_games
import com.app.market.resources.filter_quick_games_summary
import com.app.market.resources.filter_reservation_apps
import com.app.market.resources.filter_reservation_apps_summary
import com.app.market.resources.focus_notification_optimization
import com.app.market.resources.general
import com.app.market.resources.ignored_apps
import com.app.market.resources.ignored_apps_summary
import com.app.market.resources.installer_delta_fallback_notice
import com.app.market.resources.installer_delta_fallback_notice_summary
import com.app.market.resources.installer_delta_update
import com.app.market.resources.installer_delta_update_summary
import com.app.market.resources.installer_section
import com.app.market.resources.installer_section_summary
import com.app.market.resources.nav_search
import com.app.market.resources.nav_today
import com.app.market.resources.nav_updates
import com.app.market.resources.remove_search_ads
import com.app.market.resources.remove_search_ads_summary
import com.app.market.resources.saved_packages
import com.app.market.resources.saved_packages_summary
import com.app.market.resources.search_source
import com.app.market.resources.search_source_summary
import com.app.market.resources.app_category_source
import com.app.market.resources.app_category_source_summary
import com.app.market.resources.game_category_source
import com.app.market.resources.game_category_source_summary
import com.app.market.resources.show_update_source
import com.app.market.resources.show_update_source_summary
import com.app.market.resources.category_source
import com.app.market.resources.category_source_summary
import com.app.market.resources.settings_section_download_install
import com.app.market.resources.settings_section_sources
import com.app.market.resources.show_system_apps
import com.app.market.resources.show_system_apps_summary
import com.app.market.resources.strip_name_subtitle
import com.app.market.resources.strip_name_subtitle_summary
import com.app.market.resources.theme
import com.app.market.resources.theme_summary
import com.app.market.resources.today_source
import com.app.market.resources.today_source_summary
import com.app.market.resources.update_history
import com.app.market.resources.update_history_summary
import com.app.market.resources.update_source
import com.app.market.resources.update_source_aggregate
import com.app.market.resources.update_source_all
import com.app.market.resources.update_source_priority
import com.app.market.resources.update_source_priority_summary
import com.app.market.resources.update_source_summary
import com.app.market.resources.xiaomi_island_optimization
import com.app.market.resources.xiaomi_island_optimization_summary
import com.app.market.resources.proxy_setting
import com.app.market.resources.proxy_setting_summary
import com.app.market.ui.component.GitHubLoginDialog
import com.app.market.ui.component.MarketScaffold
import com.app.market.ui.component.PageVerticalPadding
import com.app.market.ui.component.UpdateSourcePriorityPicker
import com.app.market.ui.component.UpdateSourceSelectionPicker
import com.app.market.ui.util.appSourceLabel
import com.app.market.viewmodel.InstallerSettingsViewModel
import com.app.market.viewmodel.UpdatesViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import com.app.market.ui.component.AppPreferenceRow
import com.app.market.ui.component.AppSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 设置二级分类页的 key，与 [Route.SettingsCategory.category] 对应。 */
object SettingsCategories {
    const val SOURCES = "sources"
    const val UPDATES = "updates"
    const val SEARCH = "search"
    const val DOWNLOAD_INSTALL = "download_install"
    const val GENERAL = "general"
}

@Composable
fun SettingsCategoryScreen(
    category: String,
    updatesViewModel: UpdatesViewModel,
    installerSettingsViewModel: InstallerSettingsViewModel,
    onBack: () -> Unit,
    onNavigateIgnored: () -> Unit,
    onNavigateUpdateHistory: () -> Unit,
    onNavigateMultiThread: () -> Unit,
    onNavigateInstallHistory: () -> Unit,
    onNavigateSavedPackages: () -> Unit,
    onNavigateDownloadingApps: () -> Unit,
    onNavigateInstaller: () -> Unit,
    onNavigateDeviceProfile: () -> Unit,
    onNavigateTheme: () -> Unit,
    onNavigateAbout: () -> Unit,
    onNavigateProxy: () -> Unit,
    onNavigateMisc: () -> Unit,
    onNavigateTranslation: () -> Unit,
) {
    val uiPlatform = koinInject<UiPlatform>()
    val releaseToken = koinInject<ReleaseTokenRepository>()
    val releaseTokenState by releaseToken.state.collectAsStateWithLifecycle()
    var githubLogin by remember { mutableStateOf(false) }
    val themePreferences = koinInject<ThemePreferencesRepository>()
    val hiddenTabs by themePreferences.hiddenTabs.collectAsStateWithLifecycle()
    val appManagementSupported = uiPlatform.packageInstallationSupported
    val updatesState by updatesViewModel.uiState.collectAsStateWithLifecycle()
    val installerState by installerSettingsViewModel.uiState.collectAsStateWithLifecycle()
    val homePage by updatesViewModel.homePage.collectAsStateWithLifecycle()
    val searchSources by updatesViewModel.searchSources.collectAsStateWithLifecycle()
    val exactSearch by updatesViewModel.exactSearch.collectAsStateWithLifecycle()
    val googlePlayEngine by updatesViewModel.googlePlayEngine.collectAsStateWithLifecycle()
    val categorySource by updatesViewModel.categorySource.collectAsStateWithLifecycle()
    val appCategorySource by updatesViewModel.appCategorySource.collectAsStateWithLifecycle()
    val todaySource by updatesViewModel.todaySource.collectAsStateWithLifecycle()
    // 名称精简/剪贴板识别/精确搜索/日志 四个开关已挪到「杂项」二级页
    val selectedSource = AppSource.entries.firstOrNull { it in searchSources } ?: AppSource.Default.first()
    val selectedUpdateSources by updatesViewModel.updateSources.collectAsStateWithLifecycle()
    val showUpdateSource by updatesViewModel.showUpdateSource.collectAsStateWithLifecycle()
    // 多选：勾选的来源里任一命中增量能力即展示对应开关；一个都没勾时按全部支持更新的来源判断
    val updateSourceSupportsDelta = selectedUpdateSources
        .ifEmpty { AppSource.UpdateSourceOptions.toSet() }
        .any { it.capabilities.supportsDeltaUpdates }

    val title = when (category) {
        SettingsCategories.SOURCES -> stringResource(Res.string.settings_section_sources)
        SettingsCategories.UPDATES -> stringResource(Res.string.nav_updates)
        SettingsCategories.SEARCH -> stringResource(Res.string.nav_search)
        SettingsCategories.DOWNLOAD_INSTALL -> stringResource(Res.string.settings_section_download_install)
        else -> stringResource(Res.string.general)
    }
    MarketScaffold(title = title, onBack = onBack) { innerPadding, backdropModifier, scrollBehavior ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(backdropModifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + PageVerticalPadding,
                bottom = innerPadding.calculateBottomPadding() + PageVerticalPadding,
            ),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    when (category) {
                        SettingsCategories.SOURCES -> {
                            // 更新来源：含 GitHub / GitLab（Release 仓库源，能参与更新比对）
                            val updateSourceOptions =
                                AppSource.UpdateSourceOptions
                            // 更新来源是多选：点行弹出勾选面板，勾中的来源并发参与检查
                            var updateSourcesPickerVisible by remember { mutableStateOf(false) }
                            var updateSourcesRowBounds by remember {
                                mutableStateOf(androidx.compose.ui.geometry.Rect.Zero)
                            }
                            val orderedSelectedUpdateSources =
                                updateSourceOptions.filter { it in selectedUpdateSources }
                            // appSourceLabel 是 @Composable，标签要在这里先算好，
                            // 不能塞进 joinToString 的 transform
                            val orderedSelectedUpdateSourceLabels =
                                orderedSelectedUpdateSources.map { appSourceLabel(it) }
                            // 评论来源：关闭 / 跟随应用来源（不再支持自定义固定来源）
                            val commentSourceItems = listOf(
                                stringResource(Res.string.comment_source_off),
                                stringResource(Res.string.comment_source_follow_app),
                            )
                            val currentCommentSource by updatesViewModel.commentSource.collectAsStateWithLifecycle()
                            var commentSourceIndex by remember(currentCommentSource) {
                                mutableStateOf(
                                    when (currentCommentSource) {
                                        CommentSourceSetting.Off -> 0
                                        else -> 1
                                    }
                                )
                            }
                            val todaySourceOptions = AppSource.orderLikeUpdateSources(AppSource.entries
                                // 只保留支持今日内容的来源
                                .filter { it.capabilities.supportsTodayFeed && it !in AppSource.ExtraSources })
                            val categorySourceOptions = AppSource.CategorySourceOptions
                            val searchSourceOptions =
                                AppSource.SearchSourceOptions
                            AppSpinnerPreference(
                                title = stringResource(Res.string.today_source),
                                summary = stringResource(Res.string.today_source_summary),
                                items = todaySourceOptions.map { appSourceLabel(it) },
                                selectedIndex = todaySourceOptions.indexOf(todaySource).coerceAtLeast(0),
                                onSelectedIndexChange = { updatesViewModel.setTodaySource(todaySourceOptions[it]) },
                            )
                            // 应用来源与游戏来源已合并：一个设置在两个榜单页同时生效
                            AppSpinnerPreference(
                                title = stringResource(Res.string.category_source),
                                summary = stringResource(Res.string.category_source_summary),
                                items = categorySourceOptions.map { appSourceLabel(it) },
                                selectedIndex = categorySourceOptions.indexOf(appCategorySource).coerceAtLeast(0),
                                onSelectedIndexChange = {
                                    updatesViewModel.setAppCategorySource(categorySourceOptions[it])
                                },
                            )
                            if (appManagementSupported) {
                                AppPreferenceRow(
                                    title = stringResource(Res.string.update_source),
                                    summary = stringResource(Res.string.update_source_summary),
                                    value = when {
                                        // 全选 = 全部；多选但没全选 = 聚合；单选 = 该来源名称
                                        orderedSelectedUpdateSources.size == updateSourceOptions.size ->
                                            stringResource(Res.string.update_source_all)
                                        orderedSelectedUpdateSources.size > 1 ->
                                            stringResource(Res.string.update_source_aggregate)
                                        else ->
                                            orderedSelectedUpdateSourceLabels.joinToString("、")
                                    },
                                    onRowClick = { updateSourcesPickerVisible = true },
                                    onBoundsChanged = { updateSourcesRowBounds = it },
                                )
                                UpdateSourceSelectionPicker(
                                    show = updateSourcesPickerVisible,
                                    options = updateSourceOptions,
                                    selected = selectedUpdateSources,
                                    anchorBounds = updateSourcesRowBounds,
                                    onCommit = updatesViewModel::setUpdateSources,
                                    onDismissRequest = { updateSourcesPickerVisible = false },
                                )
                                // 勾了多个来源时可设置来源优先级：
                                // 同一应用有多个来源提供更新时，排更前的来源胜出展示
                                if (selectedUpdateSources.size > 1) {
                                    val updateSourcePriority by updatesViewModel.updateSourcePriority
                                        .collectAsStateWithLifecycle()
                                    // 只列出「更新来源」里勾选中的来源：没勾的在这里同步隐藏，
                                    // 避免出现一个根本没参与检查的来源还能排优先级
                                    val priorityOrder =
                                        updateSourcePriority.filter { it in updateSourceOptions && it in selectedUpdateSources } +
                                            updateSourceOptions.filter {
                                                it in selectedUpdateSources && it !in updateSourcePriority
                                            }
                                    // 提交时把未勾选来源的原顺序接在后面，避免它们在重新勾选后丢掉自定义排序
                                    val unselectedPriorityOrder =
                                        updateSourcePriority.filter { it !in selectedUpdateSources } +
                                            updateSourceOptions.filter {
                                                it !in selectedUpdateSources && it !in updateSourcePriority
                                            }
                                    var priorityPickerVisible by remember { mutableStateOf(false) }
                                    var priorityRowBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
                                    // 与其他下拉设置行共用同一组件（AppPreferenceRow）：排版逐像素一致
                                    AppPreferenceRow(
                                        title = stringResource(Res.string.update_source_priority),
                                        summary = stringResource(Res.string.update_source_priority_summary),
                                        value = priorityOrder.firstOrNull()?.let { appSourceLabel(it) }.orEmpty(),
                                        onRowClick = { priorityPickerVisible = true },
                                        onBoundsChanged = { priorityRowBounds = it },
                                    )
                                    UpdateSourcePriorityPicker(
                                        show = priorityPickerVisible,
                                        order = priorityOrder,
                                        anchorBounds = priorityRowBounds,
                                        onCommit = { newOrder ->
                                            updatesViewModel.setUpdateSourcePriority(newOrder + unselectedPriorityOrder)
                                        },
                                        onDismissRequest = { priorityPickerVisible = false },
                                    )
                                }
                            }
                            AppSpinnerPreference(
                                title = stringResource(Res.string.search_source),
                                summary = stringResource(Res.string.search_source_summary),
                                items = searchSourceOptions.map { appSourceLabel(it) },
                                selectedIndex = searchSourceOptions.indexOf(selectedSource).coerceAtLeast(0),
                                onSelectedIndexChange = {
                                    updatesViewModel.setSearchSource(searchSourceOptions[it])
                                },
                            )
                            AppSpinnerPreference(
                                title = stringResource(Res.string.comment_source),
                                summary = stringResource(Res.string.comment_source_summary),
                                items = commentSourceItems,
                                selectedIndex = commentSourceIndex,
                                onSelectedIndexChange = { index ->
                                    commentSourceIndex = index
                                    updatesViewModel.setCommentSource(
                                        when (index) {
                                            0 -> CommentSourceSetting.Off
                                            else -> CommentSourceSetting.FollowApp
                                        }
                                    )
                                },
                            )
                            AppSpinnerPreference(
                                title = stringResource(Res.string.google_play_source),
                                items = listOf("AppMarket", "AuroraStore"),
                                selectedIndex = if (googlePlayEngine == "aurorastore") 1 else 0,
                                onSelectedIndexChange = {
                                    updatesViewModel.setGooglePlayEngine(if (it == 1) "aurorastore" else "appmarket")
                                },
                            )
                        }
                        SettingsCategories.UPDATES -> {
                            // 更新检查超时：卡死的来源超过该时长即跳过并提示
                            val timeoutOptions = listOf(5, 10, 15, 20, 30, 45, 60)
                            val currentTimeout by updatesViewModel.checkTimeoutSeconds.collectAsStateWithLifecycle()
                            AppSpinnerPreference(
                                title = stringResource(Res.string.check_timeout),
                                summary = stringResource(Res.string.check_timeout_summary),
                                items = timeoutOptions.map {
                                    stringResource(Res.string.check_timeout_value, it)
                                },
                                selectedIndex = timeoutOptions.indexOf(currentTimeout).coerceAtLeast(
                                    timeoutOptions.indexOfFirst { it >= currentTimeout }.coerceAtLeast(0)
                                ),
                                onSelectedIndexChange = { index ->
                                    updatesViewModel.setCheckTimeoutSeconds(timeoutOptions[index])
                                },
                            )
                            val includePrerelease by updatesViewModel.includePrereleaseUpdates
                                .collectAsStateWithLifecycle()
                            SwitchPreference(
                                title = "包含预览版更新",
                                summary = "GitHub / LSPosed 来源把 prerelease、beta、rc 版本也算作新版本",
                                checked = includePrerelease,
                                onCheckedChange = updatesViewModel::setIncludePrereleaseUpdates,
                            )
                            SwitchPreference(
                                title = stringResource(Res.string.show_system_apps),
                                summary = stringResource(Res.string.show_system_apps_summary),
                                checked = updatesState.showSystemUpdates,
                                onCheckedChange = updatesViewModel::setShowSystemUpdates,
                            )
                            if (installerState.deltaUpdateSupported && updateSourceSupportsDelta) {
                                SwitchPreference(
                                    title = stringResource(Res.string.installer_delta_update),
                                    summary = stringResource(Res.string.installer_delta_update_summary),
                                    checked = installerState.deltaUpdateEnabled,
                                    onCheckedChange = installerSettingsViewModel::setDeltaUpdateEnabled,
                                )
                                AnimatedVisibility(
                                    visible = installerState.deltaUpdateEnabled,
                                    enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                                    exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
                                ) {
                                    SwitchPreference(
                                        title = stringResource(Res.string.installer_delta_fallback_notice),
                                        summary = stringResource(Res.string.installer_delta_fallback_notice_summary),
                                        checked = installerState.deltaFallbackNoticeEnabled,
                                        onCheckedChange = installerSettingsViewModel::setDeltaFallbackNoticeEnabled,
                                    )
                                
                                }
                            }
                            ArrowPreference(
                                title = stringResource(Res.string.ignored_apps),
                                summary = stringResource(Res.string.ignored_apps_summary),
                                onClick = onNavigateIgnored,
                            )
                            ArrowPreference(
                                title = stringResource(Res.string.update_history),
                                summary = stringResource(Res.string.update_history_summary),
                                onClick = onNavigateUpdateHistory,
                            )
                        }
                        SettingsCategories.SEARCH -> {
                            SwitchPreference(
                                title = "精确搜索",
                                summary = "只保留标题或包名命中的结果，去掉商店推荐的无关项",
                                checked = exactSearch,
                                onCheckedChange = updatesViewModel::setExactSearch,
                            )
                            // 搜索过滤开关全部常显（不随当前来源的能力时隐时现）；对不支持的来源设置自然不生效
                            SwitchPreference(
                                title = stringResource(Res.string.remove_search_ads),
                                summary = stringResource(Res.string.remove_search_ads_summary),
                                checked = updatesState.removeSearchAds,
                                onCheckedChange = updatesViewModel::setRemoveSearchAds,
                            )
                            SwitchPreference(
                                title = stringResource(Res.string.filter_quick_games),
                                summary = stringResource(Res.string.filter_quick_games_summary),
                                checked = updatesState.filterQuickGames,
                                onCheckedChange = updatesViewModel::setFilterQuickGames,
                            )
                            SwitchPreference(
                                title = stringResource(Res.string.filter_reservation_apps),
                                summary = stringResource(Res.string.filter_reservation_apps_summary),
                                checked = updatesState.filterReservationApps,
                                onCheckedChange = updatesViewModel::setFilterReservationApps,
                            )
                        }
                        SettingsCategories.DOWNLOAD_INSTALL -> {
                            val concurrentDownloadOptions = listOf(1, 2, 3, 4, 6, 8)
                            AppSpinnerPreference(
                                title = stringResource(Res.string.concurrent_downloads),
                                summary = stringResource(Res.string.concurrent_downloads_summary),
                                items = concurrentDownloadOptions.map { it.toString() },
                                selectedIndex = concurrentDownloadOptions
                                    .indexOf(installerState.concurrentDownloads)
                                    .coerceAtLeast(0),
                                onSelectedIndexChange = {
                                    installerSettingsViewModel.setConcurrentDownloads(
                                        concurrentDownloadOptions[it]
                                    )
                                },
                            )
                            if (installerState.installationSupported) {
                                if (installerState.focusNotificationSupported) {
                                    val styleValues = listOf("off", "normal", "live", "island")
                                    val styleLabels = listOf("关闭", "普通", "实时通知", "小米超级岛")
                                    val styleIndex = styleValues
                                        .indexOf(installerState.downloadNotificationStyle)
                                        .coerceAtLeast(0)
                                    // 副标题跟着当前选项走：岛那档沿用原来的白名单提示
                                    val styleSummary = when (styleValues[styleIndex]) {
                                        "off" -> "关闭下载与安装过程中的通知"
                                        "normal" -> "普通进度通知"
                                        "live" -> "安卓原生实时活动通知"
                                        else -> stringResource(Res.string.xiaomi_island_optimization_summary)
                                    }
                                    AppSpinnerPreference(
                                        title = "下载通知",
                                        summary = styleSummary,
                                        items = styleLabels,
                                        selectedIndex = styleIndex,
                                        onSelectedIndexChange = {
                                            installerSettingsViewModel.setDownloadNotificationStyle(styleValues[it])
                                        },
                                    )
                                }
                            }
                            // 「下载通知」之后是「多线程下载」二级页入口（与安装方式/安装历史同级）
                            if (installerState.multiThreadDownloadSupported) {
                                ArrowPreference(
                                    title = stringResource(Res.string.download_multi_thread),
                                    summary = stringResource(Res.string.download_multi_thread_summary),
                                    onClick = onNavigateMultiThread,
                                )
                            }
                            if (installerState.installationSupported) {
                                ArrowPreference(
                                    title = stringResource(Res.string.installer_section),
                                    summary = stringResource(Res.string.installer_section_summary),
                                    onClick = onNavigateInstaller,
                                )
                            }
                            ArrowPreference(
                                title = "安装历史",
                                summary = "记录每次安装的结果，失败原因也能回看",
                                onClick = onNavigateInstallHistory,
                            )
                        }
                        else -> {
                            // 通用：名称精简/剪贴板识别/设备信息/主题/关于
                            // 首页 = 启动第一个页面；已被挪到「主题」页里（启动页）
                            // GitHub 账号：令牌只用于 GitHub/GitLab API 读写，不参与 APK 下载
                            ArrowPreference(
                                title = stringResource(Res.string.github_login),
                                summary = if (releaseTokenState.loggedIn) {
                                    stringResource(Res.string.github_login_signed_in, releaseTokenState.login.ifBlank { "-" })
                                } else {
                                    stringResource(Res.string.github_token_hint)
                                },
                                onClick = {
                                    com.app.market.domain.diagnostic.AppLog.log("GitHub entry clicked")
                                    githubLogin = true
                                },
                            )
                            // 代理：为需要走代理的来源（如 Google Play）配置出口
                            ArrowPreference(
                                title = stringResource(Res.string.proxy_setting),
                                summary = stringResource(Res.string.proxy_setting_summary),
                                onClick = onNavigateProxy,
                            )
                            ArrowPreference(
                                title = "杂项",
                                summary = "名称精简、剪贴板识别、日志",
                                onClick = onNavigateMisc,
                            )
                            ArrowPreference(
                                title = "翻译",
                                summary = "翻译引擎、AI接口地址与密钥",
                                onClick = onNavigateTranslation,
                            )
                            ArrowPreference(
                                title = stringResource(Res.string.device_profile),
                                summary = stringResource(Res.string.device_profile_summary),
                                onClick = onNavigateDeviceProfile,
                            )
                            // 「主题」入口已在设置主页，这里不再重复
                            ArrowPreference(
                                title = stringResource(Res.string.about),
                                summary = stringResource(Res.string.about_summary),
                                onClick = onNavigateAbout,
                            )
                        }
                    }
                }
            }
        }
    }
    // miuix 的 OverlayDialog 需常驻挂载、由 show 控制显隐，条件式挂载会导致弹不出来
    GitHubLoginDialog(
        show = githubLogin,
        repository = releaseToken,
        onDismissRequest = { githubLogin = false },
    )
}

/**
 * 首页候选项与底栏页签的对应关系已挪到 [ThemeSettingsScreen.kt]（同包同文件级共享）。
 */