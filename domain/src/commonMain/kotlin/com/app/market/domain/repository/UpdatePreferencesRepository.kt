package com.app.market.domain.repository

import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.CommentSourceSetting
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.preference.HomePage
import com.app.market.domain.model.update.IgnoredUpdate
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Update-screen preferences, ignore lists, and the cached last result. */
interface UpdatePreferencesRepository {
    val initialized: StateFlow<Boolean>
    val pageCacheEnabled: StateFlow<Boolean> get() = MutableStateFlow(true)
    suspend fun setPageCacheEnabled(value: Boolean) = Unit
    suspend fun clearPageCaches() = Unit
    val showSystemUpdates: StateFlow<Boolean>
    val removeSearchAds: StateFlow<Boolean>
    val filterQuickGames: StateFlow<Boolean>
    val filterReservationApps: StateFlow<Boolean>

    /** 进入应用时识别剪贴板中的商店链接并询问是否打开。 */
    val detectClipboardLinks: StateFlow<Boolean>
    /** 精确搜索：只保留标题/包名命中的条目，丢掉商店返回的无关推荐。 */
    val exactSearch: StateFlow<Boolean>
    /** 是否写入崩溃/诊断日志；关闭后不再在 Download 目录创建日志文件。 */
    val logEnabled: StateFlow<Boolean>
    /** 日志文件体积上限（字节）；0 = 不限制。超限时截断旧内容，只保留最新日志。 */
    val logMaxSize: StateFlow<Int>
        get() = MutableStateFlow(DEFAULT_LOG_MAX_BYTES)
    /** 安装成功后自动删除已保存的安装包。 */
    val autoDeletePackage: StateFlow<Boolean>
    val stripAppNameSubtitle: StateFlow<Boolean>
    /** Google Play 来源实现：`appmarket`（自研）/ `aurorastore`（Aurora 网页通道）。 */
    val googlePlayEngine: StateFlow<String>
    /** Google Play device source: false=preset, true=from device. */
    val googleDeviceSource: StateFlow<Boolean>
    /** Google Play 商店地区：`auto` 或 ISO-3166 两位国家码。 */
    val googleRegion: StateFlow<String>

    /** Google Play 设备信息自定义覆盖：字段名 → 覆盖值（设备信息页编辑）。 */
    val googleDeviceOverrides: StateFlow<Map<String, String>>
        get() = MutableStateFlow(emptyMap())
    val googleDeviceTemplates: StateFlow<List<Pair<String, Map<String, String>>>>
        get() = MutableStateFlow(emptyList())
    suspend fun setGoogleDeviceOverride(key: String, value: String) = Unit
    suspend fun replaceGoogleDeviceOverrides(fields: Map<String, String>) = Unit
    suspend fun saveGoogleDeviceTemplate(name: String, fields: Map<String, String>) = Unit
    suspend fun deleteGoogleDeviceTemplate(name: String) = Unit
    suspend fun removeGoogleDeviceOverride(key: String) = Unit
    val homePage: StateFlow<HomePage>
    val searchSources: StateFlow<Set<AppSource>>
    /** 评论来源：关闭 / 跟随应用来源 / 指定来源。 */
    val commentSource: StateFlow<CommentSourceSetting>
    val todaySource: StateFlow<AppSource>
    val categorySource: StateFlow<AppSource>
    /** 应用页内容来源；与 [gameCategorySource] 解耦，各页独立设置。 */
    val appCategorySource: StateFlow<AppSource>
    /** 游戏页内容来源；与 [appCategorySource] 解耦，各页独立设置。 */
    val gameCategorySource: StateFlow<AppSource>
    /** 更新来源（多选）：勾选的来源并发检查，默认全选。 */
    val updateSources: StateFlow<Set<AppSource>>
    /** 更新来源为「全部」时在更新卡片上显示应用来源。 */
    val showUpdateSource: StateFlow<Boolean>
    /** 单来源检查更新超时（秒）；卡死的源超过该时长即跳过。 */
    val checkTimeoutSeconds: StateFlow<Int>
    /** 更新来源优先级列表（为「全部」来源自定义检查顺序，空列表回退默认顺序）。 */
    val updateSourcePriority: StateFlow<List<AppSource>>

