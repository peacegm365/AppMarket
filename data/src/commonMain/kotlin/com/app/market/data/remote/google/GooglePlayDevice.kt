package com.app.market.data.remote.google

/**
 * Google Play 匿名会话的设备指纹（Pixel 9a，与 Aurora 分发器接受的 36 键配置一致）。
 * 分发器按这份配置签发 token，fdfe 请求的 User-Agent / X-PS-RH 也从这里取值。
 */
/**
 * Platform-injected real device fields. Android overrides with android.os.Build when
 * the user picks "from device" in device profile settings.
 */
var GoogleDevicePropertiesOverrides: Map<String, String> = emptyMap()

/**
 * 用户在「设备信息 → Google Play」里手动编辑的字段覆盖（合并链最后，用户优先）。
 * 设备信息页写入，随偏好持久化。
 */
var GoogleDeviceUserOverrides: Map<String, String> = emptyMap()

/** 商店地区：`auto` 跟随出口 IP；其余为 ISO-3166 两位国家码（由设置写入）。 */
var GoogleRegion: String = "auto"

/** 设备指纹是否改用本机 Build（设置里的「从设备获取」开关）。 */
var GoogleDeviceSourceFromSystem: Boolean = false

/**
 * 本机指纹被 Play 判定为不可用（搜索空结果 / 详情取不到）时置 true，
 * 之后同进程内一律回退预置 Pixel 9a 指纹，避免开关一开就整个源瘫掉。
 */
var GoogleDevicePropsPresetFallback: Boolean = false

/** 地区配置每变一次 +1；会话层据此判断旧 token 是否还有效。 */
var GoogleRegionGeneration: Int = 0

