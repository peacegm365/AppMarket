package com.app.market.domain.model.installer

enum class InstallerMode(val key: String) {
    STANDARD("standard"),
    /** 系统权限静默安装：本应用自身持有 INSTALL_PACKAGES（系统应用/ADB 授权）时静默安装。 */
    SYSTEM("system"),
    ROOT("root"),
    SHIZUKU("shizuku"),
    DHIZUKU("dhizuku"),
    THIRD_PARTY("third_party");

    companion object {
        fun fromKey(key: String?): InstallerMode = when (key) {
            SYSTEM.key -> SYSTEM
            ROOT.key -> ROOT
            SHIZUKU.key -> SHIZUKU
            DHIZUKU.key -> DHIZUKU
            THIRD_PARTY.key, "custom" -> THIRD_PARTY
            else -> STANDARD
        }
    }
}
