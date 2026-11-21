package com.app.market.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.market.domain.model.installer.InstallerCandidate
import com.app.market.domain.model.installer.InstallerMode
import com.app.market.domain.repository.InstallerDiscoveryRepository
import com.app.market.domain.repository.InstallerPreferencesRepository
import com.app.market.platform.UiPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DEFAULT_DOWNLOAD_CONNECTIONS = 8
private const val DEFAULT_CONCURRENT_DOWNLOADS = 3

@Immutable
data class InstallerSettingsUiState(
    val mode: InstallerMode = InstallerMode.STANDARD,
    val saveToDownloads: Boolean = false,
    /** 另存应用安装包到指定文件夹（独立于「保存到 Download」）。 */
    val apkCopyEnabled: Boolean = false,
    val apkCopyDirectory: String = "",
    val packageSaveDirectory: String = "/storage/emulated/0/Download/AppMarket/",
    val userActionNotRequiredConfigurable: Boolean = false,
    val userActionNotRequiredEnabled: Boolean = false,
    val deltaUpdateSupported: Boolean = false,
    val deltaUpdateEnabled: Boolean = false,
    val deltaFallbackNoticeEnabled: Boolean = true,
    val multiThreadDownloadSupported: Boolean = false,
    val multiThreadDownloadEnabled: Boolean = false,
    val downloadConnections: Int = DEFAULT_DOWNLOAD_CONNECTIONS,
    val dynamicPartCreationEnabled: Boolean = true,
    val minPartSizeBytes: Long = 512L * 1024,
    val partMaxRetries: Int = 10,
    val concurrentDownloads: Int = DEFAULT_CONCURRENT_DOWNLOADS,
    val focusNotificationSupported: Boolean = false,
    val xiaomiIslandSupported: Boolean = false,
    val xiaomiIslandOptimizationEnabled: Boolean = false,
    /** 下载通知样式：off / normal / live / island。 */
    val downloadNotificationStyle: String = "island",
    val installationSupported: Boolean = false,
    val installerCandidates: List<InstallerCandidate> = emptyList(),
    val thirdPartyInstallerPackage: String = "",
    val showInstallerPicker: Boolean = false,
    /** 安装来源三档：default / google_play / custom；仅 Root/Shizuku 显示与生效。 */
    val installerSourceMode: String = "google_play",
    val customInstallerSource: String = "",
    /** 手动 dex 优化（安装后 pm compile）；仅 Root/Shizuku 工作模式显示与生效。 */
    val manualDexoptEnabled: Boolean = false,
    val dexoptMode: String = "speed-profile",
    val dexoptForceEnabled: Boolean = false,
)

