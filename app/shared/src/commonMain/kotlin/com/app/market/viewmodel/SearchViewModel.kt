package com.app.market.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.market.domain.model.download.DownloadState
import com.app.market.domain.diagnostic.AppLog
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.SearchPage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.app.market.domain.model.market.isDownloadBlocked
import com.app.market.domain.model.market.isReservation
import com.app.market.domain.repository.DownloadRepository
import com.app.market.domain.repository.MarketSourceRepository
import com.app.market.domain.repository.PackageRepository
import com.app.market.domain.repository.SearchHistoryRepository
import com.app.market.domain.repository.UpdatePreferencesRepository
import com.app.market.platform.UiPlatform
import com.app.market.ui.model.AppActionKind
import com.app.market.ui.model.SearchResultItem
import com.app.market.ui.model.resolveActionKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 每个来源独立翻页，各自的结尾位置可能不同。 */
@Immutable
data class SourcePaging(val page: Int = 0, val hasMore: Boolean = false)

@Immutable
data class SearchUiState(
    val keyword: String = "",
    val loading: Boolean = false,
    val errorMessage: String = "",
    val showNoResults: Boolean = false,
    val results: List<SearchResultItem> = emptyList(),
    val history: List<String> = emptyList(),
    val selectedHistory: String? = null,
    /** Keyword the current [results] belong to (may differ from the live input [keyword]). */
    val activeKeyword: String = "",
    val paging: Map<AppSource, SourcePaging> = emptyMap(),
    val loadingMore: Boolean = false,
    /** Bumps on every completed search; used to invalidate an in-flight page load and reset scroll. */
    val searchEpoch: Int = 0,
    val sources: Set<AppSource> = AppSource.Default,
) {
    val hasMore: Boolean get() = paging.values.any { it.hasMore }
}

/** Cap pages fetched per load-more trigger so an all-duplicate tail can't spin indefinitely. */
private const val MAX_PAGES_PER_LOAD = 4

/** 每次加载希望凑到的条目数（铺满一屏），不够就继续翻页。 */
private const val MinItemsPerLoad = 15

/** 首屏补齐时最多多翻几页，避免慢源拖太久。 */
private const val MaxFillPages = 3

/** 一次补齐/加载的总时间预算：超了就收工，别让底部的加载圈转个没完。 */
private const val FillBudgetMs = 12_000L

