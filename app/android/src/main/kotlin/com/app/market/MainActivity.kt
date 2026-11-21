package com.app.market

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.MarketLink
import com.app.market.domain.model.market.MarketLinkParser
import com.app.market.domain.model.theme.ThemeColorMode
import com.app.market.domain.repository.ThemePreferencesRepository
import com.app.market.domain.repository.UpdatePreferencesRepository
import com.app.market.platform.AndroidPermissionCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MainActivity : ComponentActivity(), KoinComponent {

    private val externalDetailRequest = MutableStateFlow<ExternalDetailRequest?>(null)
    private val externalSearchKeyword = MutableStateFlow<String?>(null)
    private val externalDownloadsRequest = MutableStateFlow(false)
    private val externalManualQuery = MutableStateFlow<String?>(null)
    private val permissions: AndroidPermissionCoordinator by inject()
    private val saveLocations: com.app.market.platform.AndroidSaveLocationCoordinator by inject()
    private val updatePreferences: UpdatePreferencesRepository by inject()
    private val themePreferences: ThemePreferencesRepository by inject()
    private var contentReady = false

    private val installedAppsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissions.dispatchInstalledAppsResult(granted)
        }
    private val postNotificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            permissions.dispatchNotificationsResult()
        }
    private val saveLocationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            saveLocations.dispatchResult(result.data?.dataString)
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        // 自定义启动图/背景图：未完成，懒得写了，暂时停用，保留原有实现。
        val startupImage = if (false && getSharedPreferences("startup_image", MODE_PRIVATE).getBoolean("enabled", false)) {
            runCatching {
                val file = File(filesDir, "startup_image.bin")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        } else null
        super.onCreate(savedInstanceState)
        if (startupImage != null) {
            window.setBackgroundDrawable(BitmapDrawable(resources, startupImage).apply { gravity = Gravity.FILL })
        }
        splashScreen.setKeepOnScreenCondition { !contentReady }
        consumeExternalIntent(intent)
        permissions.registerInstalledApps(installedAppsPermissionLauncher)
        permissions.registerNotifications(postNotificationsPermissionLauncher)
        saveLocations.register(saveLocationLauncher)
        setContent {
            val colorMode by themePreferences.colorMode.collectAsState()
            val darkMode = when (colorMode) {
                ThemeColorMode.LIGHT -> false
                ThemeColorMode.DARK -> true
                ThemeColorMode.SYSTEM -> isSystemInDarkTheme()
            }
            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkMode },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                onDispose {}
            }

            val detailRequest by externalDetailRequest.collectAsState()
            val searchKeyword by externalSearchKeyword.collectAsState()
            val downloadsRequest by externalDownloadsRequest.collectAsState()
            val manualQuery by externalManualQuery.collectAsState()
            val preferencesReady by updatePreferences.initialized.collectAsState()
            val themePreferencesReady by themePreferences.initialized.collectAsState()
            val startupBitmap = remember { startupImage?.asImageBitmap() }
            val appReady = preferencesReady && themePreferencesReady
            var showStartup by remember { mutableStateOf(startupBitmap != null) }
            LaunchedEffect(appReady, startupBitmap) {
                if (appReady && startupBitmap != null) {
                    window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    delay(250)
                    showStartup = false
                }
            }
            Box(Modifier.fillMaxSize()) {
            if (appReady) {
                App(
                    externalDetailLink = detailRequest?.link,
                    externalDetailQuery = detailRequest?.encodedQuery,
                    onExternalDetailConsumed = { consumed ->
                        if (externalDetailRequest.value?.link == consumed) {
                            externalDetailRequest.value = null
                        }
                    },
                    externalSearchKeyword = searchKeyword,
                    onExternalSearchConsumed = { consumed ->
                        if (externalSearchKeyword.value == consumed) {
                            externalSearchKeyword.value = null
                        }
                    },
                    externalOpenDownloads = downloadsRequest,
                    onExternalDownloadsConsumed = { externalDownloadsRequest.value = false },
                    externalManualQuery = manualQuery,
                    onExternalManualConsumed = { consumed ->
                        if (externalManualQuery.value == consumed) {
                            externalManualQuery.value = null
                        }
                    },
                )
            }
            if (startupBitmap != null) {
                AnimatedVisibility(visible = showStartup, exit = fadeOut(tween(220))) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(if (darkMode) ComposeColor(0xFF101014) else ComposeColor.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = startupBitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            }
            if (appReady || startupBitmap != null) SideEffect { contentReady = true }
  
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeExternalIntent(intent)
    }

    private fun consumeExternalIntent(intent: Intent?) {
        if (intent.isDownloadsRequest()) {
            externalDownloadsRequest.value = true
            return
        }
        val keyword = intent.searchKeyword()
        if (keyword != null) {
            externalSearchKeyword.value = keyword
            return
        }
        val manualQuery = intent.manualQueryRequest()
        if (manualQuery != null) {
            externalManualQuery.value = manualQuery
            return
        }
        externalDetailRequest.value = intent.detailRequest()
    }

    // 全局搜索的「去应用商店搜索」：market://search?q=xxx；命中但无关键词时返回空串，仅切到搜索页
    private fun Intent?.searchKeyword(): String? {
        if (this?.action != Intent.ACTION_VIEW) return null
        val uri = data ?: return null
        if (uri.scheme.orEmpty().lowercase() !in setOf("market", "mimarket")) return null
        if (uri.host.orEmpty().lowercase() !in setOf("search", "searchresult")) return null
        val keys = listOf("q", "keyword", "query", "key", "kw")
        return keys.firstNotNullOfOrNull { uri.getQueryParameter(it)?.asKeyword() }
            ?: keys.firstNotNullOfOrNull { getStringExtra(it)?.asKeyword() }
            ?: getStringExtra(Intent.EXTRA_TEXT)?.asKeyword()
            ?: ""
    }

    private fun String.asKeyword(): String? = trim().take(100).takeIf(String::isNotEmpty)

    // 多任务下载通知的落点：market://downloads 打开「下载中的应用」页
    private fun Intent?.isDownloadsRequest(): Boolean {
        if (this?.action != Intent.ACTION_VIEW) return false
        val uri = data ?: return false
        return uri.scheme.orEmpty().lowercase() in setOf("market", "mimarket") &&
                uri.host.orEmpty().lowercase() == "downloads"
    }

    override fun onResume() {
        super.onResume()
        permissions.checkInstalledAppsOnResume()
    }

    override fun onDestroy() {
        permissions.unregisterInstalledApps(installedAppsPermissionLauncher)
        permissions.unregisterNotifications(postNotificationsPermissionLauncher)
        super.onDestroy()
    }

    // 系统分享文本（ACTION_SEND text/*）：抠出包名后落到「手动检查更新」页预填
    private fun Intent?.manualQueryRequest(): String? {
        if (this?.action != Intent.ACTION_SEND) return null
        if (type?.startsWith("text/") != true) return null
        val text = getStringExtra(Intent.EXTRA_TEXT)?.take(2000) ?: return null
        MarketLinkParser.parseText(text)?.packageName?.takeIf(String::isNotBlank)?.let { return it }
        return text.trim().takeIf(::isPkgLikeText)
    }

    private fun isPkgLikeText(value: String): Boolean =
        value.length in 3..255 && value.any { it == '.' } &&
            value.all { it.isLetterOrDigit() || it == '_' || it == '.' }

    private fun Intent?.detailRequest(): ExternalDetailRequest? {
        if (this?.action != Intent.ACTION_VIEW) return null
        val uri = data
        val link = uri?.toString()?.let(MarketLinkParser::parse)
            ?: stringExtraPackageName()?.let { MarketLink(it, source = null) }
            ?: return null
        // 小米详情沿用原链接的查询参数（含 ref 等来源统计），其余来源只按包名 / 站内 id 加载
        val encodedQuery = uri?.encodedQuery
            ?.takeIf { it.isNotBlank() && (link.source == null || link.source == AppSource.XIAOMI) }
        return ExternalDetailRequest(link, encodedQuery)
    }

    private fun Intent.stringExtraPackageName(): String? =
        listOf("id", "packageName", "pName", "pkg", "package", "android.intent.extra.PACKAGE_NAME")
            .firstNotNullOfOrNull { key -> getStringExtra(key)?.asPackageName() }

    private fun String.asPackageName(): String? =
        trim().takeIf { value ->
            value.length in 3..255 &&
                    value.any { it == '.' } &&
                    value.all { it.isLetterOrDigit() || it == '_' || it == '.' }
        }
}

private data class ExternalDetailRequest(
    val link: MarketLink,
    val encodedQuery: String?,
)