class InstallerSettingsViewModel(
    private val controller: InstallerPreferencesRepository,
    private val discovery: InstallerDiscoveryRepository,
    private val platform: UiPlatform,
) : ViewModel() {

    private val installationSupported = platform.packageInstallationSupported

    private val _uiState = MutableStateFlow(
        InstallerSettingsUiState(installationSupported = installationSupported)
    )
    val uiState: StateFlow<InstallerSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // 偏好读取链路上任何一环抛错都不应让设置页崩溃：失败即保留默认值
            runCatchingCancellable { loadInitialState() }
        }
    }

    private val _privileges = MutableStateFlow(com.app.market.domain.model.installer.InstallerPrivileges())
    val privileges: StateFlow<com.app.market.domain.model.installer.InstallerPrivileges> =
        _privileges.asStateFlow()

    init {
        viewModelScope.launch {
            _privileges.value = runCatching { controller.installerPrivileges() }
                .getOrDefault(com.app.market.domain.model.installer.InstallerPrivileges())
        }
    }

    private suspend fun loadInitialState() {
        val candidates = if (installationSupported) {
            runCatchingCancellable { discovery.listCandidates() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val persistedPackage = controller.thirdPartyInstallerPackage()
        val selectedPackage = persistedPackage.takeIf { packageName ->
            candidates.any { it.packageName == packageName }
        }.orEmpty()
        val persistedMode = controller.mode()
        val mode = if (persistedMode == InstallerMode.THIRD_PARTY && selectedPackage.isBlank()) {
            controller.setMode(InstallerMode.STANDARD)
            InstallerMode.STANDARD
        } else {
            persistedMode
        }
        if (persistedPackage.isNotBlank() && selectedPackage.isBlank()) {
            controller.setThirdPartyInstallerPackage("")
        }
        _uiState.value = InstallerSettingsUiState(
            mode = mode,
            saveToDownloads = controller.saveToDownloads(),
            apkCopyEnabled = controller.apkCopyEnabled(),
            apkCopyDirectory = controller.apkCopyDirectory(),
            packageSaveDirectory = controller.packageSaveDirectory(),
            userActionNotRequiredConfigurable = controller.userActionNotRequiredConfigurable(),
            userActionNotRequiredEnabled = controller.userActionNotRequiredEnabled(),
            deltaUpdateSupported = controller.deltaUpdateSupported(),
            deltaUpdateEnabled = controller.deltaUpdateEnabled(),
            deltaFallbackNoticeEnabled = controller.deltaFallbackNoticeEnabled(),
            multiThreadDownloadSupported = controller.multiThreadDownloadSupported(),
            multiThreadDownloadEnabled = controller.multiThreadDownloadEnabled(),
            downloadConnections = controller.downloadConnections(),
            dynamicPartCreationEnabled = controller.dynamicPartCreationEnabled(),
            minPartSizeBytes = controller.minPartSizeBytes(),
            partMaxRetries = controller.partMaxRetries(),
            concurrentDownloads = controller.concurrentDownloads(),
            focusNotificationSupported = controller.focusNotificationSupported(),
            xiaomiIslandSupported = controller.xiaomiIslandSupported(),
            xiaomiIslandOptimizationEnabled = controller.xiaomiIslandOptimizationEnabled(),
            downloadNotificationStyle = controller.downloadNotificationStyle(),
            installationSupported = installationSupported,
            installerCandidates = candidates,
            thirdPartyInstallerPackage = selectedPackage,
            installerSourceMode = controller.installerSourceMode(),
            customInstallerSource = controller.customInstallerSource(),
            manualDexoptEnabled = controller.manualDexoptEnabled(),
            dexoptMode = controller.dexoptMode(),
            dexoptForceEnabled = controller.dexoptForceEnabled(),
        )
    }

    /** 当前 Root 提权命令（默认 su）。 */
    suspend fun privilegeCommand(): String =
        runCatching { controller.privilegeCommand() }.getOrDefault("su")

    fun setPrivilegeCommand(command: String) {
        viewModelScope.launch { controller.setPrivilegeCommand(command) }
    }

    /** 安装完成后是否自动删除安装包（默认开）。 */
    suspend fun deletePackageAfterInstall(): Boolean =
        runCatching { controller.deletePackageAfterInstall() }.getOrDefault(true)

    fun setDeletePackageAfterInstall(value: Boolean) {
        viewModelScope.launch { controller.setDeletePackageAfterInstall(value) }
    }

    fun setMode(mode: InstallerMode) {
        if (mode == InstallerMode.THIRD_PARTY) {
            showThirdPartyInstallerPicker()
            return
        }
        _uiState.update { it.copy(mode = mode) }
        viewModelScope.launch { controller.setMode(mode) }
    }

    fun showThirdPartyInstallerPicker() {
        if (!installationSupported) return
        _uiState.update { it.copy(showInstallerPicker = true) }
        viewModelScope.launch {
            val candidates = runCatchingCancellable { discovery.listCandidates() }.getOrDefault(emptyList())
            val selectedPackage = _uiState.value.thirdPartyInstallerPackage.takeIf { packageName ->
                candidates.any { it.packageName == packageName }
            }.orEmpty()
            if (_uiState.value.mode == InstallerMode.THIRD_PARTY && selectedPackage.isBlank()) {
                controller.setMode(InstallerMode.STANDARD)
            }
            if (_uiState.value.thirdPartyInstallerPackage.isNotBlank() && selectedPackage.isBlank()) {
                controller.setThirdPartyInstallerPackage("")
            }
            _uiState.update {
                it.copy(
                    mode = if (it.mode == InstallerMode.THIRD_PARTY && selectedPackage.isBlank()) {
                        InstallerMode.STANDARD
                    } else {
                        it.mode
                    },
                    installerCandidates = candidates,
                    thirdPartyInstallerPackage = selectedPackage,
                )
            }
        }
    }

    fun dismissThirdPartyInstallerPicker() {
        _uiState.update { it.copy(showInstallerPicker = false) }
    }

    fun selectThirdPartyInstaller(candidate: InstallerCandidate) {
        if (_uiState.value.installerCandidates.none { it.packageName == candidate.packageName }) return
        _uiState.update {
            it.copy(
                mode = InstallerMode.THIRD_PARTY,
                thirdPartyInstallerPackage = candidate.packageName,
                showInstallerPicker = false,
            )
        }
        viewModelScope.launch {
            controller.setThirdPartyInstallerPackage(candidate.packageName)
            controller.setMode(InstallerMode.THIRD_PARTY)
        }
    }

    fun setSaveToDownloads(enabled: Boolean) {
        _uiState.update { it.copy(saveToDownloads = enabled) }
        viewModelScope.launch { controller.setSaveToDownloads(enabled) }
    }
    fun setApkCopyEnabled(enabled: Boolean) {
        _uiState.update { it.copy(apkCopyEnabled = enabled) }
        viewModelScope.launch { controller.setApkCopyEnabled(enabled) }
    }
    fun setApkCopyDirectory(value: String) {
        _uiState.update { it.copy(apkCopyDirectory = value) }
        viewModelScope.launch { controller.setApkCopyDirectory(value) }
    }
    fun setPackageSaveDirectory(value: String) {
        _uiState.update { it.copy(packageSaveDirectory = value) }
        // 日志跟随安装包路径（应用启动时按此配置落位）
        viewModelScope.launch { controller.setPackageSaveDirectory(value) }
    }
    /** 📂：弹系统文件管理选目录，回填到安装包保存路径。 */
    fun pickSaveDirectory() {
        viewModelScope.launch {
            val path = platform.pickDirectoryPath() ?: return@launch
            setPackageSaveDirectory(if (path.endsWith("/")) path else "$path/")
        }
    }

    fun setUserActionNotRequiredEnabled(enabled: Boolean) {
        val state = _uiState.value
        if (state.mode != InstallerMode.STANDARD || !state.userActionNotRequiredConfigurable) return
        _uiState.update { it.copy(userActionNotRequiredEnabled = enabled) }
        viewModelScope.launch { controller.setUserActionNotRequiredEnabled(enabled) }
    }
    fun setInstallerSourceMode(mode: String) {
        _uiState.update { it.copy(installerSourceMode = mode) }
        viewModelScope.launch { controller.setInstallerSourceMode(mode) }
    }

    fun setCustomInstallerSource(packageName: String) {
        _uiState.update { it.copy(customInstallerSource = packageName) }
        viewModelScope.launch { controller.setCustomInstallerSource(packageName) }
    }

    fun setManualDexoptEnabled(enabled: Boolean) {
        _uiState.update { it.copy(manualDexoptEnabled = enabled) }
        viewModelScope.launch { controller.setManualDexoptEnabled(enabled) }
    }

    fun setDexoptMode(mode: String) {
        _uiState.update { it.copy(dexoptMode = mode) }
        viewModelScope.launch { controller.setDexoptMode(mode) }
    }

    fun setDexoptForceEnabled(enabled: Boolean) {
        _uiState.update { it.copy(dexoptForceEnabled = enabled) }
        viewModelScope.launch { controller.setDexoptForceEnabled(enabled) }
    }

    fun setDeltaFallbackNoticeEnabled(enabled: Boolean) {
        _uiState.update { it.copy(deltaFallbackNoticeEnabled = enabled) }
        viewModelScope.launch { controller.setDeltaFallbackNoticeEnabled(enabled) }
    }

    fun setDeltaUpdateEnabled(enabled: Boolean) {
        if (!_uiState.value.deltaUpdateSupported) return
        _uiState.update { it.copy(deltaUpdateEnabled = enabled) }
        viewModelScope.launch { controller.setDeltaUpdateEnabled(enabled) }
    }
    fun setMultiThreadDownloadEnabled(enabled: Boolean) {
        if (!_uiState.value.multiThreadDownloadSupported) return
        _uiState.update { it.copy(multiThreadDownloadEnabled = enabled) }
        viewModelScope.launch { controller.setMultiThreadDownloadEnabled(enabled) }
    }
    fun setDownloadConnections(connections: Int) {
        _uiState.update { it.copy(downloadConnections = connections) }
        viewModelScope.launch { controller.setDownloadConnections(connections) }
    }

    fun setDynamicPartCreationEnabled(enabled: Boolean) {
        _uiState.update { it.copy(dynamicPartCreationEnabled = enabled) }
        viewModelScope.launch { controller.setDynamicPartCreationEnabled(enabled) }
    }

    fun setMinPartSizeBytes(size: Long) {
        _uiState.update { it.copy(minPartSizeBytes = size) }
        viewModelScope.launch { controller.setMinPartSizeBytes(size) }
    }

    fun setPartMaxRetries(count: Int) {
        _uiState.update { it.copy(partMaxRetries = count) }
        viewModelScope.launch { controller.setPartMaxRetries(count) }
    }

    fun setConcurrentDownloads(count: Int) {
        _uiState.update { it.copy(concurrentDownloads = count) }
        viewModelScope.launch { controller.setConcurrentDownloads(count) }
    }

    fun setXiaomiIslandOptimizationEnabled(enabled: Boolean) {
        _uiState.update { it.copy(xiaomiIslandOptimizationEnabled = enabled) }
        viewModelScope.launch { controller.setXiaomiIslandOptimizationEnabled(enabled) }
    }

    fun setDownloadNotificationStyle(style: String) {
        _uiState.update { it.copy(downloadNotificationStyle = style) }
        viewModelScope.launch { controller.setDownloadNotificationStyle(style) }
    }
}