package com.app.market.domain.model.market

/** 搜索结果来源。[MarketAppInfo.appId] 语义随来源而变，均为对应商店的站内应用 id。 */
enum class AppSource(val token: String, val capabilities: SourceCapabilities) {
    XIAOMI(
        "xiaomi",
        SourceCapabilities(
            supportsComments = true,
            supportsSameDeveloperApps = true,
            prefersOpenLinkLaunch = true,
            reportsDeltaSize = true,
            supportsTodayFeed = true,
            todayFullCoverOverlay = true,
            supportsUpdates = true,
            supportsSearchAdsFilter = true,
            supportsQuickAppFilter = true,
            supportsReservationFilter = true,
            supportsPromotions = true,
        ),
    ),
    VIVO(
        "vivo",
        SourceCapabilities(
            supportsComments = false,
            supportsSameDeveloperApps = false,
            prefersOpenLinkLaunch = true,
            reportsDeltaSize = true,
            supportsTodayFeed = true,
            todayFullCoverOverlay = false,
            supportsUpdates = true,
            supportsSearchAdsFilter = true,
        ),
    ),
    WANDOUJIA(
        "wandoujia",
        SourceCapabilities(
            supportsComments = false,
            supportsSameDeveloperApps = false,
            prefersOpenLinkLaunch = true,
            reportsDeltaSize = true,
            supportsTodayFeed = false,
            todayFullCoverOverlay = false,
            // 豌豆荚无原生更新元数据协议，更新下载与手动更新均回退小米，不提供独立更新来源
            supportsUpdates = false,
        ),
    ),
    OPPO(
        "oppo",
        SourceCapabilities(
            supportsComments = true,
            supportsSameDeveloperApps = true,
            prefersOpenLinkLaunch = false,
            reportsDeltaSize = true,
            supportsTodayFeed = true,
            todayFullCoverOverlay = false,
            supportsUpdates = true,
            supportsSearchAdsFilter = true,
        ),
    ),
    SAMSUNG(
        "samsung",
        SourceCapabilities(
            supportsComments = false,
            supportsSameDeveloperApps = false,
            prefersOpenLinkLaunch = false,
            reportsDeltaSize = false,
            supportsTodayFeed = false,
            todayFullCoverOverlay = false,
            supportsUpdates = true,
        ),
    ),
    HONOR(
        "honor",
        SourceCapabilities(
            supportsComments = false,
            supportsSameDeveloperApps = false,
            prefersOpenLinkLaunch = false,
            reportsDeltaSize = true,
            supportsTodayFeed = false,
            todayFullCoverOverlay = false,
            supportsUpdates = true,
            supportsSearchAdsFilter = true,
        ),
    ),
    HUAWEI(
        "huawei",
        SourceCapabilities(
            supportsComments = false,
            supportsSameDeveloperApps = false,
            prefersOpenLinkLaunch = false,
            reportsDeltaSize = true,
            supportsTodayFeed = false,
            todayFullCoverOverlay = false,
            supportsUpdates = true,
            supportsSearchAdsFilter = true,
        ),
    ),
    TAPTAP(
        "taptap",
        SourceCapabilities(
            supportsComments = false,
            supportsSameDeveloperApps = false,
            prefersOpenLinkLaunch = false,
            reportsDeltaSize = false,
            supportsDeltaUpdates = true,
            supportsTodayFeed = true,
            todayFullCoverOverlay = false,
            supportsUpdates = true,
        ),
    ),
    // Google Play：Aurora 分发器匿名鉴权 + fdfe（getItems 批量详情 / delivery 下载）
    GOOGLE(
        "google",
        SourceCapabilities(
            supportsComments = false,
            supportsSameDeveloperApps = false,
            prefersOpenLinkLaunch = false,
            reportsDeltaSize = false,
            supportsTodayFeed = false,
            todayFullCoverOverlay = false,
            supportsUpdates = true,
        ),
    ),
    COOLAPK(
        "coolapk",
        SourceCapabilities(
            supportsComments = false,
            supportsSameDeveloperApps = false,
            prefersOpenLinkLaunch = false,
            reportsDeltaSize = false,
            supportsTodayFeed = false,
            todayFullCoverOverlay = false,
            supportsUpdates = true,
        ),
    ),
    KUAIBAO(
        "kuaibao",
        SourceCapabilities(
            supportsComments = true,
            supportsSameDeveloperApps = false,
            prefersOpenLinkLaunch = false,
            reportsDeltaSize = false,
            // 好游快爆推荐流（KuaibaoApi.recommendations）可作今日页内容
            supportsTodayFeed = true,
            todayFullCoverOverlay = false,
            // 网页渠道不暴露版本号，无独立更新协议，更新下载与手动更新均回退小米
            supportsUpdates = false,
        ),
    ),
    APPCHINA(
        "appchina",
        SourceCapabilities(
            supportsComments = true,
            supportsSameDeveloperApps = false,
            prefersOpenLinkLaunch = false,
            reportsDeltaSize = false,
            supportsTodayFeed = false,
            todayFullCoverOverlay = false,
            // 无原生批量接口，逐包搜索应用汇搜索结果中的 versionCode 做更新比对
            supportsUpdates = true,
            supportsHistoricalVersions = true,
        ),
    ),
    TENCENT(
        "tencent",
        SourceCapabilities(
            supportsComments = true,
            supportsSameDeveloperApps = false,
            prefersOpenLinkLaunch = false,
            reportsDeltaSize = false,
            supportsTodayFeed = false,
            todayFullCoverOverlay = false,
            supportsUpdates = true,
        ),
    ),
    BOX7723(
        "7723",
        SourceCapabilities(
            supportsComments = false,
            supportsSameDeveloperApps = false,
            prefersOpenLinkLaunch = true,
            reportsDeltaSize = false,
            supportsTodayFeed = false,
            todayFullCoverOverlay = false,
            supportsUpdates = false,
        ),
    ),
    FDROID(
        "fdroid",
        SourceCapabilities(
            supportsComments = false,
            supportsSameDeveloperApps = false,
            prefersOpenLinkLaunch = false,
            reportsDeltaSize = false,
            supportsTodayFeed = false,
            todayFullCoverOverlay = false,
            // 基于仓库索引（index-v2）做本地更新比对；仅推签名指纹兼容的条目
            supportsUpdates = true,
            supportsHistoricalVersions = true,
        ),
    ),
    APKPURE(
        "apkpure",
        SourceCapabilities(
            supportsComments = false,
            supportsSameDeveloperApps = false,
            prefersOpenLinkLaunch = false,
            reportsDeltaSize = false,
            supportsTodayFeed = false,
            todayFullCoverOverlay = false,
            // 网页端没有批量版本接口，更新检查与手动更新回退小米商店
            supportsUpdates = false,
        ),
    ),
    APTOIDE(
        "aptoide",
        SourceCapabilities(
            supportsComments = false,
            supportsSameDeveloperApps = false,
            prefersOpenLinkLaunch = false,
            reportsDeltaSize = false,
            supportsTodayFeed = false,
            todayFullCoverOverlay = false,
            // WebServices v7 逐包 getApp 比对版本
            supportsUpdates = true,
        ),
    ),
    GITHUB(
        "github",
        SourceCapabilities(
            supportsComments = false, supportsSameDeveloperApps = false,
            prefersOpenLinkLaunch = false, reportsDeltaSize = false,
            supportsTodayFeed = false, todayFullCoverOverlay = false, supportsUpdates = true,
        ),
    ),
    LSPOSED(
        "lsposed",
        SourceCapabilities(
            supportsComments = false,
            supportsSameDeveloperApps = false,
            prefersOpenLinkLaunch = false,
            reportsDeltaSize = false,
            supportsTodayFeed = false,
            todayFullCoverOverlay = false,
            // 以 Xposed Module Repository 的正式版 Release 做更新比对
            supportsUpdates = true,
        ),
    ),

