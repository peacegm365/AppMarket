package com.app.market.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.AppSource.OPPO
import com.app.market.domain.model.market.AppSource.SAMSUNG
import com.app.market.domain.model.profile.MarketProfile
import com.app.market.domain.model.profile.OppoStoreRegion
import com.app.market.domain.model.profile.ProfileSource
import com.app.market.domain.model.profile.ProfileTemplate
import com.app.market.domain.model.profile.SamsungStoreRegion
import com.app.market.platform.UiPlatform
import com.app.market.resources.Res
import com.app.market.resources.cancel
import com.app.market.resources.delete
import com.app.market.resources.device_profile
import com.app.market.resources.profile_delete_template_confirm
import com.app.market.resources.profile_save_template
import com.app.market.resources.profile_source
import com.app.market.resources.profile_source_current_device
import com.app.market.resources.profile_source_custom
import com.app.market.resources.profile_source_device
import com.app.market.resources.profile_source_preset
import com.app.market.resources.profile_store_region
import com.app.market.resources.profile_store_region_china
import com.app.market.resources.profile_store_region_global
import com.app.market.resources.profile_template_name
import com.app.market.resources.save
import com.app.market.resources.saved
import com.app.market.ui.component.AppButton
import com.app.market.ui.component.AppTextButton
import com.app.market.ui.component.LoadingBox
import com.app.market.ui.component.MarketScaffold
import com.app.market.ui.component.PageVerticalPadding
import com.app.market.ui.component.deferredTopPadding
import com.app.market.ui.util.appSourceLabel
import com.app.market.viewmodel.DeviceProfileViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.basic.TextField
import com.app.market.ui.component.AppSpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import com.app.market.ui.theme.PopupTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private val TabRowBottomPadding = 6.dp

