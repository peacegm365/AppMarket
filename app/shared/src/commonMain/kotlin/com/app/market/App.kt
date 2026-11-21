package com.app.market

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import com.app.market.platform.rememberAppBackgroundImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.market.domain.model.install.InstallUserAction
import com.app.market.domain.model.market.MarketLink
import com.app.market.domain.model.theme.ThemeColorMode
import com.app.market.domain.model.theme.ThemeColorSource
import com.app.market.domain.model.theme.ThemeColorSpec
import com.app.market.domain.model.theme.ThemePaletteStyle
import com.app.market.domain.repository.DownloadRepository
import com.app.market.domain.repository.InstallerPreferencesRepository
import com.app.market.domain.repository.ProfileRepository
import com.app.market.domain.repository.ThemePreferencesRepository
import com.app.market.domain.repository.UpdatePreferencesRepository
import com.app.market.platform.ApplyPredictiveBackPreference
import com.app.market.platform.UiPlatform
import com.app.market.resources.Res
import com.app.market.resources.cancel
import com.app.market.resources.confirm
import com.app.market.resources.go_to_settings
import com.app.market.resources.install_failed_title
import com.app.market.resources.install_permission_message
import com.app.market.resources.install_permission_settings_unavailable
import com.app.market.resources.install_permission_title
import com.app.market.resources.installer_delta_fallback_toast
import com.app.market.ui.component.AppTextButton
import com.app.market.ui.navigation.AppNavigation
import com.app.market.ui.theme.LocalEnableBlur
import com.app.market.ui.theme.LocalOpaqueSurface
import com.app.market.ui.theme.LocalAppBackgroundImage
import com.app.market.ui.theme.LocalEnableFloatingBottomBar
import com.app.market.ui.theme.LocalLiquidBottomBarStyle
import com.app.market.ui.theme.LocalEnableNavigationBadge
import com.app.market.ui.util.LocalStripAppNameSubtitle
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemeColorSpec as MiuixColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle as MiuixPaletteStyle
import com.app.market.ui.theme.PopupTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun App(
    externalDetailLink: MarketLink? = null,
    externalDetailQuery: String? = null,
    onExternalDetailConsumed: (MarketLink) -> Unit = {},
    externalSearchKeyword: String? = null,
    onExternalSearchConsumed: (String) -> Unit = {},
    externalOpenDownloads: Boolean = false,
    onExternalDownloadsConsumed: () -> Unit = {},
    externalManualQuery: String? = null,
    onExternalManualConsumed: (String) -> Unit = {},
) {
    val profileStore = koinInject<ProfileRepository>()
    val downloads = koinInject<DownloadRepository>()
    val uiPlatform = koinInject<UiPlatform>()
    val updatePrefs = koinInject<UpdatePreferencesRepository>()
    val themePrefs = koinInject<ThemePreferencesRepository>()
    val themeColorMode by themePrefs.colorMode.collectAsStateWithLifecycle()
    val themeColorSource by themePrefs.colorSource.collectAsStateWithLifecycle()
    val themeSeedColor by themePrefs.seedColor.collectAsStateWithLifecycle()
    val themePaletteStyle by themePrefs.paletteStyle.collectAsStateWithLifecycle()
    val themeColorSpec by themePrefs.colorSpec.collectAsStateWithLifecycle()
    val amoledDark by themePrefs.amoledDark.collectAsStateWithLifecycle()
    val enableBlur by themePrefs.enableBlur.collectAsStateWithLifecycle()
    val enableFloatingBottomBar by themePrefs.enableFloatingBottomBar.collectAsStateWithLifecycle()
    val liquidBottomBarStyle by themePrefs.liquidBottomBarStyle.collectAsStateWithLifecycle()
    val enableNavigationBadge by themePrefs.enableNavigationBadge.collectAsStateWithLifecycle()
    val enablePredictiveBack by themePrefs.enablePredictiveBack.collectAsStateWithLifecycle()
    val pageScale by themePrefs.pageScale.collectAsStateWithLifecycle()
    val stripAppNameSubtitle by updatePrefs.stripAppNameSubtitle.collectAsStateWithLifecycle()
    val pendingUserAction by downloads.pendingUserAction.collectAsStateWithLifecycle(
        minActiveState = Lifecycle.State.RESUMED,
    )
    val failureMessage = (pendingUserAction as? InstallUserAction.InstallationFailed)?.message
    var retainedFailureMessage by remember { mutableStateOf("") }
    LaunchedEffect(failureMessage) {
        if (failureMessage != null) retainedFailureMessage = failureMessage
    }
    val settingsUnavailableMessage = stringResource(Res.string.install_permission_settings_unavailable)
    val installerPrefs = koinInject<InstallerPreferencesRepository>()
    LaunchedEffect(Unit) {
        downloads.deltaFallbacks.collect { fallback ->
            if (!installerPrefs.deltaFallbackNoticeEnabled()) return@collect
            uiPlatform.showToast(
                getString(Res.string.installer_delta_fallback_toast, fallback.artifactName, fallback.reason)
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { profileStore.syncFromServerIfDue() } }
    val controller = remember(themeColorMode, themeColorSource, themeSeedColor, themePaletteStyle, themeColorSpec) {
        val darkMode = when (themeColorMode) {
            ThemeColorMode.SYSTEM -> ColorSchemeMode.System
            ThemeColorMode.LIGHT -> ColorSchemeMode.Light
            ThemeColorMode.DARK -> ColorSchemeMode.Dark
        }
        val monetMode = when (themeColorMode) {
            ThemeColorMode.SYSTEM -> ColorSchemeMode.MonetSystem
            ThemeColorMode.LIGHT -> ColorSchemeMode.MonetLight
            ThemeColorMode.DARK -> ColorSchemeMode.MonetDark
        }
        val paletteStyle = when (themePaletteStyle) {
            ThemePaletteStyle.TONAL_SPOT -> MiuixPaletteStyle.TonalSpot
            ThemePaletteStyle.NEUTRAL -> MiuixPaletteStyle.Neutral
            ThemePaletteStyle.VIBRANT -> MiuixPaletteStyle.Vibrant
            ThemePaletteStyle.EXPRESSIVE -> MiuixPaletteStyle.Expressive
            ThemePaletteStyle.RAINBOW -> MiuixPaletteStyle.Rainbow
            ThemePaletteStyle.FRUIT_SALAD -> MiuixPaletteStyle.FruitSalad
            ThemePaletteStyle.MONOCHROME -> MiuixPaletteStyle.Monochrome
            ThemePaletteStyle.FIDELITY -> MiuixPaletteStyle.Fidelity
            ThemePaletteStyle.CONTENT -> MiuixPaletteStyle.Content
        }
        val colorSpec = when (themeColorSpec) {
            ThemeColorSpec.SPEC_2021 -> MiuixColorSpec.Spec2021
            ThemeColorSpec.SPEC_2025 -> MiuixColorSpec.Spec2025
        }
        when (themeColorSource) {
            // 只有显式选了「取色（Monet）」才跟随壁纸。
            // 之前 CUSTOM 也走 monetMode，结果一打开就在用壁纸取色 —— 品牌色被系统调色板盖掉了。
            ThemeColorSource.MONET -> ThemeController(
                colorSchemeMode = monetMode,
                paletteStyle = paletteStyle,
                colorSpec = colorSpec,
            )

            ThemeColorSource.DEFAULT -> ThemeController(
                colorSchemeMode = darkMode,
                paletteStyle = paletteStyle,
                colorSpec = colorSpec,
            )

            ThemeColorSource.CUSTOM -> ThemeController(
                colorSchemeMode = darkMode,
                keyColor = Color(themeSeedColor ?: DEFAULT_SEED_COLOR),
                paletteStyle = paletteStyle,
                colorSpec = colorSpec,
            )
        }
    }
    ApplyPredictiveBackPreference(enablePredictiveBack)
    val systemDark = isSystemInDarkTheme()
    val themeIsDark = when (controller.colorSchemeMode) {
        ColorSchemeMode.Dark, ColorSchemeMode.MonetDark -> true
        ColorSchemeMode.Light, ColorSchemeMode.MonetLight -> false
        ColorSchemeMode.System, ColorSchemeMode.MonetSystem -> systemDark
    }
    val baseColors = controller.currentColors()
    // 品牌色：直接取自 Google Play 图标的四色（蓝 #4285F4 / 绿 #34A853 / 黄 #FBBC05 / 红 #EA4335）。
    // 只在这里钉死主色与次色，其余明暗层级仍由色彩算法推导，保证可读性。
    val brandColors = if (themeColorSource == ThemeColorSource.CUSTOM) {
        if (themeIsDark) {
            baseColors.copy(
                primary = Color(BrandBlueDark),
                onPrimary = Color(BrandBlueOnDark),
                primaryVariant = Color(BrandBlueDarkVariant),
                onPrimaryVariant = Color(BrandBlueOnDark),
                secondary = Color(BrandGreenDark),
                onSecondary = Color(BrandGreenOnDark),
            )
        } else {
            baseColors.copy(
                primary = Color(BrandBlue),
                onPrimary = Color.White,
                primaryVariant = Color(BrandBlueDeep),
                onPrimaryVariant = Color.White,
                secondary = Color(BrandGreen),
                onSecondary = Color.White,
            )
        }
    } else {
        baseColors
    }
    val themeColors = if (amoledDark && themeIsDark) {
        brandColors.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color.Black,
            surfaceContainerHigh = Color(0xFF0C0C0C),
            surfaceContainerHighest = Color(0xFF151515),
        )
    } else {
        baseColors
    }
    // 自定义启动图/背景图：未完成，懒得写了，暂时停用；保留实现和用户图片。
    // val backgroundImage = rememberAppBackgroundImage()
    val backgroundImage: androidx.compose.ui.graphics.ImageBitmap? = null
    val displayColors = if (backgroundImage != null) themeColors.copy(
        background = Color.Transparent,
        surface = Color.Transparent,
    ) else themeColors
    MiuixTheme(colors = displayColors) {
        val systemDensity = LocalDensity.current
        val scaledDensity = remember(systemDensity, pageScale) {
            Density(systemDensity.density * pageScale, systemDensity.fontScale)
        }
        CompositionLocalProvider(
            LocalContentColor provides MiuixTheme.colorScheme.onBackground,
            LocalStripAppNameSubtitle provides stripAppNameSubtitle,
            LocalDensity provides scaledDensity,
            LocalEnableBlur provides enableBlur,
            LocalOpaqueSurface provides themeColors.surface,
            LocalAppBackgroundImage provides backgroundImage,
            LocalEnableFloatingBottomBar provides enableFloatingBottomBar,
            LocalLiquidBottomBarStyle provides liquidBottomBarStyle,
            LocalEnableNavigationBadge provides enableNavigationBadge,
        ) {
            Box(Modifier.fillMaxSize()) {
            if (backgroundImage != null) {
                Image(bitmap = backgroundImage, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            AppNavigation(
                externalDetailLink = externalDetailLink,
                externalDetailQuery = externalDetailQuery,
                onExternalDetailConsumed = onExternalDetailConsumed,
                externalSearchKeyword = externalSearchKeyword,
                onExternalSearchConsumed = onExternalSearchConsumed,
                externalOpenDownloads = externalOpenDownloads,
                onExternalDownloadsConsumed = onExternalDownloadsConsumed,
                externalManualQuery = externalManualQuery,
                onExternalManualConsumed = onExternalManualConsumed,
            )
            InstallFailureDialog(
                show = failureMessage != null,
                message = failureMessage ?: retainedFailureMessage,
                onDismiss = downloads::consumePendingUserAction,
            )
            UnknownSourcesPermissionDialog(
                show = pendingUserAction == InstallUserAction.GrantUnknownSourcesPermission,
                onDismiss = downloads::consumePendingUserAction,
                onOpenSettings = {
                    downloads.consumePendingUserAction()
                    if (!uiPlatform.openUnknownSourcesSettings()) {
                        uiPlatform.showToast(settingsUnavailableMessage)
                    }
                },
            )
            }
        }
    }
}

@Composable
private fun InstallFailureDialog(
    show: Boolean,
    message: String,
    onDismiss: () -> Unit,
) {
    PopupTheme { WindowDialog(
        show = show,
        title = stringResource(Res.string.install_failed_title),
        onDismissRequest = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SelectionContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = message,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
            AppTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(Res.string.confirm),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = onDismiss,
            )
        }
    } }
}

@Composable
private fun UnknownSourcesPermissionDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    PopupTheme { WindowDialog(
        show = show,
        title = stringResource(Res.string.install_permission_title),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(Res.string.install_permission_message),
                color = MiuixTheme.colorScheme.onSurface,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                AppTextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(Res.string.cancel),
                    onClick = onDismiss,
                )
                Spacer(Modifier.width(16.dp))
                AppTextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(Res.string.go_to_settings),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = onOpenSettings,
                )
            }
        }
    } }
}

private const val DEFAULT_SEED_COLOR = 0xFF4285F4L

// ---- Google Play 品牌四色（从图标像素直接取样） ----
// 蓝 #4285F4 / 绿 #34A853 / 黄 #FBBC05 / 红 #EA4335
private const val BrandBlue = 0xFF4285F4L
private const val BrandBlueDeep = 0xFF3367D6L
private const val BrandGreen = 0xFF34A853L

// 暗色模式下用品牌色的高亮变体 + 深色前景区，避免亮蓝配白字对比度不足
private const val BrandBlueDark = 0xFF8AB4F8L
private const val BrandBlueDarkVariant = 0xFFAECBFAL
private const val BrandBlueOnDark = 0xFF062E6FL
private const val BrandGreenDark = 0xFF81C995L
private const val BrandGreenOnDark = 0xFF0B3D1EL