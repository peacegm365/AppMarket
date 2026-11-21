package com.app.market.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Alignment
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.app.market.ui.component.AppTextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.repository.ClashCoreController
import com.app.market.domain.repository.ClashState
import com.app.market.domain.repository.ProxyMode
import com.app.market.domain.repository.ProxySettingsRepository
import com.app.market.resources.Res
import com.app.market.resources.proxy_clash_core
import com.app.market.resources.proxy_clash_core_summary
import com.app.market.resources.proxy_clash_nodes_empty
import com.app.market.resources.proxy_clash_test
import com.app.market.resources.proxy_clash_testing
import com.app.market.resources.proxy_enable
import com.app.market.resources.proxy_enable_summary
import com.app.market.resources.proxy_mixed_port
import com.app.market.resources.proxy_mixed_port_summary
import com.app.market.resources.proxy_mode
import com.app.market.resources.proxy_mode_blacklist
import com.app.market.resources.proxy_mode_whitelist
import com.app.market.resources.proxy_node
import com.app.market.resources.proxy_sources_pick
import com.app.market.resources.proxy_subscription_url
import com.app.market.resources.proxy_title
import com.app.market.ui.component.MarketScaffold
import com.app.market.ui.component.PageVerticalPadding
import com.app.market.ui.util.appSourceLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.preference.SwitchPreference
import com.app.market.ui.component.AppSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 代理设置二级页。
 *
 * 总开关（= 连上远端节点）→ 订阅 / 测速 / 节点 → 生效模式 + 可折叠来源。
 * 流量直接走 AnyTLS 隧道：没有本地端口，也不跑内核进程。
 */
