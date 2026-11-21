package com.app.market.domain.repository

import com.app.market.domain.model.download.DownloadMeta
import com.app.market.domain.model.market.AppDetail
import com.app.market.domain.model.market.AppHistoricalVersion
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.SearchPage
import com.app.market.domain.model.update.ManualUpdateRequest
import com.app.market.domain.model.update.ManualUpdateResult

/** Google Play 匿名来源（Aurora 分发器 + fdfe 协议）：搜索、详情、下载与更新检查。 */
interface GooglePlayRepository {
    /** 当前 Google 身份 ID（gsfId），未获取时为 null。 */
    fun currentGsfId(): String? = null
    /** 手动设置 Google 身份 ID。 */
    fun setGsfId(id: String) = Unit

    /** 当前生效的 Google 设备信息快照（默认 + 真机定向 + 覆盖），设备信息页展示用。 */
    fun devicePropertiesSnapshot(): Map<String, String> = emptyMap()
    /** 重新获取 Google 身份（换一个 gsfId），结果集不理想时手动触发。 */
    suspend fun resetGoogleIdentity() = Unit
    suspend fun search(keyword: String, page: Int = 0): SearchPage

    /** Play 排行榜（网页 top 榜，一页 ~50 条，按 page 切片）。 */
    suspend fun rankedApps(
        page: Int = 0,
        chart: com.app.market.domain.model.market.ChartKind = com.app.market.domain.model.market.ChartKind.FREE,
        category: com.app.market.domain.model.market.AppCategory = com.app.market.domain.model.market.AppCategory.APPS,
    ): SearchPage
    suspend fun webpageCategories(category: com.app.market.domain.model.market.AppCategory): List<com.app.market.domain.model.market.CategoryOption> = emptyList()
    suspend fun webpageCategoryApps(optionId: String, page: Int): SearchPage = SearchPage(emptyList(), false)
    suspend fun appDetail(packageName: String): AppDetail
    suspend fun downloadMeta(app: MarketAppInfo): DownloadMeta
    suspend fun downloadUpdateMeta(app: MarketAppInfo): DownloadMeta
    suspend fun checkUpdates(): List<MarketAppInfo>
    /** 历史版本目录（Exodus 版本记录）：版本名 / 版本号 / 日期，取不到返回空。 */
    suspend fun historicalVersions(packageName: String): List<AppHistoricalVersion> = emptyList()
    /**
     * 手动检查更新：[ManualUpdateRequest.versionCode] 是用户当前安装的版本号，
     * 返回比它新的版本；不做新旧比较的「下载指定历史版本」由详情页入口负责。
     */
    suspend fun checkManualUpdate(request: ManualUpdateRequest): ManualUpdateResult
    /** AuroraStore 引擎：网页通道搜索（免鉴权、请求最少）。 */
    suspend fun auroraSearch(keyword: String, page: Int): SearchPage = SearchPage(emptyList(), false)
    /** AuroraStore 引擎：网页通道详情。 */
    suspend fun auroraDetail(packageName: String): AppDetail? = null
}