    GITLAB(
        "gitlab",
        SourceCapabilities(
            supportsComments = false, supportsSameDeveloperApps = false,
            prefersOpenLinkLaunch = false, reportsDeltaSize = false,
            supportsTodayFeed = false, todayFullCoverOverlay = false, supportsUpdates = true,
        ),
    ),
    ;

    companion object {
        val Default: Set<AppSource> = setOf(WANDOUJIA)
        /** 今日页内容来源默认值；须为声明了 [SourceCapabilities.supportsTodayFeed] 的源。 */
        val DefaultTodaySource: AppSource = XIAOMI
        /** 游戏 / 应用分区的内容来源默认值；其余来源无原生分区接口，会回退小米。 */
        val DefaultCategorySource: AppSource = XIAOMI
        /** 应用页来源默认值。 */
        val DefaultAppCategorySource: AppSource = XIAOMI
        /** 游戏页来源默认值。 */
        val DefaultGameCategorySource: AppSource = XIAOMI

        /** 评论来源默认值：默认关闭（与旧版「评论显示」开关默认关一致） */
    val DefaultCommentSource: CommentSourceSetting = CommentSourceSetting.Off

    /**
     * 附加来源：GitHub / GitLab 这类只提供 Release 包的仓库源。
     * 只提供 Release 包的仓库源：搜索/更新来源下拉照常可见，
     * 但今日流不提供内容（不能出现在今日来源里）。
     */
    val ExtraSources: Set<AppSource> = setOf(GITHUB, GITLAB)

    /** 以「仓库 release」为单位的来源：详情页按 README + 发布列表展示，不显示评分/下载量/大小。 */
    val RepositorySources: Set<AppSource> = setOf(LSPOSED, GITHUB)

    /**
     * 不再作为商店来源暴露的条目。
     * GitLab 只保留在枚举里给「App 自更新」标记来源用（自更新包走 GitLab Release），
     * 不进入搜索/更新/今日任何下拉。
     */
    val HiddenSources: Set<AppSource> = setOf(GITLAB)


    /** 提供原生分区内容的来源，仅这些值在分区来源设置中有意义。 */
    val CategorySources: List<AppSource> = listOf(XIAOMI, TAPTAP, HUAWEI, OPPO, GOOGLE, GITHUB)
    /** 统一按更新来源默认顺序排列；更新来源中没有的条目保持原有相对顺序并排在后面。 */
    fun orderLikeUpdateSources(sources: List<AppSource>): List<AppSource> =
        sources.sortedBy { source -> UpdateSourceOptions.indexOf(source).let { if (it < 0) Int.MAX_VALUE else it } }
    /** 分区来源下拉的可选项。 */
    val CategorySourceOptions: List<AppSource> by lazy { orderLikeUpdateSources(CategorySources) }
    /** 评论来源下拉的可选项：所有能取到评论的来源（取不到的不出现）。 */
    val CommentSources: List<AppSource> = entries.filter { it.capabilities.supportsComments }

    /** 搜索来源下拉的可选项：全量来源，排除已下架的 GitLab。 */
    val SearchSourceOptions: List<AppSource> by lazy { orderLikeUpdateSources(entries.filterNot { it in HiddenSources }) }
    /** 更新来源下拉的可选项：所有声明了 supportsUpdates 的来源。 */
    val UpdateSourceOptions: List<AppSource> =
        entries.filter { it.capabilities.supportsUpdates && it !in HiddenSources }
        /** 更新来源默认值；须为声明了 [SourceCapabilities.supportsUpdates] 的源。 */
        val DefaultUpdateSource: AppSource = XIAOMI

        fun parse(raw: String?): Set<AppSource> {
            if (raw.isNullOrBlank()) return Default
            // 旧版本允许多选；设置页改为单选后按枚举顺序保留第一个有效来源。
            val selected = entries.firstOrNull { source ->
                raw.split(',').any { it.trim() == source.token }
            }
            return selected?.let(::setOf) ?: Default
        }

        fun serialize(sources: Set<AppSource>): String =
            entries.filter { it in sources }.joinToString(",") { it.token }

        fun fromToken(raw: String?): AppSource? =
            entries.firstOrNull { it.token.equals(raw, ignoreCase = true) }
    }
}