internal val GoogleDevicePropertiesDefaults: Map<String, String> = mapOf(
    "HasHardKeyboard" to "false",
    "Build.HARDWARE" to "tegu",
    "Build.BRAND" to "google",
    "Build.VERSION.SDK_INT" to "35",
    "Roaming" to "mobile-notroaming",
    "Build.MODEL" to "Pixel 9a",
    "Vending.versionString" to "45.8.21-31 [0] [PR] 747433787",
    "Vending.version" to "84582130",
    "CellOperator" to "310",
    "Build.FINGERPRINT" to "google/tegu/tegu\\:15/BD4A.250405.003/13238919\\:user/release-keys",
    "Build.DEVICE" to "tegu",
    "Screen.Height" to "2424",
    "SimOperator" to "38",
    "ScreenLayout" to "2",
    "SharedLibraries" to "android.test.base,android.test.mock,com.vzw.apnlib,android.hidl.manager-V1.0-java,com.android.hotwordenrollment.common.util,com.google.pixel.camera.services.cameraidremapper,google-ril,libedgetpu_client.google.so,libOpenCL.so,libedgetpu_util.so,android.telephony.satellite,com.google.android.camera.experimental2024_system,com.google.pixel.camera.connectivity,com.android.omadm.radioconfig,android.hidl.base-V1.0-java,com.google.pixel.camera.services.lyricconfigprovider,lib_jpg_encoder.so,com.google.android.camera.experimental2024,libOpenCL-pixel.so,com.android.location.provider,androidx.window.extensions,libedgetpu_dba.google.so,com.google.pixel.camera.connectivity.impl,oemrilhook,com.google.pixel.camera.services.cameraidremapper.impl,android.net.ipsec.ike,com.android.future.usb.accessory,com.google.pixel.camera.services.lyricconfigprovider.impl,androidx.camera.extensions.impl,android.ext.shared,javax.obex,com.google.android.gms,lib_aion_buffer.so,libgxp.so,libedgetpu_tachyon.google.so,android.test.runner,com.google.android.dialer.support,org.apache.http.legacy,com.google.android.camera.extensions,com.google.android.hardwareinfo,com.android.cts.ctsshim.shared_library,com.android.nfc_extras,com.android.media.remotedisplay,com.google.android.camerax.extensions,com.android.mediadrm.signer,androidx.window.sidecar",
    "Features" to "android.hardware.sensor.proximity,com.google.android.feature.CONTEXTUAL_SEARCH,com.verizon.hardware.telephony.lte,com.google.android.feature.PIXEL_2024_EXPERIENCE,com.verizon.hardware.telephony.ehrpd,android.hardware.telephony.ims.singlereg,android.hardware.sensor.accelerometer,android.software.controls,android.hardware.faketouch,android.software.telecom,android.hardware.telephony.subscription,com.google.android.feature.D2D_CABLE_MIGRATION_FEATURE,android.hardware.telephony.euicc,android.hardware.usb.accessory,android.hardware.telephony.data,android.hardware.sensor.dynamic.head_tracker,android.software.backup,android.hardware.touchscreen,android.hardware.touchscreen.multitouch,android.software.erofs,android.software.print,android.software.activities_on_secondary_displays,android.hardware.wifi.rtt,com.android.systemui.SUPPORTS_DRAG_ASSISTANT_TO_SPLIT,android.software.device_lock,com.google.android.feature.PIXEL_2017_EXPERIENCE,com.google.android.feature.PIXEL_2024_MIDYEAR_EXPERIENCE,android.software.voice_recognizers,android.software.picture_in_picture,android.hardware.fingerprint,android.hardware.telephony.satellite,android.hardware.sensor.gyroscope,android.hardware.audio.low_latency,android.software.vulkan.deqp.level,android.software.cant_save_state,com.google.android.feature.PIXEL_2018_EXPERIENCE,android.hardware.security.model.compatible,android.hardware.telephony.messaging,com.google.android.feature.PIXEL_2019_EXPERIENCE,android.hardware.telephony.calling,android.hardware.opengles.aep,android.hardware.bluetooth,com.google.android.feature.PIXEL_2023_MIDYEAR_EXPERIENCE,android.software.window_magnification,android.hardware.telephony.radio.access,android.hardware.camera.autofocus,com.google.android.feature.GOOGLE_BUILD,android.hardware.telephony.gsm,android.hardware.telephony.ims,android.software.incremental_delivery,android.hardware.se.omapi.ese,android.software.opengles.deqp.level,vendor.android.hardware.camera.preview-dis.front,com.google.android.feature.PIXEL_2022_MIDYEAR_EXPERIENCE,android.hardware.camera.concurrent,android.hardware.usb.host,android.hardware.audio.output,android.software.ipsec_tunnel_migration,android.software.verified_boot,android.hardware.camera.flash,android.hardware.camera.front,android.hardware.sensor.hifi_sensors,android.hardware.se.omapi.uicc,android.hardware.strongbox_keystore,android.hardware.screen.portrait,android.hardware.nfc,com.google.android.feature.TURBO_PRELOAD,com.nxp.mifare,com.google.android.feature.PIXEL_2021_MIDYEAR_EXPERIENCE,android.hardware.sensor.stepdetector,android.software.home_screen,android.hardware.context_hub,vendor.android.hardware.camera.preview-dis.back,android.hardware.microphone,android.software.autofill,android.software.securely_removes_users,com.google.android.feature.PIXEL_EXPERIENCE,android.hardware.bluetooth_le,android.hardware.sensor.compass,com.google.android.feature.GOOGLE_FI_BUNDLED,android.hardware.touchscreen.multitouch.jazzhand,android.hardware.sensor.barometer,android.software.app_widgets,com.google.android.feature.PIXEL_2020_MIDYEAR_EXPERIENCE,android.software.sdksandbox.sdk_install_work_profile,android.hardware.telephony.carrierlock,android.software.input_methods,android.hardware.sensor.light,android.hardware.vulkan.version,android.software.companion_device_setup,android.software.device_admin,com.google.android.feature.WELLBEING,android.hardware.wifi.passpoint,android.hardware.camera,android.software.credentials,android.hardware.device_unique_attestation,android.hardware.screen.landscape,android.software.device_id_attestation,com.google.android.feature.AER_OPTIMIZED,android.hardware.ram.normal,com.google.android.feature.NEXT_GENERATION_ASSISTANT,com.google.android.feature.PIXEL_2019_MIDYEAR_EXPERIENCE,android.software.managed_users,android.software.webview,android.hardware.sensor.stepcounter,android.hardware.camera.capability.manual_post_processing,android.hardware.camera.any,android.hardware.camera.capability.raw,android.hardware.vulkan.compute,android.hardware.touchscreen.multitouch.distinct,android.hardware.location.network,android.software.cts,android.hardware.camera.capability.manual_sensor,android.software.app_enumeration,com.google.android.apps.dialer.SUPPORTED,android.hardware.camera.level.full,com.google.android.feature.GMS_GAME_SERVICE,android.software.game_service,android.hardware.wifi.direct,android.software.live_wallpaper,com.google.android.feature.GOOGLE_EXPERIENCE,android.software.ipsec_tunnels,com.google.android.feature.EXCHANGE_6_2,com.google.android.feature.DREAMLINER,android.hardware.audio.pro,android.hardware.nfc.hcef,android.hardware.location.gps,com.google.android.feature.ADAPTIVE_CHARGING,android.software.midi,android.hardware.nfc.any,android.hardware.nfc.ese,android.hardware.nfc.hce,android.hardware.hardware_keystore,com.google.android.feature.PIXEL_2020_EXPERIENCE,android.hardware.telephony.euicc.mep,android.hardware.wifi,android.hardware.location,android.hardware.vulkan.level,android.software.virtualization_framework,com.google.android.feature.PIXEL_2021_EXPERIENCE,android.hardware.keystore.app_attest_key,com.google.android.feature.QUICK_TAP,android.software.wallet_location_based_suggestions,android.hardware.wifi.aware,com.google.android.feature.PIXEL_2022_EXPERIENCE,android.software.secure_lock_screen,android.hardware.biometrics.face,com.google.android.feature.PIXEL_2023_EXPERIENCE,android.hardware.telephony,android.software.file_based_encryption",
    "GSF.version" to "251333035",
    "UserReadableName" to "Google Pixel 9a",
    "Build.RADIO" to "g5300t-241101-241226-B-12850354,g5300t-241101-241226-B-12850354",
    "Client" to "android-google",
    "Locales" to "af,af_ZA,am,am_ET,ar,ar_EG,ar_XB,as,ast,az,be,bg,bg_BG,bn,bs,ca,ca_ES,cs,cs_CZ,da,da_DK,de,de_DE,el,el_GR,en,en_AU,en_CA,en_GB,en_IN,en_US,en_XA,en_XC,eo,es,es_419,es_ES,es_US,et,et_EE,eu,fa,fa_IR,fi,fi_FI,fil,fil_PH,fr,fr_CA,fr_FR,gl,gu,he,hi,hi_IN,hr,hr_HR,hu,hu_HU,hy,ia,in,in_ID,is,it,it_IT,iw,iw_IL,ja,ja_JP,ka,kk,km,kmr,kn,ko,ko_KR,ky,lo,lt,lt_LT,lv,lv_LV,mk,ml,mn,mr,ms,ms_MY,my,nb,nb_NO,ne,nl,nl_NL,or,pa,pl,pl_PL,pt,pt_BR,pt_PT,ro,ro_RO,ru,ru_RU,sc,si,sk,sk_SK,sl,sl_SI,so,sq,sr,sr_Latn,sr_RS,sv,sv_SE,sw,sw_TZ,ta,te,th,th_TH,tr,tr_TR,uk,uk_UA,ur,uz,vi,vi_VN,yue,zh_CN,zh_HK,zh_TW,zu,zu_ZA",
    "Screen.Density" to "420",
    "Platforms" to "arm64-v8a",
    "Build.PRODUCT" to "tegu",
    "Navigation" to "1",
    "Keyboard" to "1",
    "Build.ID" to "BD4A.250405.003",
    "TimeZone" to "UTC-10",
    "Screen.Width" to "1080",
    "GL.Extensions" to "GL_ANDROID_extension_pack_es31a,GL_ARM_debug_assert,GL_ARM_mali_program_binary,GL_ARM_mali_shader_binary,GL_ARM_rgba8,GL_ARM_shader_core_properties,GL_ARM_shader_framebuffer_fetch,GL_ARM_shader_framebuffer_fetch_depth_stencil,GL_ARM_texture_unnormalized_coordinates,GL_EXT_EGL_image_array,GL_EXT_EGL_image_storage,GL_EXT_EGL_image_storage_compression,GL_EXT_YUV_target,GL_EXT_blend_minmax,GL_EXT_buffer_storage,GL_EXT_clear_texture,GL_EXT_clip_control,GL_EXT_color_buffer_float,GL_EXT_color_buffer_half_float,GL_EXT_copy_image,GL_EXT_debug_marker,GL_EXT_discard_framebuffer,GL_EXT_disjoint_timer_query,GL_EXT_draw_buffers_indexed,GL_EXT_draw_elements_base_vertex,GL_EXT_external_buffer,GL_EXT_float_blend,GL_EXT_fragment_shading_rate,GL_EXT_fragment_shading_rate_attachment,GL_EXT_fragment_shading_rate_primitive,GL_EXT_geometry_shader,GL_EXT_gpu_shader5,GL_EXT_multisampled_render_to_texture,GL_EXT_multisampled_render_to_texture2,GL_EXT_occlusion_query_boolean,GL_EXT_polygon_offset_clamp,GL_EXT_primitive_bounding_box,GL_EXT_protected_textures,GL_EXT_read_format_bgra,GL_EXT_robustness,GL_EXT_sRGB,GL_EXT_sRGB_write_control,GL_EXT_shader_framebuffer_fetch,GL_EXT_shader_io_blocks,GL_EXT_shader_non_constant_global_initializers,GL_EXT_shader_pixel_local_storage,GL_EXT_shadow_samplers,GL_EXT_tessellation_shader,GL_EXT_texture_border_clamp,GL_EXT_texture_buffer,GL_EXT_texture_compression_astc_decode_mode,GL_EXT_texture_compression_astc_decode_mode_rgb9e5,GL_EXT_texture_cube_map_array,GL_EXT_texture_filter_anisotropic,GL_EXT_texture_format_BGRA8888,GL_EXT_texture_norm16,GL_EXT_texture_rg,GL_EXT_texture_sRGB_R8,GL_EXT_texture_sRGB_RG8,GL_EXT_texture_sRGB_decode,GL_EXT_texture_storage,GL_EXT_texture_storage_compression,GL_EXT_texture_type_2_10_10_10_REV,GL_EXT_unpack_subimage,GL_KHR_blend_equation_advanced,GL_KHR_blend_equation_advanced_coherent,GL_KHR_debug,GL_KHR_robust_buffer_access_behavior,GL_KHR_robustness,GL_KHR_texture_compression_astc_hdr,GL_KHR_texture_compression_astc_ldr,GL_KHR_texture_compression_astc_sliced_3d,GL_OES_EGL_image,GL_OES_EGL_image_external,GL_OES_EGL_image_external_essl3,GL_OES_EGL_sync,GL_OES_blend_equation_separate,GL_OES_blend_func_separate,GL_OES_blend_subtract,GL_OES_byte_coordinates,GL_OES_compressed_ETC1_RGB8_texture,GL_OES_compressed_paletted_texture,GL_OES_copy_image,GL_OES_depth24,GL_OES_depth_texture,GL_OES_depth_texture_cube_map,GL_OES_draw_buffers_indexed,GL_OES_draw_elements_base_vertex,GL_OES_draw_texture,GL_OES_element_index_uint,GL_OES_extended_matrix_palette,GL_OES_fbo_render_mipmap,GL_OES_fixed_point,GL_OES_framebuffer_object,GL_OES_geometry_shader,GL_OES_get_program_binary,GL_OES_gpu_shader5,GL_OES_mapbuffer,GL_OES_matrix_get,GL_OES_matrix_palette,GL_OES_packed_depth_stencil,GL_OES_point_size_array,GL_OES_point_sprite,GL_OES_primitive_bounding_box,GL_OES_query_matrix,GL_OES_read_format,GL_OES_required_internalformat,GL_OES_rgb8_rgba8,GL_OES_sample_shading,GL_OES_sample_variables,GL_OES_shader_image_atomic,GL_OES_shader_io_blocks,GL_OES_shader_multisample_interpolation,GL_OES_single_precision,GL_OES_standard_derivatives,GL_OES_stencil8,GL_OES_stencil_wrap,GL_OES_surfaceless_context,GL_OES_tessellation_shader,GL_OES_texture_3D,GL_OES_texture_border_clamp,GL_OES_texture_buffer,GL_OES_texture_compression_astc,GL_OES_texture_cube_map,GL_OES_texture_cube_map_array,GL_OES_texture_float_linear,GL_OES_texture_mirrored_repeat,GL_OES_texture_npot,GL_OES_texture_stencil8,GL_OES_texture_storage_multisample_2d_array,GL_OES_vertex_array_object,GL_OES_vertex_half_float,GL_OVR_multiview,GL_OVR_multiview2,GL_OVR_multiview_multisampled_render_to_texture",
    "HasFiveWayNavigation" to "false",
    "TouchScreen" to "3",
    "GL.Version" to "196610",
    "Build.VERSION.RELEASE" to "15",
    "Build.BOOTLOADER" to "tegu-16.0-13238451",
    "Build.MANUFACTURER" to "Google",
)