@Composable
fun DeviceProfileScreen(
    viewModel: DeviceProfileViewModel,
    onBack: () -> Unit,
) {
    val uiPlatform = koinInject<UiPlatform>()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val templateNames by viewModel.templateNames.collectAsStateWithLifecycle()
    val overriddenFields by viewModel.overriddenFields.collectAsStateWithLifecycle()
    val canUseDevice by viewModel.canUseDevice.collectAsStateWithLifecycle()
    val oppoStoreRegion by viewModel.oppoStoreRegion.collectAsStateWithLifecycle()
    val oppoRequestContext by viewModel.oppoRequestContext.collectAsStateWithLifecycle()
    val samsungStoreRegion by viewModel.samsungStoreRegion.collectAsStateWithLifecycle()
    val samsungRequestContext by viewModel.samsungRequestContext.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val showSaveTemplateDialog by viewModel.showSaveTemplateDialog.collectAsStateWithLifecycle()
    var deleteTemplateSource by remember { mutableStateOf<AppSource?>(null) }
    var showGoogleTemplateDialog by remember { mutableStateOf(false) }
    val savedMsg = stringResource(Res.string.saved)
    val customLabel = stringResource(Res.string.profile_source_custom)
    val presetLabel = stringResource(Res.string.profile_source_preset)
    val chinaRegionLabel = stringResource(Res.string.profile_store_region_china)
    val globalRegionLabel = stringResource(Res.string.profile_store_region_global)
    val oppoRequestValues = listOf(
        "user-region" to oppoRequestContext.userRegion,
        "system-locale" to oppoRequestContext.systemLocale,
        "supported-locales" to oppoRequestContext.supportedLocales,
        "locale" to oppoRequestContext.locale,
    )
    val samsungRequestValues = listOf(
        "countryCode" to samsungRequestContext.countryCode,
        "lang" to samsungRequestContext.language,
        "mcc" to samsungRequestContext.mcc,
        "mnc" to samsungRequestContext.mnc,
        "csc" to samsungRequestContext.csc,
    )
    val editableSources = DeviceProfileViewModel.EDITABLE_SOURCES
    val tabs = editableSources.map { appSourceLabel(it) } + "Google Play"
    val pagerState = rememberPagerState(initialPage = 0) { editableSources.size + 1 }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    if (editableSources.any { profiles[it] == null }) {
        MarketScaffold(title = stringResource(Res.string.device_profile), onBack = onBack) { innerPadding, _, _ ->
            LoadingBox(Modifier.fillMaxSize().padding(innerPadding))
        }
        return
    }

    // 页面切换后旧页仍组合在 pager 里，焦点会留在它的输入框上；切页时统一清掉。
    LaunchedEffect(pagerState.currentPage) {
        focusManager.clearFocus()
    }

    MarketScaffold(
        title = stringResource(Res.string.device_profile),
        onBack = onBack,
        bottomContent = { scrollBehavior ->
            // Top bar expanded: extra breathing room above the tabs; collapsed: flush with the bar.
            val dynamicTopPadding = remember(scrollBehavior) {
                { PageVerticalPadding * (1f - scrollBehavior.state.collapsedFraction) }
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = TabRowBottomPadding)
                    .deferredTopPadding(dynamicTopPadding),
            ) {
                WrapContentTabs(
                    tabs = tabs,
                    selectedIndex = pagerState.currentPage,
                    onTabSelected = { index ->
                        coroutineScope.launch {
                            // 相邻页滑动切换；跨页直接跳（否则动画会穿过中间页，如小米→OPPO 闪一下 vivo）
                            if (kotlin.math.abs(index - pagerState.currentPage) > 1) {
                                pagerState.scrollToPage(index)
                            } else {
                                pagerState.animateScrollToPage(
                                    page = index,
                                    animationSpec = com.app.market.ui.navigation.PagerNavigationSpringSpec,
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { innerPadding, backdropModifier, scrollBehavior ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().then(backdropModifier),
            overscrollEffect = null,
            userScrollEnabled = false,
        ) { page ->
            if (page == editableSources.size) {
                GoogleIdentityPage(
                    viewModel = viewModel,
                    modifier = backdropModifier,
                    innerPadding = innerPadding,
                    scrollBehavior = scrollBehavior,
                    onSave = { uiPlatform.showToast(savedMsg) },
                    onSaveTemplate = { showGoogleTemplateDialog = true },
                    onRequestDeleteTemplate = { name ->
                        viewModel.deleteGoogleTemplate(name)
                        uiPlatform.showToast(savedMsg)
                    },
                )
                return@HorizontalPager
            }
            val appSource = editableSources[page]
            DeviceProfileSourcePage(
                viewModel = viewModel,
                appSource = appSource,
                profile = profiles[appSource] ?: return@HorizontalPager,
                source = sources[appSource] ?: ProfileSource.PRESET,
                overridden = overriddenFields[appSource].orEmpty(),
                selectedTemplate = templateNames[appSource],
                templates = templates,
                canUseDevice = canUseDevice[appSource] == true,
                customLabel = customLabel,
                presetLabel = presetLabel,
                deviceLabel = deviceLabel(appSource),
                chinaRegionLabel = chinaRegionLabel,
                globalRegionLabel = globalRegionLabel,
                oppoStoreRegion = oppoStoreRegion,
                oppoRequestValues = oppoRequestValues,
                samsungStoreRegion = samsungStoreRegion,
                samsungRequestValues = samsungRequestValues,
                hasCustomRequestContext = appSource == SAMSUNG &&
                        DeviceProfileViewModel.hasCustomSamsungRequestContext(
                            samsungStoreRegion,
                            samsungRequestContext,
                        ),
                modifier = backdropModifier,
                innerPadding = innerPadding,
                scrollBehavior = scrollBehavior,
                onSave = {
                    viewModel.save(appSource)
                    uiPlatform.showToast(savedMsg)
                },
                onSaveTemplate = { viewModel.setShowSaveTemplateDialog(appSource) },
                onRequestDeleteTemplate = { deleteTemplateSource = appSource },
            )
        }
    }

    SaveTemplateDialog(
        show = showSaveTemplateDialog != null,
        defaultName = viewModel.defaultTemplateName(),
        onDismiss = { viewModel.setShowSaveTemplateDialog(null) },
        onSave = { name ->
            showSaveTemplateDialog?.let { viewModel.saveTemplate(name, it) }
            uiPlatform.showToast(savedMsg)
        },
    )
    SaveTemplateDialog(
        show = showGoogleTemplateDialog,
        defaultName = "Google Play 设备信息",
        onDismiss = { showGoogleTemplateDialog = false },
        onSave = { name ->
            viewModel.saveGoogleTemplate(name)
            showGoogleTemplateDialog = false
            uiPlatform.showToast(savedMsg)
        },
    )
    val deleteTemplateName = deleteTemplateSource?.let { templateNames[it] }
    DeleteTemplateDialog(
        show = deleteTemplateSource != null && deleteTemplateName != null,
        templateName = deleteTemplateName.orEmpty(),
        onDismiss = { deleteTemplateSource = null },
        onDelete = { name ->
            viewModel.deleteTemplate(name)
            deleteTemplateSource = null
        },
    )
}

@Composable
private fun WrapContentTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        itemsIndexed(tabs) { index, title ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) MiuixTheme.colorScheme.primary else Color.Transparent,
                    )
                    .clickable { onTabSelected(index) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.subtitle,
                    color = if (selected) Color.White else MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun GoogleIdentityPage(
    viewModel: DeviceProfileViewModel,
    modifier: Modifier,
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onSave: () -> Unit,
    onSaveTemplate: () -> Unit,
    onRequestDeleteTemplate: (String) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val gsfId = viewModel.googleGsfId.value
    var draft by remember(gsfId) { mutableStateOf(gsfId) }
    val overrides by viewModel.googleDeviceOverrides.collectAsStateWithLifecycle()
    val deviceSource by viewModel.googleDeviceSource.collectAsStateWithLifecycle()
    val snapshot = remember(overrides, deviceSource) { viewModel.googleDeviceSnapshot() }
    val templates by viewModel.googleDeviceTemplates.collectAsStateWithLifecycle()
    val matchedTemplate = remember(overrides, templates) {
        templates.firstOrNull { it.second == overrides }?.first
    }
    val presetLabel = stringResource(Res.string.profile_source_preset)
    val deviceLabel = stringResource(Res.string.profile_source_device)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
            top = innerPadding.calculateTopPadding() + PageVerticalPadding - TabRowBottomPadding,
            bottom = innerPadding.calculateBottomPadding() + PageVerticalPadding,
        ),
    ) {
        item(key = "google-source") {
            Card(modifier = Modifier.fillMaxWidth()) {
                // 来源下拉：预置信息 / 从设备获取 / 已保存的模板（模板名直接列进来，选中即套用）
                val templateNames = templates.map { it.first }
                val items = listOf(presetLabel, deviceLabel) + templateNames
                val selectedIndex = when {
                    matchedTemplate != null -> 2 + templateNames.indexOf(matchedTemplate)
                    deviceSource -> 1
                    else -> 0
                }
                AppSpinnerPreference(
                    title = stringResource(Res.string.profile_source),
                    items = items,
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = { itemIndex ->
                        when {
                            itemIndex == 0 -> {
                                viewModel.replaceGoogleDeviceOverrides(emptyMap())
                                viewModel.setGoogleDeviceSource(false)
                            }
                            itemIndex == 1 -> viewModel.setGoogleDeviceSource(true)
                            else -> templateNames.getOrNull(itemIndex - 2)?.let(viewModel::applyGoogleTemplate)
                        }
                    },
                )
            }
        }
        item(key = "google-region") {
            val region by viewModel.googleRegion.collectAsStateWithLifecycle()
            val regionOptions = remember { GooglePlayRegionOptions }
            val regionIndex = remember(region, regionOptions) {
                regionOptions.indexOfFirst { it.first == region }.coerceAtLeast(0)
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                AppSpinnerPreference(
                    title = "商店地区",
                    items = regionOptions.map { it.second },
                    selectedIndex = regionIndex,
                    onSelectedIndexChange = { index ->
                        regionOptions.getOrNull(index)?.let { viewModel.setGoogleRegion(it.first) }
                    },
                )
            }
        }
        item(key = "google-gsf") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            viewModel.setGoogleGsfId(it)
                        },
                        label = "Google 身份 ID（GSF）",
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    val loading = viewModel.googleGsfIdLoading.value
                    Text(
                        text = if (loading) "获取中…" else "重新获取",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !loading) { viewModel.resetGoogleIdentity() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        style = MiuixTheme.textStyles.body2,
                        color = if (loading) {
                            MiuixTheme.colorScheme.onSurfaceSecondary
                        } else {
                            MiuixTheme.colorScheme.primary
                        },
                    )
                }
                val gsfError = viewModel.googleGsfIdError.value
                if (gsfError.isNotBlank()) {
                    Text(
                        text = gsfError,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }
        }
        items(GOOGLE_DEVICE_FIELD_KEYS, key = { it }) { name ->
            TextField(
                // 显示当前生效值：没有覆盖时回落到默认画像/本机值
                value = overrides[name] ?: snapshot[name].orEmpty(),
                onValueChange = { viewModel.setGoogleDeviceOverride(name, it) },
                label = name,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "google-save") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextButton(
                    text = stringResource(Res.string.profile_save_template),
                    onClick = onSaveTemplate,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (matchedTemplate != null) {
                    AppTextButton(
                        text = stringResource(Res.string.delete),
                        colors = ButtonDefaults.textButtonColors(
                            textColor = MiuixTheme.colorScheme.error,
                        ),
                        onClick = { onRequestDeleteTemplate(matchedTemplate) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceProfileSourcePage(
    viewModel: DeviceProfileViewModel,
    appSource: AppSource,
    profile: MarketProfile,
    source: ProfileSource,
    overridden: Set<String>,
    selectedTemplate: String?,
    templates: List<ProfileTemplate>,
    canUseDevice: Boolean,
    customLabel: String,
    presetLabel: String,
    deviceLabel: String,
    chinaRegionLabel: String,
    globalRegionLabel: String,
    oppoStoreRegion: OppoStoreRegion,
    oppoRequestValues: List<Pair<String, String>>,
    samsungStoreRegion: SamsungStoreRegion,
    samsungRequestValues: List<Pair<String, String>>,
    hasCustomRequestContext: Boolean,
    modifier: Modifier,
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onSave: () -> Unit,
    onSaveTemplate: () -> Unit,
    onRequestDeleteTemplate: () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
            top = innerPadding.calculateTopPadding() + PageVerticalPadding - TabRowBottomPadding,
            bottom = innerPadding.calculateBottomPadding() + PageVerticalPadding,
        ),
    ) {
        item(key = "source") {
            Card(modifier = Modifier.fillMaxWidth()) {
                val templateNamesForSource = templates.map { it.name }
                val sourceItems = buildList {
                    add(presetLabel)
                    addAll(templateNamesForSource)
                    if (canUseDevice) add(deviceLabel)
                }
                val selectedTemplateIndex = selectedTemplate?.let { name ->
                    templateNamesForSource.indexOf(name).takeIf { it >= 0 }?.plus(1)
                }
                val selectedIndex = when {
                    selectedTemplateIndex != null -> selectedTemplateIndex
                    source == ProfileSource.DEVICE && canUseDevice -> sourceItems.lastIndex
                    else -> 0
                }
                AppSpinnerPreference(
                    title = stringResource(Res.string.profile_source),
                    items = sourceItems,
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = { itemIndex ->
                        when {
                            itemIndex == 0 -> viewModel.setSource(ProfileSource.PRESET, appSource)
                            itemIndex in 1 until 1 + templateNamesForSource.size ->
                                viewModel.applyTemplate(templateNamesForSource[itemIndex - 1], appSource)

                            canUseDevice && itemIndex == sourceItems.lastIndex ->
                                viewModel.setSource(ProfileSource.DEVICE, appSource)
                        }
                    },
                )
            }
        }
        if (appSource == OPPO) {
            item(key = "oppo-store-region") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    AppSpinnerPreference(
                        title = stringResource(Res.string.profile_store_region),
                        items = listOf(chinaRegionLabel, globalRegionLabel),
                        selectedIndex = if (oppoStoreRegion == OppoStoreRegion.CHINA) 0 else 1,
                        onSelectedIndexChange = { regionIndex ->
                            viewModel.setOppoStoreRegion(
                                if (regionIndex == 0) OppoStoreRegion.CHINA else OppoStoreRegion.GLOBAL
                            )
                        },
                    )
                }
            }
            items(oppoRequestValues, key = { "oppo-header-${it.first}" }) { (name, value) ->
                TextField(
                    value = value,
                    onValueChange = { viewModel.updateOppoRequestContext(name, it) },
                    label = name,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (appSource == SAMSUNG) {
            item(key = "samsung-store-region") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    AppSpinnerPreference(
                        title = stringResource(Res.string.profile_store_region),
                        items = listOf(chinaRegionLabel, globalRegionLabel),
                        selectedIndex = if (samsungStoreRegion == SamsungStoreRegion.CHINA) 0 else 1,
                        onSelectedIndexChange = { regionIndex ->
                            viewModel.setSamsungStoreRegion(
                                if (regionIndex == 0) SamsungStoreRegion.CHINA else SamsungStoreRegion.GLOBAL
                            )
                        },
                    )
                }
            }
            items(samsungRequestValues, key = { "samsung-header-${it.first}" }) { (name, value) ->
                TextField(
                    value = value,
                    onValueChange = { viewModel.updateSamsungRequestContext(name, it) },
                    label = name,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        items(DeviceProfileViewModel.fieldsFor(appSource), key = { it }) { name ->
            TextField(
                value = DeviceProfileViewModel.valueOf(profile, name),
                onValueChange = { viewModel.update(appSource, name, it) },
                label = name,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "save") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(
                    text = stringResource(Res.string.save),
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextButton(
                    text = stringResource(Res.string.profile_save_template),
                    onClick = onSaveTemplate,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (selectedTemplate != null) {
                    AppTextButton(
                        text = stringResource(Res.string.delete),
                        colors = ButtonDefaults.textButtonColors(
                            textColor = MiuixTheme.colorScheme.error,
                        ),
                        onClick = onRequestDeleteTemplate,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun deviceLabel(appSource: AppSource): String = stringResource(
    if (appSource == OPPO) {
        Res.string.profile_source_current_device
    } else {
        Res.string.profile_source_device
    }
)

@Composable
private fun SaveTemplateDialog(
    show: Boolean,
    defaultName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(show, defaultName) { mutableStateOf(defaultName) }
    PopupTheme { WindowDialog(
        show = show,
        title = stringResource(Res.string.profile_save_template),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.heightIn(max = 260.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(Res.string.profile_template_name),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                AppTextButton(
                    text = stringResource(Res.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                AppTextButton(
                    text = stringResource(Res.string.save),
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = { onSave(name) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } }
}

@Composable
private fun DeleteTemplateDialog(
    show: Boolean,
    templateName: String,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit,
) {
    PopupTheme { WindowDialog(
        show = show,
        title = stringResource(Res.string.delete),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(Res.string.profile_delete_template_confirm, templateName),
                color = MiuixTheme.colorScheme.onSurface,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                AppTextButton(
                    text = stringResource(Res.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                AppTextButton(
                    text = stringResource(Res.string.delete),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = { onDelete(templateName) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } }
}


/** Google Play 设备信息可编辑字段（含身份与下发定向；Features/GL.Extensions 等长列表不开放）。 */
private val GOOGLE_DEVICE_FIELD_KEYS = listOf(
    "Build.BRAND", "Build.MANUFACTURER", "Build.MODEL", "Build.DEVICE", "Build.PRODUCT",
    "Build.ID", "Build.FINGERPRINT", "Build.HARDWARE", "Build.BOOTLOADER",
    "Build.VERSION.SDK_INT", "Build.VERSION.RELEASE",
    "Platforms", "Screen.Width", "Screen.Height", "Screen.Density", "ScreenLayout",
    "Locales", "GL.Version", "TouchScreen", "Keyboard", "Navigation", "Client",
    "UserReadableName",
)

private typealias RegionOption = Pair<String, String>

/** Google Play 商店地区：`auto` 跟随出口 IP，其余为两位国家码（对应请求的 `gl` 参数）。 */
private val GooglePlayRegionOptions: List<RegionOption> = listOf(
    "auto" to "自动",
    "us" to "美国",
    "gb" to "英国",
    "ca" to "加拿大",
    "au" to "澳大利亚",
    "nz" to "新西兰",
    "jp" to "日本",
    "kr" to "韩国",
    "tw" to "台湾",
    "hk" to "香港",
    "mo" to "澳门",
    "sg" to "新加坡",
    "my" to "马来西亚",
    "th" to "泰国",
    "id" to "印度尼西亚",
    "ph" to "菲律宾",
    "vn" to "越南",
    "in" to "印度",
    "pk" to "巴基斯坦",
    "bd" to "孟加拉国",
    "lk" to "斯里兰卡",
    "np" to "尼泊尔",
    "mm" to "缅甸",
    "kh" to "柬埔寨",
    "la" to "老挝",
    "mn" to "蒙古",
    "kz" to "哈萨克斯坦",
    "uz" to "乌兹别克斯坦",
    "kg" to "吉尔吉斯斯坦",
    "tj" to "塔吉克斯坦",
    "tm" to "土库曼斯坦",
    "ae" to "阿联酋",
    "sa" to "沙特阿拉伯",
    "qa" to "卡塔尔",
    "kw" to "科威特",
    "bh" to "巴林",
    "om" to "阿曼",
    "jo" to "约旦",
    "lb" to "黎巴嫩",
    "iq" to "伊拉克",
    "il" to "以色列",
    "tr" to "土耳其",
    "az" to "阿塞拜疆",
    "am" to "亚美尼亚",
    "ge" to "格鲁吉亚",
    "de" to "德国",
    "fr" to "法国",
    "it" to "意大利",
    "es" to "西班牙",
    "pt" to "葡萄牙",
    "nl" to "荷兰",
    "be" to "比利时",
    "lu" to "卢森堡",
    "ch" to "瑞士",
    "at" to "奥地利",
    "ie" to "爱尔兰",
    "se" to "瑞典",
    "no" to "挪威",
    "dk" to "丹麦",
    "fi" to "芬兰",
    "is" to "冰岛",
    "pl" to "波兰",
    "cz" to "捷克",
    "sk" to "斯洛伐克",
    "si" to "斯洛文尼亚",
    "hu" to "匈牙利",
    "ro" to "罗马尼亚",
    "bg" to "保加利亚",
    "gr" to "希腊",
    "hr" to "克罗地亚",
    "rs" to "塞尔维亚",
    "ba" to "波黑",
    "mk" to "北马其顿",
    "me" to "黑山",
    "al" to "阿尔巴尼亚",
    "ee" to "爱沙尼亚",
    "lv" to "拉脱维亚",
    "lt" to "立陶宛",
    "mt" to "马耳他",
    "cy" to "塞浦路斯",
    "md" to "摩尔多瓦",
    "ua" to "乌克兰",
    "by" to "白俄罗斯",
    "br" to "巴西",
    "mx" to "墨西哥",
    "ar" to "阿根廷",
    "cl" to "智利",
    "co" to "哥伦比亚",
    "pe" to "秘鲁",
    "ve" to "委内瑞拉",
    "uy" to "乌拉圭",
    "py" to "巴拉圭",
    "bo" to "玻利维亚",
    "ec" to "厄瓜多尔",
    "cr" to "哥斯达黎加",
    "pa" to "巴拿马",
    "gt" to "危地马拉",
    "sv" to "萨尔瓦多",
    "hn" to "洪都拉斯",
    "ni" to "尼加拉瓜",
    "do" to "多米尼加",
    "jm" to "牙买加",
    "tt" to "特立尼达和多巴哥",
    "bz" to "伯利兹",
    "ag" to "安提瓜和巴布达",
    "sr" to "苏里南",
    "eg" to "埃及",
    "za" to "南非",
    "ng" to "尼日利亚",
    "ke" to "肯尼亚",
    "gh" to "加纳",
    "tz" to "坦桑尼亚",
    "ug" to "乌干达",
    "dz" to "阿尔及利亚",
    "ma" to "摩洛哥",
    "tn" to "突尼斯",
    "sn" to "塞内加尔",
    "ci" to "科特迪瓦",
    "cm" to "喀麦隆",
    "bj" to "贝宁",
    "bf" to "布基纳法索",
    "ml" to "马里",
    "ne" to "尼日尔",
    "gn" to "几内亚",
    "ga" to "加蓬",
    "cg" to "刚果（布）",
    "ao" to "安哥拉",
    "zm" to "赞比亚",
    "zw" to "津巴布韦",
    "mz" to "莫桑比克",
    "bw" to "博茨瓦纳",
    "na" to "纳米比亚",
    "mg" to "马达加斯加",
    "mu" to "毛里求斯",
    "rw" to "卢旺达",
    "cv" to "佛得角",
    "fj" to "斐济",
    "to" to "汤加",
    "pg" to "巴布亚新几内亚",
)