    /** 自更新是否接收 GitLab 预发布（Upcoming Release）版本。 */
    val receivePrerelease: StateFlow<Boolean>
        get() = MutableStateFlow(false)
    val permanentIgnores: StateFlow<List<IgnoredUpdate>>
    val onceIgnores: StateFlow<List<IgnoredUpdate>>
    suspend fun setShowSystemUpdates(value: Boolean)
    suspend fun setRemoveSearchAds(value: Boolean)
    suspend fun setFilterQuickGames(value: Boolean)
    suspend fun setFilterReservationApps(value: Boolean)
    suspend fun setDetectClipboardLinks(value: Boolean)
    suspend fun setExactSearch(value: Boolean)
    /** Release 来源是否包含预发布版本（GitHub/GitLab 的 prerelease、tag 带 beta/rc 等）。 */
    val includePrereleaseUpdates: StateFlow<Boolean>
    suspend fun setIncludePrereleaseUpdates(value: Boolean)
    suspend fun setLogEnabled(value: Boolean)
    /** 设置日志文件体积上限（字节）；0 = 不限制。 */
    suspend fun setLogMaxSize(value: Int) = Unit
    suspend fun setAutoDeletePackage(value: Boolean)

    /** 上次已询问过的剪贴板链接（统一格式），同一链接只询问一次。 */
    suspend fun lastClipboardLink(): String
    suspend fun setLastClipboardLink(value: String)
    suspend fun setStripAppNameSubtitle(value: Boolean)
    suspend fun setGooglePlayEngine(value: String)
    suspend fun setGoogleDeviceSource(value: Boolean)
    suspend fun setGoogleRegion(value: String)
    suspend fun setHomePage(value: HomePage)
    suspend fun setSearchSources(value: Set<AppSource>)
    suspend fun setCommentSource(value: CommentSourceSetting)
    suspend fun setTodaySource(value: AppSource)
    suspend fun setCategorySource(value: AppSource)
    suspend fun setAppCategorySource(value: AppSource)
    suspend fun setGameCategorySource(value: AppSource)
    suspend fun setUpdateSources(value: Set<AppSource>)
    suspend fun setShowUpdateSource(value: Boolean)

    suspend fun setReceivePrerelease(value: Boolean) = Unit
    suspend fun setCheckTimeoutSeconds(value: Int)
    suspend fun setUpdateSourcePriority(value: List<AppSource>)
    /** 手动检查更新的最近查询（包名 → 版本号，最新在前，最多 8 条）。 */
    val manualUpdateRecent: StateFlow<List<Pair<String, String>>>
        get() = MutableStateFlow(emptyList())
    suspend fun addManualUpdateRecent(packageName: String, versionCode: String) = Unit
    suspend fun clearManualUpdateRecent() = Unit
    fun isIgnored(app: MarketAppInfo): Boolean
    suspend fun ignoreOnce(app: MarketAppInfo)
    suspend fun ignorePermanently(app: MarketAppInfo)
    suspend fun removePermanent(packageName: String)
    suspend fun removeOnce(packageName: String)
    suspend fun currentRemoveSearchAds(): Boolean
    suspend fun currentFilterQuickGames(): Boolean
    suspend fun currentFilterReservationApps(): Boolean
    suspend fun loadCachedUpdates(): List<MarketAppInfo>
    suspend fun saveCachedUpdates(updates: List<MarketAppInfo>)
    suspend fun readPageCache(key: String): String? = null
    suspend fun writePageCache(key: String, value: String) = Unit
}

/** 单来源检查更新默认超时（秒）。 */
const val DEFAULT_CHECK_TIMEOUT_SECONDS: Int = 20
/** 日志文件体积上限默认值：1MB。 */
const val DEFAULT_LOG_MAX_BYTES: Int = 1_048_576