/** 本机 Build 字段（桌面端为空）。 */
internal expect fun localDeviceBuildProperties(): Map<String, String>

/**
 * 本机下发定向字段（桌面端为空）：ABI / 屏幕密度与分辨率 / 语言 / 系统版本。
 * 这些字段决定 Play 下发的 split 集，永远跟真机——不是真机就会发出不兼容的 APKs
 * （32 位机拿 arm64 so、旧系统被发高 minSdk 版本、dpi 切片对不上）。
 * 搜索已改网页抓取，Play 侧只承担下发，不再要求与预置 Vending 环境保持一致。
 */
internal expect fun localDeviceDeliveryConfig(): Map<String, String>

internal val GoogleDeviceProperties: Map<String, String>
    get() {
        val useSystem = GoogleDeviceSourceFromSystem && !GoogleDevicePropsPresetFallback
        // 关键字段缺失（非 ASCII 机型名、拿不到 Build）时不用本机指纹，
        // 否则 Play 会把它当成未知设备，搜索/详情全空。
        val fromSystem = if (useSystem) validatedDeviceBuildProperties(localDeviceBuildProperties()) else emptyMap()
        // 下发定向字段（ABI/屏幕/语言/系统版本）恒定取真机值；身份字段仍按指纹来源开关
        return GoogleDevicePropertiesDefaults + localDeviceDeliveryConfig() + fromSystem +
            GoogleDevicePropertiesOverrides + GoogleDeviceUserOverrides
    }

/** 只保留 ASCII 且关键字段齐全的本机指纹；不满足就丢空表（继续用预置指纹）。 */
private fun validatedDeviceBuildProperties(raw: Map<String, String>): Map<String, String> {
    if (raw.isEmpty()) return emptyMap()
    val ascii = raw.filterValues { value -> value.all { it.code in 0x20..0x7E } }
    val required = listOf("Build.BRAND", "Build.MODEL", "Build.DEVICE", "Build.PRODUCT", "Build.FINGERPRINT")
    if (required.any { ascii[it].isNullOrBlank() }) return emptyMap()
    if (!ascii.getValue("Build.FINGERPRINT").contains('/')) return emptyMap()
    return ascii
}
