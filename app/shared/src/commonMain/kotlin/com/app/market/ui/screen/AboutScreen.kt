package com.app.market.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.market.viewmodel.UpdatesViewModel
import org.koin.compose.viewmodel.koinViewModel

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.market.resources.Res
import com.app.market.resources.about_appharbor
import com.app.market.resources.about_license_intro_dhizuku
import com.app.market.resources.about_open_source_licenses
import com.app.market.resources.about_fork_source
import com.app.market.resources.about_original_upstream
import com.app.market.resources.about_project
import com.app.market.resources.about_license_intro_miuix
import com.app.market.resources.about_license_intro_cmp
import com.app.market.resources.about_license_intro_ktor
import com.app.market.resources.about_license_intro_kotlinx
import com.app.market.resources.about_license_intro_koin
import com.app.market.resources.about_license_intro_androidx
import com.app.market.resources.about_license_intro_coil
import com.app.market.resources.about_license_intro_abdm
import com.app.market.resources.about_license_intro_hdiffpatch
import com.app.market.resources.about_license_intro_hiddenapi
import com.app.market.resources.about_license_intro_hypernotification
import com.app.market.resources.about_license_intro_shizuku
import com.app.market.resources.about_license_intro_composemedia
import com.app.market.resources.about_license_intro_aurora
import com.app.market.resources.about_license_intro_installerx
import com.app.market.resources.about_license_intro_appharbor
import com.app.market.resources.about_title
import com.app.market.resources.app_name
import com.app.market.resources.back
import com.app.market.resources.ic_launcher
import com.app.market.ui.component.SectionTitle
import com.app.market.ui.component.blur.BlurredBar
import com.app.market.ui.component.blur.ColorBlendToken
import com.app.market.ui.component.blur.rememberBlurBackdrop
import com.app.market.ui.component.blur.rememberBlurEnabled
import com.app.market.ui.component.effect.OsBackground
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun AboutScreen(
    onBack: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    // 视差进度逐帧变化：只以 State 传递，读取下沉到顶栏槽位与 graphicsLayer
    val scrollProgress = remember {
        derivedStateOf {
            when {
                lazyListState.firstVisibleItemIndex > 0 -> 1f
                else -> {
                    val spacer = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "logoSpacer" }
                    if (spacer != null && spacer.size > 0) {
                        (lazyListState.firstVisibleItemScrollOffset.toFloat() / spacer.size).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
            }
        }
    }
    val collapsed by remember { derivedStateOf { scrollProgress.value == 1f } }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null && collapsed

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                SmallTopAppBar(
                    title = stringResource(Res.string.about_title),
                    scrollBehavior = scrollBehavior,
                    color = if (blurActive || !collapsed) Color.Transparent else colorScheme.surface,
                    titleColor = colorScheme.onSurface.copy(
                        alpha = ((scrollProgress.value - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    ),
                    navigationIcon = {
                        val layoutDirection = LocalLayoutDirection.current
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(Res.string.back),
                                tint = colorScheme.onSurface,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                                },
                            )
                        }
                    },
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            AboutContent(
                innerPadding = innerPadding,
                scrollBehavior = scrollBehavior,
                lazyListState = lazyListState,
                scrollProgress = scrollProgress,
                onOpenUrl = onOpenUrl,
            )
        }
    }
}

