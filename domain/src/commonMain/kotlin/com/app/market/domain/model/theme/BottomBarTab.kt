package com.app.market.domain.model.theme

/** 底部导航栏标签；key 用于隐藏偏好的持久化。 */
enum class BottomBarTab(val key: String) {
    TODAY("today"),
    RANKING("ranking"),
    UPDATES("updates"),
    SEARCH("search"),
    DOWNLOADS("downloads"),
}