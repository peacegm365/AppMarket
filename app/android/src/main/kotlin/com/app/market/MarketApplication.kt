package com.app.market

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import com.app.market.data.platform.DebugLogging
import com.app.market.data.platform.ThemePlatformPreferenceContract
import com.app.market.di.dataModules
import com.app.market.di.uiPlatformModule
import com.app.market.di.viewModelModule
import com.app.market.domain.repository.DownloadRepository
import com.app.market.domain.repository.UpdatePreferencesRepository
import com.app.market.install.InstallNotificationCoordinator
import com.app.market.install.InstallPackageChangedReceiver
import com.app.market.install.androidInstallPlatformModule
import com.app.market.platform.CrashLogger
import com.app.market.platform.setupImageLoader
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.lsposed.hiddenapibypass.HiddenApiBypass

class MarketApplication : Application(), KoinComponent {
    companion object {
        private fun setEnableOnBackInvokedCallback(appInfo: ApplicationInfo, enabled: Boolean) {
            runCatching {
                val method = ApplicationInfo::class.java.getDeclaredMethod(
                    "setEnableOnBackInvokedCallback",
                    Boolean::class.javaPrimitiveType,
                )
                method.isAccessible = true
                method.invoke(appInfo, enabled)
            }
        }
    }

    private val downloads: DownloadRepository by inject()
    private val installerPrefs: com.app.market.domain.repository.InstallerPreferencesRepository by inject()
    private val updatePrefs: UpdatePreferencesRepository by inject()
    private val notifications: InstallNotificationCoordinator by inject()
    private val httpClient: HttpClient by inject()
    private val packageChangedReceiver = InstallPackageChangedReceiver()

    override fun onCreate() {
        super.onCreate()
        CrashLogger.init(this)
        // 日志开关（通用设置）：关闭时不写日志，也不在 Download 目录创建任何文件；
        // 启动先按当前值设置一次，并持续观察变化（运行中切开关即时生效，不再只在启动时读一次）
        runCatching {
            val logEnabled = runBlocking {
                withTimeoutOrNull(3000) {
                    updatePrefs.initialized.first { it }
                    updatePrefs.logEnabled.first()
                } ?: true
            }
            CrashLogger.enabled = logEnabled
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                updatePrefs.logEnabled.collect { CrashLogger.enabled = it }
            }
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                updatePrefs.logMaxSize.collect { CrashLogger.maxBytes = it }
            }
            // 「安装包保存路径」：log 日志跟随安装包落位目录
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                runCatching {
                    CrashLogger.setBaseDirectory(installerPrefs.packageSaveDirectory())
                }
            }
        }
        // Google Play 设备信息：设置为「从设备获取」时用本机 Build 覆盖预置指纹（默认预置）
        runCatching {
            val fromDevice = runBlocking {
                withTimeoutOrNull(3000) { updatePrefs.googleDeviceSource.first() } ?: false
            }
            if (fromDevice) {
                com.app.market.data.remote.google.GoogleDevicePropertiesOverrides = mapOf(
                    "Build.HARDWARE" to (Build.HARDWARE ?: ""),
                    "Build.BRAND" to (Build.BRAND ?: ""),
                    "Build.MODEL" to (Build.MODEL ?: ""),
                    "Build.PRODUCT" to (Build.PRODUCT ?: ""),
                    "Build.DEVICE" to (Build.DEVICE ?: ""),
                    "Build.FINGERPRINT" to (Build.FINGERPRINT ?: ""),
                    "Build.ID" to (Build.ID ?: ""),
                    "Build.MANUFACTURER" to (Build.MANUFACTURER ?: ""),
                    "Build.VERSION.SDK_INT" to Build.VERSION.SDK_INT.toString(),
                    "Build.VERSION.RELEASE" to (Build.VERSION.RELEASE ?: ""),
                )
            }
        }
        CrashLogger.log("Application.onCreate begin")
        DebugLogging.enabled = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val predictiveBackEnabled = getSharedPreferences(
                ThemePlatformPreferenceContract.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).getBoolean(ThemePlatformPreferenceContract.PREDICTIVE_BACK_KEY, false)
            setEnableOnBackInvokedCallback(applicationInfo, predictiveBackEnabled)
        }
        val applicationModule = module {
            single<Context> { this@MarketApplication.applicationContext }
        }
        // data 模块读本地应用图标时需要 Context（LSPosed 模块仓库/更新列表用）
        com.app.market.data.platform.AppIconContextHolder.context = applicationContext
        startKoin {
            modules(
                applicationModule,
                androidInstallPlatformModule,
                *dataModules.toTypedArray(),
                uiPlatformModule,
                viewModelModule,
            )
        }
        setupImageLoader(httpClient)
        registerPackageChangedReceiver()
        // Eagerly construct the download graph so persisted task and update-history
        // reconciliation also run when the user has not opened a download screen.
        downloads
        // Eagerly start the notification coordinator so it observes install progress
        // from process start, independent of any UI or the foreground service.
        notifications
        autoStartProxyCore()
        CrashLogger.log("Application.onCreate done")
    }

    /**
     * 每次冷启动都重新拉一次订阅，把节点列表刷新到最新。
     *
     * 只在用户已经配过订阅地址时执行；内核起来后会把代理指向本地混合端口。
     * 全程后台、失败静默（失败原因会在代理页显示），不影响启动速度。
     */
    private fun autoStartProxyCore() {
        Thread {
            runCatching {
                val koin = org.koin.core.context.GlobalContext.getOrNull() ?: return@runCatching
                val repository = koin.get<com.app.market.domain.repository.ProxySettingsRepository>()
                val clash = koin.get<com.app.market.domain.repository.ClashCoreController>()
                runBlocking {
                    // 等持久化设置加载完，避免读到空订阅地址
                    withTimeoutOrNull(8_000) {
                        repository.loaded.first { it }
                    }
                    val url = repository.subscriptionUrl.value
                    // 记录代理状态：上次开着才自动恢复内核；用户手动关过就保持关闭
                    if (url.isNotBlank() && repository.enabled.value) {
                        CrashLogger.log("auto proxy core: start with saved subscription")
                        // 随机端口开关：开=内核随机分配；关=固定用保存的端口
                        if (!repository.randomPort.value) {
                            clash.setMixedPort(repository.mixedPort.value.toIntOrNull() ?: 0)
                        }
                        clash.start(url)
                        // 端口留空时内核会随机分配，必须写回设置，否则代理页看到的端口与内核实际不一致
                        val actualPort = clash.mixedPort.value
                        if (actualPort > 0) repository.setMixedPort(actualPort.toString())
                        repository.setType("http")
                        repository.setHost("127.0.0.1")
                        repository.setPort(actualPort.toString())
                    }
                }
            }.onFailure { CrashLogger.log("auto proxy core failed: $it") }
        }.apply { isDaemon = true }.start()
    }

    private fun registerPackageChangedReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageChangedReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(packageChangedReceiver, filter)
        }
    }
}