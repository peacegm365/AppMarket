package com.app.market.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.market.domain.model.translation.TranslationProvider
import com.app.market.domain.repository.TranslationRepository
import com.app.market.ui.component.AppSpinnerPreference
import com.app.market.ui.component.AppTextButton
import com.app.market.ui.component.MarketScaffold
import com.app.market.ui.component.PageVerticalPadding
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 设置 → 通用 → 翻译：选引擎 + AI 端点配置（详情页右上角翻译按钮用这套配置）。 */
@Composable
fun TranslationSettingsScreen(onBack: () -> Unit) {
    val translations = koinInject<TranslationRepository>()
    val provider by translations.provider.collectAsStateWithLifecycle()
    val aiBaseUrl by translations.aiBaseUrl.collectAsStateWithLifecycle()
    val aiApiKey by translations.aiApiKey.collectAsStateWithLifecycle()
    val aiModel by translations.aiModel.collectAsStateWithLifecycle()
    val aiPrompt by translations.aiPrompt.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var baseUrlDraft by remember(aiBaseUrl) { mutableStateOf(aiBaseUrl) }
    var apiKeyDraft by remember(aiApiKey) { mutableStateOf(aiApiKey) }
    var modelDraft by remember(aiModel) { mutableStateOf(aiModel) }
    // 翻译提示词展示生效中的提示词（默认提示词原文），用户直接在此修改；
    // 只在未编辑过时跟随 aiPrompt（异步加载的已存值），编辑后不被回写覆盖。
    var promptDraft by remember { mutableStateOf(translations.defaultAiPrompt) }
    var promptEdited by remember { mutableStateOf(false) }
    LaunchedEffect(aiPrompt) {
        if (!promptEdited) promptDraft = aiPrompt.ifBlank { translations.defaultAiPrompt }
    }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var testResult by remember { mutableStateOf("") }
    var testFailed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    MarketScaffold(title = "翻译", onBack = onBack) { innerPadding, backdropModifier, scrollBehavior ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(backdropModifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = PageVerticalPadding,
                end = PageVerticalPadding,
                top = innerPadding.calculateTopPadding() + PageVerticalPadding,
                bottom = innerPadding.calculateBottomPadding() + PageVerticalPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    AppSpinnerPreference(
                        items = TranslationProvider.displayNames,
                        selectedIndex = provider.ordinal,
                        title = "翻译引擎",
                        summary = "详情页右上角翻译按钮使用的服务",
                        onSelectedIndexChange = { index ->
                            val target = TranslationProvider.entries.getOrNull(index) ?: return@AppSpinnerPreference
                            scope.launch { translations.setProvider(target) }
                        },
                    )
                }
            }
            if (provider == TranslationProvider.AI) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            TextField(
                                value = baseUrlDraft,
                                onValueChange = {
                                    baseUrlDraft = it
                                    scope.launch { translations.setAiBaseUrl(it) }
                                },
                                label = "接口地址",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextField(
                                value = apiKeyDraft,
                                onValueChange = {
                                    apiKeyDraft = it
                                    scope.launch { translations.setAiApiKey(it) }
                                },
                                label = "API Key",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                TextField(
                                    value = modelDraft,
                                    onValueChange = {
                                        modelDraft = it
                                        scope.launch { translations.setAiModel(it) }
                                    },
                                    label = "模型 ID",
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                AppTextButton(
                                    text = if (busy) "拉取中" else "拉取模型",
                                    enabled = !busy,
                                    onClick = {
                                        busy = true
                                        testResult = ""
                                        scope.launch {
                                            runCatching { translations.fetchModels() }
                                                .onSuccess { models = it }
                                                .onFailure {
                                                    testFailed = true
                                                    testResult = it.message ?: "拉取失败"
                                                }
                                            busy = false
                                        }
                                    },
                                )
                            }
                            if (models.isNotEmpty()) {
                                AppSpinnerPreference(
                                    items = models,
                                    selectedIndex = models.indexOf(modelDraft).coerceAtLeast(0),
                                    title = "选择模型",
                                    showValue = true,
                                    onSelectedIndexChange = { index ->
                                        val picked = models.getOrNull(index) ?: return@AppSpinnerPreference
                                        modelDraft = picked
                                        scope.launch { translations.setAiModel(picked) }
                                    },
                                )
                            }
                            TextField(
                                value = promptDraft,
                                onValueChange = {
                                    promptEdited = true
                                    promptDraft = it
                                    scope.launch { translations.setAiPrompt(it) }
                                },
                                label = "翻译提示词",
                                modifier = Modifier.fillMaxWidth(),
                            )
                            // 只有 AI 需要「测试连接」：URL/Key/模型都可能填错
                            AppTextButton(
                                text = if (busy) "测试中…" else "测试连接",
                                enabled = !busy,
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                onClick = {
                                    busy = true
                                    testResult = ""
                                    testFailed = false
                                    scope.launch {
                                        runCatching { translations.testConnection() }
                                            .onSuccess { testResult = "可用：" + it.take(60) }
                                            .onFailure {
                                                testFailed = true
                                                testResult = it.message ?: "测试失败"
                                            }
                                        busy = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (testResult.isNotBlank()) {
                                Text(
                                    text = testResult,
                                    style = MiuixTheme.textStyles.body2,
                                    color = if (testFailed) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}