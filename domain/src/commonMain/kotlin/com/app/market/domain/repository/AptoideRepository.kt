package com.app.market.domain.repository

import com.app.market.domain.model.download.DownloadMeta
import com.app.market.domain.model.market.AppDetail
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.SearchPage
import com.app.market.domain.model.update.ManualUpdateRequest
import com.app.market.domain.model.update.ManualUpdateResult

/** Aptoide 公开 Webservices v7 接入：搜索、详情、官方直链下载与逐包版本比对。 */
interface AptoideRepository {
    suspend fun search(keyword: String, page: Int = 0): SearchPage

    suspend fun appDetail(packageName: String): AppDetail

    suspend fun downloadMeta(app: MarketAppInfo): DownloadMeta

    /** 逐包查询已安装应用的最新版本（无批量接口，内部做并发限流）。 */
    suspend fun checkUpdates(): List<MarketAppInfo>

    suspend fun checkManualUpdate(request: ManualUpdateRequest): ManualUpdateResult
}