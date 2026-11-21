package com.app.market.domain.repository

import com.app.market.domain.model.translation.TranslationProvider
import kotlinx.coroutines.flow.StateFlow

/** 详情页翻译：三个引擎共用一套配置（引擎 + AI 端点/密钥/模型）。 */
interface TranslationRepository {
    val provider: StateFlow<TranslationProvider>
    val aiBaseUrl: StateFlow<String>
    val aiApiKey: StateFlow<String>
    val aiModel: StateFlow<String>
    val aiPrompt: StateFlow<String>

    /** AI 翻译默认提示词：用户没写自定义提示词时生效，设置页展示原文供编辑。 */
    val defaultAiPrompt: String

    suspend fun setProvider(provider: TranslationProvider)
    suspend fun setAiBaseUrl(value: String)
    suspend fun setAiApiKey(value: String)
    suspend fun setAiModel(value: String)
    suspend fun setAiPrompt(value: String)

    /** 拉取 OpenAI 兼容 /models 的模型列表。 */
    suspend fun fetchModels(): List<String>

    /** 用当前引擎翻一句话，验证配置是否可用。 */
    suspend fun testConnection(): String

    /** 翻译成简体中文；失败抛异常，由界面展示原因。 */
    suspend fun translate(text: String): String
}