@Composable
private fun AboutContent(
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    lazyListState: LazyListState,
    scrollProgress: State<Float>,
    onOpenUrl: (String) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    // 内容区至少铺满一屏（LazyColumn 视口高），内容更高时按内容增长——横屏矮视口下用固定高会把超出部分裁掉
    val viewportHeight by remember {
        derivedStateOf { lazyListState.layoutInfo.viewportSize.height }
    }

    val backdrop = rememberBlurBackdrop()

    val isDark = isSystemInDarkTheme()
    val blurEnabled by rememberBlurEnabled()
    val effectBackground = remember { isRuntimeShaderSupported() }

    val cardBlendColors = remember(isDark) {
        if (isDark) ColorBlendToken.Overlay_Extra_Thin_Dark else ColorBlendToken.Pured_Regular_Light
    }
    val logoBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1.toInt()), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500.toInt()), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a.toInt()), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f.toInt()), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200.toInt()), BlurBlendMode.Lab),
            )
        }
    }

    var logoHeightDp by remember { mutableStateOf(300.dp) }

    fun versionCodeProgress() = ((scrollProgress.value - 0.05f) / 0.15f).coerceIn(0f, 1f)
    fun projectNameProgress() = ((scrollProgress.value - 0.20f) / 0.15f).coerceIn(0f, 1f)
    fun iconProgress() = ((scrollProgress.value - 0.35f) / 0.15f).coerceIn(0f, 1f)

    val scrollPadding = PaddingValues(
        top = innerPadding.calculateTopPadding(),
        start = innerPadding.calculateStartPadding(layoutDirection),
        end = innerPadding.calculateEndPadding(layoutDirection),
    )
    val logoPadding = PaddingValues(
        top = innerPadding.calculateTopPadding() + 40.dp,
        start = innerPadding.calculateStartPadding(layoutDirection),
        end = innerPadding.calculateEndPadding(layoutDirection),
    )

    OsBackground(
        modifier = Modifier.fillMaxSize(),
        // 彩色流光背景常驻：不随滚动淡出（滚动到任何位置都保持）
        alpha = 1f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = logoPadding.calculateTopPadding() + 52.dp,
                    start = logoPadding.calculateStartPadding(layoutDirection),
                    end = logoPadding.calculateEndPadding(layoutDirection),
                )
                .onSizeChanged { size -> with(density) { logoHeightDp = size.height.toDp() } },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        alpha = 1 - iconProgress()
                        scaleX = 1 - iconProgress() * 0.05f
                        scaleY = 1 - iconProgress() * 0.05f
                    },
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .squircleClip(24.dp)
                        .background(Color.White),
                ) {
                    Image(
                        modifier = Modifier.size(88.dp),
                        painter = painterResource(Res.drawable.ic_launcher),
                        contentDescription = "icon",
                    )
                }
            }
            Text(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 5.dp)
                    .graphicsLayer {
                        alpha = 1 - projectNameProgress()
                        scaleX = 1 - projectNameProgress() * 0.05f
                        scaleY = 1 - projectNameProgress() * 0.05f
                    }
                    .then(
                        if (backdrop != null) {
                            Modifier.textureBlur(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(16.dp),
                                blurRadius = 150f,
                                noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                colors = BlurColors(blendColors = logoBlend),
                                contentBlendMode = BlendMode.DstIn,
                                enabled = true,
                            )
                        } else Modifier
                    ),
                text = stringResource(Res.string.app_name),
                color = colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 1 - versionCodeProgress()
                        scaleX = 1 - versionCodeProgress() * 0.05f
                        scaleY = 1 - versionCodeProgress() * 0.05f
                    },
                color = colorScheme.onSurfaceVariantSummary,
                text = "v${misc.VersionInfo.VERSION_NAME} (${misc.VersionInfo.VERSION_CODE})",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = scrollPadding.calculateTopPadding(),
                start = scrollPadding.calculateStartPadding(layoutDirection),
                end = scrollPadding.calculateEndPadding(layoutDirection),
            ),
        ) {
            item(key = "logoSpacer") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(logoHeightDp + 52.dp + logoPadding.calculateTopPadding() - scrollPadding.calculateTopPadding() + 126.dp),
                    contentAlignment = Alignment.TopCenter,
                    content = { },
                )
            }

            item(key = "about") {
                Column(modifier = Modifier.heightIn(min = with(density) { viewportHeight.toDp() }).padding(bottom = 12.dp)) {
                    SectionTitle(text = stringResource(Res.string.about_project))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .then(
                                if (backdrop != null) {
                                    Modifier.textureBlur(
                                        backdrop = backdrop,
                                        shape = RoundedCornerShape(16.dp),
                                        blurRadius = 60f,
                                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                        colors = BlurColors(blendColors = cardBlendColors),
                                        enabled = true,
                                    )
                                } else Modifier
                            ),
                        colors = CardDefaults.defaultColors(
                            if (backdrop != null && blurEnabled) Color.Transparent else colorScheme.surfaceContainer,
                            Color.Transparent,
                        ),
                    ) {
                        ArrowPreference(
                            title = "AppMarket",
                            summary = "gitlab.com/FengGuan/AppMarket",
                            onClick = { onOpenUrl("https://gitlab.com/FengGuan/AppMarket") },
                        )
                        // 「接收测试版」原来在「更新」分类里，收纳到 AppMarket（关于自身）这一块
                        val selfUpdateVm: UpdatesViewModel = koinViewModel()
                        val receivePrerelease by selfUpdateVm.receivePrerelease.collectAsStateWithLifecycle()
                        SwitchPreference(
                            title = "接收测试版",
                            summary = "是否接收 GitLab 预发布（Upcoming Release）的测试版本",
                            checked = receivePrerelease,
                            onCheckedChange = selfUpdateVm::setReceivePrerelease,
                        )
                        ArrowPreference(
                            title = stringResource(Res.string.about_appharbor),
                            summary = "github.com/BeiChen-CN/Trove\n" + stringResource(Res.string.about_license_intro_appharbor),
                            onClick = { onOpenUrl("https://github.com/BeiChen-CN/Trove") },
                        )
                        ArrowPreference(
                            title = stringResource(Res.string.about_fork_source),
                            summary = "github.com/zeroedx/AppMarket",
                            onClick = { onOpenUrl("https://github.com/zeroedx/AppMarket") },
                        )
                        ArrowPreference(
                            title = stringResource(Res.string.about_original_upstream),
                            summary = "github.com/YXBwbWFya2V0/AppMarket",
                            onClick = { onOpenUrl("https://github.com/YXBwbWFya2V0/AppMarket") },
                        )
                    }
                    SectionTitle(text = stringResource(Res.string.about_open_source_licenses))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .then(
                                if (backdrop != null) {
                                    Modifier.textureBlur(
                                        backdrop = backdrop,
                                        shape = RoundedCornerShape(16.dp),
                                        blurRadius = 60f,
                                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                        colors = BlurColors(blendColors = cardBlendColors),
                                        enabled = true,
                                    )
                                } else Modifier
                        ),
                        colors = CardDefaults.defaultColors(
                            if (backdrop != null && blurEnabled) Color.Transparent else colorScheme.surfaceContainer,
                            Color.Transparent,
                        ),
                    ) {
                        LicenseRow(
                            name = "miuix",
                            intro = stringResource(Res.string.about_license_intro_miuix),
                            license = "Apache-2.0",
                            onClick = { onOpenUrl("https://github.com/compose-miuix-ui/miuix") },
                        )
                        LicenseRow(
                            name = "JetBrains Compose Multiplatform",
                            intro = stringResource(Res.string.about_license_intro_cmp),
                            license = "Apache-2.0",
                            onClick = { onOpenUrl("https://github.com/JetBrains/compose-multiplatform") },
                        )
                        LicenseRow(
                            name = "Ktor",
                            intro = stringResource(Res.string.about_license_intro_ktor),
                            license = "Apache-2.0",
                            onClick = { onOpenUrl("https://github.com/ktorio/ktor") },
                        )
                        LicenseRow(
                            name = "Kotlinx (Coroutines / Serialization / DateTime)",
                            intro = stringResource(Res.string.about_license_intro_kotlinx),
                            license = "Apache-2.0",
                            onClick = { onOpenUrl("https://github.com/Kotlin/kotlinx.coroutines") },
                        )
                        LicenseRow(
                            name = "Koin",
                            intro = stringResource(Res.string.about_license_intro_koin),
                            license = "Apache-2.0",
                            onClick = { onOpenUrl("https://github.com/InsertKoinIO/koin") },
                        )
                        LicenseRow(
                            name = "AndroidX",
                            intro = stringResource(Res.string.about_license_intro_androidx),
                            license = "Apache-2.0",
                            onClick = { onOpenUrl("https://developer.android.com/jetpack/androidx") },
                        )
                        LicenseRow(
                            name = "coil",
                            intro = stringResource(Res.string.about_license_intro_coil),
                            license = "Apache-2.0",
                            onClick = { onOpenUrl("https://github.com/coil-kt/coil") },
                        )
                        LicenseRow(
                            name = "Komi Store",
                            intro = "GitHub 榜单与发现参考项目",
                            license = "Apache-2.0",
                            onClick = { onOpenUrl("https://github.com/komi-store/komi-store") },
                        )
                        LicenseRow(
                            name = "AB Download Manager",
                            intro = stringResource(Res.string.about_license_intro_abdm),
                            license = "Apache-2.0",
                            onClick = { onOpenUrl("https://github.com/amir1376/ab-download-manager") },
                        )
                        LicenseRow(
                            name = "HDiffPatch",
                            intro = stringResource(Res.string.about_license_intro_hdiffpatch),
                            license = "MIT",
                            onClick = { onOpenUrl("https://github.com/sisong/HDiffPatch") },
                        )
                        LicenseRow(
                            name = "HiddenApiBypass",
                            intro = stringResource(Res.string.about_license_intro_hiddenapi),
                            license = "Apache-2.0",
                            onClick = { onOpenUrl("https://github.com/LSPosed/AndroidHiddenApiBypass") },
                        )
                        LicenseRow(
                            name = "HyperNotification",
                            intro = stringResource(Res.string.about_license_intro_hypernotification),
                            license = "Apache-2.0",
                            onClick = { onOpenUrl("https://github.com/xzakota/HyperNotification") },
                        )
                        LicenseRow(
                            name = "Shizuku",
                            intro = stringResource(Res.string.about_license_intro_shizuku),
                            license = "Apache-2.0",
                            onClick = { onOpenUrl("https://github.com/RikkaApps/Shizuku") },
                        )
                        LicenseRow(
                            name = "Dhizuku",
                            intro = stringResource(Res.string.about_license_intro_dhizuku),
                            license = "GPL-3.0",
                            onClick = { onOpenUrl("https://github.com/iamr0s/Dhizuku") },
                        )
                        LicenseRow(
                            name = "ComposeMediaPlayer",
                            intro = stringResource(Res.string.about_license_intro_composemedia),
                            license = "MIT",
                            onClick = { onOpenUrl("https://github.com/kdroidFilter/ComposeMediaPlayer") },
                        )
                        LicenseRow(
                            name = "InstallerX Revived",
                            intro = stringResource(Res.string.about_license_intro_installerx),
                            license = "GPL-3.0",
                            onClick = { onOpenUrl("https://github.com/wxxsfxyzm/InstallerX-Revived") },
                        )
                        LicenseRow(
                            name = "Aurora Store",
                            intro = stringResource(Res.string.about_license_intro_aurora),
                            license = "GPL-3.0",
                            onClick = { onOpenUrl("https://github.com/whyorean/AuroraStore") },
                        )
                    }

                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
                }
            }
        }
    }
}


/**
 * 许可条目行（照搬 ArpCut 的 LicenseRow：名称 + 介绍 + 蓝色许可徽标，整行点击跳转网页）。
 */
@Composable
private fun LicenseRow(
    name: String,
    intro: String,
    license: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = intro,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            LicenseBadge(text = license)
        }
    }
}

/** 蓝色许可徽标（照搬 ArpCut 的 LicenseBadge） */
@Composable
private fun LicenseBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF3482FF))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}