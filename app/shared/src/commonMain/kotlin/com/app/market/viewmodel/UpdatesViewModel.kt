package com.app.market.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.market.domain.exception.InstalledPackagesUnavailableException
import com.app.market.domain.diagnostic.AppLog
import com.app.market.domain.model.download.DownloadMeta
import com.app.market.domain.model.download.DownloadPhase
import com.app.market.domain.model.download.DownloadState
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.CommentSourceSetting
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.preference.HomePage
import com.app.market.domain.model.update.IgnoredUpdate
import com.app.market.domain.model.update.SelfUpdate
import com.app.market.domain.repository.DownloadRepository
import com.app.market.domain.repository.MarketSourceRepository
import com.app.market.domain.repository.PackageRepository
import com.app.market.domain.repository.SelfUpdateRepository
import com.app.market.domain.repository.UpdatePreferencesRepository
import com.app.market.platform.UiPlatform
import com.app.market.resources.Res
import com.app.market.resources.check_update_current
import com.app.market.resources.check_update_failed
import com.app.market.resources.update_available_title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import misc.VersionInfo
import org.jetbrains.compose.resources.getString

/** 自更新行的虚拟 appId（自更新不来自任何市场源）。 */
internal const val SELF_UPDATE_APP_ID = -1L

/** 版本段开头的数字部分：容忍 v2.4.5fix 这种带字母后缀的 tag 段。 */
private val LeadingDigitsRegex = Regex("^" + "\\d+")

@Immutable
data class UpdatesUiState(
    val loading: Boolean = false,
    val checkFailed: Boolean = false,
    val appListPermissionRequired: Boolean = false,
    /** 更新检查进度：已完成来源数 / 需检查来源数。total 为 0 表示无进度可报。 */
    val checkDone: Int = 0,
    val checkTotal: Int = 0,
    val showSystemUpdates: Boolean = true,
    val removeSearchAds: Boolean = false,
    val filterQuickGames: Boolean = false,
    val filterReservationApps: Boolean = false,
    val updates: List<MarketAppInfo> = emptyList(),
)

/** Raw (unfiltered) update-check result. The visible [UpdatesUiState] is derived from this + prefs. */
private data class RawUpdates(
    val loading: Boolean = false,
    val checkFailed: Boolean = false,
    val appListPermissionRequired: Boolean = false,
    /** 已完成来源数（含失败的来源：跑完就算一个）。 */
    val checkDone: Int = 0,
    /** 待检查来源总数；单源检查时为 1，「全部」时为支持更新的来源数。 */
    val checkTotal: Int = 0,
    val all: List<MarketAppInfo> = emptyList(),
    /** 自身新版本（AppMarket 自更新），作为普通更新行并入列表。 */
    val selfUpdate: MarketAppInfo? = null,
)

/** 自更新检查状态（更新列表里的 AppMarket 行）。 */
@Immutable
data class SelfUpdateUiState(
    val checking: Boolean = false,
    val update: SelfUpdate? = null,
)

