package com.app.market.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.app.market.data.install.HyperOsFocusCapability
import com.app.market.data.local.PreferenceChanges
import com.app.market.data.local.PreferencesDataSource
import com.app.market.data.local.preferences.InstallerPreferenceKeys
import com.app.market.domain.model.installer.InstallerMode
import com.app.market.domain.repository.InstallerPreferencesRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class InstallerPreferencesRepositoryImpl(
    private val context: Context,
    private val preferences: PreferencesDataSource,
) : InstallerPreferencesRepository {
    private val migrationMutex = Mutex()

    override suspend fun mode(): InstallerMode =
        InstallerMode.fromKey(preferences.read(InstallerPreferenceKeys.Mode))

    override suspend fun setMode(mode: InstallerMode) {
        preferences.put(InstallerPreferenceKeys.Mode, mode.key)
    }

    override suspend fun thirdPartyInstallerPackage(): String =
        preferences.read(InstallerPreferenceKeys.ThirdPartyInstallerPackage).orEmpty()
    override suspend fun setThirdPartyInstallerPackage(packageName: String) {
        preferences.put(InstallerPreferenceKeys.ThirdPartyInstallerPackage, packageName)
    }
    override suspend fun apkCopyEnabled(): Boolean =
        preferences.read(InstallerPreferenceKeys.ApkCopyEnabled) ?: false
    override suspend fun setApkCopyEnabled(value: Boolean) {
        preferences.put(InstallerPreferenceKeys.ApkCopyEnabled, value)
    }
    override suspend fun apkCopyDirectory(): String =
        preferences.read(InstallerPreferenceKeys.ApkCopyDirectory).orEmpty()
    override suspend fun setApkCopyDirectory(value: String) {
        preferences.put(InstallerPreferenceKeys.ApkCopyDirectory, value.trim())
    }
    override suspend fun packageSaveDirectory(): String =
        preferences.read(InstallerPreferenceKeys.PackageSaveDirectory)
            .orEmpty()
            .ifBlank { com.app.market.domain.repository.InstallerPreferencesRepository.DEFAULT_PACKAGE_SAVE_DIRECTORY }
            .let { if (it.endsWith("/")) it else "$it/" }
    override suspend fun setPackageSaveDirectory(value: String) {
        val normalized = value.trim().let { if (it.endsWith("/")) it else "$it/" }
        preferences.put(InstallerPreferenceKeys.PackageSaveDirectory, normalized)
    }

    override suspend fun privilegeCommand(): String {
        val stored = preferences.read(InstallerPreferenceKeys.PrivilegeCommand).orEmpty().ifBlank { "su" }
        com.app.market.data.install.RootPrivilegeCommand.command = stored
        return stored
    }

    override suspend fun installerPrivileges(): com.app.market.domain.model.installer.InstallerPrivileges =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val command = runCatching { privilegeCommand() }.getOrDefault("su")
            val root = runCatching {
                val process = ProcessBuilder(command, "-c", "id")
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                process.destroy()
                output.contains("uid=0")
            }.getOrDefault(false)
            val rootManager = if (!root) "" else detectRootManager(command)
            val shizuku = runCatching {
                val cls = Class.forName("rikka.shizuku.Shizuku")
                cls.getMethod("pingBinder").invoke(null) as Boolean
            }.getOrDefault(false)
            val dhizuku = runCatching {
                val cls = Class.forName("com.rosan.dhizuku.api.Dhizuku")
                cls.getMethod("isUserManagerReady").invoke(null) as Boolean
            }.getOrDefault(false)
            val system = runCatching {
                val info = context.packageManager.getApplicationInfo(context.packageName, 0)
                val isSystemApp = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val hasPermission = context.checkSelfPermission("android.permission.INSTALL_PACKAGES") ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                isSystemApp || hasPermission
            }.getOrDefault(false)
            com.app.market.domain.model.installer.InstallerPrivileges(
                rootAvailable = root,
                rootManager = rootManager,
                shizukuAvailable = shizuku,
                dhizukuAvailable = dhizuku,
                systemAvailable = system,
            )
        }

    /**
     * 识别 root 管理器。
     *
     * 参考 InstallerX-Revived：靠包名匹配不可靠（KernelSU Next / SukiSU / Magisk Delta
     * 都换了包名），改成在 su shell 里直接试跑各家的命令行工具：
     * `ksud -V`（KernelSU 系）、`magisk -v`（Magisk 系）、`apd -V`（APatch）。
     * 二进制探不到再退回 /data/adb 目录与包名兜底。
     */
    private fun detectRootManager(privilegeCommand: String): String {
        fun probe(command: String): String? = runCatching {
            val process = ProcessBuilder(
                privilegeCommand,
                "-c",
                "export PATH=\$PATH:$ROOT_DETECTION_PATH && $command",
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroy()
                return@runCatching null
            }
            if (process.exitValue() == 0) output else null
        }.getOrNull()

        probe("ksud -V")?.let { return kernelSuVariant() + versionSuffix(it) }
        probe("magisk -v")?.let { output ->
            val variant = when {
                output.contains("DELTA", ignoreCase = true) -> "Magisk Delta"
                output.contains("KITSUNE", ignoreCase = true) -> "Magisk Kitsune"
                output.contains("ALPHA", ignoreCase = true) -> "Magisk Alpha"
                else -> "Magisk"
            }
            return variant + versionSuffix(output)
        }
        probe("apd -V")?.let { return "APatch" + versionSuffix(it) }

        // 二进制不在 PATH 里（自定义 su / 精简过的 root 实现）：退回目录与包名
        return when {
            probe("[ -d /data/adb/ksu ]") != null -> kernelSuVariant()
            probe("[ -d /data/adb/ap ]") != null -> "APatch"
            probe("[ -d /data/adb/magisk ]") != null -> "Magisk"
            isPackageInstalled("com.topjohnwu.magisk") || isPackageInstalled("io.github.vvb2060.magisk") -> "Magisk"
            else -> "Root"
        }
    }

    /** KernelSU 系的三家实现共用 ksud，靠包名再细分一下。 */
    private fun kernelSuVariant(): String = when {
        isPackageInstalled("com.sukisu.ultra") -> "SukiSU Ultra"
        isPackageInstalled("com.rifsxd.ksunext") -> "KernelSU Next"
        else -> "KernelSU"
    }

    private fun isPackageInstalled(packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    /** 从 `magisk -v`（27.0:MAGISK）或 `ksud -V` 之类输出里抠出版本号。 */
    private fun versionSuffix(output: String): String =
        VERSION_REGEX.find(output)?.value?.let { " $it" }.orEmpty()

    override suspend fun setPrivilegeCommand(command: String) {
        val value = command.trim().ifBlank { "su" }
        com.app.market.data.install.RootPrivilegeCommand.command = value
        preferences.put(InstallerPreferenceKeys.PrivilegeCommand, value)
    }

    override suspend fun deletePackageAfterInstall(): Boolean = runCatching {
        preferences.read(InstallerPreferenceKeys.DeletePackageAfterInstall)
    }.getOrDefault(true)

    override suspend fun setDeletePackageAfterInstall(value: Boolean) {
        preferences.put(InstallerPreferenceKeys.DeletePackageAfterInstall, value)
    }

    override suspend fun saveToDownloads(): Boolean {
        migrateSavePreference()
        return preferences.read(InstallerPreferenceKeys.SaveToDownloads)
    }

    override suspend fun setSaveToDownloads(enabled: Boolean) {
        migrateSavePreference()
        preferences.put(InstallerPreferenceKeys.SaveToDownloads, enabled)
    }

    override fun userActionNotRequiredConfigurable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.applicationInfo.flags and SYSTEM_APP_FLAGS == 0

    override suspend fun userActionNotRequiredEnabled(): Boolean =
        preferences.read(InstallerPreferenceKeys.UserActionNotRequired)

    override suspend fun setUserActionNotRequiredEnabled(enabled: Boolean) {
        preferences.put(InstallerPreferenceKeys.UserActionNotRequired, enabled)
    }

    override fun deltaUpdateSupported(): Boolean = true

    override suspend fun deltaUpdateEnabled(): Boolean =
        deltaUpdateSupported() && preferences.read(InstallerPreferenceKeys.DeltaUpdate)

    override suspend fun setDeltaUpdateEnabled(enabled: Boolean) {
        preferences.put(InstallerPreferenceKeys.DeltaUpdate, enabled && deltaUpdateSupported())
    }
    override fun multiThreadDownloadSupported(): Boolean = true
    override suspend fun multiThreadDownloadEnabled(): Boolean =
        multiThreadDownloadSupported() && preferences.read(InstallerPreferenceKeys.MultiThreadDownload)

    override suspend fun setMultiThreadDownloadEnabled(enabled: Boolean) {
        preferences.put(InstallerPreferenceKeys.MultiThreadDownload, enabled && multiThreadDownloadSupported())
    }
    override suspend fun autoCheckUpdatesEnabled(): Boolean =
        preferences.read(InstallerPreferenceKeys.AutoCheckUpdates)
    override suspend fun setAutoCheckUpdatesEnabled(enabled: Boolean) {
        preferences.put(InstallerPreferenceKeys.AutoCheckUpdates, enabled)
    }
    override suspend fun installerSourceMode(): String =
        preferences.read(InstallerPreferenceKeys.InstallerSourceMode)
            ?.takeIf { it in INSTALLER_SOURCE_MODES } ?: DEFAULT_INSTALLER_SOURCE_MODE
    override suspend fun setInstallerSourceMode(mode: String) {
        preferences.put(
            InstallerPreferenceKeys.InstallerSourceMode,
            if (mode in INSTALLER_SOURCE_MODES) mode else DEFAULT_INSTALLER_SOURCE_MODE,
        )
    }
    override suspend fun customInstallerSource(): String =
        preferences.read(InstallerPreferenceKeys.CustomInstallerSource).orEmpty()
    override suspend fun setCustomInstallerSource(packageName: String) {
        preferences.put(InstallerPreferenceKeys.CustomInstallerSource, packageName)
    }
    override suspend fun manualDexoptEnabled(): Boolean =
        preferences.read(InstallerPreferenceKeys.ManualDexopt)
    override suspend fun setManualDexoptEnabled(enabled: Boolean) {
        preferences.put(InstallerPreferenceKeys.ManualDexopt, enabled)
    }
    override suspend fun dexoptMode(): String =
        preferences.read(InstallerPreferenceKeys.DexoptMode)?.takeIf { it in DEXOPT_MODES }
            ?: DEFAULT_DEXOPT_MODE
    override suspend fun setDexoptMode(mode: String) {
        preferences.put(
            InstallerPreferenceKeys.DexoptMode,
            if (mode in DEXOPT_MODES) mode else DEFAULT_DEXOPT_MODE,
        )
    }
    override suspend fun dexoptForceEnabled(): Boolean =
        preferences.read(InstallerPreferenceKeys.DexoptForce)
    override suspend fun setDexoptForceEnabled(enabled: Boolean) {
        preferences.put(InstallerPreferenceKeys.DexoptForce, enabled)
    }
    override suspend fun downloadConnections(): Int =
        preferences.read(InstallerPreferenceKeys.DownloadConnections)
            ?.toIntOrNull()?.coerceIn(DOWNLOAD_CONNECTIONS_RANGE)
            ?: DEFAULT_DOWNLOAD_CONNECTIONS

    override suspend fun setDownloadConnections(connections: Int) {
        preferences.put(
            InstallerPreferenceKeys.DownloadConnections,
            connections.coerceIn(DOWNLOAD_CONNECTIONS_RANGE).toString(),
        )
    }

    override suspend fun dynamicPartCreationEnabled(): Boolean =
        preferences.read(InstallerPreferenceKeys.DynamicPartCreation)

    override suspend fun setDynamicPartCreationEnabled(enabled: Boolean) {
        preferences.put(InstallerPreferenceKeys.DynamicPartCreation, enabled)
    }

    override suspend fun minPartSizeBytes(): Long =
        preferences.read(InstallerPreferenceKeys.MinPartSize)
            ?.toLongOrNull()?.coerceIn(MIN_PART_SIZE_RANGE)
            ?: DEFAULT_MIN_PART_SIZE

    override suspend fun setMinPartSizeBytes(size: Long) {
        preferences.put(
            InstallerPreferenceKeys.MinPartSize,
            size.coerceIn(MIN_PART_SIZE_RANGE).toString(),
        )
    }

    override suspend fun partMaxRetries(): Int =
        preferences.read(InstallerPreferenceKeys.PartMaxRetries)
            ?.toIntOrNull()?.coerceIn(PART_RETRIES_RANGE)
            ?: DEFAULT_PART_MAX_RETRIES

    override suspend fun setPartMaxRetries(count: Int) {
        preferences.put(
            InstallerPreferenceKeys.PartMaxRetries,
            count.coerceIn(PART_RETRIES_RANGE).toString(),
        )
    }

    override suspend fun concurrentDownloads(): Int =
        preferences.read(InstallerPreferenceKeys.ConcurrentDownloads)
            ?.toIntOrNull()?.coerceIn(CONCURRENT_DOWNLOADS_RANGE)
            ?: DEFAULT_CONCURRENT_DOWNLOADS

    override suspend fun setConcurrentDownloads(count: Int) {
        preferences.put(
            InstallerPreferenceKeys.ConcurrentDownloads,
            count.coerceIn(CONCURRENT_DOWNLOADS_RANGE).toString(),
        )
    }

    override suspend fun deltaFallbackNoticeEnabled(): Boolean =
        preferences.read(InstallerPreferenceKeys.DeltaFallbackNotice)

    override suspend fun setDeltaFallbackNoticeEnabled(enabled: Boolean) {
        preferences.put(InstallerPreferenceKeys.DeltaFallbackNotice, enabled)
    }

    override fun focusNotificationSupported(): Boolean =
        HyperOsFocusCapability.supportsFocus(context)

    override fun xiaomiIslandSupported(): Boolean =
        HyperOsFocusCapability.isIslandMode(context)

    override suspend fun xiaomiIslandOptimizationEnabled(): Boolean =
        // 用户从没改过这个开关时，按设备能力给默认值：支持的机型默认开岛，
        // 避免「装完没岛 → 去设置里拨一下才有 → 重进又没了」这种凭空消失。
        // 未设置过时默认开启：设备真不支持时 resolveFocus 的探测会拿到 mode=null，自动降级回普通通知，
        // 所以这里默认 true 不会误报岛，但能避免「装完没岛 → 去设置里拨一下才有」。
        preferences.readNullable(InstallerPreferenceKeys.XiaomiIslandOptimization) ?: true

    override suspend fun setXiaomiIslandOptimizationEnabled(enabled: Boolean) {
        preferences.put(InstallerPreferenceKeys.XiaomiIslandOptimization, enabled)
    }

    override suspend fun downloadNotificationStyle(): String {
        preferences.read(InstallerPreferenceKeys.DownloadNotificationStyle)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        // 从未选过：按旧开关兼容。旧开关关过（=false）说明用户想要原生通知，其余情况默认给岛。
        val legacy = preferences.readNullable(InstallerPreferenceKeys.XiaomiIslandOptimization)
        return if (legacy == false) "live" else "island"
    }

    override suspend fun setDownloadNotificationStyle(style: String) {
        preferences.put(InstallerPreferenceKeys.DownloadNotificationStyle, style)
    }

    private suspend fun migrateSavePreference() = migrationMutex.withLock {
        if (preferences.read(InstallerPreferenceKeys.SaveToDownloadsMigrated)) return@withLock
        val legacyDeleteAfterInstall = preferences.read(InstallerPreferenceKeys.LegacyDeleteAfterInstall)
        preferences.update(
            InstallerPreferenceKeys.SaveToDownloads.namespace,
            PreferenceChanges(
                booleans = mapOf(
                    InstallerPreferenceKeys.SaveToDownloads to !legacyDeleteAfterInstall,
                    InstallerPreferenceKeys.SaveToDownloadsMigrated to true,
                    InstallerPreferenceKeys.LegacyDeleteAfterInstall to null,
                )
            ),
        )
    }

    private companion object {
        const val SYSTEM_APP_FLAGS = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        val DOWNLOAD_CONNECTIONS_RANGE = 1..32
        const val DEFAULT_DOWNLOAD_CONNECTIONS = 8

        /** 最小分片大小范围（256KB ~ 8MB），默认与 ab-dm 一致为 512KB。 */
        val MIN_PART_SIZE_RANGE = (256L * 1024)..(8L * 1024 * 1024)
        const val DEFAULT_MIN_PART_SIZE = 512L * 1024

        /** 分片重试次数范围，默认与 ab-dm 的 PART_MAX_TRIES 一致为 10。 */
        val PART_RETRIES_RANGE = 1..20
        const val DEFAULT_PART_MAX_RETRIES = 10
        val CONCURRENT_DOWNLOADS_RANGE = 1..8
        const val DEFAULT_CONCURRENT_DOWNLOADS = 3
        val DEXOPT_MODES = setOf("verify", "speed-profile", "speed", "everything")
        const val DEFAULT_DEXOPT_MODE = "speed-profile"
        val INSTALLER_SOURCE_MODES = setOf("default", "google_play", "custom")
/** 默认选中 Google Play：直接解决「此应用并非来自 Google Play」检查。 */
        const val DEFAULT_INSTALLER_SOURCE_MODE = "google_play"
        /** root 管理器检测时补进 PATH 的目录：ksud / apd / magisk 都在各自的 bin 下。 */
        const val ROOT_DETECTION_PATH = "/data/adb/ksu/bin:/data/adb/ap/bin:/data/adb/magisk/bin"
        val VERSION_REGEX = Regex("""\d+(?:\.\d+)+""")
    }
}