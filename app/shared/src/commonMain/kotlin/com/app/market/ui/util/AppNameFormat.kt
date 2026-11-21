package com.app.market.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import com.app.market.domain.model.market.AppSource

data class AppDisplayName(
    val appName: String,
    val description: String,
)

private val OppoAppDescription = Regex("（([^（）]*)）$")

fun String.splitAppDisplayName(source: AppSource?): AppDisplayName {
    val unchanged = AppDisplayName(this, "")
    return when (source) {
        AppSource.XIAOMI, AppSource.VIVO, AppSource.GOOGLE -> {
            val separator = indexOf('-')
            if (separator <= 0) return unchanged
            val appName = substring(0, separator).trim()
            if (appName.isEmpty()) return unchanged
            AppDisplayName(appName, substring(separator + 1).trim())
        }

        AppSource.OPPO -> {
            val match = OppoAppDescription.find(trimEnd()) ?: return unchanged
            val appName = substring(0, match.range.first).trim()
            if (appName.isEmpty()) return unchanged
            AppDisplayName(appName, match.groupValues[1].trim())
        }

        else -> unchanged
    }
}

val LocalStripAppNameSubtitle = compositionLocalOf { false }
/** 就地裁剪：去掉商店标题里的宣传副标题（始终生效）。 */
@Composable
@ReadOnlyComposable
fun appDisplayName(raw: String, source: AppSource?): String = raw.splitAppDisplayName(source).appName
/** 应用名显示：开关「获取真实应用名」开 → 本地真实名优先、关 → 商店名；宣传语一律就地裁剪。 */
@Composable
@ReadOnlyComposable
fun appDisplayName(app: com.app.market.domain.model.market.MarketAppInfo): String =
    (if (LocalStripAppNameSubtitle.current) app.localLabel.ifBlank { app.displayName } else app.displayName)
        .splitAppDisplayName(app.source).appName
/** 记录行应用名（安装/更新历史等）：开「获取真实应用名」且有本地真实名 → 真实名，否则就地裁剪记录里的名字。 */
@Composable
@ReadOnlyComposable
fun appDisplayName(raw: String, source: AppSource?, localLabel: String?): String {
    val base = if (LocalStripAppNameSubtitle.current) localLabel?.ifBlank { null } ?: raw else raw
    return base.splitAppDisplayName(source).appName
}