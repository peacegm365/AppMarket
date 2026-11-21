package com.app.market.data.install.backend

import android.content.pm.PackageInstaller
import com.app.market.domain.model.installer.InstallerMode
import java.io.Closeable

internal interface InstallerBackend {
    val mode: InstallerMode
    suspend fun open(installerPackageName: String? = null): PackageInstallerAccess
}

internal class PackageInstallerAccess(
    val packageInstaller: PackageInstaller,
    val installerPackageName: String,
    private val sessionWrapper: (PackageInstaller.Session) -> Unit = {},
    private val closeAction: () -> Unit = {},
) : Closeable {
    fun prepare(session: PackageInstaller.Session) = sessionWrapper(session)
    override fun close() = closeAction()
}

internal class InstallerBackendSelector(
    standard: StandardInstallerBackend,
    root: RootInstallerBackend,
    shizuku: ShizukuInstallerBackend,
    dhizuku: DhizukuInstallerBackend,
) {
    private val backends = listOf(standard, root, shizuku, dhizuku).associateBy { it.mode }

    fun get(mode: InstallerMode): InstallerBackend =
        // 系统权限静默安装复用标准安装的会话通道（自身持有 INSTALL_PACKAGES 时系统会静默装）
        backends[mode] ?: requireNotNull(backends[InstallerMode.STANDARD]) {
            "No installer backend registered for $mode"
        }
}

internal const val XIAOMI_MARKET_PACKAGE_NAME = "com.xiaomi.market"