class UpdatesViewModel(
    private val sources: MarketSourceRepository,
    private val prefs: UpdatePreferencesRepository,
    private val downloads: DownloadRepository,
    private val packages: PackageRepository,
    private val uiPlatform: UiPlatform,
    private val selfUpdateRepository: SelfUpdateRepository,
    private val google: com.app.market.domain.repository.GooglePlayRepository,
) : ViewModel() {

    // Seeded loading=true so Compose can request notification permission before the first live check
    // without flashing "no updates".
    private val _raw = MutableStateFlow(RawUpdates(loading = true))

    val downloadStates: StateFlow<Map<String, DownloadState>> = downloads.states

    /** LSPosed 条目的本地图标覆盖：仓库接口不给图标，模块已装在设备上，直接读 PackageManager。 */
    val iconOverrides = MutableStateFlow<Map<String, String>>(emptyMap())

    // Separate flow (not in [uiState]) so the pager can read the seeded value synchronously.
    val homePage: StateFlow<HomePage> = prefs.homePage
    val searchSources: StateFlow<Set<AppSource>> = prefs.searchSources
    val todaySource: StateFlow<AppSource> = prefs.todaySource
    val commentSource: StateFlow<CommentSourceSetting> = prefs.commentSource
    val categorySource: StateFlow<AppSource> = prefs.categorySource
    val appCategorySource: StateFlow<AppSource> = prefs.appCategorySource
    val gameCategorySource: StateFlow<AppSource> = prefs.gameCategorySource
    val updateSources: StateFlow<Set<AppSource>> = prefs.updateSources
    val updateSourcePriority: StateFlow<List<AppSource>> = prefs.updateSourcePriority

    // App-detail display toggles; consumed by the detail screen, surfaced here for the settings UI.

    /** 进入应用时识别剪贴板中的商店链接。 */
    val detectClipboardLinks: StateFlow<Boolean> = prefs.detectClipboardLinks
    /** 精确搜索：只保留标题/包名命中的条目，丢掉商店推荐的无关项。 */
    val exactSearch: StateFlow<Boolean> = prefs.exactSearch
    val includePrereleaseUpdates: StateFlow<Boolean> = prefs.includePrereleaseUpdates
    /** 是否写入诊断日志；关闭后不再在 Download 目录创建日志文件。 */
    fun clearPageCaches() = persist {
        try { prefs.clearPageCaches(); uiPlatform.showToast("缓存已清除") }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { uiPlatform.showToast(e.message ?: "清除缓存失败") }
    }
    val pageCacheEnabled: StateFlow<Boolean> = prefs.pageCacheEnabled
    fun setPageCacheEnabled(value: Boolean) = persist { prefs.setPageCacheEnabled(value) }
    val logEnabled: StateFlow<Boolean> = prefs.logEnabled
    val logMaxSize: StateFlow<Int> = prefs.logMaxSize
    /** 安装成功后自动删除已保存的安装包。 */
    val autoDeletePackage: StateFlow<Boolean> = prefs.autoDeletePackage

    // 全局应用名精简开关；App 根部经 CompositionLocal 下发，这里仅供设置页读写。
    val stripAppNameSubtitle: StateFlow<Boolean> = prefs.stripAppNameSubtitle

    // Visible list = pure function of the raw result + preference flows; filtered off-main via flowOn.
    val uiState: StateFlow<UpdatesUiState> =
        combine(
            _raw,
            prefs.showSystemUpdates,
            prefs.removeSearchAds,
            combine(prefs.filterQuickGames, prefs.filterReservationApps) { games, reservations -> games to reservations },
            combine(prefs.permanentIgnores, prefs.onceIgnores) { permanent, once -> permanent to once },
        ) { raw, showSystem, removeAds, filters, ignores ->
            derive(raw, showSystem, removeAds, filters.first, filters.second, ignores.first, ignores.second)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, UpdatesUiState(loading = true))

    private val pendingDownloads = mutableSetOf<String>()
    private var initialCheckStarted = false
    private var checkJob: kotlinx.coroutines.Job? = null
    /** 磁盘缓存加载任务：进前台时要先等它把缓存铺好，再决定是否联网。 */
    private var cacheLoadJob: kotlinx.coroutines.Job? = null
    /** 进前台处理中（防抖：冷启动会被 ON_START 触发两次）。 */
    private var foregroundHandling = false
    /** 进前台的异步任务（等缓存 → 决定是否联网），切后台要一起取消。 */
    private var foregroundJob: kotlinx.coroutines.Job? = null
    /** 本轮尚未检查完的来源（切后台被打断时保留，回前台只补这些）。 */
    private val remainingCheckSources = linkedSetOf<AppSource>()
    /** 本轮是否已完整跑完（跑完后回前台不再重查）。 */
    private var checkRoundCompleted = false
    /** 「全部」模式下最近一次分源检查结果；优先级变更时据此重合并，无需重新联网。 */
    private var latestPerSourceResults: Map<AppSource, List<MarketAppInfo>> = emptyMap()
    /** 检查更新过程中收到安装完成事件的「包名 → 已装版本号」，用于剔除被来源结果重新带回来的条目。 */
    private val installedDuringCheck = mutableMapOf<String, Long>()
    /** 本轮检查的来源总数（续跑时用它算「已完成多少」，进度条不回退）。 */
    private var roundTotal = 0
    /** 本轮检查因超时被跳过的来源；UI 消费后调 [clearSkippedSources] 复位。 */
    private val _skippedSources = MutableStateFlow<List<AppSource>>(emptyList())
    val skippedSources: StateFlow<List<AppSource>> = _skippedSources.asStateFlow()
    fun clearSkippedSources() { _skippedSources.value = emptyList() }

    /** 自更新检查状态（AppMarket 新版本行 / 关于页开关）。 */
    private val _selfUpdateState = MutableStateFlow(SelfUpdateUiState())
    val selfUpdateState: StateFlow<SelfUpdateUiState> = _selfUpdateState.asStateFlow()
    private var latestSelfUpdate: SelfUpdate? = null

    init {
        // 更新列表里的 LSPosed 模块：懒加载本地图标（未安装的拿不到，保持空白）
        viewModelScope.launch {
            uiState
                .map { state -> state.updates.filter { it.source == AppSource.LSPOSED }.map { it.packageName } }
                .distinctUntilChanged()
                .collect { packagesToLoad ->
                    packagesToLoad
                        .filter { it !in iconOverrides.value }
                        .forEach { pkg ->
                            launch {
                                val url = sources.installedIcon(pkg) ?: return@launch
                                iconOverrides.update { current -> current + (pkg to url) }
                            }
                        }
                }
        }
        viewModelScope.launch {
            packages.changes.collect { change ->
                val installedVersion = change.installedVersionCode ?: return@collect
                AppLog.log("PackageChange: ${change.packageName} installedVersionCode=$installedVersion")
                // 检查更新进行中时，来源结果会整体覆盖 all，把刚装好的项又带回来；
                // 登记已装版本，写入时统一剔除。
                installedDuringCheck[change.packageName] = installedVersion
                _raw.update {
                    it.copy(
                        all = it.all.filterNot { app ->
                            app.packageName == change.packageName && installedVersion >= app.versionCode
                        },
                        selfUpdate = it.selfUpdate?.takeUnless { app ->
                            app.packageName == change.packageName && installedVersion >= app.versionCode
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            // 安装被系统拒绝（典型：VERSION_DOWNGRADE，已装版本比待装版本高）后，这条更新
            // 已经不可能装上，继续留在更新页只会反复点、反复失败：直接移除该行，
            // 并把已装版本登记下来，后续检查结果再带回来也会被 dropAlreadyInstalled 剔掉。
            downloads.states.collect { states ->
                states.values.forEach { state ->
                    if (state.phase != DownloadPhase.FAILED) return@forEach
                    val message = state.errorMessage
                    val downgradeBlocked = message.contains("低于已安装版本") ||
                        message.contains("VERSION_DOWNGRADE")
                    if (!downgradeBlocked) return@forEach
                    val installed = runCatching {
                        packages.freshInstalledVersionCode(state.packageName)
                    }.getOrNull()
                    if (installed != null) {
                        installedDuringCheck[state.packageName] = installed
                    }
                    AppLog.log("UpdateDropped: ${state.packageName} ${message.take(120)}")
                    removeUpdateRow(state.packageName)
                }
            }
        }

        viewModelScope.launch {
            var activeSources = prefs.updateSources.value
            prefs.updateSources.collect { nextSources ->
                if (nextSources == activeSources) return@collect
                activeSources = nextSources
                // Source-owned IDs, refs and delta metadata cannot survive a source switch.
                _raw.value = RawUpdates(loading = initialCheckStarted)
                if (initialCheckStarted) runCheck()
            }
        }
        viewModelScope.launch {
            var activePriority = prefs.updateSourcePriority.value
            prefs.updateSourcePriority.collect { nextPriority ->
                if (nextPriority == activePriority) return@collect
                activePriority = nextPriority
                // 优先级只决定「同一应用多来源都有更新」时的展示归属：
                // 对最近一次分源结果直接重合并，不用重新联网
                val perSource = latestPerSourceResults
                if (perSource.isNotEmpty()) {
                    val merged = mergeByPriority(perSource.values.flatten(), nextPriority)
                    _raw.update {
                        it.copy(all = dropAlreadyInstalled(preserveKnownDeltaSizes(it.all, orderByPriority(merged))))
                    }
                }
            }
        }
    }

    fun setShowSystemUpdates(value: Boolean) = persist { prefs.setShowSystemUpdates(value) }
    fun setRemoveSearchAds(value: Boolean) = persist { prefs.setRemoveSearchAds(value) }
    fun setFilterQuickGames(value: Boolean) = persist { prefs.setFilterQuickGames(value) }
    fun setFilterReservationApps(value: Boolean) = persist { prefs.setFilterReservationApps(value) }
    fun setDetectClipboardLinks(value: Boolean) = persist { prefs.setDetectClipboardLinks(value) }
    fun setExactSearch(value: Boolean) = persist { prefs.setExactSearch(value) }
    fun setIncludePrereleaseUpdates(value: Boolean) =
        persist { prefs.setIncludePrereleaseUpdates(value) }
    fun setLogEnabled(value: Boolean) = persist { prefs.setLogEnabled(value) }
    fun setLogMaxSize(value: Int) = persist { prefs.setLogMaxSize(value) }
    fun setAutoDeletePackage(value: Boolean) = persist { prefs.setAutoDeletePackage(value) }
    fun setStripAppNameSubtitle(value: Boolean) = persist { prefs.setStripAppNameSubtitle(value) }
    fun setHomePage(value: HomePage) = persist { prefs.setHomePage(value) }

    /** 设置项「更新检查超时时间」（秒）。 */
    val checkTimeoutSeconds: StateFlow<Int> = prefs.checkTimeoutSeconds
    fun setCheckTimeoutSeconds(value: Int) = persist { prefs.setCheckTimeoutSeconds(value) }
    /** 单来源检查超时毫秒数；卡死的源超过该时长即跳过。 */
    private fun checkTimeoutMillis(): Long = prefs.checkTimeoutSeconds.value.coerceAtLeast(1) * 1000L

    /**
     * 单个来源的超时。
     *
     * Google Play 要先把一批已装包（几百个）分批发 fdfe/getItems，首轮还要先做一次匿名鉴权，
     * 默认超时里跑不完就只能整源放弃，表现就是「更新页里看不到 Gmail、Google 这些 GP 应用的更新」，
     * 而下拉刷新（token/连接已热）又突然能出来。所以给 GP 一个更宽的下限，其它源维持用户设置。
     */
    private fun checkTimeoutFor(source: AppSource): Long {
        val base = checkTimeoutMillis()
        return if (source == AppSource.GOOGLE) maxOf(base, 30_000L) else base
    }

    /** 从更新列表里移除某个应用（安装被系统拒绝后，这条更新已经没有意义）。 */
    private fun removeUpdateRow(packageName: String) {
        _raw.update { raw ->
            raw.copy(
                all = raw.all.filterNot { it.packageName == packageName },
                selfUpdate = raw.selfUpdate?.takeUnless { it.packageName == packageName },
            )
        }
    }

    /** 剔除已装版本已追平/超过目标版本的条目（检查更新进行中安装完成时，来源结果会把它重新带回来）。 */
    private fun dropAlreadyInstalled(apps: List<MarketAppInfo>): List<MarketAppInfo> =
        apps.filterNot { app ->
            val installed = installedDuringCheck[app.packageName] ?: return@filterNot false
            installed >= app.versionCode
        }
    fun setSearchSource(value: AppSource) = persist { prefs.setSearchSources(setOf(value)) }
    fun setCategorySource(value: AppSource) = persist { prefs.setCategorySource(value) }
    fun setAppCategorySource(value: AppSource) = persist { prefs.setAppCategorySource(value) }
    fun setGameCategorySource(value: AppSource) = persist { prefs.setGameCategorySource(value) }
    fun setTodaySource(value: AppSource) = persist { prefs.setTodaySource(value) }
    fun setCommentSource(value: CommentSourceSetting) = persist { prefs.setCommentSource(value) }
    /** 「Google Play 来源设置」：appmarket（自研）/ aurorastore（Aurora 网页通道）。 */
    val googlePlayEngine: StateFlow<String> = prefs.googlePlayEngine
    fun setGooglePlayEngine(value: String) = persist { prefs.setGooglePlayEngine(value) }
    fun setUpdateSources(value: Set<AppSource>) = persist { prefs.setUpdateSources(value) }
    fun setUpdateSourcePriority(value: List<AppSource>) = persist { prefs.setUpdateSourcePriority(value) }
    /** 更新页卡片来源角标（更新来源为「全部」时显示）。 */
    val showUpdateSource: StateFlow<Boolean> = prefs.showUpdateSource
    fun setShowUpdateSource(value: Boolean) = persist { prefs.setShowUpdateSource(value) }

    /** 是否把 GitLab 预发布（Upcoming Release）也算作自更新候选。 */
    val receivePrerelease: StateFlow<Boolean> = prefs.receivePrerelease
    fun setReceivePrerelease(value: Boolean) = persist { prefs.setReceivePrerelease(value) }

    /** 用户主动刷新（下拉 / 点检查更新）：只有这条路径会联网，并顺带检查自更新。 */
    fun refresh() {
        runCheck()
        checkSelfUpdate(userInitiated = false)
    }

    /**
     * 应用进入前台（冷启动、从后台切回）：
     * 1. 先把磁盘缓存直接铺到列表上（不联网、不等接口，进程被回收也不会丢）；
     * 2. 缓存就绪后再决定要不要联网：本轮没跑完只补没检查过的来源，跑完了就不重查。
     */
    fun onEnterForeground() {
        // MainScreen 里 addObserver 会立刻补发一次 ON_START，加上首次注册的手动补触发，
        // 冷启动时这里会被调两次；不加防抖就会把刚发起的检查再取消重来一遍。
        if (foregroundHandling) return
        // 当前已经有一轮检查在跑（冷启动时 ON_START 会来两次），不要打断重来
        if (checkJob?.isActive == true) return
        foregroundHandling = true
        startInitialCheck()
        foregroundJob = viewModelScope.launch {
            // 等缓存先落到列表上，避免下面的联网检查把空列表当成「无缓存」而显示进度圈
            cacheLoadJob?.join()
            try {
                when {
                    // 本轮没跑完：只补没检查过的来源
                    remainingCheckSources.isNotEmpty() -> runCheck(remainingCheckSources.toList())
                    // 已经完整跑过一轮：回来不重查
                    checkRoundCompleted -> Unit
                    else -> refresh()
                }
            } finally {
                foregroundHandling = false
            }
        }
    }

    /**
     * 应用退到后台：检查继续跑，不暂停、不取消。
     *
     * 只撤掉「等缓存就绪再决定要不要联网」这道调度，检查本身（checkJob）保留：
     * 回到前台时 onEnterForeground 看到 checkJob 还活着就直接返回，不会重头再拉一遍，
     * 进度自然从当前位置接着走。
     */
    fun suspendCheck() {
        foregroundJob?.cancel()
        foregroundJob = null
        foregroundHandling = false
    }

    /**
     * 进入应用的初始化：只把上次的检查结果（磁盘缓存）摆出来，不发起任何网络请求。
     *
     * 缓存是持久化的（UpdatePreferenceKeys.CachedUpdates），退出应用、进程被回收都不会丢；
     * 读出时会按本地已安装版本对账：本地版本已经追平或超过缓存版本的条目直接剔除。
     */
    fun startInitialCheck() {
        if (initialCheckStarted) return
        initialCheckStarted = true
        cacheLoadJob = viewModelScope.launch {
            // 第一步（毫秒级）：读磁盘缓存，只查这几十个包名的本地版本来过滤「已装上」的行，
            // 立刻铺列表 —— 第一眼看到的是缓存，不是进度圈。
            // 之前为了压制「更新完幽灵行」改成先全量对账再铺，但全量扫描要 1~2 秒，
            // 那段时间 loading=true，页面只能先显示进度条，就是用户抱怨的那个现象。
            val raw = runCatchingCancellable { sources.loadRawCachedUpdates() }
                .getOrDefault(emptyList())
            val installed = if (raw.isEmpty()) emptyMap()
            else runCatchingCancellable {
                packages.installedVersionCodes(raw.map { it.packageName }.distinct())
            }.getOrDefault(emptyMap())
            // 登记本地版本：dropAlreadyInstalled 据此剔除已装上/已装更新版本的行，
            // 后续检查结果再把这些行带回来也会被同样剔掉
            installed.forEach { (name, code) -> installedDuringCheck[name] = code }
            val list = dropAlreadyInstalled(raw)
            if (list.isEmpty()) return@launch
            val ordered = orderByPriority(list)
            _raw.update { current ->
                if (current.all.isNotEmpty()) current
                else current.copy(loading = false, checkFailed = false, all = ordered)
            }
            // 第二步（后台）：全量扫描对账 + 结果写回磁盘（loadReconciledCachedUpdates 内部
            // 就会把对账结果写回缓存），只做事、不碰列表。
            // 不能拿 settled 去覆盖 current.all：检查已经在跑，列表里可能已有本轮新发现
            // （或合并中）的条目，覆盖会把它们抹掉，等检查完合并又「回来」——那正是用户
            // 报的「更新检查过程被移除，检查完又回来」。
            runCatchingCancellable { sources.loadReconciledCachedUpdates() }
        }
    }

    private fun runCheck(onlySources: List<AppSource>? = null) {
        checkJob?.cancel()
        if (onlySources == null) {
            checkRoundCompleted = false
            remainingCheckSources.clear()
            // 单一来源模式只跑那一个，否则会把整份来源列表当成〘未完成〙无限续跑
            remainingCheckSources.addAll(
                prefs.updateSources.value.ifEmpty { AppSource.UpdateSourceOptions.toSet() }
            )
            roundTotal = remainingCheckSources.size
        } else {
            remainingCheckSources.clear()
            remainingCheckSources.addAll(onlySources)
            if (roundTotal < onlySources.size) roundTotal = onlySources.size
        }
        // 续跑时进度不回退：从「本轮已完成的来源数」接着走，而不是从 0 重来
        val startDone = (roundTotal - remainingCheckSources.size).coerceIn(0, roundTotal)
        // 注意：这里只置 loading / 进度，绝不清空 all —— 列表内容由下面的「按来源增量合并」决定
        _raw.update {
            it.copy(
                loading = true,
                checkFailed = false,
                appListPermissionRequired = false,
                checkDone = startDone,
                checkTotal = roundTotal,
            )
        }
        checkJob = viewModelScope.launch {
            // 本轮检查因超时被跳过的来源，检查结束后统一提示
            val timedOut = mutableListOf<AppSource>()
            // 检查开始前列表里已有的内容（磁盘缓存 / 上一轮结果）。
            // 整个检查过程都不清空它：只有「某个来源真的返回了结果」才用结果替换这个来源的旧条目，
            // 拉取失败 / 超时 / 本轮没轮到的来源，旧条目原样保留（缓存不会被空结果或半截结果冲掉）。
            val listBeforeCheck = _raw.value.all
            val keepPrevious = listBeforeCheck.isNotEmpty()
            // 本次真正拿到完整结果的来源；只有这些来源的条目允许被覆盖
            val refreshedSources = linkedSetOf<AppSource>()
            val perSourceResults = mutableMapOf<AppSource, List<MarketAppInfo>>()
            // 没有缓存时才把中间结果推给 UI；有缓存时等全部跑完一次性替换（和下拉刷新一致）
            fun publish(list: List<MarketAppInfo>) {
                if (keepPrevious) return
                _raw.update { current ->
                    current.copy(
                        all = dropAlreadyInstalled(preserveKnownDeltaSizes(current.all, orderByPriority(list))),
                        appListPermissionRequired = false,
                    )
                }
            }
            try {
                // 多选：只勾了一路时走单源分支（保留原有的单源提示/对账行为），
                // 勾了多路就走并发合并分支
                val selectedSource = prefs.updateSources.value.ifEmpty {
                    AppSource.UpdateSourceOptions.toSet()
                }.singleOrNull()
                var latestLiveUpdates: List<MarketAppInfo>? = null
                if (selectedSource == null) {
                    // 全部来源：并发检查，单源失败不拖垮整体，同一应用取最高版本号展示
                    val pendingSources = remainingCheckSources.toList()
                    var succeededSources = 0
                    coroutineScope {
                        pendingSources.forEach { source ->
                            launch {
                                // 「完成」和「超时」必须分开记：collect 正常结束但结果为空，
                                // 或者来源自己抛异常，都不该显示成「检查超时」。
                                var completed = false
                                var cancelled = false
                                try {
                                    // 卡住的来源（lsposed / GitHub / GitLab 等自建源常见）不能拖住整体：
                                    // 超时即视为该源失败，进度条推进，最后统一提示哪几个源跳过了。
                                    // 关键：整段检查放到 Default 线程。
                                    // viewModelScope 默认跑在 Main，一旦用户进设置页（大量包管理查询/重组），
                                    // 主线程被占满，检查的续体迟迟拿不到线程，wall-clock 超时就会先到 —— 表现为
                                    // 「检查更新时打开设置就提示检查超时」。放到 Default 后与 UI 线程解耦。
                                    withContext(Dispatchers.Default) {
                                    withTimeoutOrNull(checkTimeoutFor(source)) {
                                    sources.checkUpdatesFlow(source).collect { updates ->
                                        perSourceResults[source] = updates
                                        latestPerSourceResults = perSourceResults.toMap()
                                        val merged = mergeByPriority(
                                            perSourceResults.values.flatten(),
                                            prefs.updateSourcePriority.value,
                                        )
                                        latestLiveUpdates = merged
                                        publish(merged)
                                    }
                                    completed = true
                                    succeededSources++
                                    }
                                    }
                                } catch (e: CancellationException) {
                                    cancelled = true
                                    throw e
                                } catch (e: InstalledPackagesUnavailableException) {
                                    throw e
                                } catch (e: Throwable) {
                                    // 单个来源检查失败（网络/协议）不阻断其他来源
                                    com.app.market.domain.diagnostic.AppLog.log(
                                        "UpdateCheck[$source] failed: ${e::class.simpleName}: ${e.message}",
                                    )
                                } finally {
                                    if (cancelled) {
                                        // 切后台被打断：保留在待检查列表，回前台续跑，不算超时
                                    } else {
                                        synchronized(refreshedSources) {
                                            remainingCheckSources.remove(source)
                                            // 只有完整跑完的来源才允许覆盖旧条目
                                            if (completed) refreshedSources.add(source)
                                        }
                                        // 只有真正没跑完（超时）才计入「跳过」，空结果不算
                                        if (!completed && perSourceResults[source] == null) {
                                            synchronized(timedOut) { timedOut.add(source) }
                                        }
                                    }
                                    _raw.update { current ->
                                        current.copy(checkDone = (current.checkDone + 1).coerceAtMost(current.checkTotal))
                                    }
                                }
                            }
                        }
                    }
                    if (succeededSources == 0) {
                        throw IllegalStateException("All update sources failed")
                    }
                } else {
                    var completed = false
                    var cancelled = false
                    try {
                        withContext(Dispatchers.Default) {
                        withTimeoutOrNull(checkTimeoutFor(selectedSource)) {
                        sources.checkUpdatesFlow(selectedSource).collect { updates ->
                            latestLiveUpdates = updates
                            publish(updates)
                        }
                        completed = true
                        }
                        }
                    } catch (e: CancellationException) {
                        cancelled = true
                        throw e
                    } finally {
                        if (cancelled) {
                            // 切后台被打断：保留待检查，回前台续跑
                        } else {
                            remainingCheckSources.remove(selectedSource)
                            if (completed) refreshedSources.add(selectedSource)
                            if (!completed && latestLiveUpdates == null) {
                                synchronized(timedOut) { timedOut.add(selectedSource) }
                            }
                            _raw.update { current ->
                                current.copy(checkDone = (current.checkDone + 1).coerceAtMost(current.checkTotal))
                            }
                        }
                    }
                }
                checkRoundCompleted = remainingCheckSources.isEmpty()
                // 按来源对齐缓存：本轮「完整拉到结果」的来源，以这次结果为准 ——
                // 缓存里属于这些来源、但这次结果里没有的应用，一律移除（来源撤下的更新不再残留）。
                // 失败 / 超时 / 本轮没跑到的来源，旧条目原样保留，不会被空结果冲掉。
                val freshFromRefreshed = (latestLiveUpdates ?: emptyList())
                    .filter { it.source in refreshedSources }
                val keptFromBefore = listBeforeCheck.filter { it.source !in refreshedSources }
                val orderedUpdates = if (refreshedSources.isEmpty()) {
                    orderByPriority(listBeforeCheck)
                } else {
                    orderByPriority(
                        mergeByPriority(
                            freshFromRefreshed + keptFromBefore,
                            prefs.updateSourcePriority.value,
                        ),
                    )
                }
                // 有来源成功时，即使它这次一条更新都没有，也照此覆盖（= 清掉该来源的失效缓存）；
                // 只有「一个来源都没跑成」时才沿用检查前的列表。
                val finalList = if (refreshedSources.isEmpty() && listBeforeCheck.isNotEmpty()) {
                    listBeforeCheck
                } else {
                    orderedUpdates
                }
                _raw.update {
                    it.copy(
                        loading = false,
                        checkFailed = false,
                        appListPermissionRequired = false,
                        all = finalList,
                    )
                }
                // 结果落盘：下次进入应用直接展示这份结果（见 startInitialCheck）。
                // 有来源成功就写（空列表也写，等于把被来源撤下的旧缓存清掉）；
                // 全失败时既不覆盖列表也不覆盖缓存。
                if (finalList.isNotEmpty() || refreshedSources.isNotEmpty()) {
                    runCatchingCancellable { prefs.saveCachedUpdates(finalList) }
                }
                // 有来源因超时被跳过：提示用户（不阻断其他来源的检查结果）
                if (timedOut.isNotEmpty()) {
                    _skippedSources.value = timedOut.distinct()
                }
            } catch (_: InstalledPackagesUnavailableException) {
                _raw.update { it.copy(loading = false, checkFailed = false, appListPermissionRequired = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 整体失败：保留 listBeforeCheck（缓存）不动，只标记失败
                _raw.update { it.copy(loading = false, checkFailed = true, appListPermissionRequired = false) }
            }
        }
    }

    fun download(app: MarketAppInfo) {
        if (!pendingDownloads.add(app.packageName)) return
        viewModelScope.launch {
            try {
                if (app.appId == SELF_UPDATE_APP_ID) {
                    // 自更新：走与普通应用完全相同的下载/安装管道（进度、速度、取消、安装提示共享）
                    val update = latestSelfUpdate ?: return@launch
                    downloads.start(
                        DownloadMeta(
                            appId = SELF_UPDATE_APP_ID,
                            packageName = app.packageName,
                            displayName = app.displayName,
                            versionName = app.versionName,
                            versionCode = app.versionCode,
                            url = update.apkUrl,
                            size = app.apkSize,
                            changeLog = app.changeLog,
                            requestHeaders = selfUpdateRepository.authHeaders,
                            source = AppSource.GITLAB,
                        ),
                    )
                    return@launch
                }
                runCatchingCancellable {
                    sources.downloadUpdateMeta(app.source, app)
                }
                    .onSuccess { downloads.start(it) }
                    .onFailure { uiPlatform.showToast(it.message ?: "Download failed") }
            } finally {
                pendingDownloads.remove(app.packageName)
            }
        }
    }

    fun downloadAll(apps: List<MarketAppInfo>) {
        apps.forEach(::download)
    }
    /** 全部来源合并：同一应用多个来源都有更新时，优先级列表里排更前的来源胜出展示；
     *  未列入优先级的来源排在已列入的之后（保持 [AppSource.UpdateSourceOptions] 默认顺序），
     *  同来源/同序时才回退版本比较。 */
    private fun mergeByPriority(apps: List<MarketAppInfo>, priority: List<AppSource>): List<MarketAppInfo> {
        fun rank(source: AppSource): Int {
            val index = priority.indexOf(source)
            if (index >= 0) return index
            val default = AppSource.UpdateSourceOptions.indexOf(source)
            return priority.size + if (default >= 0) default else AppSource.UpdateSourceOptions.size
        }
        return apps.groupBy(MarketAppInfo::packageName)
            .mapNotNull { (_, group) ->
                group.minWithOrNull(Comparator { a, b ->
                    val byRank = rank(a.source).compareTo(rank(b.source))
                    if (byRank != 0) return@Comparator byRank
                    val byName = compareVersionNames(a.versionName, b.versionName)
                    if (byName != null && byName != 0) -byName else b.versionCode.compareTo(a.versionCode)
                })
            }
    }

    /** 来源在「更新来源优先级」里的名次：越小越靠前；不在自定义优先级里的排在后面。 */
    private fun sourceRank(source: AppSource): Int {
        val priority = prefs.updateSourcePriority.value
        val index = priority.indexOf(source)
        if (index >= 0) return index
        val default = AppSource.UpdateSourceOptions.indexOf(source)
        return priority.size + if (default >= 0) default else AppSource.UpdateSourceOptions.size
    }

    /**
     * 按「更新来源优先级」实时排序。
     *
     * 每个来源的检查结果一到就对整张表重排，而不是等所有来源跑完再排一次 ——
     * 后者会让列表在检查过程中一直是乱的，最后又整体跳一下。
     */
    private fun orderByPriority(apps: List<MarketAppInfo>): List<MarketAppInfo> =
        apps.sortedWith(
            compareBy<MarketAppInfo> { sourceRank(it.source) }
                .thenBy { it.displayName.lowercase() }
                .thenBy { it.packageName },
        )

    /** 版本名数字化分段：「2.1.13.5」→ [2,1,13,5]；无法解析返回 null。 */
    private fun versionNameSegments(name: String): List<Long>? {
        val runs = Regex("\\d+").findAll(name).toList()
        if (runs.isEmpty()) return null
        return runs.map { it.value.toLongOrNull() ?: return null }
    }

    /** 比较版本名：负=lhs 更旧，0=相当，正=lhs 更新；任一无法解析返回 null（调用方保守放行）。 */
    private fun compareVersionNames(lhs: String, rhs: String): Int? {
        val a = versionNameSegments(lhs) ?: return null
        val b = versionNameSegments(rhs) ?: return null
        val len = maxOf(a.size, b.size)
        for (i in 0 until len) {
            val diff = a.getOrElse(i) { 0L }.compareTo(b.getOrElse(i) { 0L })
            if (diff != 0) return diff
        }
        return 0
    }

    /** 关于页「检查更新」/启动自检：检查 AppMarket 自身新版本，命中后作为普通更新行并入更新列表。 */
    fun checkSelfUpdate(userInitiated: Boolean) {
        if (_selfUpdateState.value.checking) return
        viewModelScope.launch {
            _selfUpdateState.update { it.copy(checking = true) }
            val latest = try {
                selfUpdateRepository.latestRelease()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _selfUpdateState.update { it.copy(checking = false) }
                if (userInitiated) uiPlatform.showToast(getString(Res.string.check_update_failed))
                return@launch
            }
            val update = latest?.takeIf { isSelfUpdateNewer(it) }
            latestSelfUpdate = update
            _selfUpdateState.update { it.copy(checking = false, update = update) }
            _raw.update { it.copy(selfUpdate = update?.toMarketApp()) }
            when {
                update != null && userInitiated ->
                    uiPlatform.showToast("${getString(Res.string.update_available_title)} ${update.tag}")
                update == null && userInitiated ->
                    uiPlatform.showToast(getString(Res.string.check_update_current))
            }
        }
    }

    /** 自身新版本折算为普通更新行，随其余更新共同展示/下载/安装。 */
    private fun SelfUpdate.toMarketApp(): MarketAppInfo = MarketAppInfo(
        appId = SELF_UPDATE_APP_ID,
        packageName = packages.selfPackageName,
        displayName = "AppMarket",
        publisherName = "",
        versionName = tag.removePrefix("v"),
        versionCode = versionCode,
        icon = "",
        apkSize = size,
        ratingScore = 0.0,
        changeLog = notes,
        installedVersionName = VersionInfo.VERSION_NAME,
        installedVersionCode = VersionInfo.VERSION_CODE.toLong(),
        // 自更新来自 GitLab Release，不填会被 MarketAppInfo 的默认值当成「小米」
        source = AppSource.GITLAB,
    )

    private fun isSelfUpdateNewer(update: SelfUpdate): Boolean {
        // 版本名、versionCode 任意一个更高就推送：
        // - 版本名牌（tag）更高 → 正常的新版本
        // - 版本名不变或更低、但代号更高 → 补丁式发版（如 v2.4.5fix 这种 tag）
        val codeNewer = update.versionCode > 0L && update.versionCode > VersionInfo.VERSION_CODE
        val remote = parseSelfUpdateTag(update.tag)
        // 用本机实际版本做基准，避免发版时漏改常量导致同版本反复推送
        val current = parseSelfUpdateTag(VersionInfo.VERSION_NAME)
        val nameNewer = if (remote != null && current != null) {
            var result = false
            for (index in remote.indices) {
                if (remote[index] != current[index]) {
                    result = remote[index] > current[index]
                    break
                }
            }
            result
        } else {
            false
        }
        return codeNewer || nameNewer
    }

    private fun parseSelfUpdateTag(tag: String): List<Int>? {
        val body = tag.trim().removePrefix("v")
        // 容忍后缀字母（如 v2.4.5fix）：每段只取开头的数字
        val numbers = body.substringBefore('-').split('.').map { part ->
            LeadingDigitsRegex.find(part)?.value?.toIntOrNull() ?: return null
        }
        if (numbers.size != 3) return null
        val suffix = body.substringAfter('-', "").removePrefix("mt").toIntOrNull() ?: 0
        return numbers + suffix
    }

    fun installDownloaded(packageName: String) = downloads.install(packageName)
    fun cancelDownload(packageName: String) = downloads.cancel(packageName)

    fun ignoreOnce(app: MarketAppInfo) = persist { prefs.ignoreOnce(app) }
    fun ignorePermanently(app: MarketAppInfo) = persist { prefs.ignorePermanently(app) }

    // ===== 更新列表多选模式（长按进入） =====
    private val _multiSelect = MutableStateFlow(false)
    val multiSelect: StateFlow<Boolean> = _multiSelect.asStateFlow()
    private val _multiSelected = MutableStateFlow<Set<String>>(emptySet())
    val multiSelected: StateFlow<Set<String>> = _multiSelected.asStateFlow()

    fun enterMultiSelect(packageName: String) {
        _multiSelect.value = true
        _multiSelected.value = setOf(packageName)
    }

    fun toggleMultiSelected(packageName: String) {
        _multiSelected.value = _multiSelected.value
            .let { current -> if (packageName in current) current - packageName else current + packageName }
    }

    /** 全选/取消全选切换。 */
    fun toggleMultiSelectAll() {
        val all = uiState.value.updates.map { it.packageName }
        _multiSelected.value = if (all.isNotEmpty() && _multiSelected.value.containsAll(all)) {
            emptySet()
        } else {
            all.toSet()
        }
    }

    fun exitMultiSelect() {
        _multiSelect.value = false
        _multiSelected.value = emptySet()
    }

    fun ignoreSelectedOnce() {
        uiState.value.updates.filter { it.packageName in _multiSelected.value }.forEach { ignoreOnce(it) }
        exitMultiSelect()
    }

    fun ignoreSelectedPermanent() {
        uiState.value.updates.filter { it.packageName in _multiSelected.value }.forEach { ignorePermanently(it) }
        exitMultiSelect()
    }

    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun derive(
        raw: RawUpdates,
        showSystem: Boolean,
        removeAds: Boolean,
        filterGames: Boolean,
        filterReservations: Boolean,
        permanent: List<IgnoredUpdate>,
        once: List<IgnoredUpdate>,
    ): UpdatesUiState {
        val visible = (listOfNotNull(raw.selfUpdate) + raw.all)
            .filter { showSystem || !it.isSystemApp }
            .filterNot { app ->
                // 市场脏数据防护：版本名低于本机已装的「更新」直接剔除（versionCode 跨源不可比，
                // 会出现 code 更大但版本名更旧的假更新，如 2.1.13.5 → 2.1.0）
                val installedName = app.installedVersionName
                val nameOlder = installedName.isNotBlank() &&
                        (compareVersionNames(app.versionName, installedName) ?: 0) < 0
                // 同版本名但已装版本号 ≥ 目标版本号：同样剔除（如智能助理 25.41.85.00-1015，已装 ≥ 商店版）
                val codeNotNewer = app.installedVersionCode > 0L &&
                        app.versionCode > 0L &&
                        app.installedVersionCode >= app.versionCode
                nameOlder || codeNotNewer
            }
            .filterNot { app ->
                permanent.any { it.packageName == app.packageName } ||
                        once.any { it.packageName == app.packageName && it.versionCode == app.versionCode }
            }
        return UpdatesUiState(
            loading = raw.loading,
            checkFailed = raw.checkFailed,
            appListPermissionRequired = raw.appListPermissionRequired,
            checkDone = raw.checkDone,
            checkTotal = raw.checkTotal,
            showSystemUpdates = showSystem,
            removeSearchAds = removeAds,
            filterQuickGames = filterGames,
            filterReservationApps = filterReservations,
            updates = visible,
        )
    }
}

internal fun preserveKnownDeltaSizes(
    previous: List<MarketAppInfo>,
    incoming: List<MarketAppInfo>,
): List<MarketAppInfo> {
    if (incoming.isEmpty()) return incoming
    val sourceSafeIncoming = incoming.map { app ->
        // 三星商店的 deltaSize 语义不可信，展示阶段强制清零
        if (!app.source.capabilities.reportsDeltaSize && app.deltaSize != 0L) {
            app.copy(deltaSize = 0L)
        } else {
            app
        }
    }
    if (previous.isEmpty()) return sourceSafeIncoming
    val previousByPackage = previous.associateBy(MarketAppInfo::packageName)
    return sourceSafeIncoming.map { current ->
        val cached = previousByPackage[current.packageName] ?: return@map current
        val cachedDeltaSize = cached.deltaSize.takeIf { deltaSize ->
            current.deltaSize <= 0L &&
                    deltaSize > 0L &&
                    deltaSize < current.apkSize &&
                    cached.source == current.source &&
                    cached.versionCode == current.versionCode &&
                    cached.installedVersionCode == current.installedVersionCode &&
                    cached.apkSize == current.apkSize &&
                    cached.installedBaseApkPath == current.installedBaseApkPath &&
                    cached.installedSplits == current.installedSplits
        }
        if (cachedDeltaSize == null) current else current.copy(deltaSize = cachedDeltaSize)
    }
}