package com.app.market.domain.model.theme

/** 更新 / 下载安装历史里「来源」标记的展示方式。 */
enum class DownloadSourceBadge(val key: String) {
    /** 不显示来源 */
    OFF("off"),

    /** 仅应用名右侧一个小圆点指示器 */
    PLAIN("plain"),

    /** 来源品牌色胶囊 */
    BRAND("brand"),
    ;

    companion object {
        fun fromKey(raw: String?): DownloadSourceBadge =
            entries.firstOrNull { it.key == raw } ?: BRAND
    }
}
