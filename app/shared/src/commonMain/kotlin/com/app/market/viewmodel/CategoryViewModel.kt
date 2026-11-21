package com.app.market.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.market.domain.model.download.DownloadState
import com.app.market.domain.model.market.AppCategory
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.AppSubCategory
import com.app.market.domain.model.market.CategoryOption
import com.app.market.domain.model.market.GameRanking
import com.app.market.domain.model.market.GameSubCategory
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.isDownloadBlocked
import com.app.market.domain.model.market.isReservation
import com.app.market.domain.repository.DownloadRepository
import com.app.market.domain.repository.MarketSourceRepository
import com.app.market.domain.repository.PackageRepository
import com.app.market.domain.repository.UpdatePreferencesRepository
import com.app.market.platform.UiPlatform
import com.app.market.ui.model.AppActionKind
import com.app.market.ui.model.SearchResultItem
import com.app.market.ui.model.resolveActionKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class CategoryUiState(
    val items: List<SearchResultItem> = emptyList(),
    val loading: Boolean = false,
    val refreshingByPull: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String = "",
    val epoch: Int = 0,
)

/** 当前选中的子分类 / 榜单 / 动态大类；各分区只读取与自己相关的那一项。 */
@Immutable
data class CategorySelection(
    val appSubCategory: AppSubCategory = AppSubCategory.TOOLS,
    val gameRanking: GameRanking = GameRanking.HOT,
    val gameSubCategory: GameSubCategory = GameSubCategory.ALL,
    /** 来源动态提供的大类 id（OPPO）；选项加载完成前为 null，之后默认选第一项。 */
    val appOption: String? = null,
    val gameOption: String? = null,
    /** Google Play 榜单类型（AuroraStore 引擎四选一：免费/付费/畅销/飙升）。 */
    val chart: com.app.market.domain.model.market.ChartKind = com.app.market.domain.model.market.ChartKind.FREE,
)

/** 分区列表的加载键：应用按子分类、游戏按榜单（TapTap）/ 细分类（小米）/ 动态大类（OPPO）各自独立翻页与缓存。 */
data class CategoryKey(
    val category: AppCategory,
    val subCategory: AppSubCategory?,
    val ranking: GameRanking?,
    val gameSubCategory: GameSubCategory?,
    val option: String?,
    /** 来源也进 key：否则切来源会直接命中上一个来源的缓存，看起来像「切了没反应」。 */
    val source: AppSource? = null,
    /** Google Play 榜单类型也进 key：四种榜单各自独立缓存与翻页。 */
    val chart: com.app.market.domain.model.market.ChartKind = com.app.market.domain.model.market.ChartKind.FREE,
)

private fun sectionKey(
    category: AppCategory,
    selection: CategorySelection,
    source: AppSource,
): CategoryKey = when (category) {
    AppCategory.APPS -> CategoryKey(category, selection.appSubCategory, null, null, selection.appOption, source, selection.chart)
    AppCategory.GAMES ->
        CategoryKey(category, null, selection.gameRanking, selection.gameSubCategory, selection.gameOption, source, selection.chart)
}

/** 取指定分区状态，尚未加载时为空状态。 */
fun Map<CategoryKey, CategoryUiState>.of(
    category: AppCategory,
    selection: CategorySelection,
    source: AppSource,
): CategoryUiState = this[sectionKey(category, selection, source)] ?: CategoryUiState()

