package com.app.market.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.market.domain.model.download.DownloadMeta
import com.app.market.domain.model.download.DownloadPart
import com.app.market.domain.model.download.DownloadState
import com.app.market.domain.model.market.AppComments
import com.app.market.domain.model.market.AppDetail
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.isDownloadBlocked
import com.app.market.domain.model.market.isReservation
import com.app.market.domain.repository.DownloadRepository
import com.app.market.domain.repository.MarketRepository
import com.app.market.domain.repository.MarketSourceRepository
import com.app.market.domain.repository.PackageRepository
import com.app.market.domain.repository.UpdatePreferencesRepository
import com.app.market.platform.UiPlatform
import com.app.market.ui.model.AppActionKind
import com.app.market.ui.model.actionKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

const val APP_NOT_LISTED_MESSAGE = "应用商店尚未收录此应用"

@Immutable
data class AppDetailUiState(
    val loading: Boolean = true,
    val errorMessage: String = "",
    val detail: AppDetail? = null,
    /** Primary action for [detail]; derived from the app's resolved installed version (no I/O). */
    val actionKind: AppActionKind? = null,
)

class AppDetailViewModel(
    private val sources: MarketSourceRepository,
    private val packages: PackageRepository,
    private val downloads: DownloadRepository,
    private val prefs: UpdatePreferencesRepository,
    private val uiPlatform: UiPlatform,
    private val google: com.app.market.domain.repository.GooglePlayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppDetailUiState())
    val uiState: StateFlow<AppDetailUiState> = _uiState.asStateFlow()

    /** 历史版本目录（下载指定版本面板用）；按包名缓存，换应用重新取。 */
    data class HistoricalVersionsState(
        val packageName: String = "",
        val loading: Boolean = false,
        val loaded: Boolean = false,
        val errorMessage: String = "",
        val versions: List<com.app.market.domain.model.market.AppHistoricalVersion> = emptyList(),
    )

    private val _historicalVersions = MutableStateFlow(HistoricalVersionsState())
    val historicalVersions: StateFlow<HistoricalVersionsState> = _historicalVersions.asStateFlow()
    private var historyJob: Job? = null

    fun loadHistoricalVersions(packageName: String, force: Boolean = false) {
        val state = _historicalVersions.value
        if (state.loading && state.packageName == packageName) return
        // 同一应用已成功取过就复用；失败过的允许重取（重新打开面板或点重试）
        if (!force && state.loaded && state.errorMessage.isEmpty() && state.packageName == packageName) return
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            _historicalVersions.update { it.copy(packageName = packageName, loading = true, errorMessage = "") }
            val result = runCatchingCancellable { google.historicalVersions(packageName) }
            _historicalVersions.update {
                it.copy(
                    loading = false,
                    loaded = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.message.orEmpty(),
                    versions = result.getOrDefault(emptyList()),
                )
            }
        }
    }

    val downloadStates: StateFlow<Map<String, DownloadState>> = downloads.states

    /** LSPosed 一版多附件：当前正式版可下载的 APK 列表（其余来源为空）。 */
    private val _moduleAssets = MutableStateFlow<List<com.app.market.domain.model.market.ModuleAsset>>(emptyList())
    val moduleAssets: StateFlow<List<com.app.market.domain.model.market.ModuleAsset>> = _moduleAssets.asStateFlow()

    /** LSPosed 模块的历史发布（新到旧），用于「显示历史版本」。 */
    private val _moduleReleases =
        MutableStateFlow<List<com.app.market.domain.model.market.ModuleRelease>>(emptyList())
    val moduleReleases: StateFlow<List<com.app.market.domain.model.market.ModuleRelease>> =
        _moduleReleases.asStateFlow()
    private val _moduleReleasesLoading = MutableStateFlow(false)
    val moduleReleasesLoading: StateFlow<Boolean> = _moduleReleasesLoading.asStateFlow()

    /** 用户在详情页选中的附件名；null 表示自动挑最合适的一份。 */
    private val _selectedAssetName = MutableStateFlow<String?>(null)
    val selectedAssetName: StateFlow<String?> = _selectedAssetName.asStateFlow()

    /** README 文档：先显示持久缓存，再从原地址刷新。 */
    suspend fun cachedDocText(url: String): String? =
        runCatchingCancellable { prefs.readPageCache("readme_doc_v1:$url") }.getOrNull()

    suspend fun loadDocText(url: String): String? {
        val text = runCatchingCancellable { sources.docText(url) }.getOrNull() ?: return null
        val key = "readme_doc_v1:$url"
        val cached = runCatchingCancellable { prefs.readPageCache(key) }.getOrNull()
        if (text != cached) runCatchingCancellable { prefs.writePageCache(key, text) }
        return text
    }

    /** 重新加载当前详情（README 空白、发布列表为空时的兜底重试）。 */
    fun retryLoad() {
        val detail = _uiState.value.detail ?: return
        loadJob?.cancel()
        loadJob = null
        _uiState.update { it.copy(detail = null, loading = true, errorMessage = "") }
        load(
            appId = detail.app.appId,
            packageName = detail.app.packageName,
            source = detail.app.source,
        )
    }

    fun selectModuleAsset(name: String?) {
        _selectedAssetName.value = name
    }


    // in-flight 去重靠 job 而非布尔标记：失败后必须允许重新加载，否则错误态永久卡死
    private var loadJob: Job? = null
    private var assetsJob: Job? = null
    private var releasesJob: Job? = null
    private var moduleReleasesGeneration = 0L
    private var moduleCacheIdentity: String? = null
    private val pendingDownloads = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            packages.changes.collect { change ->
                refreshInstalledState(change)
            }
        }
    }

    fun load(
        appId: Long,
        packageName: String,
        externalQuery: String? = null,
        source: AppSource = AppSource.XIAOMI,
    ) {
        if (loadJob?.isActive == true) return
        if (_uiState.value.detail != null) return
        _uiState.update { it.copy(loading = true, errorMessage = "") }
        loadJob = viewModelScope.launch {
            val cacheKey = "detail_v1:${source.token}:$appId:$packageName:${externalQuery.orEmpty()}"
            // 榜单仓库标识可直接用于请求发布记录，不必等待详情、README 或 APK 解析。
            if (source == AppSource.GITHUB && packageName.startsWith("github-repo:")) {
                val path = packageName.removePrefix("github-repo:")
                loadModuleAssets(MarketAppInfo(appId = appId, packageName = packageName,
                    displayName = path.substringAfterLast('/'), publisherName = path.substringBeforeLast('/'),
                    versionName = "", versionCode = 0L, icon = "", apkSize = 0L,
                    ratingScore = 0.0, openLink = "https://github.com/$path", source = source))
            }
            val cached = runCatchingCancellable { prefs.readPageCache(cacheKey) }.getOrNull()
                ?.let(PageCacheCodec::decodeDetail)
            if (cached != null) {
                val local = cached.withInstalledState()
                _moduleReleasesLoading.value = true
                _uiState.update { it.copy(loading = false, detail = local, actionKind = local.app.actionKind()) }
                // 缓存只包含详情正文，附件与历史版本必须独立联网刷新。
                loadModuleAssets(local.app)
            }
            try {
                val detail = sources.appDetail(source, appId, packageName, externalQuery).withInstalledState()
                _moduleReleasesLoading.value = true
                _uiState.update {
                    it.copy(loading = false, errorMessage = "", detail = detail, actionKind = detail.app.actionKind())
                }
                val encoded = PageCacheCodec.encodeDetail(detail)
                if (encoded != cached?.let(PageCacheCodec::encodeDetail)) {
                    runCatchingCancellable { prefs.writePageCache(cacheKey, encoded) }
                }
                loadOptionalSections(source, detail)
                loadModuleAssets(detail.app)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(loading = false, errorMessage = error.message ?: APP_NOT_LISTED_MESSAGE)
                }
            }
        }
    }

    private fun loadOptionalSections(source: AppSource, detail: AppDetail) {
        viewModelScope.launch {
            prefs.initialized.first { it }
            supervisorScope {
                // 评论：由「评论来源」决定取不取——关闭、或跟随到取不到评论的来源，就不显示
                val commentSource = prefs.commentSource.value.resolve(source)
                if (commentSource == null) {
                    // 关闭：只丢弃评论正文（不显示评论列表），评论数量仍保留展示
                    updateCurrentDetail(detail) {
                        it.copy(comments = emptyList())
                    }
                } else launch {
                    runCatchingCancellable {
                        sources.appComments(commentSource, detail.app)
                    }
                        .onSuccess { comments ->
                            updateCurrentDetail(detail) {
                                it.copy(
                                    comments = comments.items,
                                    commentCount = maxOf(it.commentCount, comments.totalCount),
                                )
                            }
                        }
                }
            }
        }
    }

    /** 匿名第三方商店不认识本机，已安装版本得自己补，否则已装应用会显示成「安装」。 */
    private suspend fun AppDetail.withInstalledState(): AppDetail {
        val name = app.packageName
        val installedVersionCode = packages.installedVersionCodes(listOf(name))[name] ?: 0L
        if (installedVersionCode <= 0L) return copy(app = app.copy(installedVersionCode = 0L, installedVersionName = ""))
        return copy(
            app = app.copy(
                installedVersionCode = installedVersionCode,
                installedVersionName = packages.installedVersionName(name).orEmpty(),
            )
        )
    }

    private fun updateCurrentDetail(expected: AppDetail, transform: (AppDetail) -> AppDetail) {
        _uiState.update { state ->
            val current = state.detail
            if (current == null ||
                current.app.appId != expected.app.appId ||
                current.app.packageName != expected.app.packageName
            ) {
                state
            } else {
                state.copy(detail = transform(current))
            }
        }
    }

    /** 仓库类来源（LSPosed / GitHub）：加载附件与发布列表（一版多份 APK 时给用户选）。 */
    private fun loadModuleAssets(app: MarketAppInfo) {
        if (app.source !in com.app.market.domain.model.market.AppSource.RepositorySources) {
            _moduleAssets.value = emptyList()
            _moduleReleases.value = emptyList()
            _selectedAssetName.value = null
            return
        }
        val identity = "${app.source.token}:${app.openLink.ifBlank { app.packageName }}"
        val cacheKey = "module_releases_v1:$identity"
        if (moduleCacheIdentity == identity && releasesJob?.isActive == true) return
        if (moduleCacheIdentity != identity) {
            moduleCacheIdentity = identity
            _moduleReleases.value = emptyList()
            _moduleAssets.value = emptyList()
        }
        assetsJob?.cancel()
        val releasesGeneration = ++moduleReleasesGeneration
        releasesJob?.cancel()
        assetsJob = viewModelScope.launch {
            runCatchingCancellable { sources.moduleAssets(app.source, app) }
                .onSuccess { assets ->
                    _moduleAssets.value = assets
                    if (assets.none { it.name == _selectedAssetName.value }) {
                        _selectedAssetName.value = assets.firstOrNull()?.name
                    }
                }
                .onFailure { uiPlatform.showToast(it.message ?: "获取下载附件失败") }
        }
        _moduleReleasesLoading.value = true
        releasesJob = viewModelScope.launch {
            try {
                val cachedReleases = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    runCatchingCancellable { prefs.readPageCache(cacheKey) }
                        .getOrNull()?.let(PageCacheCodec::decodeModuleReleases)
                }
                if (releasesGeneration == moduleReleasesGeneration && _moduleReleases.value.isEmpty() && cachedReleases != null) {
                    _moduleReleases.value = cachedReleases
                }
                val result = runCatchingCancellable {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        sources.moduleReleases(app.source, app)
                    }
                }
                result.onSuccess { releases ->
                    if (releasesGeneration == moduleReleasesGeneration) {
                        _moduleReleases.value = releases
                        runCatchingCancellable {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                prefs.writePageCache(cacheKey, PageCacheCodec.encodeModuleReleases(releases))
                            }
                        }
                    }
                }.onFailure { uiPlatform.showToast(it.message ?: "获取历史版本失败") }
            } finally {
                if (releasesGeneration == moduleReleasesGeneration) _moduleReleasesLoading.value = false
            }
        }
    }

    private fun refreshInstalledState(change: com.app.market.domain.model.installed.PackageChange) {
        val packageName = change.packageName
        val current = _uiState.value.detail ?: return
        if (current.app.packageName != packageName) return
        _uiState.update { state ->
            val detail = state.detail
            if (detail?.app?.packageName != packageName) {
                state
            } else {
                val app = detail.app.copy(
                    installedVersionCode = change.installedVersionCode ?: 0L,
                    installedVersionName = change.installedVersionName,
                )
                state.copy(
                    detail = detail.copy(app = app),
                    actionKind = app.actionKind(),
                )
            }
        }
    }

    fun install(app: MarketAppInfo) {
        if (app.isReservation() || app.isDownloadBlocked()) return
        if (!pendingDownloads.add(app.packageName)) return
        viewModelScope.launch {
            try {
                runCatchingCancellable {
                    downloadMeta(app, app.actionKind() == AppActionKind.UPDATE, _selectedAssetName.value)
                }
                    // 下载完成即自动安装（「下载」按钮只是入口文案）
                    .onSuccess { downloads.start(it) }
                    .onFailure { uiPlatform.showToast(it.message ?: "Download failed") }
            } finally {
                pendingDownloads.remove(app.packageName)
            }
        }
    }

    /** 下载指定 versionCode（Google Play 历史版本）：按用户输入的版本号取包，不做新旧比较。仅下载不安装。 */
    fun installVersion(app: MarketAppInfo, versionCode: Long, versionName: String) {
        if (app.isReservation() || app.isDownloadBlocked()) return
        if (!pendingDownloads.add(app.packageName)) return
        viewModelScope.launch {
            try {
                runCatchingCancellable {
                    downloadMeta(app.copy(versionCode = versionCode), update = true)
                }
                    .onSuccess { downloads.start(it.asPickedVersion(versionName), installAfterDownload = false) }
                    .onFailure { uiPlatform.showToast(it.message ?: "Download failed") }
            } finally {
                pendingDownloads.remove(app.packageName)
            }
        }
    }

    /** 「另存」历史版本：弹系统文档创建器选保存位置，下载完成后把安装包写到所选位置（仅下载不安装）。 */
    fun saveVersionAs(app: MarketAppInfo, versionCode: Long, versionName: String) {
        if (app.isReservation() || app.isDownloadBlocked()) return
        if (!pendingDownloads.add(app.packageName)) return
        viewModelScope.launch {
            try {
                val fileName = "${app.displayName}-$versionCode.apk"
                val uri = uiPlatform.pickSaveUri(fileName, "application/vnd.android.package-archive")
                if (uri == null) return@launch
                runCatchingCancellable {
                    downloadMeta(app.copy(versionCode = versionCode), update = true)
                }
                    .onSuccess { downloads.start(it.asPickedVersion(versionName), installAfterDownload = false, saveUri = uri) }
                    .onFailure { uiPlatform.showToast(it.message ?: "Download failed") }
            } finally {
                pendingDownloads.remove(app.packageName)
            }
        }
    }

    /** 历史版本「另存/下载」：下载管理的版本行只显示所选版本，而不是「当前版本 → 最新版本」。 */
    private fun DownloadMeta.asPickedVersion(versionName: String): DownloadMeta =
        copy(
            versionName = versionName.ifBlank { versionCode.toString() },
            installedVersionName = "",
        )

    fun redownload(app: MarketAppInfo) {
        if (app.isDownloadBlocked()) return
        if (!pendingDownloads.add(app.packageName)) return
        viewModelScope.launch {
            try {
                runCatchingCancellable { downloadMeta(app, update = false, assetName = _selectedAssetName.value) }
                    .onSuccess { downloads.start(it, installAfterDownload = false) }
                    .onFailure { uiPlatform.showToast(it.message ?: "Download failed") }
            } finally {
                pendingDownloads.remove(app.packageName)
            }
        }
    }

    private suspend fun downloadMeta(app: MarketAppInfo, update: Boolean, assetName: String? = null) =
        if (update) sources.downloadUpdateMeta(app.source, app)
        else sources.downloadMeta(app.source, app, app.displayName, assetName)

    /** 历史版本卡片的附件下载：downloadMeta 解析链路只认最新版，这里直接用发布资产直链。 */
    fun downloadReleaseAsset(
        app: MarketAppInfo,
        versionName: String,
        versionCode: Long,
        asset: com.app.market.domain.model.market.ModuleAsset,
    ) {
        if (!pendingDownloads.add(asset.name)) return
        viewModelScope.launch {
            try {
                downloads.start(
                    DownloadMeta(
                        appId = app.appId,
                        packageName = app.packageName,
                        displayName = app.displayName,
                        versionName = versionName,
                        versionCode = versionCode,
                        url = asset.url,
                        size = asset.size,
                        parts = listOf(DownloadPart(asset.name, "base", asset.url, asset.size)),
                        icon = app.icon,
                        source = app.source,
                    ),
                    // 只有 APK 才下完即装；zip/内核镜像等只下载，去下载页取
                    installAfterDownload = asset.name.endsWith(".apk", ignoreCase = true),
                )
            } finally {
                pendingDownloads.remove(asset.name)
            }
        }
    }

    fun openApp(app: MarketAppInfo) {
        // OPPO/三星/华为的 openLink 只是商店深链回退，不是已安装应用的启动目标
        val opened = if (app.source.capabilities.prefersOpenLinkLaunch) {
            packages.openLink(app.openLink) || packages.openApp(app.packageName)
        } else {
            packages.openApp(app.packageName) || packages.openLink(app.openLink)
        }
        if (!opened) {
            uiPlatform.showToast(app.packageName)
        }
    }

    fun installDownloaded(packageName: String) = downloads.install(packageName)
    fun cancelDownload(packageName: String) = downloads.cancel(packageName)
    fun clearDownload(packageName: String) = downloads.clear(packageName)
}

