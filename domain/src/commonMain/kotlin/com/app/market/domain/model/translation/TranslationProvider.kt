package com.app.market.domain.model.translation

/** 详情页翻译可选的三个引擎。 */
enum class TranslationProvider {
    GOOGLE,
    MICROSOFT,
    AI;

    companion object {
        val displayNames = listOf("Google翻译", "微软翻译", "AI翻译")

        fun parse(raw: String?): TranslationProvider =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: GOOGLE
    }
}
