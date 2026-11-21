package com.app.market.data.repository

import com.app.market.data.remote.apkpure.ApkPureApi
import com.app.market.domain.model.download.DownloadMeta
import com.app.market.domain.model.market.AppDetail
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.SearchPage
import com.app.market.domain.repository.ApkPureRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ApkPureRepositoryImpl(
    private val api: ApkPureApi,
) : ApkPureRepository {
    override suspend fun search(keyword: String, page: Int): SearchPage = withContext(Dispatchers.Default) {
        api.search(keyword, page)
    }

    override suspend fun appDetail(packageName: String, hintUrl: String): AppDetail =
        withContext(Dispatchers.Default) {
            api.appDetail(packageName, hintUrl)
        }

    override suspend fun downloadMeta(app: MarketAppInfo): DownloadMeta = withContext(Dispatchers.Default) {
        // 搜索结果不带版本号，下载前先补一次详情，保证版本号/包名与直链一致
        val detail = runCatching { api.appDetail(app.packageName, app.openLink) }.getOrNull()
        api.downloadMeta(detail?.app ?: app)
    }
}