/**
 * 评论来源设置：关闭 / 跟随应用来源 / 指定来源。
 *
 * 「跟随应用来源」时若详情页来源本身取不到评论，就不加载也不显示评论区，
 * 不再像旧版那样回退小米（回退会让用户看到一个跟当前来源无关的评论区）。
 */
sealed interface CommentSourceSetting {
    /** 关闭：不加载、不显示评论。 */
    data object Off : CommentSourceSetting

    /** 跟随应用来源。 */
    data object FollowApp : CommentSourceSetting
    /** 实际用于取评论的来源；null 表示不显示评论。 */
    fun resolve(app: AppSource): AppSource? = when (this) {
        Off -> null
        FollowApp -> app.takeIf { it.capabilities.supportsComments }
    }

    companion object {
        const val OFF_TOKEN = "off"
        const val FOLLOW_TOKEN = "follow"

        fun parse(raw: String?): CommentSourceSetting = when {
            // 未设置 = 默认关闭；"follow" 是显式「跟随应用来源」，别混成默认值
            raw.isNullOrBlank() -> AppSource.DefaultCommentSource
            raw == FOLLOW_TOKEN -> FollowApp
            raw == OFF_TOKEN -> Off
            // 自定义固定来源已移除：历史配置统一归到「跟随应用来源」
            else -> FollowApp
        }
        fun serialize(value: CommentSourceSetting): String = when (value) {
            Off -> OFF_TOKEN
            FollowApp -> FOLLOW_TOKEN
        }
    }
}