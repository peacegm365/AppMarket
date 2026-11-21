package com.app.market.viewmodel

import com.app.market.domain.model.market.*
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.today.TodayFeaturedItem
import com.app.market.domain.model.today.TodayFeedPage
import kotlinx.serialization.json.*

/** Durable snapshots use source-specific keys; bad or older snapshots are ignored. */
internal object PageCacheCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encodeModuleReleases(releases: List<ModuleRelease>): String = buildJsonArray {
        releases.forEach { release -> add(buildJsonObject {
            put("name", release.name); put("tag", release.tagName); put("code", release.versionCode)
            put("published", release.publishedAt); put("changes", release.changeLog)
            put("assets", buildJsonArray { release.assets.forEach { asset -> add(buildJsonObject {
                put("name", asset.name); put("url", asset.url); put("size", asset.size)
            }) } })
        }) }
    }.toString()
    fun decodeModuleReleases(raw: String): List<ModuleRelease>? = runCatching {
        json.parseToJsonElement(raw).jsonArray.map { entry ->
            val o = entry.jsonObject
            ModuleRelease(o.s("name"), o.s("tag"), o.l("code"), o.l("published"), o.s("changes"),
                (o["assets"] as? JsonArray).orEmpty().map { asset ->
                    val a = asset.jsonObject
                    ModuleAsset(a.s("name"), a.s("url"), a.l("size"))
                })
        }
    }.getOrNull()
    private fun JsonObject.s(key: String, default: String = "") = (this[key] as? JsonPrimitive)?.contentOrNull ?: default
    private fun JsonObject.l(key: String) = (this[key] as? JsonPrimitive)?.longOrNull ?: 0L
    private fun JsonObject.b(key: String, default: Boolean = false) = (this[key] as? JsonPrimitive)?.booleanOrNull ?: default
    private fun app(a: MarketAppInfo) = buildJsonObject {
        put("id", a.appId); put("pkg", a.packageName); put("name", a.displayName); put("publisher", a.publisherName)
        put("version", a.versionName); put("code", a.versionCode); put("icon", a.icon); put("size", a.apkSize)
        put("delta", a.deltaSize); put("rating", a.ratingScore); put("changes", a.changeLog); put("system", a.isSystemApp)
        put("link", a.openLink); put("installedName", a.installedVersionName); put("installedCode", a.installedVersionCode)
        put("hash", a.installedOldApkHash); put("path", a.installedBaseApkPath); put("splits", a.installedSplits)
        put("type", a.type); put("ad", a.isAd); put("subscribe", a.subscribeState); put("blocked", a.downloadBlockReason)
        put("source", a.source.token); put("category", a.category); put("downloads", a.downloadCount)
        put("summary", a.summaryText); put("release", a.releaseTimeText); put("label", a.localLabel)
    }
    private fun app(o: JsonObject): MarketAppInfo = MarketAppInfo(
        appId = o.l("id"), packageName = o.s("pkg"), displayName = o.s("name"), publisherName = o.s("publisher"),
        versionName = o.s("version"), versionCode = o.l("code"), icon = o.s("icon"), apkSize = o.l("size"),
        deltaSize = o.l("delta"), ratingScore = (o["rating"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
        changeLog = o.s("changes"), isSystemApp = o.b("system"), openLink = o.s("link"),
        installedVersionName = o.s("installedName"), installedVersionCode = o.l("installedCode"),
        installedOldApkHash = o.s("hash", "0"), installedBaseApkPath = o.s("path"), installedSplits = o.s("splits", "0"),
        type = o.s("type"), isAd = o.b("ad"), subscribeState = o.l("subscribe").toInt(), downloadBlockReason = o.s("blocked"),
        source = AppSource.fromToken(o.s("source")) ?: AppSource.XIAOMI, category = o.s("category"),
        downloadCount = o.l("downloads"), summaryText = o.s("summary"), releaseTimeText = o.s("release"), localLabel = o.s("label"),
    )
    fun encodeCategory(apps: List<MarketAppInfo>, hasMore: Boolean, nextPage: Int): String = buildJsonObject {
        put("hasMore", hasMore); put("nextPage", nextPage); put("items", buildJsonArray { apps.forEach { add(app(it)) } })
    }.toString()
    fun decodeCategory(raw: String): Triple<List<MarketAppInfo>, Boolean, Int>? = runCatching {
        val o = json.parseToJsonElement(raw).jsonObject
        Triple(o["items"]!!.jsonArray.map { app(it.jsonObject) }, o.b("hasMore"), o.l("nextPage").toInt())
    }.getOrNull()
    fun encodeToday(feed: TodayFeedPage, nextPage: Int): String = buildJsonObject {
        put("hasMore", feed.hasMore); put("nextPage", nextPage)
        put("items", buildJsonArray { feed.items.forEach { item -> add(buildJsonObject {
            put("id", item.rId); put("title", item.title); put("summary", item.summary); put("cover", item.coverImage)
            put("link", item.articleLink); put("award", item.awardName); put("titleLabel", item.showTitleLabel)
            put("coverText", item.showCoverText); item.app?.let { put("app", app(it)) }
            put("apps", buildJsonArray { item.apps.forEach { add(app(it)) } })
        }) } })
    }.toString()
    fun decodeToday(raw: String): Pair<TodayFeedPage, Int>? = runCatching {
        val o = json.parseToJsonElement(raw).jsonObject
        val items = o["items"]!!.jsonArray.map { e ->
            val item = e.jsonObject
            TodayFeaturedItem(rId = item.s("id"), title = item.s("title"), summary = item.s("summary"),
                coverImage = item.s("cover"), app = (item["app"] as? JsonObject)?.let(::app),
                articleLink = item.s("link"), apps = (item["apps"] as? JsonArray)?.map { app(it.jsonObject) }.orEmpty(),
                awardName = item.s("award"), showTitleLabel = item.b("titleLabel", true), showCoverText = item.b("coverText", true))
        }
        TodayFeedPage(items, o.b("hasMore")) to o.l("nextPage").toInt()
    }.getOrNull()
    fun encodeDetail(d: AppDetail): String = buildJsonObject {
        put("app", app(d.app)); put("brief", d.brief); put("intro", d.introduction); put("html", d.introductionHtml)
        put("change", d.changeLog); put("category", d.category); put("age", d.ageClassification); put("count", d.downloadCount)
        put("registration", d.registrationNum); put("time", d.updateTime); put("privacy", d.privacyUrl); put("homepage", d.homepageUrl); put("commentCount", d.commentCount)
        put("screens", buildJsonArray { d.screenshots.forEach { add(buildJsonObject { put("url", it.url); put("orientation", it.orientation.name); put("expanded", it.expandedUrl) }) } })
        put("videos", buildJsonArray { d.videos.forEach { add(buildJsonObject { put("url", it.url); put("cover", it.coverUrl); put("orientation", it.orientation.name); put("title", it.title) }) } })
        put("collaborators", buildJsonArray { d.collaborators.forEach { add(buildJsonObject { put("login", it.login); put("name", it.name) }) } })
        put("comments", buildJsonArray { d.comments.forEach { add(buildJsonObject { put("user", it.userName); put("content", it.content); put("score", it.score) }) } })
        put("same", buildJsonArray { d.sameDeveloperApps.forEach { add(app(it)) } })
        put("promotions", buildJsonArray { d.promotions.forEach { add(buildJsonObject { put("preview", it.previewImageUrl); put("expanded", it.expandedImageUrl); put("title", it.title); put("description", it.description); put("category", it.category); put("tag", it.activityTag); put("jump", it.jumpUrl) }) } })
    }.toString()
    fun decodeDetail(raw: String): AppDetail? = runCatching {
        val o = json.parseToJsonElement(raw).jsonObject
        fun items(k: String) = (o[k] as? JsonArray).orEmpty().map { it.jsonObject }
        fun orientation(o: JsonObject) = ScreenshotOrientation.entries.firstOrNull { it.name == o.s("orientation") } ?: ScreenshotOrientation.PORTRAIT
        AppDetail(app = app(o["app"]!!.jsonObject), brief = o.s("brief"), introduction = o.s("intro"), changeLog = o.s("change"), category = o.s("category"), ageClassification = o.s("age"), downloadCount = o.l("count"), registrationNum = o.s("registration"), updateTime = o.l("time"), privacyUrl = o.s("privacy"),
          screenshots = items("screens").map { AppScreenshot(it.s("url"), orientation(it), it.s("expanded")) }, videos = items("videos").map { AppVideo(it.s("url"), it.s("cover"), orientation(it), it.s("title")) }, commentCount = o.l("commentCount"), comments = items("comments").map { AppComment(it.s("user"), it.s("content"), (it["score"] as? JsonPrimitive)?.doubleOrNull ?: 0.0) }, sameDeveloperApps = items("same").map(::app), promotions = items("promotions").map { AppPromotion(it.s("preview"), it.s("expanded"), it.s("title"), it.s("description"), it.s("category"), it.s("tag"), it.s("jump")) }, introductionHtml = o.s("html"), homepageUrl = o.s("homepage"), collaborators = items("collaborators").map { AppCollaborator(it.s("login"), it.s("name")) })
    }.getOrNull()
}