internal suspend fun loadRequestedDetail(
    market: MarketRepository,
    appId: Long,
    packageName: String,
    externalQuery: String?,
): AppDetail {
    val detail = market.appDetail(appId.coerceAtLeast(0L), packageName, externalQuery)
    if (detail.app.appId <= 0L || detail.app.packageName.isBlank()) {
        error(APP_NOT_LISTED_MESSAGE)
    }
    return detail
}

internal suspend fun loadOptionalDetailSections(
    market: MarketRepository,
    detail: AppDetail,
    loadComments: Boolean,
    loadSameDeveloper: Boolean,
    onCommentsLoaded: (AppComments) -> Unit,
    onSameDeveloperLoaded: (List<MarketAppInfo>) -> Unit,
) = supervisorScope {
    if (loadComments) {
        launch {
            loadOptionalSection(
                request = { market.appComments(detail.app.appId, detail.app.versionCode) },
                onLoaded = onCommentsLoaded,
            )
        }
    }
    if (loadSameDeveloper) {
        launch {
            loadOptionalSection(
                request = { market.sameDeveloperApps(detail.app.appId) },
                onLoaded = onSameDeveloperLoaded,
            )
        }
    }
}

private suspend fun <T> loadOptionalSection(
    request: suspend () -> T,
    onLoaded: (T) -> Unit,
) {
    try {
        onLoaded(request())
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        // Optional sections must never hold back or replace the basic detail content.
    }
}