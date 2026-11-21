package com.app.market.domain.model.market

data class AppDetail(
    val app: MarketAppInfo,
    val brief: String,
    val introduction: String,
    val changeLog: String,
    val category: String,
    val ageClassification: String,
    val downloadCount: Long,
    val registrationNum: String,
    val updateTime: Long = 0L,
    val privacyUrl: String,
    val screenshots: List<AppScreenshot>,
    val videos: List<AppVideo> = emptyList(),
    val commentCount: Long = 0L,
    val comments: List<AppComment>,
    val sameDeveloperApps: List<MarketAppInfo>,
    val promotions: List<AppPromotion> = emptyList(),
    /** README 原始 HTML（仅 LSPosed 模块有），给详情页做富文本渲染用。 */
    val introductionHtml: String = "",
    /** LSPosed 模块的主页地址（GitHub 仓库页）。 */
    val homepageUrl: String = "",
    /** LSPosed 模块的协作者（登录名 + 显示名）。 */
    val collaborators: List<AppCollaborator> = emptyList(),
)

/** LSPosed 模块协作者。 */
data class AppCollaborator(
    val login: String = "",
    val name: String = "",
)
