package com.app.market.data.store

import com.app.market.data.local.PreferenceChanges
import com.app.market.data.local.PreferencesDataSource
import com.app.market.data.local.StringPreferenceKey
import com.app.market.data.local.preferences.HistoryPreferenceKeys
import com.app.market.domain.model.history.InstallHistoryAction
import com.app.market.domain.model.history.InstallHistoryEntry
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.repository.InstallHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** 下载 / 安装历史存储：与其他历史一样落在 Preferences 的 JSON 数组里。 */
internal class InstallHistoryRepositoryImpl(
    private val preferences: PreferencesDataSource,
    private val scope: CoroutineScope,
    private val json: Json,
) : InstallHistoryRepository {
    private val maxSize = 500
    private val serialDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val mutex = Mutex()
    private var loaded = false
    private val _entries = MutableStateFlow<List<InstallHistoryEntry>>(emptyList())
    override val entries: StateFlow<List<InstallHistoryEntry>> = _entries.asStateFlow()

    init {
        scope.launch { serialized { ensureLoaded() } }
    }

    override suspend fun record(entry: InstallHistoryEntry) = serialized {
        ensureLoaded()
        val updated = (listOf(entry) + _entries.value).take(maxSize)
        preferences.update(
            HistoryPreferenceKeys.Installs.namespace,
            PreferenceChanges(strings = mapOf(HistoryPreferenceKeys.Installs to encode(updated))),
        )
        _entries.value = updated
    }

    override suspend fun clear() = serialized {
        preferences.remove(HistoryPreferenceKeys.Installs)
        loaded = true
        _entries.value = emptyList()
    }

    private suspend inline fun <T> serialized(crossinline block: suspend () -> T): T =
        withContext(serialDispatcher) { mutex.withLock { block() } }

    private suspend fun ensureLoaded() {
        if (loaded) return
        _entries.value = read(HistoryPreferenceKeys.Installs)
        loaded = true
    }

    private suspend fun read(key: StringPreferenceKey): List<InstallHistoryEntry> {
        val raw = preferences.read(key)?.takeIf { it.isNotBlank() } ?: return emptyList()
        val arr = runCatching { json.parseToJsonElement(raw) as? JsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val pkg = o.str("packageName")
            if (pkg.isBlank()) return@mapNotNull null
            InstallHistoryEntry(
                packageName = pkg,
                displayName = o.str("displayName", pkg),
                versionName = o.str("versionName"),
                icon = o.str("icon"),
                action = runCatching {
                    InstallHistoryAction.valueOf(o.str("action", InstallHistoryAction.DOWNLOAD.name))
                }.getOrDefault(InstallHistoryAction.DOWNLOAD),
                success = o.str("success", "true") == "true",
                timestamp = o.num("timestamp"),
                detail = o.str("detail"),
                apkSize = o.num("apkSize"),
                source = AppSource.fromToken(o.str("source")),
                appId = o.num("appId"),
                installerMode = o.str("installerMode"),
            )
        }
    }

    private fun encode(items: List<InstallHistoryEntry>): String {
        val arr = buildJsonArray {
            items.forEach { e ->
                add(
                    buildJsonObject {
                        put("packageName", e.packageName)
                        put("displayName", e.displayName)
                        put("versionName", e.versionName)
                        put("icon", e.icon)
                        put("action", e.action.name)
                        put("success", e.success.toString())
                        put("timestamp", e.timestamp)
                        put("detail", e.detail)
                        e.source?.let { put("source", it.token) }
                        if (e.installerMode.isNotBlank()) put("installerMode", e.installerMode)
                        if (e.apkSize > 0L) put("apkSize", e.apkSize)
                        if (e.appId != 0L) put("appId", e.appId)
                    },
                )
            }
        }
        return arr.toString()
    }

    private fun JsonObject.str(name: String, fallback: String = ""): String =
        (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: fallback

    private fun JsonObject.num(name: String): Long =
        (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull ?: 0L
}
