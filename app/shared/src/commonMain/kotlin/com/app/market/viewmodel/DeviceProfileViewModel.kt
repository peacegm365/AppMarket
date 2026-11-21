package com.app.market.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.market.domain.repository.UpdatePreferencesRepository
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.profile.MarketProfile
import com.app.market.domain.model.profile.MarketProfileFields
import com.app.market.domain.model.profile.OppoRequestContext
import com.app.market.domain.model.profile.OppoStoreRegion
import com.app.market.domain.model.profile.ProfileSource
import com.app.market.domain.model.profile.ProfileTemplate
import com.app.market.domain.model.profile.SamsungRequestContext
import com.app.market.domain.model.profile.SamsungStoreRegion
import com.app.market.domain.model.profile.oppoRequestContext
import com.app.market.domain.model.profile.requestContext
import com.app.market.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceProfileViewModel(
    private val store: ProfileRepository,
    private val updatePrefs: UpdatePreferencesRepository,
    private val googleRepo: com.app.market.domain.repository.GooglePlayRepository,
) : ViewModel() {

    /** Google 身份 ID（gsfId）：显示/手动修改/云端获取。 */
    val googleGsfId = androidx.compose.runtime.mutableStateOf("")
    /** 云端获取进行中（期间禁止重复点击）。 */
    val googleGsfIdLoading = androidx.compose.runtime.mutableStateOf(false)
    /** 云端获取失败的可见原因（空=没失败）。 */
    val googleGsfIdError = androidx.compose.runtime.mutableStateOf("")
    init {
        refreshGoogleGsfId()
    }
    fun refreshGoogleGsfId() {
        val current = googleRepo.currentGsfId().orEmpty()
        if (current.isBlank()) {
            // 没有 ID 就自动获取（复用手动入口，带 loading）
            resetGoogleIdentity()
        } else {
            googleGsfId.value = current
        }
    }
    fun setGoogleGsfId(id: String) {
        googleRepo.setGsfId(id)
        refreshGoogleGsfId()
    }

    /** 重新获取 Google 身份：换一个 gsfId（结果集不理想时用）。 */
    fun resetGoogleIdentity() {
        if (googleGsfIdLoading.value) return
        googleGsfIdLoading.value = true
        viewModelScope.launch {
            googleGsfIdError.value = ""
            try {
                googleRepo.resetGoogleIdentity()
                refreshGoogleGsfId()
                if (googleGsfId.value.isBlank()) {
                    googleGsfIdError.value = "云端没有返回设备 ID，请检查代理是否可用后重试"
                }
            } catch (e: Throwable) {
                // 鉴权分发器（auroraoss）走网络：代理没开/被墙时连接会直接失败，
                // 之前异常直接抛出 → 界面只看到一直空着，用户完全不知道发生了什么
                val raw = e.message.orEmpty()
                googleGsfIdError.value = when {
                    raw.contains("127.0.0.1") || raw.contains("Failed to connect", true) ||
                        raw.contains("refused", true) ->
                        "无法连接本机代理（$raw）：请到「设置 → 代理」确认内核已启动"
                    raw.contains("鉴权", true) || raw.contains("auth", true) ->
                        "Google 鉴权服务不可用：$raw"
                    else -> "获取失败：${raw.ifBlank { e::class.simpleName ?: "未知错误" }}"
                }
            } finally {
                googleGsfIdLoading.value = false
            }
        }
    }

    /** Google Play 设备信息来源：false=预置信息（Pixel 9a 指纹），true=从设备获取（本机 Build）。 */
    val googleDeviceSource = updatePrefs.googleDeviceSource
    /** Google Play 商店地区：`auto` 跟随出口 IP，其余为两位国家码。 */
    val googleRegion = updatePrefs.googleRegion
    fun setGoogleRegion(value: String) {
        viewModelScope.launch { updatePrefs.setGoogleRegion(value) }
    }
    fun setGoogleDeviceSource(value: Boolean) {
        viewModelScope.launch { updatePrefs.setGoogleDeviceSource(value) }
    }

    /** Google Play 设备信息自定义覆盖（字段名 → 覆盖值）。 */
    val googleDeviceOverrides = updatePrefs.googleDeviceOverrides

    /** 空值 = 恢复默认（移除覆盖）。 */
    fun setGoogleDeviceOverride(key: String, value: String) {
        viewModelScope.launch {
            if (value.isBlank()) {
                updatePrefs.removeGoogleDeviceOverride(key)
            } else {
                updatePrefs.setGoogleDeviceOverride(key, value)
            }
        }
    }

    /** 当前生效的 Google 设备信息快照（默认 + 真机定向 + 覆盖）。 */
    fun googleDeviceSnapshot(): Map<String, String> = googleRepo.devicePropertiesSnapshot()

    /** Google Play 设备信息模板（模板名 → 字段覆盖）。 */
    val googleDeviceTemplates = updatePrefs.googleDeviceTemplates

    fun replaceGoogleDeviceOverrides(fields: Map<String, String>) {
        viewModelScope.launch { updatePrefs.replaceGoogleDeviceOverrides(fields) }
    }

    /** 把当前覆盖另存为模板。 */
    fun saveGoogleTemplate(name: String) {
        val fields = googleDeviceOverrides.value
        viewModelScope.launch { updatePrefs.saveGoogleDeviceTemplate(name, fields) }
    }

    /** 应用已保存的设备信息模板：直接把模板里的字段覆盖套回去。 */
    fun applyGoogleTemplate(name: String) {
        val fields = googleDeviceTemplates.value.firstOrNull { it.first == name }?.second ?: return
        viewModelScope.launch { updatePrefs.replaceGoogleDeviceOverrides(fields) }
    }

    fun deleteGoogleTemplate(name: String) {
        viewModelScope.launch { updatePrefs.deleteGoogleDeviceTemplate(name) }
    }

    private val _profiles = MutableStateFlow<Map<AppSource, MarketProfile>>(emptyMap())
    val profiles: StateFlow<Map<AppSource, MarketProfile>> = _profiles.asStateFlow()

    private val _sources = MutableStateFlow<Map<AppSource, ProfileSource>>(emptyMap())
    val sources: StateFlow<Map<AppSource, ProfileSource>> = _sources.asStateFlow()

    private val _templateNames = MutableStateFlow<Map<AppSource, String?>>(emptyMap())
    val templateNames: StateFlow<Map<AppSource, String?>> = _templateNames.asStateFlow()

    private val _overriddenFields = MutableStateFlow<Map<AppSource, Set<String>>>(emptyMap())
    val overriddenFields: StateFlow<Map<AppSource, Set<String>>> = _overriddenFields.asStateFlow()

    private val _canUseDevice = MutableStateFlow<Map<AppSource, Boolean>>(emptyMap())
    val canUseDevice: StateFlow<Map<AppSource, Boolean>> = _canUseDevice.asStateFlow()

    private val _oppoStoreRegion = MutableStateFlow(OppoStoreRegion.CHINA)
    val oppoStoreRegion: StateFlow<OppoStoreRegion> = _oppoStoreRegion.asStateFlow()

    private val _oppoRequestContext = MutableStateFlow(OppoStoreRegion.CHINA.oppoRequestContext())
    val oppoRequestContext: StateFlow<OppoRequestContext> = _oppoRequestContext.asStateFlow()

    private val _samsungStoreRegion = MutableStateFlow(SamsungStoreRegion.CHINA)
    val samsungStoreRegion: StateFlow<SamsungStoreRegion> = _samsungStoreRegion.asStateFlow()

    private val _samsungRequestContext = MutableStateFlow(SamsungStoreRegion.CHINA.requestContext())
    val samsungRequestContext: StateFlow<SamsungRequestContext> = _samsungRequestContext.asStateFlow()

    private val _templates = MutableStateFlow<List<ProfileTemplate>>(emptyList())
    val templates: StateFlow<List<ProfileTemplate>> = _templates.asStateFlow()

    private val _showSaveTemplateDialog = MutableStateFlow<AppSource?>(null)
    val showSaveTemplateDialog: StateFlow<AppSource?> = _showSaveTemplateDialog.asStateFlow()

    private val edited = mutableMapOf<AppSource, MutableSet<String>>()
    private val editedOppoRequestContexts = mutableMapOf<OppoStoreRegion, OppoRequestContext>()
    private val editedSamsungRequestContexts = mutableMapOf<SamsungStoreRegion, SamsungRequestContext>()

    init {
        viewModelScope.launch {
            refreshAll()
        }
    }

    fun setSource(source: ProfileSource, appSource: AppSource) = mutate {
        store.setSource(source, appSource)
        if (appSource == AppSource.SAMSUNG) resetSamsungRequestContexts()
        edited.remove(appSource)
        refreshAll()
    }

    fun setOppoStoreRegion(region: OppoStoreRegion) = mutate {
        store.setOppoStoreRegion(region)
        _oppoStoreRegion.value = region
        _oppoRequestContext.value = editedOppoRequestContexts[region] ?: store.oppoRequestContext(region)
    }

    fun setSamsungStoreRegion(region: SamsungStoreRegion) = mutate {
        store.setSamsungStoreRegion(region)
        _samsungStoreRegion.value = region
        _samsungRequestContext.value =
            editedSamsungRequestContexts[region] ?: store.samsungRequestContext(region)
    }

    fun updateOppoRequestContext(name: String, value: String) {
        val current = _oppoRequestContext.value
        val next = when (name) {
            "user-region" -> current.copy(userRegion = value)
            "system-locale" -> current.copy(systemLocale = value)
            "supported-locales" -> current.copy(supportedLocales = value)
            "locale" -> current.copy(locale = value)
            else -> return
        }
        editedOppoRequestContexts[_oppoStoreRegion.value] = next
        _oppoRequestContext.value = next
    }

    fun updateSamsungRequestContext(name: String, value: String) {
        val current = _samsungRequestContext.value
        val next = when (name) {
            "countryCode" -> current.copy(countryCode = value)
            "lang" -> current.copy(language = value)
            "mcc" -> current.copy(mcc = value)
            "mnc" -> current.copy(mnc = value)
            "csc" -> current.copy(csc = value)
            else -> return
        }
        editedSamsungRequestContexts[_samsungStoreRegion.value] = next
        _templateNames.update { it + (AppSource.SAMSUNG to null) }
        _samsungRequestContext.value = next
    }

    fun applyTemplate(name: String, appSource: AppSource) = mutate {
        if (store.applyTemplate(name, appSource)) {
            if (appSource == AppSource.SAMSUNG) resetSamsungRequestContexts()
            edited.remove(appSource)
            refreshAll()
        }
    }

    fun useCustom(appSource: AppSource) = mutate {
        val profile = _profiles.value[appSource] ?: return@mutate
        store.save(profile, fieldsFor(appSource).toSet(), appSource)
        store.setCurrentTemplateName(null, appSource)
        edited.remove(appSource)
        refreshAll()
    }

    fun update(appSource: AppSource, name: String, value: String) {
        val profile = _profiles.value[appSource] ?: return
        edited.getOrPut(appSource) { mutableSetOf() }.add(name)
        _templateNames.update { it + (appSource to null) }
        _profiles.update { it + (appSource to MarketProfileFields.set(profile, name, value)) }
        _overriddenFields.update { overridden ->
            overridden + (appSource to (overridden[appSource].orEmpty() + name))
        }
    }

    fun save(appSource: AppSource) {
        val oppoContextEdits = if (appSource == AppSource.OPPO) editedOppoRequestContexts.toMap() else emptyMap()
        val samsungContextEdits =
            if (appSource == AppSource.SAMSUNG) editedSamsungRequestContexts.toMap() else emptyMap()
        mutate {
            val profile = _profiles.value[appSource] ?: return@mutate
            store.save(
                profile,
                (edited[appSource].orEmpty() + _overriddenFields.value[appSource].orEmpty()).toSet(),
                appSource,
            )
            oppoContextEdits.forEach { (region, context) ->
                store.saveOppoRequestContext(region, context)
                if (editedOppoRequestContexts[region] == context) {
                    editedOppoRequestContexts.remove(region)
                }
            }
            samsungContextEdits.forEach { (region, context) ->
                store.saveSamsungRequestContext(region, context)
                if (editedSamsungRequestContexts[region] == context) {
                    editedSamsungRequestContexts.remove(region)
                }
            }
            edited.remove(appSource)
            refreshAll()
        }
    }

    fun setShowSaveTemplateDialog(appSource: AppSource?) {
        _showSaveTemplateDialog.value = appSource
    }

    fun defaultTemplateName(): String {
        val used = _templates.value.mapTo(mutableSetOf()) { it.name }
        var index = 1
        while ("模板$index" in used) index++
        return "模板$index"
    }

    fun saveTemplate(name: String, appSource: AppSource) = mutate {
        val profile = _profiles.value[appSource] ?: return@mutate
        store.saveTemplate(name.ifBlank { defaultTemplateName() }, profile, appSource)
        edited.remove(appSource)
        refreshAll()
        _showSaveTemplateDialog.value = null
    }

    fun deleteTemplate(name: String) = mutate {
        if (store.deleteTemplate(name)) {
            edited.clear()
            refreshAll()
        }
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private suspend fun resetSamsungRequestContexts() {
        editedSamsungRequestContexts.clear()
        SamsungStoreRegion.entries.forEach { region ->
            store.saveSamsungRequestContext(region, region.requestContext())
        }
    }

    private suspend fun refreshAll() {
        _templates.value = store.templates()
        EDITABLE_SOURCES.forEach { refreshSource(it) }
        val region = store.currentOppoStoreRegion()
        _oppoStoreRegion.value = region
        _oppoRequestContext.value = editedOppoRequestContexts[region] ?: store.oppoRequestContext(region)
        val samsungRegion = store.currentSamsungStoreRegion()
        _samsungStoreRegion.value = samsungRegion
        _samsungRequestContext.value =
            editedSamsungRequestContexts[samsungRegion] ?: store.samsungRequestContext(samsungRegion)
    }

    private suspend fun refreshSource(appSource: AppSource) {
        _sources.update { it + (appSource to store.currentSource(appSource)) }
        _templateNames.update { it + (appSource to store.currentTemplateName(appSource)) }
        _profiles.update { it + (appSource to store.load(appSource)) }
        _overriddenFields.update {
            it + (appSource to MarketProfileFields.ALL.filterTo(mutableSetOf()) { field ->
                store.isOverridden(field, appSource)
            })
        }
        _canUseDevice.update { it + (appSource to store.canUseDevice(appSource)) }
    }

    companion object {
        /** 有独立协议/指纹消费方的来源；豌豆荚请求不携带设备信息，故不在编辑页展示。 */
        val EDITABLE_SOURCES = listOf(
            AppSource.XIAOMI,
            AppSource.VIVO,
            AppSource.OPPO,
            AppSource.SAMSUNG,
            AppSource.HONOR,
            AppSource.HUAWEI,
        )

        private val HONOR_ONLY_FIELDS = setOf(
            "hman", "htype", "spreadModelName", "deliveryCountry", "roamingCountry",
            "osVer", "magicVersion", "androidApiVersion", "apkVer", "apkVerName",
            "language", "dpi", "cpu", "supportGms", "terminalType",
        )
        val FIELDS = MarketProfileFields.ALL.filterNot { it in HONOR_ONLY_FIELDS }

        // Only values consumed by OppoSigner/openId are editable in OPPO mode. Region/locale
        // request values are shown separately by DeviceProfileScreen.
        val OPPO_FIELDS = listOf(
            "co",
            "device",
            "model",
            "os",
            "osV2",
            "androidVersion",
            "sdk",
            "resolution",
            "osBigVersionName",
            "buildId",
            "instanceId",
        )

        // vivo's update protocol consumes these device/request values. Xiaomi/HyperOS-only
        // version, page-resource and island fields are intentionally hidden in vivo mode.
        val VIVO_FIELDS = listOf(
            "co",
            "la",
            "lo",
            "cpuArchitecture",
            "device",
            "model",
            "os",
            "osV2",
            "androidVersion",
            "sdk",
            "resolution",
            "densityDpi",
            "densityScaleFactor",
            "marketVersion",
            "buildId",
            "instanceId",
            "hasGMSCore",
        )

        val SAMSUNG_FIELDS = listOf(
            "cpuArchitecture",
            "model",
            "sdk",
            "instanceId",
        )

        val HUAWEI_FIELDS = listOf(
            "co",
            "la",
            "lo",
            "cpuArchitecture",
            "model",
            "androidVersion",
            "buildId",
            "instanceId",
        )

        val HONOR_FIELDS = listOf(
            "hman",
            "htype",
            "spreadModelName",
            "deliveryCountry",
            "roamingCountry",
            "osVer",
            "magicVersion",
            "androidApiVersion",
            "apkVer",
            "apkVerName",
            "language",
            "dpi",
            "resolution",
            "cpu",
            "supportGms",
            "terminalType",
            "instanceId",
        )

        fun fieldsFor(source: AppSource): List<String> = when (source) {
            AppSource.OPPO -> OPPO_FIELDS
            AppSource.VIVO -> VIVO_FIELDS
            AppSource.SAMSUNG -> SAMSUNG_FIELDS
            AppSource.HONOR -> HONOR_FIELDS
            AppSource.HUAWEI -> HUAWEI_FIELDS
            AppSource.XIAOMI, AppSource.WANDOUJIA, AppSource.TAPTAP, AppSource.COOLAPK, AppSource.GOOGLE, AppSource.KUAIBAO, AppSource.APPCHINA, AppSource.TENCENT, AppSource.BOX7723, AppSource.FDROID, AppSource.APKPURE, AppSource.APTOIDE, AppSource.GITHUB, AppSource.LSPOSED, AppSource.GITLAB -> FIELDS
        }

        fun hasCustomSamsungRequestContext(
            region: SamsungStoreRegion,
            context: SamsungRequestContext,
        ): Boolean = context != region.requestContext()

        fun valueOf(profile: MarketProfile, name: String): String = MarketProfileFields.valueOf(profile, name)
    }
}
