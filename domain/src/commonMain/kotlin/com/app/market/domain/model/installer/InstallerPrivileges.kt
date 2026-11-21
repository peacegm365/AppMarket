package com.app.market.domain.model.installer

/** 当前可用的安装特权（用于设置页的权限校验副标题）。 */
data class InstallerPrivileges(
    val rootAvailable: Boolean = false,
    /** root 管理器名字，如 Magisk / KernelSU / APatch。 */
    val rootManager: String = "",
    val shizukuAvailable: Boolean = false,
    val dhizukuAvailable: Boolean = false,
    val systemAvailable: Boolean = false,
)