class CategoryViewModel(
    private val sources: MarketSourceRepository,
    private val prefs: UpdatePreferencesRepository,
    private val packages: PackageRepository,
    private val downloads: DownloadRepository,
    private val uiPlatform: UiPlatform,
) : ViewModel() {
    val categorySource: StateFlow<AppSource> = prefs.categorySource
    /** 应用页来源（与游戏页解耦）。 */
    val appCategorySource: StateFlow<AppSource> = prefs.appCategorySource
    /** 游戏页来源（与应用页解耦）。 */
    val gameCategorySource: StateFlow<AppSource> = prefs.gameCategorySource

    /** 取某个分区应当使用的来源。 */
    fun sourceFor(category: AppCategory): AppSource =
        if (category == AppCategory.GAMES) prefs.gameCategorySource.value else prefs.appCategorySource.value

    private val _selection = MutableStateFlow(CategorySelection())
    val selection: StateFlow<CategorySelection> = _selection.asStateFlow()

    /** 来源动态提供的分类选项；key 缺失 = 尚未加载，空列表 = 该来源没有动态选项。 */
    private val _options = MutableStateFlow<Map<AppCategory, List<CategoryOption>>>(emptyMap())
    val options: StateFlow<Map<AppCategory, List<CategoryOption>>> = _options.asStateFlow()
    private val optionJobs = mutableMapOf<AppCategory, Job>()

    private val sections = MutableStateFlow<Map<CategoryKey, CategoryUiState>>(emptyMap())
    val sectionStates: StateFlow<Map<CategoryKey, CategoryUiState>> = sections.asStateFlow()
    private val nextPage = mutableMapOf<CategoryKey, Int>()
    private val refreshedCacheKeys = mutableSetOf<String>()
    private val jobs = mutableMapOf<CategoryKey, Job>()
    private var activeSource = prefs.categorySource.value
    /** 各分区当前生效的来源，用于判断来源是否真的变化。 */
    private val activeSources = mutableMapOf(
        AppCategory.APPS to prefs.appCategorySource.value,
        AppCategory.GAMES to prefs.gameCategorySource.value,
    )

    /** 该分区当前生效的来源。 */
    private fun activeSource(category: AppCategory): AppSource =
        activeSources[category] ?: prefs.appCategorySource.value
    private val generations = mutableMapOf<AppCategory, Long>()
    private fun generation(category: AppCategory) = generations[category] ?: 0L
    private var activeGoogleEngine = prefs.googlePlayEngine.value
    private val pendingDownloads = mutableSetOf<String>()
    private val repositoryResolveJobs = mutableMapOf<String, Job>()
    private val _resolvingRepositories = MutableStateFlow<Set<String>>(emptySet())
    val resolvingRepositories: StateFlow<Set<String>> = _resolvingRepositories.asStateFlow()

    val downloadStates: StateFlow<Map<String, DownloadState>> = downloads.states
    private val _repositoryDownloadPackages = MutableStateFlow<Map<String, String>>(emptyMap())
    val repositoryDownloadPackages: StateFlow<Map<String, String>> = _repositoryDownloadPackages.asStateFlow()
    private val _repositoryApps = MutableStateFlow<Map<String, MarketAppInfo>>(emptyMap())
    val repositoryApps: StateFlow<Map<String, MarketAppInfo>> = _repositoryApps.asStateFlow()

    fun refreshRepositoryInstalledState() {
        viewModelScope.launch {
            val packageNames = _repositoryApps.value.values.map { it.packageName }.distinct()
            packageNames.forEach { packageName ->
                val result = runCatchingCancellable { packages.freshInstalledVersionCode(packageName) }
                if (result.isFailure) return@forEach
                val code = result.getOrNull() ?: 0L
                val name = if (code > 0L) runCatchingCancellable { packages.installedVersionName(packageName) }.getOrNull().orEmpty() else ""
                applyInstalledState(packageName, code, name)
            }
        }
    }

    fun syncRepositoryDetail(repositoryId: String, app: MarketAppInfo) {
        if (!repositoryId.startsWith("github-repo:") || app.source != AppSource.GITHUB || app.packageName.startsWith("github-repo:")) return
        _repositoryApps.update { it + (repositoryId to app) }
        _repositoryDownloadPackages.update { it + (repositoryId to app.packageName) }
    }

    private fun repositoryCacheKey(app: MarketAppInfo) = "github_identity_v1:${app.openLink.ifBlank { app.packageName }}"

    private suspend fun rememberRepositoryApp(app: MarketAppInfo, meta: com.app.market.domain.model.download.DownloadMeta) {
        val code = packages.freshInstalledVersionCode(meta.packageName) ?: 0L
        val name = if (code > 0L) packages.installedVersionName(meta.packageName).orEmpty() else ""
        val resolved = app.copy(appId = meta.appId, packageName = meta.packageName, versionCode = meta.versionCode,
            versionName = meta.versionName, apkSize = meta.size, installedVersionCode = code, installedVersionName = name)
        _repositoryApps.update { it + (app.packageName to resolved) }
        _repositoryDownloadPackages.update { it + (app.packageName to meta.packageName) }
        runCatchingCancellable {
            prefs.writePageCache(repositoryCacheKey(app), PageCacheCodec.encodeCategory(listOf(resolved), false, 0))
        }
    }

    init {
        // 榜单先上屏；GitHub 元信息在独立后台协程限并发预取，失败不影响列表。
        viewModelScope.launch {
            sections.map { all ->
                all.values.flatMap { state -> state.items.map { it.app } }
                    .filter { it.source == AppSource.GITHUB && it.packageName.startsWith("github-repo:") }
                    .distinctBy { it.packageName }
            }.distinctUntilChanged().collectLatest { apps ->
                delay(300)
                // 先恢复真实包名与版本，安装状态必须重新查询本机，不能沿用缓存。
                apps.forEach { app ->
                    if (app.packageName !in _repositoryApps.value) {
                        val cached = runCatchingCancellable { prefs.readPageCache(repositoryCacheKey(app)) }
                            .getOrNull()?.let(PageCacheCodec::decodeCategory)?.first?.firstOrNull()
                        if (cached != null && cached.source == AppSource.GITHUB &&
                            cached.packageName.isNotBlank() && !cached.packageName.startsWith("github-repo:")) {
                            val result = runCatchingCancellable { packages.freshInstalledVersionCode(cached.packageName) }
                            if (result.isSuccess) {
                                val code = result.getOrNull() ?: 0L
                                val name = if (code > 0L) runCatchingCancellable { packages.installedVersionName(cached.packageName) }.getOrNull().orEmpty() else ""
                                _repositoryApps.update { current ->
                                    if (app.packageName in current) current else current + (app.packageName to cached.copy(installedVersionCode = code, installedVersionName = name))
                                }
                                _repositoryDownloadPackages.update { current ->
                                    if (app.packageName in current) current else current + (app.packageName to cached.packageName)
                                }
                            }
                        }
                    }
                }
                apps.chunked(4).forEach { batch ->
                    coroutineScope {
                        batch.map { app -> async {
                            withTimeoutOrNull(20_000) {
                                try {
                                    val meta = sources.downloadMeta(AppSource.GITHUB, app)
                                    rememberRepositoryApp(app, meta)
                                }
                                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                catch (_: Exception) { }
                            }
                        } }.awaitAll()
                    }
                }
            }
        }
        viewModelScope.launch {
            prefs.googlePlayEngine.collectLatest { engine ->
                if (engine != activeGoogleEngine) {
                    activeGoogleEngine = engine
                    if (activeSource(AppCategory.APPS) == AppSource.GOOGLE) invalidate(AppCategory.APPS)
                    if (activeSource(AppCategory.GAMES) == AppSource.GOOGLE) invalidate(AppCategory.GAMES)
                }
            }
        }
        viewModelScope.launch {
            // 应用页来源变化：只作废应用分区，游戏分区不受影响（两页来源已解耦）
            prefs.appCategorySource.collectLatest { source ->
                if (source == activeSources[AppCategory.APPS]) return@collectLatest
                activeSources[AppCategory.APPS] = source
                invalidate(valueOf = AppCategory.APPS)
            }
        }
        viewModelScope.launch {
            // 游戏页来源变化：只作废游戏分区
            prefs.gameCategorySource.collectLatest { source ->
                if (source == activeSources[AppCategory.GAMES]) return@collectLatest
                activeSources[AppCategory.GAMES] = source
                invalidate(valueOf = AppCategory.GAMES)
            }
        }
        viewModelScope.launch {
            packages.changes.collect { change ->
                val initialCode = change.installedVersionCode ?: 0L
                applyInstalledState(change.packageName, initialCode, change.installedVersionName)
                launch {
                    val fresh = runCatchingCancellable { packages.freshInstalledVersionCode(change.packageName) }.getOrNull() ?: 0L
                    if (fresh != initialCode) {
                        val name = runCatchingCancellable { packages.installedVersionName(change.packageName) }.getOrNull()
                            ?: change.installedVersionName
                        applyInstalledState(change.packageName, fresh, name)
                    }
                }
            }
        }
    }

    private fun applyInstalledState(packageName: String, code: Long, name: String) {
        _repositoryApps.update { all -> all.mapValues { (_, app) ->
            if (app.packageName == packageName) app.copy(installedVersionCode = code, installedVersionName = name) else app
        } }
        sections.update { all -> all.mapValues { (_, state) ->
            state.copy(items = state.items.map { item ->
                if (item.app.packageName != packageName) item else {
                    val updated = item.app.copy(installedVersionCode = code, installedVersionName = name)
                    item.copy(app = updated, actionKind = actionKind(updated, code))
                }
            })
        } }
    }

    /** 某个分区的来源变了：只清掉该分区的任务、分页与缓存，另一分区保持不动。 */
    private fun invalidate(valueOf: AppCategory) {
        generations[valueOf] = generation(valueOf) + 1
        val keys = sections.value.keys.filter { it.category == valueOf }
        keys.forEach { key ->
            if (nextPage[key] == null) refreshedCacheKeys.remove(cacheKey(key))
            jobs[key]?.cancel()
            jobs.remove(key)
            nextPage.remove(key)
        }
        optionJobs[valueOf]?.cancel()
        optionJobs.remove(valueOf)
        _options.update { it - valueOf }
        _selection.update {
            if (valueOf == AppCategory.APPS) it.copy(appOption = null) else it.copy(gameOption = null)
        }
        sections.update { all -> all - keys.toSet() }
    }

    fun selectGameRanking(value: GameRanking) = _selection.update { it.copy(gameRanking = value) }
    fun selectGameSubCategory(value: GameSubCategory) = _selection.update { it.copy(gameSubCategory = value) }
    fun selectSubCategory(value: AppSubCategory) = _selection.update { it.copy(appSubCategory = value) }
    /** Google Play 榜单类型（AuroraStore 引擎）：免费/付费/畅销/飙升。 */
    fun selectChart(value: com.app.market.domain.model.market.ChartKind) = _selection.update { it.copy(chart = value) }
    /** 「Google Play 来源设置」引擎（aurorastore 时榜单走 Aurora 方案）。 */
    val googlePlayEngine: StateFlow<String> = prefs.googlePlayEngine

    /** 页面下拉直接切来源：写回偏好，init 里的 collect 会作废该分区缓存并触发重新加载。 */
    fun setSource(category: AppCategory, source: AppSource) {
        viewModelScope.launch {
            if (category == AppCategory.GAMES) prefs.setGameCategorySource(source)
            else prefs.setAppCategorySource(source)
        }
    }
    fun selectOption(category: AppCategory, id: String) = _selection.update {
        if (category == AppCategory.APPS) it.copy(appOption = id) else it.copy(gameOption = id)
    }

    private fun keyOf(category: AppCategory) =
        sectionKey(category, _selection.value, activeSource(category))

    /** 该来源提供动态选项（目前仅 OPPO）而本分区还没加载过选项。 */
    private fun needsOptions(category: AppCategory) =
        (activeSource(category) == AppSource.OPPO ||
            (activeSource(category) == AppSource.GOOGLE && prefs.googlePlayEngine.value != "aurorastore")) &&
            _options.value[category] == null

    /** 先取选项并默认选第一项；选择变化会让界面重新调用 [ensureLoaded] 继续加载列表。 */
    private fun loadOptions(category: AppCategory) {
        if (optionJobs[category]?.isActive == true) return
        val source = activeSource(category)
        val gen = generation(category)
        val failedKey = keyOf(category)
        update(failedKey) { it.copy(errorMessage = "") }
        optionJobs[category] = viewModelScope.launch {
            val result = runCatchingCancellable { sources.categoryOptions(source, category) }
            if (gen != generation(category)) return@launch
            val list = result.getOrNull()
            if (list == null) {
                update(failedKey) { it.copy(errorMessage = result.exceptionOrNull()?.message ?: "Load failed") }
                return@launch
            }
            _options.update { it + (category to list) }
            val first = list.firstOrNull()?.id ?: return@launch
            _selection.update { s ->
                if (category == AppCategory.APPS) s.copy(appOption = s.appOption ?: first)
                else s.copy(gameOption = s.gameOption ?: first)
            }
        }
    }

    /** 首次进入分区时加载第一页；已有数据或正在加载则忽略。 */
    /** 分区是否已经有状态（首次切到某分类时还没有，界面要沿用上一份列表而不是先清空）。 */
    fun hasSection(category: AppCategory): Boolean = sections.value.containsKey(keyOf(category))

    fun ensureLoaded(category: AppCategory) {
        if (needsOptions(category)) {
            loadOptions(category)
            return
        }
        val key = keyOf(category)
        val state = sections.value[key]
        if (state != null && (state.items.isNotEmpty() || state.loading)) return
        load(key, replace = true)
    }

    /** 失败重试与下拉刷新共用：重载第一页，成功前保留现有列表。 */
    fun refresh(category: AppCategory) {
        if (needsOptions(category)) loadOptions(category) else load(keyOf(category), replace = true, restoreCache = false)
    }

    fun refreshFromPull(category: AppCategory) {
        if (needsOptions(category)) loadOptions(category)
        else load(keyOf(category), replace = true, restoreCache = false, fromPull = true)
    }

    suspend fun loadMoreAfterCurrent(category: AppCategory) {
        val key = keyOf(category)
        jobs[key]?.join()
        if (key == keyOf(category)) loadMore(category)
    }

    fun loadMore(category: AppCategory) {
        val key = keyOf(category)
        val state = sections.value[key] ?: return
        if (state.loading || state.loadingMore || !state.hasMore || (state.items.isEmpty() && activeSource(category) != AppSource.GITHUB)) return
        load(key, replace = false)
    }

    private fun cacheKey(key: CategoryKey): String = listOf(
        if (key.source == AppSource.GITHUB) "komi_category_v3" else "category", key.source?.token.orEmpty(), key.category.name, key.subCategory?.name.orEmpty(),
        key.ranking?.name.orEmpty(), key.gameSubCategory?.name.orEmpty(),
        key.option?.substringBefore(":S:").orEmpty(), key.chart.name,
        if (key.source == AppSource.GOOGLE) prefs.googlePlayEngine.value else "",
    ).joinToString("_")

    private fun load(key: CategoryKey, replace: Boolean, restoreCache: Boolean = true, fromPull: Boolean = false) {
        if (jobs[key]?.isActive == true) return
        val storedKey = cacheKey(key)
        val source = activeSource(key.category)
        val gen = generation(key.category)
        val page = if (replace) 0 else nextPage[key] ?: 0
        update(key) { it.copy(loading = replace, refreshingByPull = fromPull, loadingMore = !replace, errorMessage = "") }
        jobs[key] = viewModelScope.launch {
            if (restoreCache && replace && sections.value[key]?.items.isNullOrEmpty()) {
                val cached = runCatchingCancellable { prefs.readPageCache(storedKey) }.getOrNull()
                    ?.let(PageCacheCodec::decodeCategory)
                if (gen != generation(key.category)) return@launch
                if (cached != null) {
                    val restored = resolveItems(cached.first)
                    if (gen != generation(key.category)) return@launch
                    nextPage[key] = cached.third
                    update(key) { it.copy(items = restored, loading = true, hasMore = cached.second) }
                }
            }
            if (replace) refreshedCacheKeys.add(storedKey)
            val result = runCatchingCancellable {
                sources.categoryApps(
                    source = source,
                    category = key.category,
                    subCategory = key.subCategory ?: AppSubCategory.TOOLS,
                    ranking = key.ranking ?: GameRanking.HOT,
                    gameSubCategory = key.gameSubCategory ?: GameSubCategory.ALL,
                    optionId = if (source == AppSource.GITHUB) (key.option ?:
                        if (key.category == AppCategory.APPS) "trending" else "all") else key.option,
                    page = page,
                    chart = key.chart,
                )
            }
            if (gen != generation(key.category)) return@launch
            val fetched = result.getOrNull()
            if (fetched == null) {
                update(key) {
                    it.copy(
                        loading = false,
                        refreshingByPull = false,
                        loadingMore = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Load failed",
                    )
                }
                return@launch
            }
            nextPage[key] = page + 1
            val existing = if (replace) emptyList() else sections.value[key]?.items.orEmpty()
            val known = existing.mapTo(HashSet()) { it.app.packageName }
            val incoming = resolveItems(fetched.items.filter { known.add(it.packageName) })
            if (gen != generation(key.category)) return@launch
            update(key) {
                it.copy(
                    items = existing + incoming,
                    loading = false,
                    refreshingByPull = false,
                    loadingMore = false,
                    hasMore = fetched.hasMore && (fetched.items.isNotEmpty() || source == AppSource.GITHUB),
                    epoch = if (replace) it.epoch + 1 else it.epoch,
                )
            }
            val snapshot = sections.value[key]
            if (snapshot != null) runCatchingCancellable {
                prefs.writePageCache(storedKey, PageCacheCodec.encodeCategory(snapshot.items.map { it.app }, snapshot.hasMore, page + 1))
            }
        }
    }

    private fun update(key: CategoryKey, transform: (CategoryUiState) -> CategoryUiState) {
        sections.update { it + (key to transform(it[key] ?: CategoryUiState())) }
    }

    private suspend fun resolveItems(apps: List<MarketAppInfo>): List<SearchResultItem> {
        if (apps.isEmpty()) return emptyList()
        val installed = packages.installedVersionCodes(apps.map { it.packageName })
        return apps.map { app ->
            val cachedCode = installed[app.packageName] ?: 0L
            val code = if (cachedCode in 1 until app.versionCode) {
                packages.freshInstalledVersionCode(app.packageName) ?: cachedCode
            } else cachedCode
            val resolved = app.copy(installedVersionCode = code)
            SearchResultItem(resolved, actionKind(resolved, code))
        }
    }

    private fun actionKind(app: MarketAppInfo, installedVersionCode: Long): AppActionKind =
        if (app.isReservation()) AppActionKind.RESERVE
        else if (installedVersionCode > 0L && app.isDownloadBlocked()) AppActionKind.OPEN
        else resolveActionKind(installedVersionCode, app.versionCode)

    fun onAction(item: SearchResultItem) {
        when (item.actionKind) {
            AppActionKind.RESERVE -> Unit
            AppActionKind.OPEN -> {
                val app = item.app
                val opened = if (app.source.capabilities.prefersOpenLinkLaunch) {
                    packages.openLink(app.openLink) || packages.openApp(app.packageName)
                } else {
                    packages.openApp(app.packageName) || packages.openLink(app.openLink)
                }
                if (!opened) uiPlatform.showToast(app.displayName)
            }

            else -> download(item.app, item.actionKind == AppActionKind.UPDATE)
        }
    }

    fun download(app: MarketAppInfo, update: Boolean = false) {
        if (app.isDownloadBlocked()) return
        // GitHub 榜单的仓库标识不是实际包名，解析完成前不能创建错误的下载占位。
        if (!app.packageName.startsWith("github-repo:")) downloads.prepareDownload(app)
        if (!pendingDownloads.add(app.packageName)) return
        val repositoryPlaceholder = app.packageName.startsWith("github-repo:")
        if (repositoryPlaceholder) _resolvingRepositories.update { it + app.packageName }
        val job = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                runCatchingCancellable {
                    if (repositoryPlaceholder) {
                        withTimeoutOrNull(30_000) { sources.downloadMeta(app.source, app) }
                            ?: throw IllegalStateException("获取下载信息超时，请检查网络后重试")
                    } else if (update) sources.downloadUpdateMeta(app.source, app)
                    else sources.downloadMeta(app.source, app)
                }
                    // 下载完成即自动安装
                    .onSuccess { meta ->
                        if (repositoryPlaceholder) {
                            rememberRepositoryApp(app, meta)
                        }
                        downloads.start(meta)
                    }
                    .onFailure { uiPlatform.showToast(it.message ?: "Download failed") }
            } finally {
                pendingDownloads.remove(app.packageName)
                if (repositoryPlaceholder) {
                    _resolvingRepositories.update { it - app.packageName }
                    repositoryResolveJobs.remove(app.packageName)
                }
            }
        }
        if (repositoryPlaceholder) repositoryResolveJobs[app.packageName] = job
        job.start()
    }

    fun clearRepositoryDownload(packageName: String) {
        repositoryResolveJobs[packageName]?.cancel()
    }

    fun installDownloaded(packageName: String) = downloads.install(packageName)
    fun cancelDownload(packageName: String) = downloads.cancel(packageName)
}