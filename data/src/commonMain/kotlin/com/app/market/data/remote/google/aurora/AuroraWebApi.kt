package com.app.market.data.remote.google.aurora

import com.app.market.data.remote.google.GoogleAppRecord
import com.app.market.data.remote.google.GoogleHtmlCards
import com.app.market.data.remote.google.GoogleRegionGeneration
import com.app.market.domain.diagnostic.AppLog
import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonElement

/**
 * Aurora GPlayAPI 网页通道的搜索/详情（GPL-3.0 移植，字段下标对照 WebAppMapper）：
 * 搜索直接用 `lGYRle` / `qnKhOb` 返回的列表字段，不等待 `Ws7gDc` 详情。
 */
internal class AuroraWebApi(client: HttpClient) {
    private val web = AuroraWebClient(client)

    /** 搜索翻页游标（地区代数|query → 上一页的 next token；地区一换游标作废）。 */
    private val nextTokens = mutableMapOf<String, String>()

    /** 榜单结果缓存（chart|category → 时间戳 + 整榜），一次 RPC 的整榜复用 10 分钟。 */
    private val chartCache = mutableMapOf<String, Pair<Long, List<GoogleAppRecord>>>()

    suspend fun search(keyword: String, page: Int): List<GoogleAppRecord> {
        val query = keyword.trim()
        if (query.isEmpty()) return emptyList()
        val tokenKey = "$GoogleRegionGeneration|$query"
        val token = if (page == 0) "" else nextTokens[tokenKey] ?: return emptyList()
        if (page == 0) nextTokens.remove(tokenKey)
        AppLog.log("AuroraSearch: RPC request page=$page")
        val response = web.fetch(listOf(AuroraSearchRpc.build(query, token)))
        val payload = response[AuroraSearchRpc.TAG]?.get(query)
            ?: error("Aurora Google Play 未返回搜索结果帧")
        var sections = payload.dig(0)
        if (sections.digList().isEmpty()) return emptyList()
        // 命中「精确匹配」时第一个 cluster 不是 Apps，真正的列表在其内层
        if (sections.digString(0, 1) != "Apps") {
            sections = sections.dig(1, 0)
        }
        val records = sections.digList(0, 0).mapNotNull { entry ->
            val pkg = entry.digString(12, 0)
            val title = entry.digString(2)
            if (pkg.isBlank() || title.isBlank()) return@mapNotNull null
            GoogleAppRecord(
                packageName = pkg,
                title = GoogleHtmlCards.unescape(title),
                publisher = GoogleHtmlCards.unescape(entry.digString(4, 0, 0, 0)),
                icon = entry.digString(1, 1, 0, 3, 2),
                versionCode = 0L,
                versionString = "",
                offerType = 1,
                fileSize = 0L,
                ratingText = entry.digString(6, 0, 2, 1, 0),
                description = GoogleHtmlCards.unescape(entry.digString(4, 1, 1, 1)),
                subtitle = "",
                recentChanges = "",
                downloadCountText = "",
                screenshots = emptyList(),
            )
        }.distinctBy { it.packageName }
        val next = sections.digString(0, 7, 1)
        if (records.isNotEmpty() && next.isNotBlank()) nextTokens[tokenKey] = next else nextTokens.remove(tokenKey)
        AppLog.log("AuroraSearch: RPC returned ${records.size} items")
        return records
    }

    suspend fun details(packageName: String): GoogleAppRecord? =
        details(listOf(packageName)).firstOrNull()

    /**
     * 榜单（`vyAe2`）：chart 取 `topselling_free` / `topselling_paid` / `topgrossing` / `movers_shakers`，
     * category 取 `APPLICATION` / `GAME` / `FAMILY`。结果按 Play 真实名次返回，稳定可复现。
     */
    suspend fun chart(chart: String, category: String, forceRefresh: Boolean = false): List<GoogleAppRecord> {
        val webChart = chart.removePrefix("apps_")
        // 缓存按地区代数分桶：商店地区一变，旧店面榜单不得复用；
        // 帧 key 仍用 frameKey 对位 RPC 响应（协议里的 key 不带代数）。
        val frameKey = "$category$webChart"
        val key = "$GoogleRegionGeneration|$frameKey"
        chartCache[key]?.let { (at, cached) ->
            if (!forceRefresh && kotlin.time.Clock.System.now().toEpochMilliseconds() - at < 10 * 60_000L) return cached
        }
        val response = web.fetch(listOf(AuroraChartRpc.build(category, webChart)))
        val payload = response[AuroraChartRpc.TAG]?.get(frameKey) ?: error("AuroraStore 未返回榜单数据")
        val packageNames = payload.digList(0, 1, 0, 28, 0).mapNotNull { entry ->
            entry.digString(0, 0, 0).takeIf { it.isNotBlank() }
        }
        if (packageNames.isEmpty()) error("AuroraStore 榜单未返回应用")
        val byPkg = details(packageNames).associateBy { it.packageName }
        val records = packageNames.mapNotNull { byPkg[it] }
        if (records.isEmpty()) error("AuroraStore 榜单未返回应用详情")
        chartCache[key] = kotlin.time.Clock.System.now().toEpochMilliseconds() to records
        return records
    }

    /** 批量元数据：20 个包一批（更大批量未经验证，响应被截断会静默丢条目）。 */
    suspend fun details(packageNames: List<String>): List<GoogleAppRecord> {
        if (packageNames.isEmpty()) return emptyList()
        return packageNames.distinct().chunked(MaxPackagesPerBatch).flatMap { chunk ->
            val response = web.fetch(chunk.map { AuroraMetadataRpc.build(it) })
            chunk.mapNotNull { pkg ->
                val payload = response[AuroraMetadataRpc.TAG]?.get(pkg) ?: return@mapNotNull null
                mapApp(pkg, payload)
            }
        }
    }

    /** `Ws7gDc` 载荷下标对照 GPlayAPI 的 WebAppMapper；无 displayName 视为不可映射。 */
    private fun mapApp(packageName: String, payload: JsonElement?): GoogleAppRecord? {
        val appInfo = payload.dig(1, 2)
        val title = appInfo.digString(0, 0)
        if (title.isBlank()) return null
        val downloads = appInfo.dig(13)
        val offer = appInfo.dig(57, 0, 0, 0, 0)
        val rating = appInfo.dig(51)
        return GoogleAppRecord(
            packageName = packageName,
            title = GoogleHtmlCards.unescape(title),
            publisher = GoogleHtmlCards.unescape(appInfo.digString(68, 0)),
            icon = appInfo.dig(95, 0).digString(3, 2),
            versionCode = 0L,
            versionString = appInfo.digString(140, 0, 0, 0),
            offerType = if (offer.digDouble(1, 0, 0) == 0.0) 1 else 3,
            fileSize = 0L,
            description = GoogleHtmlCards.unescape(appInfo.digString(72, 0, 1)),
            subtitle = GoogleHtmlCards.unescape(appInfo.digString(73, 0, 1)),
            recentChanges = GoogleHtmlCards.unescape(appInfo.digString(144, 1, 1)),
            ratingText = rating.digString(0, 0),
            downloadCountText = appInfo.digString(13, 3).ifBlank { downloads.digDouble(2).toLong().toString() },
            screenshots = appInfo.digList(78, 0).map { it.digString(3, 2) }.filter { it.isNotBlank() },
        )
    }

    private companion object {
        const val MaxPackagesPerBatch = 20
    }
}