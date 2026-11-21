package com.app.market.di

import com.app.market.data.download.RecordingDownloadRepositoryImpl
import com.app.market.data.platform.debugLog
import com.app.market.data.remote.honor.HonorApi
import com.app.market.data.remote.honor.HonorProtocol
import com.app.market.data.remote.huawei.HuaweiApi
import com.app.market.data.remote.huawei.HuaweiProtocol
import com.app.market.data.remote.oppo.OppoApi
import com.app.market.data.remote.samsung.SamsungApi
import com.app.market.data.remote.taptap.TapTapApi
import com.app.market.data.remote.taptap.TapTapApiConfig
import com.app.market.data.remote.vivo.VivoApi
import com.app.market.data.remote.vivo.VivoAuroraApi
import com.app.market.data.remote.vivo.VivoUpdateApi
import com.app.market.data.remote.wandoujia.WandoujiaApi
import com.app.market.data.remote.wandoujia.WandoujiaApiConfig
import com.app.market.data.remote.xiaomi.UpdateInfoCache
import com.app.market.data.remote.xiaomi.XiaomiApi
import com.app.market.data.remote.xiaomi.XiaomiClient
import com.app.market.data.remote.xiaomi.XiaomiHttpClient
import com.app.market.data.repository.AnonymousAccountRepositoryImpl
import com.app.market.data.repository.FdroidRepositoryImpl
import com.app.market.data.repository.HonorRepositoryImpl
import com.app.market.data.repository.HuaweiRepositoryImpl
import com.app.market.data.repository.MarketLinkResolverImpl
import com.app.market.data.repository.MarketRepositoryImpl
import com.app.market.data.repository.MarketSourceRepositoryImpl
import com.app.market.data.repository.OppoRepositoryImpl
import com.app.market.data.repository.ProfileRepositoryImpl
import com.app.market.data.repository.SamsungRepositoryImpl
import com.app.market.data.remote.appchina.AppchinaApi
import com.app.market.data.remote.appchina.AppchinaApiConfig
import com.app.market.data.remote.apkpure.ApkPureApi
import com.app.market.data.remote.apkpure.ApkPureApiConfig
import com.app.market.data.remote.aptoide.AptoideApi
import com.app.market.data.remote.aptoide.AptoideApiConfig
import com.app.market.data.remote.box7723.Box7723Api
import com.app.market.data.remote.box7723.Box7723ApiConfig
import com.app.market.data.remote.fdroid.FdroidApi
import com.app.market.data.remote.fdroid.FdroidApiConfig
import com.app.market.data.remote.kuaibao.KuaibaoApi
import com.app.market.data.remote.kuaibao.KuaibaoApiConfig
import com.app.market.data.remote.tencent.TencentApi
import com.app.market.data.remote.tencent.TencentApiConfig
import com.app.market.data.repository.AppchinaRepositoryImpl
import com.app.market.data.repository.ApkPureRepositoryImpl
import com.app.market.data.repository.AptoideRepositoryImpl
import com.app.market.data.repository.Box7723RepositoryImpl
import com.app.market.data.repository.KuaibaoRepositoryImpl
import com.app.market.data.repository.TencentRepositoryImpl
import com.app.market.domain.repository.AppchinaRepository
import com.app.market.domain.repository.ApkPureRepository
import com.app.market.domain.repository.AptoideRepository
import com.app.market.domain.repository.Box7723Repository
import com.app.market.domain.repository.KuaibaoRepository
import com.app.market.domain.repository.TencentRepository
import com.app.market.data.remote.lsposed.LsposedApi
import com.app.market.data.remote.releases.GithubTokenHolder
import com.app.market.data.remote.releases.ReleaseApi
import com.app.market.data.repository.LsposedRepository
import com.app.market.data.repository.ReleaseSourceRepository
import com.app.market.data.store.ReleaseTokenRepositoryImpl
import com.app.market.domain.repository.ReleaseTokenRepository
import com.app.market.data.remote.coolapk.CoolapkApi
import com.app.market.data.remote.google.GooglePlayApi
import com.app.market.data.repository.CoolapkRepositoryImpl
import com.app.market.data.repository.GooglePlayRepositoryImpl
import com.app.market.data.repository.TapTapRepositoryImpl
import com.app.market.data.repository.TodayRepositoryImpl
import com.app.market.data.repository.VivoRepositoryImpl
import com.app.market.data.repository.WandoujiaRepositoryImpl
import com.app.market.data.store.SearchHistoryRepositoryImpl
import com.app.market.data.store.ThemePreferencesRepositoryImpl
import com.app.market.data.store.ProxySettingsRepositoryImpl
import com.app.market.data.store.UpdateHistoryRepositoryImpl
import com.app.market.data.store.InstallHistoryRepositoryImpl
import com.app.market.data.store.TranslationRepositoryImpl
import com.app.market.data.store.UpdatePreferencesRepositoryImpl
import com.app.market.domain.repository.AccountRepository
import com.app.market.domain.repository.DownloadRepository
import com.app.market.domain.repository.HonorRepository
import com.app.market.domain.repository.HuaweiRepository
import com.app.market.domain.repository.MarketLinkResolver
import com.app.market.domain.repository.MarketRepository
import com.app.market.domain.repository.MarketSourceRepository
import com.app.market.domain.repository.OppoRepository
import com.app.market.domain.repository.ProfileRepository
import com.app.market.domain.repository.ProxySettingsRepository
import com.app.market.domain.repository.TranslationRepository
import com.app.market.domain.repository.SamsungRepository
import com.app.market.domain.repository.CoolapkRepository
import com.app.market.domain.repository.FdroidRepository
import com.app.market.domain.repository.GooglePlayRepository
import com.app.market.domain.repository.TapTapRepository
import com.app.market.domain.repository.SearchHistoryRepository
import com.app.market.domain.repository.ThemePreferencesRepository
import com.app.market.domain.repository.TodayRepository
import com.app.market.domain.repository.UpdateHistoryRepository
import com.app.market.domain.repository.InstallHistoryRepository
import com.app.market.domain.repository.UpdatePreferencesRepository
import com.app.market.domain.repository.VivoRepository
import com.app.market.domain.repository.WandoujiaRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

