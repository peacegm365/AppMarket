package com.app.market.domain.model.update

/** 应用自身的最新发布信息，tag 形如 v2.3.4-mt1。 */
data class SelfUpdate(
    val tag: String,
    val apkUrl: String,
    val notes: String,
    /** 目标版本号（从资产文件名解析）；0 表示未知，安装完整性校验时跳过版本比对。 */
    val versionCode: Long = 0L,
    /** APK 大小（字节）；未知时为 0。 */
    val size: Long = 0L,
    /** 是否取自 GitLab 的 Upcoming Release（预发布）：更新卡片上打「测试版」标签。 */
    val prerelease: Boolean = false,
)