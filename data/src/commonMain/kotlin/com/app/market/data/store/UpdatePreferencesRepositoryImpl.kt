package com.app.market.data.store

import com.app.market.data.local.PreferenceChanges
import com.app.market.data.local.PreferencesDataSource
import com.app.market.data.local.StringPreferenceKey
import com.app.market.data.local.preferences.UpdatePreferenceKeys
import com.app.market.data.platform.debugLog
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.CommentSourceSetting
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.preference.HomePage
import com.app.market.domain.model.update.IgnoredUpdate
import com.app.market.domain.repository.UpdatePreferencesRepository
import com.app.market.domain.repository.DEFAULT_CHECK_TIMEOUT_SECONDS
import com.app.market.domain.repository.DEFAULT_LOG_MAX_BYTES
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put


internal class UpdatePreferencesRepositoryImpl(
    private val preferences: PreferencesDataSource,
    private val scope: CoroutineScope,
    private val json: Json
) : UpdatePreferencesRepository {

    private val _pageCacheEnabled = MutableStateFlow(true)
    override val pageCacheEnabled: StateFlow<Boolean> = _pageCacheEnabled.asStateFlow()
    override suspend fun setPageCacheEnabled(value: Boolean) {
        preferences.put(UpdatePreferenceKeys.PageCacheEnabled, value)
        _pageCacheEnabled.value = value
    }
    override suspend fun clearPageCaches() {
        preferences.clearNamespace("page_cache")
        preferences.clearNamespace("translation_cache")
        preferences.remove(UpdatePreferenceKeys.CachedUpdates)
    }
    private val _initialized = MutableStateFlow(false)
    override val initialized: StateFlow<Boolean> = _initialized.asStateFlow()
    private val ignoreMutex = Mutex()
    private val _showSystemUpdates = MutableStateFlow(true)
    override val showSystemUpdates: StateFlow<Boolean> = _showSystemUpdates.asStateFlow()
    private val _removeSearchAds = MutableStateFlow(false)
    override val removeSearchAds: StateFlow<Boolean> = _removeSearchAds.asStateFlow()
    private val _filterQuickGames = MutableStateFlow(false)
    override val filterQuickGames: StateFlow<Boolean> = _filterQuickGames.asStateFlow()
    private val _filterReservationApps = MutableStateFlow(false)
    override val filterReservationApps: StateFlow<Boolean> = _filterReservationApps.asStateFlow()
    private val _detectClipboardLinks = MutableStateFlow(UpdatePreferenceKeys.DetectClipboardLinks.default)
    override val detectClipboardLinks: StateFlow<Boolean> = _detectClipboardLinks.asStateFlow()
    private val _exactSearch = MutableStateFlow(UpdatePreferenceKeys.ExactSearch.default)
    override val exactSearch: StateFlow<Boolean> = _exactSearch.asStateFlow()
    private val _includePrereleaseUpdates =
        MutableStateFlow(UpdatePreferenceKeys.IncludePrereleaseUpdates.default)
    override val includePrereleaseUpdates: StateFlow<Boolean> = _includePrereleaseUpdates.asStateFlow()
    private val _logEnabled = MutableStateFlow(UpdatePreferenceKeys.LogEnabled.default)
    override val logEnabled: StateFlow<Boolean> = _logEnabled.asStateFlow()
    private val _logMaxSize = MutableStateFlow(DEFAULT_LOG_MAX_BYTES)
    override val logMaxSize: StateFlow<Int> = _logMaxSize.asStateFlow()
    private val _autoDeletePackage = MutableStateFlow(UpdatePreferenceKeys.AutoDeletePackage.default)
    override val autoDeletePackage: StateFlow<Boolean> = _autoDeletePackage.asStateFlow()
    private val _googleDeviceSource = MutableStateFlow(false)
    override val googleDeviceSource: StateFlow<Boolean> = _googleDeviceSource.asStateFlow()
    private val _googleRegion = MutableStateFlow("auto")
    override val googleRegion: StateFlow<String> = _googleRegion.asStateFlow()
    private val _googleDeviceOverrides = MutableStateFlow<Map<String, String>>(emptyMap())
    override val googleDeviceOverrides: StateFlow<Map<String, String>> = _googleDeviceOverrides.asStateFlow()
    private val _googleDeviceTemplates = MutableStateFlow<List<Pair<String, Map<String, String>>>>(emptyList())
    override val googleDeviceTemplates: StateFlow<List<Pair<String, Map<String, String>>>> =
        _googleDeviceTemplates.asStateFlow()
    private val _stripAppNameSubtitle = MutableStateFlow(false)
    override val stripAppNameSubtitle: StateFlow<Boolean> = _stripAppNameSubtitle.asStateFlow()
    private val _googlePlayEngine = MutableStateFlow("appmarket")
    override val googlePlayEngine: StateFlow<String> = _googlePlayEngine.asStateFlow()

    private val _homePage = MutableStateFlow(HomePage.TODAY)
    override val homePage: StateFlow<HomePage> = _homePage.asStateFlow()
    private val _searchSources = MutableStateFlow(AppSource.Default)
    override val searchSources: StateFlow<Set<AppSource>> = _searchSources.asStateFlow()
    private val _todaySource = MutableStateFlow(AppSource.DefaultTodaySource)
    override val todaySource: StateFlow<AppSource> = _todaySource.asStateFlow()
    private val _commentSource = MutableStateFlow(AppSource.DefaultCommentSource)
    override val commentSource: StateFlow<CommentSourceSetting> = _commentSource.asStateFlow()
    private val _categorySource = MutableStateFlow(AppSource.DefaultCategorySource)
    override val categorySource: StateFlow<AppSource> = _categorySource.asStateFlow()
    // 应用 / 游戏来源已合并成一个设置，两者都指向同一个 categorySource
    override val appCategorySource: StateFlow<AppSource> = categorySource
    override val gameCategorySource: StateFlow<AppSource> = categorySource
    private val _updateSources = MutableStateFlow(AppSource.UpdateSourceOptions.toSet())
    override val updateSources: StateFlow<Set<AppSource>> = _updateSources.asStateFlow()
    private val _showUpdateSource = MutableStateFlow(UpdatePreferenceKeys.ShowUpdateSource.default)
    override val showUpdateSource: StateFlow<Boolean> = _showUpdateSource.asStateFlow()
    private val _receivePrerelease = MutableStateFlow(UpdatePreferenceKeys.ReceivePrerelease.default)
    override val receivePrerelease: StateFlow<Boolean> = _receivePrerelease.asStateFlow()
    private val _checkTimeoutSeconds = MutableStateFlow(DEFAULT_CHECK_TIMEOUT_SECONDS)
    override val checkTimeoutSeconds: StateFlow<Int> = _checkTimeoutSeconds.asStateFlow()
    private val _updateSourcePriority = MutableStateFlow(emptyList<AppSource>())
    override val updateSourcePriority: StateFlow<List<AppSource>> = _updateSourcePriority.asStateFlow()
    private val _manualUpdateRecent = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    override val manualUpdateRecent: StateFlow<List<Pair<String, String>>> = _manualUpdateRecent.asStateFlow()
    private val _permanentIgnores = MutableStateFlow<List<IgnoredUpdate>>(emptyList())
    override val permanentIgnores: StateFlow<List<IgnoredUpdate>> = _permanentIgnores.asStateFlow()
    private val _onceIgnores = MutableStateFlow<List<IgnoredUpdate>>(emptyList())
    override val onceIgnores: StateFlow<List<IgnoredUpdate>> = _onceIgnores.asStateFlow()

    init {
        // observe 的首个发射即当前值，无需再串行读一遍
        val arrivals = listOf(
            observe("pageCacheEnabled", preferences.observe(UpdatePreferenceKeys.PageCacheEnabled)) { _pageCacheEnabled.value = it },
            observe("showSystemUpdates", preferences.observe(UpdatePreferenceKeys.ShowSystemUpdates)) { _showSystemUpdates.value = it },
            observe("removeSearchAds", preferences.observe(UpdatePreferenceKeys.RemoveSearchAds)) { _removeSearchAds.value = it },
            observe("filterQuickGames", preferences.observe(UpdatePreferenceKeys.FilterQuickGames)) { _filterQuickGames.value = it },
            observe(
                "filterReservationApps",
                preferences.observe(UpdatePreferenceKeys.FilterReservationApps)
            ) { _filterReservationApps.value = it },
            observe("detectClipboardLinks", preferences.observe(UpdatePreferenceKeys.DetectClipboardLinks)) {
                _detectClipboardLinks.value = it
            },
            observe("exactSearch", preferences.observe(UpdatePreferenceKeys.ExactSearch)) {
                _exactSearch.value = it
            },
            observe(
                "includePrereleaseUpdates",
                preferences.observe(UpdatePreferenceKeys.IncludePrereleaseUpdates),
            ) {
                _includePrereleaseUpdates.value = it
            },
            observe("logEnabled", preferences.observe(UpdatePreferenceKeys.LogEnabled)) {
                _logEnabled.value = it
            },
            observe("logMaxSize", preferences.observe(UpdatePreferenceKeys.LogMaxSize)) {
                _logMaxSize.value = it?.toIntOrNull()?.takeIf { size -> size >= 0 } ?: DEFAULT_LOG_MAX_BYTES
            },
            observe("autoDeletePackage", preferences.observe(UpdatePreferenceKeys.AutoDeletePackage)) {
                _autoDeletePackage.value = it
            },
            observe("stripAppNameSubtitle", preferences.observe(UpdatePreferenceKeys.StripAppNameSubtitle)) {
                _stripAppNameSubtitle.value = it
            },
            observe("googlePlayEngine", preferences.observe(UpdatePreferenceKeys.GooglePlayEngine)) {
                _googlePlayEngine.value = it?.takeIf { v -> v.isNotBlank() } ?: "appmarket"
            },
            observe("googleDeviceSource", preferences.observe(UpdatePreferenceKeys.GoogleDeviceSource)) {
                _googleDeviceSource.value = it
                // 设置项真正参与设备指纹构造：选「从设备获取」时必须放行本机 Build 覆盖，
                // 之前这里恒置 false，导致选了本机还是预置信息
                com.app.market.data.remote.google.GoogleDeviceSourceFromSystem = it
                com.app.market.data.remote.google.GoogleDevicePropsPresetFallback = false
            },
            observe("googleRegion", preferences.observe(UpdatePreferenceKeys.GoogleRegion)) {
                val region = it?.lowercase().orEmpty().ifBlank { "auto" }
                val changed = com.app.market.data.remote.google.GoogleRegion != region
                _googleRegion.value = region
                com.app.market.data.remote.google.GoogleRegion = region
                // 地区变了必须让匿名 token 失效重签：token 绑定的是签发时的国家，
                // 只改请求参数不改身份，Play 仍按旧国家返回。
                if (changed) com.app.market.data.remote.google.GoogleRegionGeneration++
            },
            observe("googleDeviceTemplates", preferences.observe(UpdatePreferenceKeys.GoogleDeviceTemplates)) {
                _googleDeviceTemplates.value = parseGoogleDeviceTemplates(it.orEmpty())
            },
            observe("googleDeviceOverrides", preferences.observe(UpdatePreferenceKeys.GoogleDeviceOverrides)) {
                val parsed = parseGoogleDeviceOverrides(it.orEmpty())
                _googleDeviceOverrides.value = parsed
                com.app.market.data.remote.google.GoogleDeviceUserOverrides = parsed
            },
            observe("homePage", preferences.observe(UpdatePreferenceKeys.HomePage)) { _homePage.value = HomePage.fromToken(it) },
            observe("searchSources", preferences.observe(UpdatePreferenceKeys.SearchSources)) {
                _searchSources.value = AppSource.parse(it)
            },
            observe("todaySource", preferences.observe(UpdatePreferenceKeys.TodaySource)) {
                _todaySource.value = AppSource.fromToken(it).todayOrDefault()
            },
            observe("commentSource", preferences.observe(UpdatePreferenceKeys.CommentSource)) {
                // "off" = 关闭，"follow" = 跟随应用来源（旧版未设置也按跟随处理）
                _commentSource.value = CommentSourceSetting.parse(it)
            },
            observe("categorySource", preferences.observe(UpdatePreferenceKeys.CategorySource)) {
                _categorySource.value = AppSource.fromToken(it).categoryOrDefault()
            },
            observe("updateSources", preferences.observe(UpdatePreferenceKeys.UpdateSource)) {
                // 多选：逗号分隔的 token 列表；"all"/空值 = 全选（兼容旧版本写的单值）
                val parsed = it?.split(',')
                    ?.mapNotNull { token -> AppSource.fromToken(token.trim()) }
                    ?.toSet()
                    .orEmpty()
                _updateSources.value = if (it == null || it.isBlank() || it == "all" || parsed.isEmpty()) {
                    AppSource.UpdateSourceOptions.toSet()
                } else {
                    parsed
                }
            },
            observe("updateSourcePriority", preferences.observe(UpdatePreferenceKeys.UpdateSourcePriority)) {
                // 优先级是有序列表：按逗号顺序逐 token 解析。AppSource.parse 是
                // 「多选取第一个」语义且返回 Set，会丢顺序，不能用在这里。
                _updateSourcePriority.value = it?.split(',')
                    ?.mapNotNull { token -> AppSource.fromToken(token.trim()) }
                    ?: emptyList()
            },
            observe("showUpdateSource", preferences.observe(UpdatePreferenceKeys.ShowUpdateSource)) {
                _showUpdateSource.value = it
            },
            observe("checkTimeoutSeconds", preferences.observe(UpdatePreferenceKeys.CheckTimeoutSeconds)) {
                // 空/非法/<=0 一律回退默认值，避免设置坏值把检查卡死
                _checkTimeoutSeconds.value = it?.toIntOrNull()?.takeIf { sec -> sec > 0 } ?: DEFAULT_CHECK_TIMEOUT_SECONDS
            },
            observe("receivePrerelease", preferences.observe(UpdatePreferenceKeys.ReceivePrerelease)) {
                _receivePrerelease.value = it
            },
            observe("permanentIgnores", preferences.observe(UpdatePreferenceKeys.PermanentIgnores)) {
                _permanentIgnores.value = parseIgnored(it)
            },
            observe("onceIgnores", preferences.observe(UpdatePreferenceKeys.OnceIgnores)) { _onceIgnores.value = parseIgnored(it) },
            observe("manualUpdateRecent", preferences.observe(UpdatePreferenceKeys.ManualUpdateRecents)) {
                _manualUpdateRecent.value = parseManualUpdateRecent(it.orEmpty())
            },
        )
        scope.launch {
            arrivals.forEach { it.await() }
            _initialized.value = true
            migrateSplitCategorySources()
        }
        // 旧版的游戏 / 应用分区来源在 1255116 被合并为单一 category_source，这里做一次性迁移。
        scope.launch { migrateLegacyCategorySources() }
    }

    /**
     * 把旧的合并键 `category_source` 一次性铺到拆开后的两个键上。
     * 只在新键尚未写入时执行，因此用户此后分别设置应用/游戏来源不会被覆盖。
     */
    private suspend fun migrateSplitCategorySources() {
        runCatching {
            val legacy = AppSource.fromToken(preferences.read(UpdatePreferenceKeys.CategorySource))
                ?: return@runCatching
            val token = legacy.token
            val changes = buildMap {
                if (preferences.read(UpdatePreferenceKeys.AppCategorySource).isNullOrBlank()) put(UpdatePreferenceKeys.AppCategorySource, token)
                if (preferences.read(UpdatePreferenceKeys.GameCategorySource).isNullOrBlank()) put(UpdatePreferenceKeys.GameCategorySource, token)
            }
            if (changes.isNotEmpty()) {
                preferences.update(
                    UpdatePreferenceKeys.CategorySource.namespace,
                    PreferenceChanges(strings = changes),
                )
            }
        }
    }

    /**
     * 把旧版的 `game_sources` / `app_sources` 迁移到 `category_source`。
     *
     * 旧版两个键各自独立控制游戏页与应用页；只有它们指向同一个受支持来源时才能无损合并，
     * 其余情况（缺失、取值不同、来源不受分区支持）保持新版默认值，并保留旧键不做猜测。
     * 迁移只在 `category_source` 尚未写入时执行，写入与清理旧键在同一次原子更新里完成，
     * 因此重复启动不会重复覆盖。
     */
    private suspend fun migrateLegacyCategorySources() {
        runCatching {
            val existing = preferences.read(UpdatePreferenceKeys.CategorySource)
            if (!existing.isNullOrBlank()) return@runCatching
            val gameRaw = preferences.read(UpdatePreferenceKeys.LegacyGameSources)
            val appRaw = preferences.read(UpdatePreferenceKeys.LegacyAppSources)
            if (gameRaw.isNullOrBlank() || appRaw.isNullOrBlank()) return@runCatching
            val game = AppSource.parse(gameRaw).singleOrNull()
            val app = AppSource.parse(appRaw).singleOrNull()
            if (game == null || game != app || game !in AppSource.CategorySources) return@runCatching
            preferences.update(
                UpdatePreferenceKeys.CategorySource.namespace,
                PreferenceChanges(
                    strings = mapOf(
                        UpdatePreferenceKeys.CategorySource to game.token,
                        UpdatePreferenceKeys.LegacyGameSources to null,
                        UpdatePreferenceKeys.LegacyAppSources to null,
                    ),
                ),
            )
        }.onFailure { error ->
            if (error is CancellationException) throw error
            debugLog("UpdatePreferencesRepository") { "legacy category source migration failed: $error" }
        }
    }

    /** 收集 [flow] 到状态，返回一个在首个值到达（或读取失败）时完成的信号。 */
    private fun <T> observe(name: String, flow: Flow<T>, collect: (T) -> Unit): CompletableDeferred<Unit> {
        val firstValue = CompletableDeferred<Unit>()
        scope.launch {
            try {
                flow.collect {
                    collect(it)
                    firstValue.complete(Unit)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                debugLog("UpdatePreferencesRepository") { "observe $name failed: $error" }
            } finally {
                firstValue.complete(Unit)
            }
        }
        return firstValue
    }

    override suspend fun setShowSystemUpdates(value: Boolean) =
        preferences.put(UpdatePreferenceKeys.ShowSystemUpdates, value)

    override suspend fun setRemoveSearchAds(value: Boolean) =
        preferences.put(UpdatePreferenceKeys.RemoveSearchAds, value)

    override suspend fun setFilterQuickGames(value: Boolean) =
        preferences.put(UpdatePreferenceKeys.FilterQuickGames, value)

    override suspend fun setFilterReservationApps(value: Boolean) =
        preferences.put(UpdatePreferenceKeys.FilterReservationApps, value)

    override suspend fun setDetectClipboardLinks(value: Boolean) =
        preferences.put(UpdatePreferenceKeys.DetectClipboardLinks, value)

    override suspend fun setExactSearch(value: Boolean) =
        preferences.put(UpdatePreferenceKeys.ExactSearch, value)
    override suspend fun setIncludePrereleaseUpdates(value: Boolean) =
        preferences.put(UpdatePreferenceKeys.IncludePrereleaseUpdates, value)

    override suspend fun setLogEnabled(value: Boolean) =
        preferences.put(UpdatePreferenceKeys.LogEnabled, value)

    override suspend fun setLogMaxSize(value: Int) =
        preferences.put(UpdatePreferenceKeys.LogMaxSize, value.coerceAtLeast(0).toString())

    override suspend fun setAutoDeletePackage(value: Boolean) =
        preferences.put(UpdatePreferenceKeys.AutoDeletePackage, value)

    override suspend fun lastClipboardLink(): String =
        preferences.read(UpdatePreferenceKeys.LastClipboardLink).orEmpty()

    override suspend fun setLastClipboardLink(value: String) =
        preferences.put(UpdatePreferenceKeys.LastClipboardLink, value)

    override suspend fun setGoogleDeviceOverride(key: String, value: String) {
        val next = _googleDeviceOverrides.value + (key to value)
        _googleDeviceOverrides.value = next
        preferences.put(UpdatePreferenceKeys.GoogleDeviceOverrides, serializeGoogleDeviceOverrides(next))
    }

    override suspend fun removeGoogleDeviceOverride(key: String) {
        val next = _googleDeviceOverrides.value - key
        _googleDeviceOverrides.value = next
        preferences.put(UpdatePreferenceKeys.GoogleDeviceOverrides, serializeGoogleDeviceOverrides(next))
    }

    override suspend fun setGoogleDeviceSource(value: Boolean) =
        preferences.put(UpdatePreferenceKeys.GoogleDeviceSource, value)

    override suspend fun replaceGoogleDeviceOverrides(fields: Map<String, String>) {
        _googleDeviceOverrides.value = fields
        preferences.put(UpdatePreferenceKeys.GoogleDeviceOverrides, serializeGoogleDeviceOverrides(fields))
    }

    override suspend fun saveGoogleDeviceTemplate(name: String, fields: Map<String, String>) {
        val next = _googleDeviceTemplates.value.filterNot { it.first == name } + (name to fields)
        _googleDeviceTemplates.value = next
        preferences.put(UpdatePreferenceKeys.GoogleDeviceTemplates, serializeGoogleDeviceTemplates(next))
    }

    override suspend fun deleteGoogleDeviceTemplate(name: String) {
        val next = _googleDeviceTemplates.value.filterNot { it.first == name }
        _googleDeviceTemplates.value = next
        preferences.put(UpdatePreferenceKeys.GoogleDeviceTemplates, serializeGoogleDeviceTemplates(next))
    }

    override suspend fun setGoogleRegion(value: String) =
        preferences.put(UpdatePreferenceKeys.GoogleRegion, value.lowercase().ifBlank { "auto" })

    override suspend fun setStripAppNameSubtitle(value: Boolean) =
        preferences.put(UpdatePreferenceKeys.StripAppNameSubtitle, value)

    override suspend fun setGooglePlayEngine(value: String) =
        preferences.put(UpdatePreferenceKeys.GooglePlayEngine, value)

    override suspend fun addManualUpdateRecent(packageName: String, versionCode: String) {
        val entry = packageName to versionCode
        val next = (listOf(entry) + _manualUpdateRecent.value.filterNot { it == entry }).take(8)
        _manualUpdateRecent.value = next
        preferences.put(UpdatePreferenceKeys.ManualUpdateRecents, serializeManualUpdateRecent(next))
    }

    override suspend fun clearManualUpdateRecent() {
        _manualUpdateRecent.value = emptyList()
        preferences.put(UpdatePreferenceKeys.ManualUpdateRecents, "")
    }

    private fun serializeManualUpdateRecent(entries: List<Pair<String, String>>): String =
        entries.joinToString("\n") { "${it.first} ${it.second}" }

    private fun parseManualUpdateRecent(raw: String): List<Pair<String, String>> =
        raw.lineSequence().mapNotNull { line ->
            val parts = line.trim().split(' ')
            if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) null
            else parts[0] to parts[1]
        }.toList()

    override suspend fun setHomePage(value: HomePage) =
        preferences.put(UpdatePreferenceKeys.HomePage, value.token)

    override suspend fun setSearchSources(value: Set<AppSource>) =
        preferences.put(UpdatePreferenceKeys.SearchSources, AppSource.serialize(value.ifEmpty { AppSource.Default }))

    override suspend fun setTodaySource(value: AppSource) =
        preferences.put(UpdatePreferenceKeys.TodaySource, value.todayOrDefault().token)

    override suspend fun setCommentSource(value: CommentSourceSetting) =
        preferences.put(UpdatePreferenceKeys.CommentSource, CommentSourceSetting.serialize(value))

    override suspend fun setCategorySource(value: AppSource) =
        preferences.put(UpdatePreferenceKeys.CategorySource, value.categoryOrDefault().token)

    override suspend fun setAppCategorySource(value: AppSource) = setCategorySource(value)

    override suspend fun setGameCategorySource(value: AppSource) = setCategorySource(value)

    override suspend fun setUpdateSources(value: Set<AppSource>) =
        // 全选写 "all"（旧格式，任何版本都能读回全选）；否则写逗号分隔的 token 列表
        preferences.put(
            UpdatePreferenceKeys.UpdateSource,
            if (value.containsAll(AppSource.UpdateSourceOptions)) "all"
            else value.joinToString(",") { it.token },
        )
    override suspend fun setShowUpdateSource(value: Boolean) =
        preferences.put(UpdatePreferenceKeys.ShowUpdateSource, value)

    override suspend fun setReceivePrerelease(value: Boolean) =
        preferences.put(UpdatePreferenceKeys.ReceivePrerelease, value)
    override suspend fun setUpdateSourcePriority(value: List<AppSource>) =
        // 保序写入：优先级次序即列表顺序，不能经 Set（AppSource.serialize）中转
        preferences.put(UpdatePreferenceKeys.UpdateSourcePriority, value.joinToString(",") { it.token })

    override suspend fun setCheckTimeoutSeconds(value: Int) =
        preferences.put(
            UpdatePreferenceKeys.CheckTimeoutSeconds,
            (value.takeIf { it > 0 } ?: DEFAULT_CHECK_TIMEOUT_SECONDS).toString(),
        )

    override fun isIgnored(app: MarketAppInfo): Boolean {
        if (_permanentIgnores.value.any { it.packageName == app.packageName }) return true
        return _onceIgnores.value.any { it.packageName == app.packageName && it.versionCode == app.versionCode }
    }

    override suspend fun ignorePermanently(app: MarketAppInfo) = ignoreMutex.withLock {
        val once = readIgnored(UpdatePreferenceKeys.OnceIgnores).filterNot { it.packageName == app.packageName }
        val permanent = upsert(readIgnored(UpdatePreferenceKeys.PermanentIgnores), app)
        persistIgnores(permanent, once)
    }

    override suspend fun ignoreOnce(app: MarketAppInfo) = ignoreMutex.withLock {
        val permanent = readIgnored(UpdatePreferenceKeys.PermanentIgnores)
        if (permanent.none { it.packageName == app.packageName }) {
            val once = upsert(readIgnored(UpdatePreferenceKeys.OnceIgnores), app)
            persistIgnores(permanent, once)
        }
    }

    override suspend fun removePermanent(packageName: String) = ignoreMutex.withLock {
        val permanent = readIgnored(UpdatePreferenceKeys.PermanentIgnores).filterNot { it.packageName == packageName }
        persistIgnores(permanent, readIgnored(UpdatePreferenceKeys.OnceIgnores))
    }

    override suspend fun removeOnce(packageName: String) = ignoreMutex.withLock {
        val once = readIgnored(UpdatePreferenceKeys.OnceIgnores).filterNot { it.packageName == packageName }
        persistIgnores(readIgnored(UpdatePreferenceKeys.PermanentIgnores), once)
    }

    private suspend fun persistIgnores(permanent: List<IgnoredUpdate>, once: List<IgnoredUpdate>) {
        preferences.update(
            UpdatePreferenceKeys.PermanentIgnores.namespace,
            PreferenceChanges(
                strings = mapOf(
                    UpdatePreferenceKeys.PermanentIgnores to encodeIgnored(permanent),
                    UpdatePreferenceKeys.OnceIgnores to encodeIgnored(once),
                )
            ),
        )
    }

    private suspend fun readIgnored(key: StringPreferenceKey): List<IgnoredUpdate> =
        parseIgnored(preferences.read(key))

    private fun upsert(existing: List<IgnoredUpdate>, app: MarketAppInfo): List<IgnoredUpdate> =
        (existing.filterNot { it.packageName == app.packageName } + IgnoredUpdate(
            appId = app.appId,
            packageName = app.packageName,
            displayName = app.displayName,
            versionName = app.versionName,
            versionCode = app.versionCode,
            icon = app.icon,
            isSystemApp = app.isSystemApp,
            source = app.source,
        )).sortedIgnores()

    private fun List<IgnoredUpdate>.sortedIgnores(): List<IgnoredUpdate> =
        sortedWith(compareBy<IgnoredUpdate> { !it.isSystemApp }.thenBy { it.displayName })

    private fun parseIgnored(value: String?): List<IgnoredUpdate> {
        val raw = value?.takeIf { it.isNotBlank() } ?: return emptyList()
        val arr = runCatching { json.parseToJsonElement(raw) as? JsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val pkg = o.str("packageName")
            if (pkg.isBlank()) return@mapNotNull null
            IgnoredUpdate(
                appId = o.long("appId"),
                packageName = pkg,
                displayName = o.str("displayName", pkg),
                versionName = o.str("versionName"),
                versionCode = o.long("versionCode"),
                icon = o.str("icon"),
                isSystemApp = o.bool("isSystemApp"),
                source = AppSource.fromToken(o.str("source")),
            )
        }
    }

    private fun encodeIgnored(items: List<IgnoredUpdate>): String {
        val sorted = items.sortedWith(compareBy<IgnoredUpdate> { !it.isSystemApp }.thenBy { it.displayName })
        val arr = buildJsonArray {
            sorted.forEach { item ->
                add(
                    buildJsonObject {
                        put("appId", item.appId)
                        put("packageName", item.packageName)
                        put("displayName", item.displayName)
                        put("versionName", item.versionName)
                        put("versionCode", item.versionCode)
                        put("icon", item.icon)
                        put("isSystemApp", item.isSystemApp)
                        item.source?.let { put("source", it.token) }
                    }
                )
            }
        }
        return arr.toString()
    }

    private fun pageCacheKey(key: String) = StringPreferenceKey("page_cache", key)

    override suspend fun readPageCache(key: String): String? =
        if (preferences.read(UpdatePreferenceKeys.PageCacheEnabled)) preferences.read(pageCacheKey(key)) else null
    override suspend fun writePageCache(key: String, value: String) {
        if (preferences.read(UpdatePreferenceKeys.PageCacheEnabled)) preferences.put(pageCacheKey(key), value)
    }

    override suspend fun loadCachedUpdates(): List<MarketAppInfo> = withContext(Dispatchers.Default) {
        if (!preferences.read(UpdatePreferenceKeys.PageCacheEnabled)) return@withContext emptyList()
        val raw = preferences.read(UpdatePreferenceKeys.CachedUpdates)?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
        val arr = runCatching { json.parseToJsonElement(raw) as? JsonArray }.getOrNull() ?: return@withContext emptyList()
        arr.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val pkg = o.str("packageName")
            if (pkg.isBlank()) return@mapNotNull null
            MarketAppInfo(
                appId = o.long("appId"),
                packageName = pkg,
                displayName = o.str("displayName", pkg),
                publisherName = o.str("publisherName"),
                versionName = o.str("versionName"),
                versionCode = o.long("versionCode"),
                icon = o.str("icon"),
                apkSize = o.long("apkSize"),
                deltaSize = o.long("deltaSize"),
                ratingScore = o.double("ratingScore"),
                changeLog = o.str("changeLog"),
                isSystemApp = o.bool("isSystemApp"),
                openLink = o.str("openLink"),
                installedVersionName = o.str("installedVersionName"),
                installedVersionCode = o.long("installedVersionCode"),
                installedOldApkHash = o.str("installedOldApkHash", "0"),
                installedBaseApkPath = o.str("installedBaseApkPath"),
                installedSplits = o.str("installedSplits", "0"),
                type = o.str("type"),
                source = AppSource.entries.firstOrNull { it.token == o.str("source") } ?: AppSource.XIAOMI,
            )
        }
    }

    // 编码整份更新列表（含 changeLog 全文）不能留在调用方的主线程上，与 loadCachedUpdates 对称
    override suspend fun saveCachedUpdates(updates: List<MarketAppInfo>) = withContext(Dispatchers.Default) {
        if (!preferences.read(UpdatePreferenceKeys.PageCacheEnabled)) return@withContext
        val arr = buildJsonArray {
            updates.forEach { app ->
                add(
                    buildJsonObject {
                        put("appId", app.appId)
                        put("packageName", app.packageName)
                        put("displayName", app.displayName)
                        put("publisherName", app.publisherName)
                        put("versionName", app.versionName)
                        put("versionCode", app.versionCode)
                        put("icon", app.icon)
                        put("apkSize", app.apkSize)
                        put("deltaSize", app.deltaSize)
                        put("ratingScore", app.ratingScore)
                        put("changeLog", app.changeLog)
                        put("isSystemApp", app.isSystemApp)
                        put("openLink", app.openLink)
                        put("installedVersionName", app.installedVersionName)
                        put("installedVersionCode", app.installedVersionCode)
                        put("installedOldApkHash", app.installedOldApkHash)
                        put("installedBaseApkPath", app.installedBaseApkPath)
                        put("installedSplits", app.installedSplits)
                        put("type", app.type)
                        put("source", app.source.token)
                    }
                )
            }
        }
        preferences.put(UpdatePreferenceKeys.CachedUpdates, arr.toString())
    }

    // 这三个值已有常驻热 StateFlow；每次搜索/翻页再往磁盘跑一趟纯属浪费
    override suspend fun currentRemoveSearchAds(): Boolean = current(_removeSearchAds)

    override suspend fun currentFilterQuickGames(): Boolean = current(_filterQuickGames)

    override suspend fun currentFilterReservationApps(): Boolean = current(_filterReservationApps)

    private suspend fun current(state: StateFlow<Boolean>): Boolean {
        initialized.first { it }
        return state.value
    }
}

