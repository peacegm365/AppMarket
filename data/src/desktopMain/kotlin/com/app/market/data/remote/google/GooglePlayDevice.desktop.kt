package com.app.market.data.remote.google

/** 桌面端没有 Android Build：返回空表，继续用预置指纹。 */
internal actual fun localDeviceBuildProperties(): Map<String, String> = emptyMap()

/** 桌面端没有真机硬件配置：返回空表，继续用预置值。 */
internal actual fun localDeviceDeliveryConfig(): Map<String, String> = emptyMap()
