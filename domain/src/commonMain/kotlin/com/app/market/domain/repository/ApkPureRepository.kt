package com.app.market.domain.repository

import com.app.market.domain.model.download.DownloadMeta
import com.app.market.domain.model.market.AppDetail
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.SearchPage

/** APKPure 匿名只读接入：网页搜索 + 详情页结构化数据 + 官方直链下载。 */
interface ApkPureRepository {
    suspend fun search(keyword: String, page: Int = 0): SearchPage

    /** [hintUrl] 为搜索结果里的详情页地址；为空时按包名反查。 */
    suspend fun appDetail(packageName: String, hintUrl: String = ""): AppDetail

    suspend fun downloadMeta(app: MarketAppInfo): DownloadMeta
}