class SearchViewModel(
    private val sources: MarketSourceRepository,
    private val historyStore: SearchHistoryRepository,
    private val updatePrefs: UpdatePreferencesRepository,
    private val packages: PackageRepository,
    private val downloads: DownloadRepository,
    private val uiPlatform: UiPlatform,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val downloadStates: StateFlow<Map<String, DownloadState>> = downloads.states
    private val pendingDownloads = mutableSetOf<String>()

    /** 搜索结果的图标懒加载覆盖：packageName → 真图标（README 首图）。 */
    val iconOverrides = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        // 流式结果出来后，后台懒加载 LSPosed 条目的真图标（search.json 里没有图标数据）
        viewModelScope.launch {
            _uiState.map { it.results }.distinctUntilChanged().collect { results ->
                enrichLsposedIcons(results)
            }
        }
    }

    private fun enrichLsposedIcons(results: List<SearchResultItem>) {
        val pending = results.filter {
            it.app.source == AppSource.LSPOSED && it.app.packageName !in iconOverrides.value
        }
        if (pending.isEmpty()) return
        viewModelScope.launch {
            pending.forEach { item ->
                launch {
                    val raw = sources.lsposedIcon(item.app.packageName) ?: return@launch
                    val icon = raw.trim()
                    if (icon.isEmpty()) return@launch
                    iconOverrides.update { current -> current + mapOf(item.app.packageName to icon) }
                }
            }
        }
    }
    private var searchJob: Job? = null
    private var sourceOverride: Set<AppSource>? = null

    init {
        viewModelScope.launch { reloadHistory() }
        viewModelScope.launch {
            updatePrefs.searchSources.collect { sources ->
                if (sourceOverride != null) return@collect
                val changed = _uiState.value.sources != sources
                _uiState.update { it.copy(sources = sources) }
                // 源变了旧结果就不作数，按当前关键词重搜
                if (changed) rerunActiveSearch()
            }
        }
        viewModelScope.launch {
            packages.changes.collect { change ->
                applyInstalledState(
                    change.packageName,
                    change.installedVersionCode ?: 0L,
                    change.installedVersionName,
                )
                // 包变化广播里的版本号取自本地缓存快照，刚装完那一刻可能还是 0 或旧值；
                // 再直查一次 PackageManager 纠正，避免「装完了按钮还写着安装」。
                viewModelScope.launch {
                    val fresh = runCatching { packages.freshInstalledVersionCode(change.packageName) }
                        .getOrNull() ?: 0L
                    if (fresh != (change.installedVersionCode ?: 0L)) {
                        val name = runCatching { packages.installedVersionName(change.packageName) }
                            .getOrNull()
                            ?: change.installedVersionName
                        applyInstalledState(change.packageName, fresh, name)
                    }
                }
            }
        }
    }

    fun setKeyword(value: String) {
        // Clearing the field drops results (history reappears) and resets pagination so infinite-scroll
        // won't refetch the old query; the in-flight search is cancelled so its late result can't repopulate.
        if (value.isBlank()) {
            searchJob?.cancel()
            searchJob = null
            _uiState.update {
                it.copy(
                    keyword = value,
                    loading = false,
                    results = emptyList(),
                    showNoResults = false,
                    errorMessage = "",
                    activeKeyword = "",
                    paging = emptyMap(),
                    loadingMore = false,
                )
            }
        } else {
            _uiState.update { it.copy(keyword = value) }
        }
    }

    fun clearSearch() {
        // Cancel the in-flight search and drop loading; else the spinner stays up and the late result
        // repopulates the just-cancelled search.
        searchJob?.cancel()
        searchJob = null
        sourceOverride = null
        _uiState.update {
            it.copy(
                keyword = "",
                loading = false,
                results = emptyList(),
                showNoResults = false,
                errorMessage = "",
                selectedHistory = null,
                activeKeyword = "",
                paging = emptyMap(),
                loadingMore = false,
            )
        }
    }

    fun selectHistory(keyword: String?) = _uiState.update { it.copy(selectedHistory = keyword) }
    /** 多选搜索来源：点一下切换勾选，立即按新来源集重搜。 */
    fun toggleSearchSource(source: AppSource) {
        val current = _uiState.value.sources
        val next = if (source in current) {
            if (current.size == 1) current else current - source
        } else {
            current + source
        }
        if (next == current) return
        setSearchSources(next)
    }
    fun setSearchSources(sources: Set<AppSource>) {
        if (sources.isEmpty()) return
        viewModelScope.launch {
            updatePrefs.setSearchSources(sources)
            // 切换来源后清掉上一次的错误/空结果，并用当前关键词立即重搜，
            // 否则上一个来源的超时提示会一直挂着不刷新
            _uiState.update {
                it.copy(sources = sources, errorMessage = "", showNoResults = false)
            }
            if (_uiState.value.keyword.isNotBlank()) runSearch()
        }
    }

    fun searchWith(keyword: String, sourcesOverride: Set<AppSource>? = null) {
        sourceOverride = sourcesOverride
        _uiState.update { state ->
            state.copy(keyword = keyword, sources = sourcesOverride ?: state.sources)
        }
        runSearch()
    }

    fun runSearch() {
        val keyword = _uiState.value.keyword.trim()
        if (keyword.isBlank() || _uiState.value.loading) return
        executeSearch(keyword, recordHistory = true, keepResults = keyword == _uiState.value.activeKeyword)
    }

    private fun rerunActiveSearch() {
        val keyword = _uiState.value.activeKeyword
        if (keyword.isBlank()) return
        executeSearch(keyword, recordHistory = false, keepResults = false)
    }

    private fun executeSearch(keyword: String, recordHistory: Boolean, keepResults: Boolean) {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                loading = true,
                loadingMore = false,
                errorMessage = "",
                showNoResults = false,
                results = if (keepResults) it.results else emptyList(),
                activeKeyword = if (keepResults) it.activeKeyword else "",
                paging = if (keepResults) it.paging else emptyMap(),
            )
        }
        if (recordHistory) {
            // 历史写入独立于 searchJob：清空搜索会取消 searchJob，不应连带丢掉刚搜过的词
            viewModelScope.launch {
                historyStore.add(keyword)
                reloadHistory()
            }
        }
        searchJob = viewModelScope.launch {
            val targetSources = sourceOverride ?: _uiState.value.sources
            fun cacheKey(src: AppSource) = "search_v1:${src.token}:${updatePrefs.exactSearch.value}:${updatePrefs.googlePlayEngine.value}:$keyword"
            val fetched = LinkedHashMap<AppSource, Result<SearchPage>>()
            targetSources.forEach { src ->
                val cached = runCatchingCancellable { updatePrefs.readPageCache(cacheKey(src)) }.getOrNull()
                    ?.let(PageCacheCodec::decodeCategory)
                if (cached != null) fetched[src] = Result.success(SearchPage(cached.first, cached.second))
            }
            if (fetched.isNotEmpty()) {
                val restored = toItems(mergeApps(emptyList(), orderedApps(fetched, keyword)), emptyList())
                _uiState.update { it.copy(loading = false, results = restored, activeKeyword = keyword, paging = pagingOf(fetched, targetSources.associateWith { 0 })) }
            }
            // 各来源独立刷新；失败时保留该来源的旧数据。
            val gate = Mutex()
            kotlinx.coroutines.coroutineScope {
                targetSources.forEach { src ->
                    launch {
                        // GitHub 仓库源：逐条上屏，不等到全部解析完
                        val incremental = runCatching { sources.searchIncremental(src, keyword, 0) }.getOrNull()
                        if (incremental != null) {
                            var incrementalLast: SearchPage? = null
                            try {
                            incremental.collect { page ->
                                incrementalLast = page
                                val snapshot: Map<AppSource, Result<SearchPage>> = gate.withLock {
                                    fetched[src] = Result.success(page)
                                    fetched.toMap()
                                }
                                val streamed = toItems(
                                    mergeApps(emptyList(), orderedApps(snapshot, keyword)),
                                    emptyList(),
                                )
                                _uiState.update {
                                    it.copy(
                                        loading = false,
                                        results = streamed,
                                        activeKeyword = keyword,
                                        paging = pagingOf(snapshot, pages = targetSources.associateWith { 0 }),
                                        showNoResults = streamed.isEmpty(),
                                    )
                                }
                            }
                            incrementalLast?.let { page ->
                                runCatchingCancellable { updatePrefs.writePageCache(cacheKey(src), PageCacheCodec.encodeCategory(page.items, page.hasMore, 1)) }
                            }
                            } catch (error: kotlinx.coroutines.CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                gate.withLock { if (src !in fetched) fetched[src] = Result.failure(error) }
                            }
                            return@launch
                        }
                        val one = fetchPages(keyword, mapOf(src to 0))
                        one[src]?.getOrNull()?.let { page ->
                            runCatchingCancellable { updatePrefs.writePageCache(cacheKey(src), PageCacheCodec.encodeCategory(page.items, page.hasMore, 1)) }
                        }
                        val snapshot: Map<AppSource, Result<SearchPage>> = gate.withLock {
                            one.forEach { (source, result) -> if (result.isSuccess || source !in fetched) fetched[source] = result }
                            fetched.toMap()
                        }
                        if (snapshot.values.none { it.isSuccess }) return@launch
                        val streamed = toItems(mergeApps(emptyList(), orderedApps(snapshot, keyword)), emptyList())
                        _uiState.update {
                            it.copy(
                                loading = false,
                                results = streamed,
                                activeKeyword = keyword,
                                paging = pagingOf(snapshot, pages = targetSources.associateWith { 0 }),
                                showNoResults = streamed.isEmpty(),
                            )
                        }
                    }
                }
            }
            val all = fetched.toMap()
            // 单源故障不该挡住另一源的结果
            if (all.values.none { it.isSuccess }) {
                val failure = firstError(all)
                _uiState.update { it.copy(loading = false, errorMessage = failure?.message ?: "Search failed") }
                return@launch
            }
            var currentApps = mergeApps(emptyList(), orderedApps(all, keyword))
            var pagingNow = pagingOf(all, pages = targetSources.associateWith { 0 })
            var items = toItems(currentApps, emptyList())
            var fillAttempts = 0
            val fillDeadline = System.currentTimeMillis() + FillBudgetMs
            // 一屏铺满：精确搜索常常一页只命中 1~3 个，继续翻页补齐，别让首屏只有两三条
            while (
                items.size < MinItemsPerLoad &&
                fillAttempts < MaxFillPages &&
                System.currentTimeMillis() < fillDeadline &&
                pagingNow.values.any { it.hasMore }
            ) {
                fillAttempts++
                val nextPages = pagingNow.filterValues { it.hasMore }
                    .mapValues { (_, state) -> state.page + 1 }
                val more = fetchPages(keyword, nextPages)
                if (more.values.none { it.isSuccess }) break
                pagingNow = pagingNow + pagingOf(more, nextPages)
                val merged = mergeApps(currentApps, orderedApps(more, keyword))
                if (merged.size == currentApps.size) continue
                currentApps = merged
                items = toItems(currentApps, emptyList())
                _uiState.update {
                    it.copy(
                        loading = false,
                        results = items,
                        activeKeyword = keyword,
                        paging = pagingNow,
                    )
                }
            }
            _uiState.update {
                it.copy(
                    loading = false,
                    results = items,
                    activeKeyword = keyword,
                    paging = pagingNow,
                    showNoResults = items.isEmpty(),
                    searchEpoch = it.searchEpoch + 1,
                )
            }
        }
    }

    /**
     * Appends the next page(s), skipping all-duplicate pages so each trigger makes progress. Aborts if a
     * newer search supersedes this one (tracked by [SearchUiState.activeKeyword] + [SearchUiState.searchEpoch]).
     */
    fun loadMore() {
        val snapshot = _uiState.value
        if (snapshot.loading || snapshot.loadingMore || !snapshot.hasMore || snapshot.activeKeyword.isBlank()) return
        val baseKeyword = snapshot.activeKeyword
        val baseEpoch = snapshot.searchEpoch
        _uiState.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            fun superseded() = _uiState.value.let { it.activeKeyword != baseKeyword || it.searchEpoch != baseEpoch }
            var paging = snapshot.paging
            var addedCount = 0
            var failed = false
            var attempts = 0
            val loadDeadline = System.currentTimeMillis() + FillBudgetMs
            // 一次尽量铺满一屏：只要还没凑够 [MinItemsPerLoad] 条且还有更多页，就继续翻（受时间预算约束）
            while (addedCount < MinItemsPerLoad && attempts < MAX_PAGES_PER_LOAD &&
                System.currentTimeMillis() < loadDeadline &&
                paging.values.any { it.hasMore } && !superseded()
            ) {
                attempts++
                val nextPages = paging.filterValues { it.hasMore }.mapValues { (_, state) -> state.page + 1 }
                val fetched = fetchPages(baseKeyword, nextPages)
                if (fetched.values.none { it.isSuccess }) {
                    failed = true; break
                }
                // 失败的源保留原页码，下次触发重试
                paging = paging + pagingOf(fetched, nextPages)
                val incoming = orderedApps(fetched, baseKeyword)
                // Dedup + commit under the staleness guard: a superseded search flips it, so its page is dropped.
                val before = _uiState.value
                if (before.activeKeyword != baseKeyword || before.searchEpoch != baseEpoch) break
                val mergedApps = mergeApps(before.results.map { it.app }, incoming)
                addedCount += (mergedApps.size - before.results.size).coerceAtLeast(0)
                val resolved = toItems(mergedApps, before.results)
                val committedPaging = paging
                _uiState.update { state ->
                    if (state.activeKeyword != baseKeyword || state.searchEpoch != baseEpoch) state
                    else state.copy(results = resolved, paging = committedPaging)
                }
            }
            val addedAny = addedCount > 0
            // Stop the spinner; keep hasMore on a transient failure (retry), else end pagination when no new items.
            _uiState.update { state ->
                when {
                    state.activeKeyword != baseKeyword || state.searchEpoch != baseEpoch -> state
                    addedAny || failed -> state.copy(loadingMore = false)
                    else -> state.copy(
                        loadingMore = false,
                        paging = state.paging.mapValues { (_, value) -> value.copy(hasMore = false) },
                    )
                }
            }
        }
    }

    /** 各源并发取指定页，单源异常收进 Result 交调用方判定；慢源超时返回空页不拖累其它源。 */
    private suspend fun fetchPages(keyword: String, pages: Map<AppSource, Int>): Map<AppSource, Result<SearchPage>> =
        coroutineScope {
            pages.map { (source, page) ->
                source to async {
                    val started = System.currentTimeMillis()
                    val timeoutMs = timeoutFor(source)
                    val result = runCatchingCancellable {
                        kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                            searchSource(source, keyword, page)
                        } ?: throw IllegalStateException("搜索请求超时（${timeoutMs}ms）")
                    }
                    result.onSuccess {
                        AppLog.log("Search: $source kw=$keyword -> ${it.items.size} items in ${System.currentTimeMillis() - started}ms")
                    }.onFailure {
                        AppLog.log("Search: $source kw=$keyword -> FAILED in ${System.currentTimeMillis() - started}ms: ${it.message}")
                    }
                    result
                }
            }.associate { (source, deferred) -> source to deferred.await() }
        }

    /**
     * 单个来源取一页。
     */
    private suspend fun searchSource(source: AppSource, keyword: String, page: Int): SearchPage =
        sources.search(source, keyword, page)

    /** 小米在前，再按关键词相关性排序 —— 相关的排前面，明显不相关的直接丢掉。 */
    private fun orderedApps(
        fetched: Map<AppSource, Result<SearchPage>>,
        keyword: String,
    ): List<MarketAppInfo> =
        rankByKeyword(
            AppSource.entries.flatMap { fetched[it]?.getOrNull()?.items.orEmpty() },
            keyword,
            exact = updatePrefs.exactSearch.value,
        )

    /** 精确搜索豁免的严格检索源：Google Play/LSPosed/GitLab/GitHub 返回的本就按关键词命中，别名/翻译不误杀。 */
    private val ExactSearchExemptSources = setOf(AppSource.GOOGLE, AppSource.LSPOSED, AppSource.GITLAB, AppSource.GITHUB)
    private val SOURCE_SEARCH_TIMEOUT_MS = 12_000L
    /** Google 首次搜索要现做分发器鉴权 + getItems 补图标 + 网页兜底，12 秒必然超时。 */
    private val GOOGLE_SEARCH_TIMEOUT_MS = 30_000L

    private fun timeoutFor(source: AppSource): Long =
        if (source == AppSource.GOOGLE) GOOGLE_SEARCH_TIMEOUT_MS else SOURCE_SEARCH_TIMEOUT_MS

    /**
     * 按关键词相关性排序 + 过滤掉明显不相关的条目。
     *
     * 小米/酷安这类商店的搜索是「猜你喜欢」式的，搜「微信」会带出一堆标题里根本没有「微信」
     * 的东西；Google Play 则是严格按关键词检索，所以只返回真正命中的。
     * 这里把两者拉齐：标题/副标题/包名至少有一处命中，否则丢弃。
     * 全部被丢时回退到原始顺序，避免关键词不在任何标题里时搜出一片空白。
     */
    private fun rankByKeyword(
        apps: List<MarketAppInfo>,
        keyword: String,
        exact: Boolean,
    ): List<MarketAppInfo> {
        val needle = keyword.trim().lowercase()
        if (needle.isEmpty()) return apps
        // 归一化：去空格/标点，「Chat GPT」「chat-gpt」都能命中「chatgpt」
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val key = norm(needle)
        if (key.isEmpty()) return apps
        fun score(app: MarketAppInfo): Int {
            val name = norm(app.displayName)
            val pkg = norm(app.packageName)
            val publisher = norm(app.publisherName)
            // 只在精确搜索时给 Google 放宽：简介命中也算相关内容
            val summary = if (app.source == AppSource.GOOGLE) norm(app.summaryText) else ""
            return when {
                pkg == key || name == key -> 100
                name.startsWith(key) -> 80
                name.contains(key) -> 60
                pkg.contains(key) -> 40
                publisher.contains(key) -> 20
                summary.isNotEmpty() && summary.contains(key) -> 30
                else -> 0
            }
        }
        val scored = apps.map { it to score(it) }
        // 精确搜索：只接受「名称/包名命中」（≥40 分）。发行商命中、以及原本豁免的严格检索源，
// 在用户主动开精确搜索时一并不放行——否则搜「微信」会被腾讯系应用和商店推荐位塞满。
        val base = if (exact) {
            // 精确搜索对所有来源生效（包括 Google Play）：Google 额外承认「简介命中」（30 分），
            // 因为它在网页结果里常把相关应用放在一起，标题未必直接含关键词。
            scored.filter { (app, s) ->
                s >= 40 || (app.source == AppSource.GOOGLE && s >= 30)
            }
        } else {
            scored
        }
        AppLog.log("SearchFilter: kw=$keyword exact=$exact ${scored.size} -> ${base.size} (dropped ${scored.size - base.size})")
        return base
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /** 把某个包的最新「已安装版本」写回搜索结果，并重算按钮动作（安装/更新/打开）。 */
    private fun applyInstalledState(
        packageName: String,
        installedVersionCode: Long,
        installedVersionName: String,
    ) {
        _uiState.update { state ->
            state.copy(
                results = state.results.map { item ->
                    if (item.app.packageName != packageName) {
                        item
                    } else {
                        val app = item.app.copy(
                            installedVersionCode = installedVersionCode,
                            installedVersionName = installedVersionName,
                        )
                        item.copy(
                            app = app,
                            actionKind = searchActionKind(app, installedVersionCode),
                        )
                    }
                },
            )
        }
    }

    private fun pagingOf(
        fetched: Map<AppSource, Result<SearchPage>>,
        pages: Map<AppSource, Int>,
    ): Map<AppSource, SourcePaging> = fetched.mapNotNull { (source, result) ->
        val page = result.getOrNull() ?: return@mapNotNull null
        source to SourcePaging(pages[source] ?: 0, page.hasMore)
    }.toMap()

    private fun firstError(fetched: Map<AppSource, Result<SearchPage>>): Throwable? =
        fetched.values.firstNotNullOfOrNull { it.exceptionOrNull() }

    /** 同包名保留高版本；位置由先入者定，避免翻页补进来的条目把列表重排。 */
    private fun mergeApps(existing: List<MarketAppInfo>, incoming: List<MarketAppInfo>): List<MarketAppInfo> {
        val byPackage = LinkedHashMap<String, MarketAppInfo>(existing.size + incoming.size)
        existing.forEach { byPackage[it.packageName] = it }
        incoming.forEach { app ->
            val current = byPackage[app.packageName]
            if (current == null || app.versionCode > current.versionCode) byPackage[app.packageName] = app
        }
        return byPackage.values.toList()
    }

    /** Resolves each app's install/update/open action off-main (cached PackageManager lookup). */
    private suspend fun toItems(
        apps: List<MarketAppInfo>,
        existing: List<SearchResultItem>,
    ): List<SearchResultItem> {
        if (apps.isEmpty()) return emptyList()
        val known = existing.associateBy { it.app.packageName }
        // 只对新增或被替换的条目重查安装状态，翻页不整表重算
        val changed = apps.filter { known[it.packageName]?.app != it }
        if (changed.isEmpty()) return apps.mapNotNull { known[it.packageName] }
        val installed = packages.installedVersionCodes(changed.map { it.packageName })
        val resolved = changed.associate { app ->
            val cachedVersionCode = installed[app.packageName] ?: 0L
            // A stale package-list cache can make a system app that was just upgraded look older
            // than the store result. Verify only would-be updates with a direct PackageManager
            // lookup before exposing the update action.
            val installedVersionCode = if (cachedVersionCode in 1 until app.versionCode) {
                packages.freshInstalledVersionCode(app.packageName) ?: cachedVersionCode
            } else {
                cachedVersionCode
            }
            val resolvedApp = app.copy(installedVersionCode = installedVersionCode)
            app.packageName to SearchResultItem(
                resolvedApp,
                searchActionKind(resolvedApp, installedVersionCode),
            )
        }
        return apps.mapNotNull { resolved[it.packageName] ?: known[it.packageName] }
    }

    private fun searchActionKind(app: MarketAppInfo, installedVersionCode: Long): AppActionKind =
        if (app.isReservation()) AppActionKind.RESERVE
        else if (installedVersionCode > 0L && app.isDownloadBlocked()) AppActionKind.OPEN
        else resolveActionKind(installedVersionCode, app.versionCode)

    fun removeHistory(keyword: String) {
        _uiState.update { it.copy(selectedHistory = null) }
        viewModelScope.launch {
            historyStore.remove(keyword)
            reloadHistory()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyStore.clear()
            reloadHistory()
        }
    }

    fun onAction(item: SearchResultItem) {
        if (item.actionKind == AppActionKind.RESERVE) return
        if (item.actionKind == AppActionKind.OPEN) {
            // OPPO/三星/华为的 openLink 只是商店深链回退，不是已安装应用的启动目标
            val opened = if (item.app.source.capabilities.prefersOpenLinkLaunch) {
                packages.openLink(item.app.openLink) || packages.openApp(item.app.packageName)
            } else {
                packages.openApp(item.app.packageName) || packages.openLink(item.app.openLink)
            }
            if (!opened) {
                uiPlatform.showToast(item.app.displayName)
            }
            return
        }
        download(item.app, item.actionKind == AppActionKind.UPDATE)
    }

    fun download(app: MarketAppInfo, update: Boolean = false) {
        startDownload(app, update)
    }

    private fun startDownload(app: MarketAppInfo, update: Boolean) {
        if (app.isDownloadBlocked()) return
        downloads.prepareDownload(app)
        if (!pendingDownloads.add(app.packageName)) return
        viewModelScope.launch {
            try {
                runCatchingCancellable {
                    if (update) {
                        sources.downloadUpdateMeta(app.source, app)
                    } else {
                        sources.downloadMeta(app.source, app, _uiState.value.keyword)
                    }
                }
                    // 下载完成即自动安装
                    .onSuccess { downloads.start(it) }
                    .onFailure { uiPlatform.showToast(it.message ?: "Download failed") }
            } finally {
                pendingDownloads.remove(app.packageName)
            }
        }
    }

    fun installDownloaded(packageName: String) = downloads.install(packageName)
    fun cancelDownload(packageName: String) = downloads.cancel(packageName)
    fun clearDownload(packageName: String) = downloads.clear(packageName)

    private suspend fun reloadHistory() {
        val history = historyStore.load()
        _uiState.update { it.copy(history = history) }
    }
}