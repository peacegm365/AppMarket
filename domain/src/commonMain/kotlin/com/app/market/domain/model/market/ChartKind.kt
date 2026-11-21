package com.app.market.domain.model.market

/** Google Play 榜单类型（Aurora 网页通道提供全四种）：免费 / 付费 / 畅销 / 飙升。 */
enum class ChartKind {
    FREE,
    PAID,
    GROSSING,
    MOVERS,
}