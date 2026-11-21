package com.app.market.data.repository

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
import com.app.market.domain.repository.HonorRepository
import com.app.market.domain.repository.HuaweiRepository
import com.app.market.domain.repository.MarketRepository
import com.app.market.domain.repository.ApkPureRepository
import com.app.market.domain.repository.AppchinaRepository
import com.app.market.domain.repository.AptoideRepository
import com.app.market.domain.repository.Box7723Repository
import com.app.market.domain.repository.KuaibaoRepository
import com.app.market.domain.repository.TencentRepository
import com.app.market.domain.repository.CoolapkRepository
import com.app.market.domain.repository.FdroidRepository
import com.app.market.domain.repository.GooglePlayRepository
import com.app.market.domain.repository.MarketSourceRepository
import com.app.market.domain.repository.OppoRepository
import com.app.market.domain.repository.SamsungRepository
import com.app.market.domain.repository.TodayRepository
import com.app.market.domain.repository.TapTapRepository
import com.app.market.domain.repository.VivoRepository
import com.app.market.domain.repository.WandoujiaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow

/** Central source router. Capabilities are declared on [AppSource.capabilities]. */
internal class MarketSourceRepositoryImpl(
    private val market: MarketRepository,
    private val today: TodayRepository,
    private val vivo: VivoRepository,
    private val wandoujia: WandoujiaRepository,
    private val oppo: OppoRepository,
    private val samsung: SamsungRepository,
    private val honor: HonorRepository,
    private val huawei: HuaweiRepository,
    private val tapTap: TapTapRepository,
    private val coolapk: CoolapkRepository,
    private val google: GooglePlayRepository,
    private val kuaibao: KuaibaoRepository,
    private val appchina: AppchinaRepository,
    private val tencent: TencentRepository,
    private val box7723: Box7723Repository,
    private val fdroid: FdroidRepository,
    private val apkPure: ApkPureRepository,
    private val aptoide: AptoideRepository,
    private val releaseSource: ReleaseSourceRepository,
    private val lsposed: LsposedRepository,
    private val updatePrefs: com.app.market.domain.repository.UpdatePreferencesRepository,
) : MarketSourceRepository {
    override suspend fun search(source: AppSource, keyword: String, page: Int): SearchPage = (when (source) {
        AppSource.XIAOMI -> market.search(keyword, page)
        AppSource.VIVO -> vivo.search(keyword, page)
        AppSource.WANDOUJIA -> wandoujia.search(keyword, page)
        AppSource.OPPO -> oppo.search(keyword, page)
        AppSource.SAMSUNG -> samsung.search(keyword, page)
        AppSource.HONOR -> honor.search(keyword, page)
        AppSource.HUAWEI -> huawei.search(keyword, page)
        AppSource.TAPTAP -> tapTap.search(keyword, page)
        AppSource.COOLAPK -> coolapk.search(keyword, page)
        AppSource.GOOGLE -> google.search(keyword, page)
        AppSource.KUAIBAO -> kuaibao.search(keyword, page)
        AppSource.APPCHINA -> appchina.search(keyword, page)
        AppSource.TENCENT -> tencent.search(keyword, page)
        AppSource.BOX7723 -> box7723.search(keyword, page)
        AppSource.FDROID -> fdroid.search(keyword, page)
        AppSource.APKPURE -> apkPure.search(keyword, page)
        AppSource.APTOIDE -> aptoide.search(keyword, page)
        AppSource.GITHUB, AppSource.GITLAB -> releaseSource.search(source, keyword, page)
        AppSource.LSPOSED -> lsposed.search(keyword, page)
    }).withRealNames()

    override fun searchIncremental(
        source: AppSource,
        keyword: String,
        page: Int,
    ): kotlinx.coroutines.flow.Flow<SearchPage>? =
        if (source == AppSource.GITHUB) releaseSource.searchIncremental(source, keyword, page) else null

    override suspend fun lsposedIcon(packageName: String): String? = lsposed.readmeIcon(packageName)

    override suspend fun installedIcon(packageName: String): String? =
        com.app.market.data.platform.installedAppIconUrl(packageName)
    override suspend fun installedLabel(packageName: String): String? =
        // 「获取真实应用名」开关只决定显示选用；查询能力始终提供（历史记录等要随开关实时切换）
        com.app.market.data.platform.installedAppLabel(packageName)

    /** 已装应用补本地真实 label；显示层按「获取真实应用名」开关即时选用（切换立即生效）。 */
    private suspend fun MarketAppInfo.withRealName(): MarketAppInfo {
        val label = runCatching { com.app.market.data.platform.installedAppLabel(packageName) }.getOrNull()
        return if (label.isNullOrBlank()) this else copy(localLabel = label)
    }
    private suspend fun List<MarketAppInfo>.withRealNames(): List<MarketAppInfo> = map { it.withRealName() }
    private suspend fun SearchPage.withRealNames(): SearchPage = copy(items = items.withRealNames())
    private suspend fun AppDetail.withRealName(): AppDetail = copy(app = app.withRealName())
    override suspend fun docText(url: String): String? = lsposed.fileText(url)

    override suspend fun moduleReleases(
        source: AppSource,
        app: MarketAppInfo,
    ): List<com.app.market.domain.model.market.ModuleRelease> =
        when (source) {
            AppSource.LSPOSED -> lsposed.releases(app.packageName)
            AppSource.GITHUB, AppSource.GITLAB -> releaseSource.releases(source, app)
            else -> emptyList()
        }

    override suspend fun categoryApps(
        source: AppSource,
        category: AppCategory,
        subCategory: AppSubCategory,
        ranking: GameRanking,
        gameSubCategory: GameSubCategory,
        optionId: String?,
        page: Int,
        chart: com.app.market.domain.model.market.ChartKind,
    ): SearchPage = (when {
        source == AppSource.GITHUB -> releaseSource.githubRanking(optionId ?: "trending", page)
        // Google Play：AppMarket 引擎只有一份 Top 免费榜；AuroraStore 引擎有四种榜单 × 应用/游戏
        source == AppSource.GOOGLE && updatePrefs.googlePlayEngine.value != "aurorastore" -> google.webpageCategoryApps(optionId ?: error("Google Play 分类尚未加载"), page)
        source == AppSource.GOOGLE -> google.rankedApps(page, chart, category)
        category == AppCategory.GAMES && source == AppSource.TAPTAP -> tapTap.rankedGames(ranking, page)
        source == AppSource.HUAWEI -> huawei.rankedApps(games = category == AppCategory.GAMES, page = page)
        source == AppSource.OPPO -> oppo.categoryApps(
            games = category == AppCategory.GAMES,
            optionId = optionId ?: oppo.categoryOptions(category == AppCategory.GAMES).first().id,
            page = page,
        )

        category == AppCategory.GAMES -> market.categoryApps(gameSubCategory.xiaomiCategoryId, page)
        else -> market.categoryApps(subCategory.xiaomiCategoryId, page)
    }).withRealNames()

    override suspend fun categoryOptions(source: AppSource, category: AppCategory): List<CategoryOption> =
        when {
            source == AppSource.OPPO -> oppo.categoryOptions(category == AppCategory.GAMES)
            source == AppSource.GOOGLE && updatePrefs.googlePlayEngine.value != "aurorastore" -> google.webpageCategories(category)
            else -> emptyList()
        }

    override suspend fun appDetail(
        source: AppSource,
        appId: Long,
        packageName: String,
        externalQuery: String?,
    ): AppDetail = (when (source) {
        AppSource.XIAOMI -> market.appDetail(appId, packageName, externalQuery)
        AppSource.VIVO -> vivo.appDetail(
            resolveStoreAppId(source, packageName, appId) { vivo.search(it, 0) },
            packageName,
        )

        AppSource.WANDOUJIA -> wandoujia.appDetail(
            resolveStoreAppId(source, packageName, appId) { wandoujia.search(it, 0) },
        )

        AppSource.OPPO -> oppo.appDetail(appId, packageName, externalQuery)
        AppSource.SAMSUNG -> samsung.appDetail(appId, packageName, externalQuery)
        AppSource.HONOR -> honor.appDetail(appId, packageName)
        AppSource.HUAWEI -> huawei.appDetail(appId, packageName)
        AppSource.TAPTAP -> tapTap.appDetail(appId, packageName)
        AppSource.COOLAPK -> coolapk.appDetail(packageName)
        AppSource.GOOGLE -> google.appDetail(packageName)
        AppSource.KUAIBAO -> kuaibao.appDetail(resolveStoreAppId(source, packageName, appId) { kuaibao.search(it, 0) })
        AppSource.APPCHINA -> appchina.appDetail(resolveStoreAppId(source, packageName, appId) { appchina.search(it, 0) }, packageName)
        AppSource.TENCENT -> tencent.appDetail(packageName)
        AppSource.BOX7723 -> box7723.appDetail(resolveStoreAppId(source, packageName, appId) { box7723.search(it, 0) })
        AppSource.FDROID -> fdroid.appDetail(packageName)
        AppSource.APKPURE -> apkPure.appDetail(packageName)
        AppSource.APTOIDE -> aptoide.appDetail(packageName)
        AppSource.GITHUB, AppSource.GITLAB -> releaseSource.appDetail(source, appId, packageName)
        AppSource.LSPOSED -> lsposed.appDetail(packageName)
    }).withRealName()

    override suspend fun appComments(source: AppSource, app: MarketAppInfo): AppComments {
        if (!source.capabilities.supportsComments) return AppComments(emptyList(), 0L)
        when (source) {
            AppSource.KUAIBAO -> return kuaibao.appComments(app)
            AppSource.APPCHINA -> return appchina.appComments(app)
            AppSource.TENCENT -> return tencent.appComments(app)
            else -> Unit
        }
        val xiaomi = app.onXiaomi()
        return market.appComments(xiaomi.appId, xiaomi.versionCode)
    }

    override suspend fun sameDeveloperApps(source: AppSource, app: MarketAppInfo): List<MarketAppInfo> {
        if (!source.capabilities.supportsSameDeveloperApps) return emptyList()
        val xiaomi = app.onXiaomi()
        return filterSameDeveloperCandidates(app.packageName, market.sameDeveloperApps(xiaomi.appId)).withRealNames()
    }

    override suspend fun moduleAssets(source: AppSource, app: MarketAppInfo): List<ModuleAsset> =
        when (source) {
            AppSource.LSPOSED -> runCatching { lsposed.assets(app.packageName) }.getOrDefault(emptyList())
            AppSource.GITHUB, AppSource.GITLAB -> releaseSource.currentAsset(source, app)
            else -> emptyList()
        }

    override suspend fun downloadMeta(
        source: AppSource,
        app: MarketAppInfo,
        keyword: String,
        assetName: String?,
    ): DownloadMeta {
        val meta = when (source) {
        AppSource.XIAOMI -> market.downloadMeta(app, keyword)
        AppSource.VIVO -> vivo.downloadMeta(app)
        AppSource.WANDOUJIA -> wandoujia.downloadMeta(app)
        AppSource.OPPO -> oppo.downloadMeta(app)
        AppSource.SAMSUNG -> samsung.downloadMeta(app)
        AppSource.HONOR -> honor.downloadMeta(app)
        AppSource.HUAWEI -> huawei.downloadMeta(app)
        AppSource.TAPTAP -> tapTap.downloadMeta(app)
        AppSource.COOLAPK -> coolapk.downloadMeta(app)
        AppSource.GOOGLE -> google.downloadMeta(app)
        AppSource.KUAIBAO -> kuaibao.downloadMeta(app)
        AppSource.APPCHINA -> appchina.downloadMeta(app)
        AppSource.TENCENT -> tencent.downloadMeta(app)
        AppSource.BOX7723 -> box7723.downloadMeta(app)
        AppSource.FDROID -> fdroid.downloadMeta(app)
        AppSource.APKPURE -> apkPure.downloadMeta(app)
        AppSource.APTOIDE -> aptoide.downloadMeta(app)
        AppSource.GITHUB, AppSource.GITLAB -> releaseSource.downloadMeta(source, app)
        AppSource.LSPOSED -> lsposed.downloadMeta(app, assetName)
        }
        // 各来源 Api 构造 DownloadMeta 时大多不填 source（默认 XIAOMI），统一回填真实来源：
        // 否则下载页水印、详情跳转全按小米走（酷安下载也显示小米、点进详情报「未收录」）
        return meta.copy(source = source, installedVersionName = app.installedVersionName, displayName = (if (updatePrefs.stripAppNameSubtitle.value) app.localLabel.ifBlank { app.displayName } else app.displayName).ifBlank { meta.displayName })
    }

    override suspend fun downloadUpdateMeta(source: AppSource, app: MarketAppInfo): DownloadMeta {
        val meta = when (source) {
        AppSource.XIAOMI -> market.downloadUpdateMeta(app)
        AppSource.VIVO -> vivo.downloadUpdateMeta(app)
        // 豌豆荚无原生更新元数据协议，回退小米商店解析
        AppSource.WANDOUJIA -> market.downloadUpdateMeta(app.onXiaomi())
        AppSource.OPPO -> oppo.downloadUpdateMeta(app)
        AppSource.SAMSUNG -> samsung.downloadUpdateMeta(app)
        AppSource.HONOR -> honor.downloadUpdateMeta(app)
        AppSource.HUAWEI -> huawei.downloadUpdateMeta(app)
        AppSource.TAPTAP -> tapTap.downloadUpdateMeta(app)
        AppSource.COOLAPK -> coolapk.downloadUpdateMeta(app)
        AppSource.GOOGLE -> google.downloadUpdateMeta(app)
        AppSource.KUAIBAO -> kuaibao.downloadMeta(app)
        AppSource.APPCHINA -> appchina.downloadMeta(app)
        AppSource.TENCENT -> tencent.downloadMeta(app)
        AppSource.BOX7723 -> box7723.downloadMeta(app)
        AppSource.FDROID -> fdroid.downloadMeta(app)
        AppSource.APKPURE -> apkPure.downloadMeta(app)
        AppSource.APTOIDE -> aptoide.downloadMeta(app)
        AppSource.GITHUB, AppSource.GITLAB -> releaseSource.downloadMeta(source, app)
        AppSource.LSPOSED -> lsposed.downloadMeta(app)
        }
        // 与 downloadMeta 同款兜底：各来源 Api 不填 source 时统一回填真实来源，
        // 否则更新页下载的任务水印/详情跳转全按小米走
        return meta.copy(source = source, installedVersionName = app.installedVersionName, displayName = (if (updatePrefs.stripAppNameSubtitle.value) app.localLabel.ifBlank { app.displayName } else app.displayName).ifBlank { meta.displayName })
    }

    override suspend fun loadRawCachedUpdates(): List<MarketAppInfo> =
        market.loadRawCachedUpdates().withRealNames()

    override suspend fun loadReconciledCachedUpdates(): List<MarketAppInfo> =
        market.loadReconciledCachedUpdates().withRealNames()

    override fun checkUpdatesFlow(source: AppSource): Flow<List<MarketAppInfo>> = (when (source) {
        AppSource.XIAOMI -> market.checkUpdatesFlow()
        AppSource.VIVO -> flow { emit(vivo.checkUpdates()) }
        AppSource.WANDOUJIA -> flow { emit(wandoujia.checkUpdates()) }
        AppSource.OPPO -> flow { emit(oppo.checkUpdates()) }
        AppSource.SAMSUNG -> flow { emit(samsung.checkUpdates()) }
        AppSource.HONOR -> flow { emit(honor.checkUpdates()) }
        AppSource.HUAWEI -> flow { emit(huawei.checkUpdates()) }
        AppSource.TAPTAP -> flow { emit(tapTap.checkUpdates()) }
        AppSource.COOLAPK -> flow { emit(coolapk.checkUpdates()) }
        AppSource.GOOGLE -> flow { emit(google.checkUpdates()) }
        AppSource.KUAIBAO -> flow { emit(emptyList()) }
        AppSource.APPCHINA -> flow { emit(appchina.checkUpdates()) }
        AppSource.TENCENT -> flow { emit(tencent.checkUpdates()) }
        AppSource.BOX7723 -> flow { emit(emptyList()) }
        AppSource.FDROID -> flow { emit(fdroid.checkUpdates()) }
        // APKPure 网页端没有版本接口，不提供独立更新来源
        AppSource.APKPURE -> flow { emit(emptyList()) }
        AppSource.APTOIDE -> flow { emit(aptoide.checkUpdates()) }
        AppSource.GITHUB, AppSource.GITLAB -> flow { emit(releaseSource.checkUpdates(source)) }
        AppSource.LSPOSED -> flow { emit(lsposed.checkUpdates()) }
    }).map { it.withRealNames() }

    override suspend fun checkManualUpdate(source: AppSource, request: ManualUpdateRequest): ManualUpdateResult =
        when (source) {
            AppSource.XIAOMI -> market.checkManualUpdate(request)
            AppSource.VIVO -> vivo.checkManualUpdate(request)
            // 豌豆荚无原生手动更新协议，回退小米商店解析
            AppSource.WANDOUJIA -> market.checkManualUpdate(request)
            AppSource.OPPO -> oppo.checkManualUpdate(request)
            AppSource.SAMSUNG -> samsung.checkManualUpdate(request)
            AppSource.HONOR -> honor.checkManualUpdate(request)
            AppSource.HUAWEI -> huawei.checkManualUpdate(request)
            AppSource.TAPTAP -> tapTap.checkManualUpdate(request)
            // 酷安无手动单包更新协议，回退小米商店解析
            AppSource.COOLAPK -> market.checkManualUpdate(request)
            // Google Play：输入的版本号是目标下载版本（含历史版本），走 delivery 解析
            AppSource.GOOGLE -> google.checkManualUpdate(request)
            AppSource.KUAIBAO -> market.checkManualUpdate(request)
            AppSource.APPCHINA -> appchina.checkManualUpdate(request)
            AppSource.TENCENT -> tencent.checkManualUpdate(request)
            AppSource.BOX7723 -> market.checkManualUpdate(request)
            AppSource.FDROID -> fdroid.checkManualUpdate(request)
            AppSource.APKPURE -> market.checkManualUpdate(request)
            AppSource.APTOIDE -> aptoide.checkManualUpdate(request)
            AppSource.GITHUB, AppSource.GITLAB -> releaseSource.checkManualUpdate(source, request)
            AppSource.LSPOSED -> lsposed.checkManualUpdate(request)
        }

    override suspend fun goldMiFeed(source: AppSource, page: Int, pageSize: Int): TodayFeedPage =
        when (source) {
            AppSource.OPPO -> oppo.beautyFeed(page, pageSize)
            // 豌豆荚 / 三星 / 华为 / 荣耀无独立今日内容，回退小米商店今日
            AppSource.XIAOMI, AppSource.WANDOUJIA, AppSource.SAMSUNG, AppSource.HUAWEI, AppSource.HONOR ->
                today.goldMiFeed(page, pageSize)

            AppSource.VIVO -> vivo.auroraFeed(page, pageSize)
            AppSource.TAPTAP -> tapTap.todayFeed(page, pageSize)
            // 酷安无独立今日内容，回退小米商店今日
            AppSource.COOLAPK -> today.goldMiFeed(page, pageSize)
            // 好游快爆提供推荐流（KuaibaoApi.recommendations）；其余来源走小米今日
            AppSource.KUAIBAO -> kuaibao.recommendations(page)
            AppSource.APPCHINA, AppSource.TENCENT, AppSource.BOX7723, AppSource.FDROID,
            AppSource.APKPURE, AppSource.APTOIDE, AppSource.GOOGLE,
            AppSource.GITHUB, AppSource.LSPOSED, AppSource.GITLAB -> today.goldMiFeed(page, pageSize)
        }

    override suspend fun todayArticle(source: AppSource, rId: String): TodayArticle =
        when (source) {
            AppSource.OPPO -> oppo.beautyArticle(rId)
            AppSource.XIAOMI, AppSource.WANDOUJIA, AppSource.SAMSUNG, AppSource.HUAWEI, AppSource.HONOR ->
                today.todayArticle(rId)

            AppSource.VIVO -> vivo.auroraArticle(rId)
            AppSource.TAPTAP -> tapTap.todayArticle(rId)
            AppSource.COOLAPK -> today.todayArticle(rId)
            // 好游快爆没有图文体系，rId 是站内应用 id，直接用应用详情拼内容
            AppSource.KUAIBAO -> kuaibao.article(rId)
            AppSource.APPCHINA, AppSource.TENCENT, AppSource.BOX7723, AppSource.FDROID,
            AppSource.APKPURE, AppSource.APTOIDE, AppSource.GOOGLE,
            AppSource.GITHUB, AppSource.LSPOSED, AppSource.GITLAB -> today.todayArticle(rId)
        }

    private suspend fun MarketAppInfo.onXiaomi(): MarketAppInfo {
        if (source == AppSource.XIAOMI) return this
        return market.appDetail(
            appId = 0L,
            packageName = packageName,
            externalQuery = "id=$packageName",
        ).app.copy(
            installedVersionName = installedVersionName,
            installedVersionCode = installedVersionCode,
            installedOldApkHash = installedOldApkHash,
            installedBaseApkPath = installedBaseApkPath,
            installedSplits = installedSplits,
        )
    }
}

private suspend fun resolveStoreAppId(
    source: AppSource,
    packageName: String,
    appId: Long,
    search: suspend (String) -> SearchPage,
): Long = appId.takeIf { it > 0L } ?: search(packageName).items
    .firstOrNull { it.packageName.equals(packageName, true) }
    ?.appId
?: throw IllegalStateException("${source.token} 未收录该应用")

internal fun filterSameDeveloperCandidates(
    currentPackageName: String,
    candidates: List<MarketAppInfo>,
): List<MarketAppInfo> = candidates
    .filterNot { it.packageName.equals(currentPackageName, ignoreCase = true) }
    .distinctBy { it.packageName.lowercase() }