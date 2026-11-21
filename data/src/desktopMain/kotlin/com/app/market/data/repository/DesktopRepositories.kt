package com.app.market.data.repository

import com.app.market.domain.model.install.InstallEvent
import com.app.market.domain.model.install.InstallRequest
import com.app.market.domain.model.installed.PackageChange
import com.app.market.domain.model.installer.InstallerCandidate
import com.app.market.domain.model.installer.InstallerMode
import com.app.market.domain.model.installer.SavedPackage
import com.app.market.domain.model.update.SelfUpdate
import com.app.market.domain.repository.InstallRepository
import com.app.market.domain.repository.InstalledApkHashRepository
import com.app.market.domain.repository.InstalledPackagesRepository
import com.app.market.domain.repository.InstallerDiscoveryRepository
import com.app.market.domain.repository.InstallerPreferencesRepository
import com.app.market.domain.repository.PackageRepository
import com.app.market.domain.repository.SavedPackageRepository
import com.app.market.domain.repository.SelfUpdateRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
import java.awt.Desktop
import java.net.URI

class DesktopPackageRepositoryImpl : PackageRepository {
    override val selfPackageName: String = "com.app.market"
    override val changes: SharedFlow<PackageChange> = MutableSharedFlow()
    override suspend fun installedVersionCodes(packageNames: Collection<String>): Map<String, Long> = emptyMap()
    override suspend fun installedVersionName(packageName: String): String? = null
    override suspend fun freshInstalledVersionCode(packageName: String): Long? = null
    override fun openApp(packageName: String): Boolean = false
    override fun openLink(link: String): Boolean {
        if (link.isBlank()) return false
        return runCatching { openInBrowser(link) }.isSuccess
    }
}

class DesktopInstallerPreferencesRepositoryImpl : InstallerPreferencesRepository {
    override suspend fun mode(): InstallerMode = InstallerMode.STANDARD
    override suspend fun setMode(mode: InstallerMode) {}
    override suspend fun thirdPartyInstallerPackage(): String = ""
    override suspend fun setThirdPartyInstallerPackage(packageName: String) {}
    override suspend fun saveToDownloads(): Boolean = true
    override suspend fun setSaveToDownloads(enabled: Boolean) {}
    override fun userActionNotRequiredConfigurable(): Boolean = false
    override suspend fun userActionNotRequiredEnabled(): Boolean = false
    override suspend fun setUserActionNotRequiredEnabled(enabled: Boolean) {}
    override fun deltaUpdateSupported(): Boolean = false
    override suspend fun deltaUpdateEnabled(): Boolean = false
    override suspend fun setDeltaUpdateEnabled(enabled: Boolean) {}
    override fun multiThreadDownloadSupported(): Boolean = false
    override suspend fun multiThreadDownloadEnabled(): Boolean = false
    override suspend fun setMultiThreadDownloadEnabled(enabled: Boolean) {}
    override suspend fun autoCheckUpdatesEnabled(): Boolean = false
    override suspend fun setAutoCheckUpdatesEnabled(enabled: Boolean) {}
    override suspend fun downloadConnections(): Int = 1
    override suspend fun setDownloadConnections(connections: Int) {}
    override suspend fun dynamicPartCreationEnabled(): Boolean = false
    override suspend fun setDynamicPartCreationEnabled(enabled: Boolean) {}
    override suspend fun minPartSizeBytes(): Long = 512L * 1024
    override suspend fun setMinPartSizeBytes(size: Long) {}
    override suspend fun partMaxRetries(): Int = 10
    override suspend fun setPartMaxRetries(count: Int) {}
    override suspend fun concurrentDownloads(): Int = 3
    override suspend fun setConcurrentDownloads(count: Int) {}
    override suspend fun deltaFallbackNoticeEnabled(): Boolean = false
    override suspend fun setDeltaFallbackNoticeEnabled(enabled: Boolean) {}
    override fun focusNotificationSupported(): Boolean = false
    override fun xiaomiIslandSupported(): Boolean = false
    override suspend fun xiaomiIslandOptimizationEnabled(): Boolean = false
    override suspend fun setXiaomiIslandOptimizationEnabled(enabled: Boolean) {}
    override suspend fun downloadNotificationStyle(): String = "island"
    override suspend fun setDownloadNotificationStyle(style: String) {}
    override suspend fun installerSourceMode(): String = "google_play"
    override suspend fun setInstallerSourceMode(mode: String) {}
    override suspend fun customInstallerSource(): String = ""
    override suspend fun setCustomInstallerSource(packageName: String) {}
    override suspend fun manualDexoptEnabled(): Boolean = false
    override suspend fun setManualDexoptEnabled(enabled: Boolean) {}
    override suspend fun dexoptMode(): String = "speed-profile"
    override suspend fun setDexoptMode(mode: String) {}
    override suspend fun dexoptForceEnabled(): Boolean = false
    override suspend fun setDexoptForceEnabled(enabled: Boolean) {}
}

class DesktopInstallerDiscoveryRepositoryImpl : InstallerDiscoveryRepository {
    override suspend fun listCandidates(): List<InstallerCandidate> = emptyList()
}

class DesktopSavedPackageRepositoryImpl : SavedPackageRepository {
    override suspend fun list(): List<SavedPackage> = emptyList()
    override suspend fun install(id: String) {}
    override suspend fun delete(id: String) {}
}

class DesktopInstalledPackagesRepositoryImpl : InstalledPackagesRepository {
    override suspend fun installed() = emptyList<com.app.market.domain.model.installed.InstalledPackage>()
}

class DesktopInstalledApkHashRepositoryImpl : InstalledApkHashRepository {
    override suspend fun tapTap(path: String): String = com.app.market.data.platform.tapTapApkHash(path)

    override suspend fun md5(path: String): String = "0"
}

private fun openInBrowser(url: String) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI(url))
}
}
class DesktopSelfUpdateRepositoryImpl : SelfUpdateRepository {
    override val projectUrl: String = "https://gitlab.com/FengGuan/AppMarket"
    override val authHeaders: Map<String, String> = emptyMap()
    override suspend fun latestRelease(): SelfUpdate? = null
}
class DesktopInstallRepositoryImpl : InstallRepository {
    override fun install(request: InstallRequest) = emptyFlow<InstallEvent>()
    override suspend fun cancel(id: String) {}
}