private val commonDataModule = module {
    single<CoroutineScope> {
        // 数据层后台协程的最后防线：存储/IO 异常记录日志而不是崩溃整个进程
        val handler = CoroutineExceptionHandler { _, error ->
            debugLog("DataScope") { "Uncaught exception in data scope: ${error.stackTraceToString()}" }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
    }
    single { Json { ignoreUnknownKeys = true; isLenient = true } }

    singleOf(::UpdatePreferencesRepositoryImpl) { bind<UpdatePreferencesRepository>() }
    single<TranslationRepository> {
        TranslationRepositoryImpl(get(), get(), get()) { get() }
    }
    singleOf(::ThemePreferencesRepositoryImpl) { bind<ThemePreferencesRepository>() }
    // 第三个参数延迟取 HttpClient，避免与全局 client（构造需读代理设置）形成依赖环
    single<ProxySettingsRepository> {
        ProxySettingsRepositoryImpl(get(), get()) { get() }
    }
    singleOf(::SearchHistoryRepositoryImpl) { bind<SearchHistoryRepository>() }
    singleOf(::UpdateHistoryRepositoryImpl) { bind<UpdateHistoryRepository>() }
    singleOf(::InstallHistoryRepositoryImpl) { bind<InstallHistoryRepository>() }

    singleOf(::AnonymousAccountRepositoryImpl) { bind<AccountRepository>() }
    singleOf(::UpdateInfoCache)
    singleOf(::XiaomiClient)
    singleOf(::XiaomiHttpClient)
    singleOf(::XiaomiApi)
    singleOf(::ProfileRepositoryImpl) { bind<ProfileRepository>() }
    singleOf(::MarketRepositoryImpl) { bind<MarketRepository>() }
    singleOf(::TodayRepositoryImpl) { bind<TodayRepository>() }
    singleOf(::VivoApi)
    singleOf(::VivoUpdateApi)
    singleOf(::VivoAuroraApi)
    singleOf(::VivoRepositoryImpl) { bind<VivoRepository>() }
    single { WandoujiaApiConfig() }
    singleOf(::WandoujiaApi)
    singleOf(::WandoujiaRepositoryImpl) { bind<WandoujiaRepository>() }
    singleOf(::OppoApi)
    singleOf(::OppoRepositoryImpl) { bind<OppoRepository>() }
    singleOf(::SamsungApi)
    singleOf(::SamsungRepositoryImpl) { bind<SamsungRepository>() }
    singleOf(::HonorProtocol)
    singleOf(::HonorApi)
    singleOf(::HonorRepositoryImpl) { bind<HonorRepository>() }
    singleOf(::HuaweiProtocol)
    singleOf(::HuaweiApi)
    singleOf(::HuaweiRepositoryImpl) { bind<HuaweiRepository>() }
    singleOf(::MarketLinkResolverImpl) { bind<MarketLinkResolver>() }
    single { TapTapApiConfig() }
    singleOf(::TapTapApi)
    singleOf(::TapTapRepositoryImpl) { bind<TapTapRepository>() }
    singleOf(::CoolapkApi)
    single { GithubTokenHolder() }
    single { ReleaseApi(get(), get()) }
    singleOf(::ReleaseTokenRepositoryImpl) { bind<ReleaseTokenRepository>() }
    // cpuArchitecture 带默认值，同 LsposedRepository，显式传入避免 Koin 解析 String
    single { ReleaseSourceRepository(get(), get(), get(), get(), cpuArchitecture = "arm64-v8a,armeabi-v7a") }
    singleOf(::LsposedApi)
    // cpuArchitecture 带默认值，Koin singleOf 会按类型解析 String 而失败，这里显式传入
    single { LsposedRepository(get(), get(), get(), cpuArchitecture = "arm64-v8a,armeabi-v7a") }
    single { KuaibaoApiConfig() }
    singleOf(::KuaibaoApi)
    singleOf(::KuaibaoRepositoryImpl) { bind<KuaibaoRepository>() }
    single { AppchinaApiConfig() }
    singleOf(::AppchinaApi)
    singleOf(::AppchinaRepositoryImpl) { bind<AppchinaRepository>() }
    single { Box7723ApiConfig() }
    singleOf(::Box7723Api)
    singleOf(::Box7723RepositoryImpl) { bind<Box7723Repository>() }
    single { ApkPureApiConfig() }
    singleOf(::ApkPureApi)
    singleOf(::ApkPureRepositoryImpl) { bind<ApkPureRepository>() }
    single { AptoideApiConfig() }
    singleOf(::AptoideApi)
    singleOf(::AptoideRepositoryImpl) { bind<AptoideRepository>() }
    single { TencentApiConfig() }
    singleOf(::TencentApi)
    singleOf(::TencentRepositoryImpl) { bind<TencentRepository>() }
    singleOf(::CoolapkRepositoryImpl) { bind<CoolapkRepository>() }
    single<GooglePlayRepository> {
        GooglePlayRepositoryImpl(api = get(), installedPackages = get(), prefs = get())
    }
    // F-Droid：索引缓存 + 仓库实例
    single { FdroidApiConfig() }
    singleOf(::FdroidApi)
    singleOf(::FdroidRepositoryImpl) { bind<FdroidRepository>() }

    // 依赖 20 个仓库参数，超出 Koin singleOf 的构造器重载上限，手动展开注册（具名参数以免同类型多实例错注）
    single<MarketSourceRepository> {
        MarketSourceRepositoryImpl(
            market = get(),
            today = get(),
            vivo = get(),
            wandoujia = get(),
            oppo = get(),
            samsung = get(),
            honor = get(),
            huawei = get(),
            tapTap = get(),
            coolapk = get(),
            google = get(),
            kuaibao = get(),
            appchina = get(),
            tencent = get(),
            box7723 = get(),
            fdroid = get(),
            apkPure = get(),
            aptoide = get(),
            releaseSource = get(),
            lsposed = get(),
            updatePrefs = get(),
        )
    }

    singleOf(::RecordingDownloadRepositoryImpl) { bind<DownloadRepository>() }
}

internal expect val platformDataModule: Module

/** Complete data graph loaded by an application composition root. */
val dataModules: List<Module>
    get() = listOf(commonDataModule, platformDataModule)