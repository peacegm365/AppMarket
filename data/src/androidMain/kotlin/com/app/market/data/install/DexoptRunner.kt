package com.app.market.data.install

import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.ResultReceiver
import com.app.market.domain.model.installer.InstallerMode
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.io.File

/**
 * 手动 dex 优化：安装成功后触发 dex2oat。
 *
 * 与 InstallerX Revived 一致：把 `pm compile -m <filter> [-f] <pkg>` 作为 shell 命令
 * 经提权 binder（Shizuku 包装 / Root 直发）oneway 发给 package 服务；
 * 普通安装与 Dhizuku 无权限执行，由调用方先行过滤。
 */
internal class DexoptRunner {
    fun run(mode: InstallerMode, packageName: String, compilerFilter: String, force: Boolean): Boolean {
        val args = buildList {
            add("compile")
            add("-m")
            add(compilerFilter)
            if (force) add("-f")
            add(packageName)
        }.toTypedArray()
        return when (mode) {
            InstallerMode.SHIZUKU -> shizukuShell(args)
            InstallerMode.ROOT -> rootShell(args)
            else -> false
        }
    }

    private fun shizukuShell(args: Array<String>): Boolean = runCatching {
        if (!Shizuku.pingBinder()) return false
        val binder = serviceManagerGetService("package")
        sendShellCommandOneway(ShizukuBinderWrapper(binder), args)
        true
    }.getOrDefault(false)

    private fun rootShell(args: Array<String>): Boolean = runCatching {
        val command = "pm " + args.joinToString(" ") { shellQuote(it) }
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        process.inputStream.readBytes()
        process.errorStream.readBytes()
        process.waitFor() == 0
    }.getOrDefault(false)

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun serviceManagerGetService(name: String): IBinder {
        val serviceManager = Class.forName("android.os.ServiceManager")
        return serviceManager.getDeclaredMethod("getService", String::class.java)
            .invoke(null, name) as IBinder
    }

    /** 照抄 InstallerX Revived：oneway 发送 shell 命令，不等待回执。 */
    private fun sendShellCommandOneway(binder: IBinder, args: Array<String>) {
        val data = Parcel.obtain()
        val stdin = ParcelFileDescriptor.open(File("/dev/null"), ParcelFileDescriptor.MODE_READ_ONLY)
        val stdout = ParcelFileDescriptor.open(File("/dev/null"), ParcelFileDescriptor.MODE_WRITE_ONLY)
        val stderr = ParcelFileDescriptor.open(File("/dev/null"), ParcelFileDescriptor.MODE_WRITE_ONLY)
        try {
            data.writeFileDescriptor(stdin.fileDescriptor)
            data.writeFileDescriptor(stdout.fileDescriptor)
            data.writeFileDescriptor(stderr.fileDescriptor)
            data.writeStringArray(args)
            writeNullShellCallback(data)
            // 服务端仍要求一个 ResultReceiver 对象，但我们不等待它
            ResultReceiver(null).writeToParcel(data, 0)
            binder.transact(SHELL_COMMAND_TRANSACTION, data, null, IBinder.FLAG_ONEWAY)
        } finally {
            data.recycle()
            runCatching { stdin.close() }
            runCatching { stdout.close() }
            runCatching { stderr.close() }
        }
    }

    private fun writeNullShellCallback(parcel: Parcel) {
        val shellCallbackClass = Class.forName("android.os.ShellCallback")
        val method = shellCallbackClass.getDeclaredMethod(
            "writeToParcel",
            shellCallbackClass,
            Parcel::class.java,
        )
        method.invoke(null, null, parcel)
    }

    private companion object {
        /** IBinder.SHELL_COMMAND_TRANSACTION（'_CMD'），隐藏常量照抄。 */
        const val SHELL_COMMAND_TRANSACTION = 0x5F434D44
    }
}
