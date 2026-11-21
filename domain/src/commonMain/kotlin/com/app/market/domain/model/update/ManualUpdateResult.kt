package com.app.market.domain.model.update

import com.app.market.domain.model.market.MarketAppInfo

data class ManualUpdateResult(
    val status: ManualUpdateStatus,
    val app: MarketAppInfo? = null,
    /** 失败原因补充说明（如「商店中查不到该应用」），空串表示用通用文案。 */
    val message: String = "",
)