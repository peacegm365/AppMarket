package com.app.market.data.local.preferences

import com.app.market.data.local.StringPreferenceKey

object TranslationPreferenceKeys {
    private const val NS = "translation_preferences"

    val Provider = StringPreferenceKey(NS, "provider")
    val AiBaseUrl = StringPreferenceKey(NS, "ai_base_url")
    val AiApiKey = StringPreferenceKey(NS, "ai_api_key")
    val AiModel = StringPreferenceKey(NS, "ai_model")
    val AiPrompt = StringPreferenceKey(NS, "ai_prompt")
}
