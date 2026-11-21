package com.app.market.di

import android.content.Context
import com.app.market.data.remote.fdroid.FdroidIndexCache
import com.app.market.data.remote.fdroid.JvmFdroidIndexCache
import com.app.market.data.remote.releases.JvmReleaseApkInspector
import com.app.market.data.remote.releases.ReleaseApkInspector
import java.io.File
import org.koin.core.qualifier.named
import com.app.market.data.download.PlatformDownloadDataSource
import com.app.market.data.install.AndroidInstallRepositoryImpl
import com.app.market.data.install.DeltaFallbackBus
import com.app.market.data.install.PackageStagingDownloader
import com.app.market.data.install.RemotePackageDownloader
import com.app.market.data.install.ThirdPartyInstallEngine
import com.app.market.data.install.backend.InstallerBackendSelector
import com.app.market.data.install.backend.PrivilegedPackageInstallerFactory
import com.app.market.data.install.backend.RootInstallerBackend
import com.app.market.data.install.backend.ShizukuInstallerBackend
import com.app.market.data.install.backend.DhizukuInstallerBackend
import com.app.market.data.install.backend.StandardInstallerBackend
import com.app.market.data.install.backend.root.RootBinderBridge
import com.app.market.data.install.network.ArtifactSourceReader
import com.app.market.data.install.network.InstallHttpClient
import com.app.market.data.install.network.SegmentedDownloader
import com.app.market.data.update.GitLabSelfUpdateRepository
import com.app.market.data.install.platform.InstallResultHandler
import com.app.market.data.install.platform.InstallTaskProcessor
import com.app.market.data.install.platform.PackageChangeHandler
import com.app.market.data.install.platform.SavedPackageInstallLauncher
import com.app.market.data.install.storage.ArtifactStagingStore
import com.app.market.data.install.storage.MediaStorePackageStore
import com.app.market.data.install.storage.SavedPackageIndex
import com.app.market.data.install.task.InstallTaskStore
import com.app.market.data.local.PreferencesDataSource
import com.app.market.data.local.PreferencesDataSourceImpl
import com.app.market.data.platform.AndroidThemePlatformPreferences
import com.app.market.data.platform.ThemePlatformPreferences
import com.app.market.data.platform.createAndroidHttpClient
import com.app.market.data.remote.xiaomi.platform.AndroidDeviceDefaultsDataSource
import com.app.market.data.remote.xiaomi.platform.AndroidInstalledApkHashRepositoryImpl
import com.app.market.data.remote.xiaomi.platform.AndroidInstalledPackagesRepositoryImpl
import com.app.market.data.remote.xiaomi.platform.AndroidXiaomiDeviceIdentityDataSource
import com.app.market.data.remote.xiaomi.platform.DeviceDefaultsDataSource
import com.app.market.data.remote.xiaomi.platform.XiaomiDeviceIdentityDataSource
import com.app.market.data.repository.AndroidDownloadDataSource
import com.app.market.data.repository.AndroidInstallerDiscoveryRepositoryImpl
import com.app.market.data.repository.InstallerPreferencesRepositoryImpl
import com.app.market.data.repository.PackageRepositoryImpl
import com.app.market.data.repository.SavedPackageRepositoryImpl
import com.app.market.domain.repository.InstallRepository
import com.app.market.domain.repository.InstalledApkHashRepository
import com.app.market.domain.repository.InstalledPackagesRepository
import com.app.market.domain.repository.InstallerDiscoveryRepository
import com.app.market.domain.repository.InstallerPreferencesRepository
import com.app.market.domain.repository.PackageRepository
import com.app.market.domain.repository.SavedPackageRepository
import com.app.market.domain.repository.SelfUpdateRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.core.qualifier.named

internal actual val platformDataModule: Module = module {
    // ReleaseApkInspector：Release 附件检查，由平台实现提供
    single<ReleaseApkInspector> { JvmReleaseApkInspector(get()) }

    single { createAndroidHttpClient(get()) }
    // Google Play 会话持久化：固定 gsfId（设备身份）跨冷启动复用，结果集才稳定
    single {
        val ctx: android.content.Context = get()
        val file = java.io.File(ctx.filesDir, "google_play_session.json")
        com.app.market.data.remote.google.GooglePlayApi(
            client = get(),
            json = get(),
            restoreSession = { runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() } },
            persistSession = { body -> runCatching { file.writeText(body) } },
        )
    }
    // 内置 Clash 内核：延迟取 client，避免与全局 client 形成依赖环
    single<com.app.market.domain.repository.ClashCoreController> {
        com.app.market.data.clash.AndroidAnyTlsProxyController(get(), { get() })
    }
    singleOf(::PreferencesDataSourceImpl) { bind<PreferencesDataSource>() }
    singleOf(::AndroidThemePlatformPreferences) { bind<ThemePlatformPreferences>() }
    singleOf(::AndroidDeviceDefaultsDataSource) { bind<DeviceDefaultsDataSource>() }
    singleOf(::AndroidXiaomiDeviceIdentityDataSource) { bind<XiaomiDeviceIdentityDataSource>() }
    singleOf(::AndroidInstalledPackagesRepositoryImpl) { bind<InstalledPackagesRepository>() }
    singleOf(::AndroidInstalledApkHashRepositoryImpl) { bind<InstalledApkHashRepository>() }
    singleOf(::PackageRepositoryImpl) {
        bind<PackageRepository>()
        bind<PackageChangeHandler>()
    }
    singleOf(::InstallerPreferencesRepositoryImpl) { bind<InstallerPreferencesRepository>() }
    singleOf(::AndroidInstallerDiscoveryRepositoryImpl) { bind<InstallerDiscoveryRepository>() }

    singleOf(::InstallTaskStore)
    singleOf(::SavedPackageIndex)
    singleOf(::MediaStorePackageStore)
    singleOf(::ArtifactStagingStore)
    singleOf(::InstallHttpClient)
    singleOf(::DeltaFallbackBus)
    singleOf(::SegmentedDownloader)
    singleOf(::GitLabSelfUpdateRepository) { bind<SelfUpdateRepository>() }
    singleOf(::ArtifactSourceReader)
    singleOf(::PackageStagingDownloader)
    singleOf(::PrivilegedPackageInstallerFactory)
    singleOf(::RootBinderBridge)
    singleOf(::StandardInstallerBackend)
    singleOf(::RootInstallerBackend)
    singleOf(::ShizukuInstallerBackend)
    singleOf(::DhizukuInstallerBackend)
    singleOf(::InstallerBackendSelector)
    singleOf(::ThirdPartyInstallEngine)
    singleOf(::AndroidInstallRepositoryImpl) { bind<InstallRepository>() }
    singleOf(::RemotePackageDownloader)
    singleOf(::SavedPackageRepositoryImpl) { bind<SavedPackageRepository>() }
    // F-Droid 索引缓存：缺了这个 Koin 会解析不到 FdroidIndexCache
    single<FdroidIndexCache> { JvmFdroidIndexCache(File(get<Context>().cacheDir, "fdroid"), get(), get()) }

    singleOf(::AndroidDownloadDataSource) {
        bind<PlatformDownloadDataSource>()
        bind<InstallTaskProcessor>()
        bind<InstallResultHandler>()
        bind<SavedPackageInstallLauncher>()
    }
}