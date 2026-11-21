package com.app.market.domain.repository

import com.app.market.domain.model.download.DownloadMeta
import com.app.market.domain.model.market.AppCategory
import com.app.market.domain.model.market.AppComments
import com.app.market.domain.model.market.AppDetail
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.AppSubCategory
import com.app.market.domain.model.market.CategoryOption
import com.app.market.domain.model.market.GameRanking
import com.app.market.domain.model.market.GameSubCategory
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.ModuleAsset
import com.app.market.domain.model.market.SearchPage
import com.app.market.domain.model.today.TodayArticle
import com.app.market.domain.model.today.TodayFeedPage
import com.app.market.domain.model.update.ManualUpdateRequest
import com.app.market.domain.model.update.ManualUpdateResult
import kotlinx.coroutines.flow.Flow

/**
 * Unified market entry point used by the UI.
 *
 * A source is selected once here, rather than in every screen. Implementations may fall back to
 * Xiaomi for an operation that a third-party store does not expose; the returned app keeps the
 * source that actually owns its download URL.
 */
interface MarketSourceRepository {
    /** LSPosed 模块的 README 首图图标（搜索列表懒加载用）；其他来源返回 null。 */
    suspend fun lsposedIcon(packageName: String): String? = null

    /** 本地已安装应用的图标（`file://` URL）：LSPosed 模块仓库接口不带图标，直接用设备上的。 */
    suspend fun installedIcon(packageName: String): String? = null
    /** 本地已安装应用的用户可见名称；未安装或平台不支持时返回 null。 */
    suspend fun installedLabel(packageName: String): String? = null

    /** README 里引用的文档原文（README_EN.md 等），供应用内查看。 */
    suspend fun docText(url: String): String? = null
    suspend fun search(source: AppSource, keyword: String, page: Int = 0): SearchPage

    /**
     * 增量搜索：每解析出一个结果就立刻产出一次（目前只有 GitHub 仓库源支持）。
     * 返回 null 表示该来源不支持增量，调用方回退到 [search]。
     */
    fun searchIncremental(source: AppSource, keyword: String, page: Int = 0): kotlinx.coroutines.flow.Flow<SearchPage>? = null

    suspend fun categoryApps(
        source: AppSource,
        category: AppCategory,
        subCategory: AppSubCategory = AppSubCategory.TOOLS,
        ranking: GameRanking = GameRanking.HOT,
        gameSubCategory: GameSubCategory = GameSubCategory.ALL,
        optionId: String? = null,
        page: Int = 0,
        chart: com.app.market.domain.model.market.ChartKind = com.app.market.domain.model.market.ChartKind.FREE,
    ): SearchPage

    suspend fun categoryOptions(source: AppSource, category: AppCategory): List<CategoryOption>

    suspend fun appDetail(
        source: AppSource,
        appId: Long,
        packageName: String,
        externalQuery: String? = null,
    ): AppDetail

    suspend fun appComments(source: AppSource, app: MarketAppInfo): AppComments

    suspend fun sameDeveloperApps(source: AppSource, app: MarketAppInfo): List<MarketAppInfo>

    suspend fun downloadMeta(
        source: AppSource,
        app: MarketAppInfo,
        keyword: String = app.displayName,
        /** LSPosed 等「一个版本多份附件」的来源：指定要下的附件名（null=自动挑最合适的一份）。 */
        assetName: String? = null,
    ): DownloadMeta

    /** 一版多附件来源（目前只有 LSPosed）的附件列表；其余来源返回空表。 */
    suspend fun moduleAssets(source: AppSource, app: MarketAppInfo): List<ModuleAsset> = emptyList()

    /** LSPosed 模块的历史发布记录（新到旧）；其它来源为空。 */
    suspend fun moduleReleases(
        source: AppSource,
        app: MarketAppInfo,
    ): List<com.app.market.domain.model.market.ModuleRelease> = emptyList()

    suspend fun downloadUpdateMeta(source: AppSource, app: MarketAppInfo): DownloadMeta

    /** 直接读未对账的缓存（首屏秒开用）。默认回退到对账版本，测试实现无需覆盖。 */
    suspend fun loadRawCachedUpdates(): List<MarketAppInfo> = loadReconciledCachedUpdates()
    suspend fun loadReconciledCachedUpdates(): List<MarketAppInfo>

    fun checkUpdatesFlow(source: AppSource): Flow<List<MarketAppInfo>>

    suspend fun checkManualUpdate(source: AppSource, request: ManualUpdateRequest): ManualUpdateResult

    suspend fun goldMiFeed(source: AppSource, page: Int = 0, pageSize: Int = 9): TodayFeedPage

    suspend fun todayArticle(source: AppSource, rId: String): TodayArticle
}