private fun AppSource?.todayOrDefault(): AppSource =
    this?.takeIf { it.capabilities.supportsTodayFeed } ?: AppSource.DefaultTodaySource

private fun AppSource?.categoryOrDefault(): AppSource =
    this?.takeIf { it in AppSource.CategorySources } ?: AppSource.DefaultCategorySource


private fun parseGoogleDeviceOverrides(raw: String): Map<String, String> =
    raw.lineSequence().mapNotNull { line ->
        val idx = line.indexOf('=')
        if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
    }.toMap()

private fun serializeGoogleDeviceOverrides(map: Map<String, String>): String =
    map.entries.joinToString("\n") { "${it.key}=${it.value}" }

private fun parseGoogleDeviceTemplates(raw: String): List<Pair<String, Map<String, String>>> =
    raw.split('\u0004').filter { it.isNotBlank() }.mapNotNull { block ->
        val sep = block.indexOf('\u0003')
        if (sep <= 0) return@mapNotNull null
        val name = block.substring(0, sep)
        val fields = block.substring(sep + 1)
            .split('\u0002')
            .filter { it.isNotEmpty() }
            .mapNotNull { pair ->
                val i = pair.indexOf('\u0001')
                if (i <= 0) null else pair.substring(0, i) to pair.substring(i + 1)
            }
            .toMap()
        name to fields
    }

private fun serializeGoogleDeviceTemplates(list: List<Pair<String, Map<String, String>>>): String =
    list.joinToString("\u0004") { (name, fields) ->
        name + '\u0003' + fields.entries.joinToString("\u0002") { "${it.key}\u0001${it.value}" }
    }