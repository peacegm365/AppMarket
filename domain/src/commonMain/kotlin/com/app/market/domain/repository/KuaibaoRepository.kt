package com.app.market.domain.repository

import com.app.market.domain.model.download.DownloadMeta
import com.app.market.domain.model.market.AppComments
import com.app.market.domain.model.market.AppDetail
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.SearchPage

/** 好游快爆（3839.com）网页渠道匿名只读接入：搜索、详情、评论、当前版本下载。 */
interface KuaibaoRepository {
    suspend fun search(keyword: String, page: Int = 0): SearchPage
    /** 好游快爆推荐流，用作今日页内容。 */
    suspend fun recommendations(page: Int = 0): com.app.market.domain.model.today.TodayFeedPage
    suspend fun appDetail(appId: Long): AppDetail
    /** 今日推荐条目转成卡片内容（好游快爆没有图文体系，用应用详情拼）。 */
    suspend fun article(rId: String): com.app.market.domain.model.today.TodayArticle
    suspend fun appComments(app: MarketAppInfo): AppComments
    suspend fun downloadMeta(app: MarketAppInfo): DownloadMeta
}