@Composable
fun ProxySettingsScreen(onBack: () -> Unit) {
    val repository = koinInject<ProxySettingsRepository>()
    val mode by repository.mode.collectAsStateWithLifecycle()
    val whitelist by repository.whitelist.collectAsStateWithLifecycle()
    val blacklist by repository.blacklist.collectAsStateWithLifecycle()
    val subscriptionUrl by repository.subscriptionUrl.collectAsStateWithLifecycle()

    val clash = koinInject<ClashCoreController>()
    val clashState by clash.state.collectAsStateWithLifecycle()
    val clashError by clash.lastError.collectAsStateWithLifecycle()
    val clashNodes by clash.nodes.collectAsStateWithLifecycle()
    val currentNode by clash.selectedNode.collectAsStateWithLifecycle()
    val delays by clash.delays.collectAsStateWithLifecycle()
    val timeouts by clash.timeouts.collectAsStateWithLifecycle()
    val testing by clash.testing.collectAsStateWithLifecycle()
    val subscriptionInfo by clash.subscriptionInfo.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var subscriptionInput by remember(subscriptionUrl) { mutableStateOf(subscriptionUrl) }
    var switchError by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sourcesExpanded by remember { mutableStateOf(false) }


    val running = clashState == ClashState.RUNNING
    // 上次选中的节点（持久化值）：连接后把保存的选择重新应用一次，
    // 页面这边在内核就绪后把保存的选择重新应用一次，否则重启 App 后节点总是回到第一个。
    val savedNode by repository.selectedNode.collectAsStateWithLifecycle()
    LaunchedEffect(running, clashNodes, savedNode) {
        if (!running) return@LaunchedEffect
        val target = savedNode.takeIf { it.isNotBlank() && it in clashNodes } ?: return@LaunchedEffect
        if (target == currentNode) return@LaunchedEffect
        runCatching { clash.selectNode(target) }
            .onFailure { switchError = it.message ?: it.toString() }
    }
    // 当前生效模式对应的那份名单
    val activeSources = if (mode == ProxyMode.WHITELIST) whitelist else blacklist
    // 已选中的排在最前，其余按来源顺序
    val availableSources = AppSource.orderLikeUpdateSources(AppSource.entries)
    val orderedSources = availableSources.sortedByDescending { it in activeSources }

    /** 起内核并把系统代理指向它（端口由内核决定，可能随机）。 */
    suspend fun startCore() {
        switchError = ""
        busy = true
        try {
            clash.start(subscriptionInput.ifBlank { subscriptionUrl })
            repository.setEnabled(true)
            val saved = repository.selectedNode.value
            if (saved.isNotBlank() && clash.nodes.value.contains(saved)) clash.selectNode(saved)
        } catch (e: Throwable) {
            switchError = e.message ?: e.toString()
        } finally {
            busy = false
        }
    }

    suspend fun stopCore() {
        busy = true
        try {
            runCatching { clash.stop() }
            repository.setEnabled(false)
        } finally {
            busy = false
        }
    }


    MarketScaffold(
        title = stringResource(Res.string.proxy_title),
        onBack = onBack,
    ) { innerPadding, backdropModifier, scrollBehavior ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(backdropModifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + PageVerticalPadding,
                bottom = innerPadding.calculateBottomPadding() + PageVerticalPadding,
            ),
        ) {
            // 1) 总开关：启用/断开代理
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SwitchPreference(
                        title = stringResource(Res.string.proxy_enable),
                        summary = if (running) {
                            "已连接 · " + currentNode
                        } else {
                            stringResource(Res.string.proxy_enable_summary)
                        },
                        checked = running,
                        enabled = !busy && clashState != ClashState.STARTING,
                        onCheckedChange = { on ->
                            scope.launch { if (on) startCore() else stopCore() }
                        },
                    )
                }
            }

            // 2) 订阅信息（机场塞在节点列表里的剩余流量 / 到期时间）
            if (subscriptionInfo.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 12.dp),
                    ) {
                        SectionHeader(
                            title = "订阅信息",
                            summary = "",
                        )
                        subscriptionInfo.forEach { line ->
                            Text(
                                text = line,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            )
                        }
                    }
                }
            }

            // 3) 订阅与节点
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
                    SectionHeader(
                        title = stringResource(Res.string.proxy_clash_core),
                        summary = stringResource(Res.string.proxy_clash_core_summary),
                    )
                    TextField(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        value = subscriptionInput,
                        label = stringResource(Res.string.proxy_subscription_url),
                        singleLine = true,
                        onValueChange = {
                            subscriptionInput = it.trim()
                            scope.launch { repository.setSubscriptionUrl(subscriptionInput) }
                        },
                    )
                    if (running && clashNodes.isNotEmpty()) {
                        // 测速：布局与同页其它行一致（左对齐），进度文本平滑撑开
                        BasicComponent(
                            modifier = Modifier.fillMaxWidth(),
                            // 测速进行中整行不可点（之前只是 onClick 里判空，按下去仍有水波纹反馈）
                            enabled = !testing,
                            onClick = {
                                if (!testing) clash.startDelayTest()
                            },
                        ) {
                            Text(
                                text = if (testing) {
                                    stringResource(Res.string.proxy_clash_testing)
                                } else {
                                    stringResource(Res.string.proxy_clash_test)
                                },
                                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            AnimatedVisibility(
                                visible = delays.isNotEmpty(),
                                enter = expandVertically(
                                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                                ) + fadeIn(animationSpec = tween(180)),
                                exit = shrinkVertically(
                                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                                ) + fadeOut(animationSpec = tween(120)),
                            ) {
                                Text(
                                    text = delaySummary(delays, clashNodes.size),
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        // 节点切换：与「来源设置」等其它下拉完全同一套写法（AppSpinnerPreference →
                        // miuix GlassDropdownPreference），位置/动画/尺寸全由组件决定，不自绘弹层。
                        // 延迟与超时只作为选项文字的一部分（组件只接受纯字符串）。
                        val nodeNames = clashNodes.distinct()
                        // miuix 的选项行按 216dp 截断，整串超长时会把尾巴（延迟）吃掉。
                        // 所以先按「显示宽度」把名称收短（名称才允许被挤压），再拼延迟 / 超时。
                        val nodeItems = nodeNames.map { name ->
                            val ms = delays[name]
                            when {
                                ms != null -> "${nodeLabelBudget(name, 9.0)} · ${ms}ms"
                                name in timeouts -> "${nodeLabelBudget(name, 10.0)} · 超时"
                                else -> name
                            }
                        }
                        AppSpinnerPreference(
                            items = nodeItems,
                            selectedIndex = nodeNames.indexOf(currentNode).coerceAtLeast(0),
                            title = stringResource(Res.string.proxy_node),
                            onSelectedIndexChange = { index ->
                                val target = nodeNames.getOrNull(index)
                                if (target != null) {
                                    scope.launch {
                                        runCatching { clash.selectNode(target) }
                                            .onSuccess { repository.setSelectedNode(target) }
                                            .onFailure { switchError = it.message ?: it.toString() }
                                    }
                                }
                            },
                        )
                    } else if (running) {
                        Text(
                            text = stringResource(Res.string.proxy_clash_nodes_empty),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp),
                        )
                    }
                    // 失败原因只显示短摘要，不把内核整段日志糊到界面上
                    val errorText = switchError.ifBlank { if (clashState == ClashState.FAILED) clashError else "" }
                    if (errorText.isNotBlank()) {
                        Text(
                            text = errorText.lineSequence().firstOrNull().orEmpty().take(120),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 12.dp),
                        )
                    }
                }
            }

            // 3) 生效模式 + 可折叠来源
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    AppSpinnerPreference(
                        title = stringResource(Res.string.proxy_mode),
                        // 传 null 而不是空串：miuix 在有 summary 时才把标题排到上方，
                        // 空串会让标题偏上、看起来没居中
                        summary = null,
                        items = listOf(
                            stringResource(Res.string.proxy_mode_whitelist),
                            stringResource(Res.string.proxy_mode_blacklist),
                        ),
                        selectedIndex = if (mode == ProxyMode.BLACKLIST) 1 else 0,
                        onSelectedIndexChange = {
                            scope.launch {
                                // 只切名单，不强制收起：展开状态保留，内容随模式平滑替换
                                repository.setMode(if (it == 1) ProxyMode.BLACKLIST else ProxyMode.WHITELIST)
                            }
                        },
                    )
                    ExpandableRow(
                        title = stringResource(Res.string.proxy_sources_pick),
                        value = "${activeSources.size}/${availableSources.size}",
                        expanded = sourcesExpanded,
                        onToggle = { sourcesExpanded = !sourcesExpanded },
                    ) {
                        orderedSources.forEach { source ->
                            SwitchPreference(
                                title = appSourceLabel(source),
                                summary = "",
                                checked = source in activeSources,
                                onCheckedChange = { checked ->
                                    scope.launch { repository.setSourceIncluded(source, checked) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 「12/87 · 最快 96 · 平均 143 ms」；只测到一半也照样显示。 */
/**
 * 按「显示宽度」截断节点名，给延迟腾出位置。
 *
 * 中日韩/全角字符算 1 个单位，ASCII 算 0.5 个单位（大致等于 body1 字号的宽度比）；
 * 超出 [budget] 就用省略号收尾，保证拼上「 · 1155ms」后整串不会被 miuix 的 216dp 上限截掉。
 */
private fun nodeLabelBudget(name: String, budget: Double): String {
    var units = 0.0
    val sb = StringBuilder()
    for (ch in name) {
        val w = if (ch.code < 0x2E80) 0.5 else 1.0
        if (units + w > budget) {
            sb.append('…')
            return sb.toString()
        }
        sb.append(ch)
        units += w
    }
    return name
}

private fun delaySummary(delays: Map<String, Int>, total: Int): String {
    if (delays.isEmpty()) return ""
    val sorted = delays.values.sorted()
    val avg = sorted.average().toInt()
    return "${sorted.size}/$total · 最快 ${sorted.first()} · 平均 $avg ms"
}

/**
 * 卡片内的分组标题。
 *
 * 字号与 miuix `BasicComponent` 的 title/summary 完全一致（headline1 / body2）——
 * 之前这里用了 body2 / footnote1，比卡片里其它行的标题小一大截，看起来像两套字体。
 */
@Composable
private fun SectionHeader(title: String, summary: String) {
    Text(
        text = title,
        fontSize = MiuixTheme.textStyles.headline1.fontSize,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier.padding(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = if (summary.isBlank()) 6.dp else 2.dp,
        ),
    )
    if (summary.isNotBlank()) {
        Text(
            text = summary,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
    }
}

/**
 * 扁平的可展开行。
 *
 * 底层用 miuix 的 [BasicComponent]，行高/内边距/标题字号与同页 [WindowDropdownPreference] 一致；
 * 右侧值与指示器逐字段照抄其内部实现（body2 字号、onSurfaceVariantActions 颜色、
 * 末尾 8dp 内缩、↕ 上下双箭头），保证整页视觉统一。
 */
@Composable
private fun ExpandableRow(
    title: String,
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val actionColor = MiuixTheme.colorScheme.onSurfaceVariantActions
    val colorFilter = remember(actionColor) { ColorFilter.tint(actionColor) }
    // 收起时箭头指向下、展开时指向上（同一枚右箭头旋转实现，过渡带动画）
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 270f else 90f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "expandArrow",
    )

    BasicComponent(
        title = title,
        endActions = {
            Text(
                text = value,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .align(Alignment.CenterVertically)
                    .weight(1f, fill = false),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = actionColor,
                textAlign = TextAlign.End,
            )
            Image(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(width = 10.dp, height = 16.dp)
                    .rotate(arrowRotation)
                    .align(Alignment.CenterVertically),
                imageVector = MiuixIcons.Basic.ArrowRight,
                colorFilter = colorFilter,
                contentDescription = null,
            )
        },
        onClick = onToggle,
    )

    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        ) + fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = shrinkVertically(
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        ) + fadeOut(animationSpec = tween(durationMillis = 120)),
    ) {
        Column { content() }
    }
}