package com.app.market.data.install.backend

import android.content.Context
import android.content.pm.PackageManager
import com.app.market.data.install.InstallPipelineException
import com.app.market.domain.model.install.InstallFailureCode
import com.app.market.domain.model.installer.InstallerMode
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

/**
 * Dhizuku 安装后端。
 *
 * Dhizuku 把「设备所有者（DeviceOwner）」权限借给普通应用：
 * 用 [Dhizuku.binderWrapper] 包一层系统服务的 binder，之后所有调用都以 Dhizuku 自身身份执行，
 * 于是可以直接走 PackageInstaller 的静默安装，不需要 root、也不需要 Shizuku 常驻进程。
 *
 * 会话归属必须填 Dhizuku 的包名：真正的调用方是它，填自己的包名会被系统判成无权安装。
 */
internal class DhizukuInstallerBackend(
    private val context: Context,
    private val factory: PrivilegedPackageInstallerFactory,
) : InstallerBackend {

    override val mode: InstallerMode = InstallerMode.DHIZUKU

    override suspend fun open(installerPackageName: String?): PackageInstallerAccess {
        val ready = runCatching { Dhizuku.init(context) }.getOrDefault(false)
        if (!ready) {
            throw InstallPipelineException(
                InstallFailureCode.DHIZUKU_UNAVAILABLE,
                "Dhizuku 未安装或未激活（需要把设备所有者权限交给 Dhizuku）",
            )
        }
        requirePermission()
        val owner = runCatching { Dhizuku.getOwnerPackageName() }
            .getOrNull()
            .orEmpty()
            .ifBlank { DHIZUKU_PACKAGE }
        return factory.create(
            wrapBinder = { binder -> Dhizuku.binderWrapper(binder) },
            callerPackageName = owner,
        )
    }

    private suspend fun requirePermission() {
        val granted = runCatching { Dhizuku.isPermissionGranted() }.getOrDefault(false)
        if (granted) return
        val result = CompletableDeferred<Int>()
        val listener = object : DhizukuRequestPermissionListener() {
            override fun onRequestPermission(grantResult: Int) {
                result.complete(grantResult)
            }
        }
        val requested = runCatching { Dhizuku.requestPermission(listener) }.isSuccess
        if (!requested) {
            throw InstallPipelineException(
                InstallFailureCode.DHIZUKU_UNAVAILABLE,
                "无法向 Dhizuku 申请权限",
            )
        }
        val grantResult = try {
            withTimeout(60_000L.milliseconds) { result.await() }
        } catch (error: TimeoutCancellationException) {
            throw InstallPipelineException(
                InstallFailureCode.DHIZUKU_PERMISSION_DENIED,
                "Dhizuku 授权超时",
                error,
            )
        }
        if (grantResult != PackageManager.PERMISSION_GRANTED) {
            throw InstallPipelineException(
                InstallFailureCode.DHIZUKU_PERMISSION_DENIED,
                "Dhizuku 授权被拒绝",
            )
        }
    }

    private companion object {
        const val DHIZUKU_PACKAGE = "com.rosan.dhizuku"
    }
}