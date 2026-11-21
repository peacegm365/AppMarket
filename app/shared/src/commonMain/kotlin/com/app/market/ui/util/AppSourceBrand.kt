package com.app.market.ui.util

import androidx.compose.ui.graphics.Color
import com.app.market.domain.model.market.AppSource

/**
 * 来源品牌色：更新卡片「来源品牌化」底色用，取各商店标志性主色。
 * 实际使用时按比例混入卡片底色（见 UpdatesScreen），保证深浅主题下正文可读。
 */
fun appSourceBrandColor(source: AppSource): Color = when (source) {
    AppSource.XIAOMI -> Color(0xFFFF6900) // 小米橙
    AppSource.VIVO -> Color(0xFF415FFF) // vivo 蓝
    AppSource.WANDOUJIA -> Color(0xFF66BB44) // 豌豆荚绿
    AppSource.OPPO -> Color(0xFF00A651) // OPPO 绿
    AppSource.SAMSUNG -> Color(0xFF1428A0) // 三星蓝
    AppSource.HONOR -> Color(0xFF0A84FF) // 荣耀蓝
    AppSource.HUAWEI -> Color(0xFFC7000B) // 华为红
    AppSource.TAPTAP -> Color(0xFF28A8FF) // TapTap 蓝
    AppSource.COOLAPK -> Color(0xFF1AAD19) // 酷安绿
    AppSource.KUAIBAO -> Color(0xFFFF6A00) // 好游快爆橙
    AppSource.APPCHINA -> Color(0xFF00A0E9) // 应用汇蓝
    AppSource.TENCENT -> Color(0xFF00A4FF) // 应用宝蓝
    AppSource.BOX7723 -> Color(0xFF3EB575) // 7723 绿
    AppSource.FDROID -> Color(0xFF1976D2) // F-Droid 蓝
    AppSource.APKPURE -> Color(0xFF2CB472) // APKPure 绿
    AppSource.APTOIDE -> Color(0xFFFD6A00) // Aptoide 橙
    AppSource.GITHUB -> Color(0xFF24292F) // GitHub 墨黑
    AppSource.LSPOSED -> Color(0xFFD32F2F) // LSPosed 红
    AppSource.GITLAB -> Color(0xFFFC6D26) // GitLab 橙
    AppSource.GOOGLE -> Color(0xFF4285F4) // Google 蓝
}

/** AppMarket 自更新卡片的水印名：不带来源，直接写品牌名。 */
const val APP_MARKET_LABEL = "AppMarket"

/** AppMarket 自更新卡片的主品牌色。 */
val AppMarketBrandColor = Color(0xFF3B82F6)

// ---- Google Play 图标四色（从图标像素取样） ----
private val GoogleBlue = Color(0xFF4285F4)
private val GoogleGreen = Color(0xFF34A853)
private val GoogleYellow = Color(0xFFFBBC05)
private val GoogleRed = Color(0xFFEA4335)

/**
 * 来源标签文字用的渐变；没有专属配色的来源返回 null（用默认单色文字）。
 *
 * Google Play 用图标四色，但按主题各出一套：
 * 浅色主题用「深色版」四色（原色的黄/绿在浅底上几乎看不见），
 * 深色主题用「亮色版」四色。这样在任何背景下都有足够对比度，又保留品牌识别。
 */
fun appSourceLabelBrush(source: AppSource, dark: Boolean): androidx.compose.ui.graphics.Brush? =
    when (source) {
        AppSource.GOOGLE -> androidx.compose.ui.graphics.Brush.horizontalGradient(
            if (dark) {
                listOf(GoogleBlueOnDark, GoogleGreenOnDark, GoogleYellowOnDark, GoogleRedOnDark)
            } else {
                listOf(GoogleBlueOnLight, GoogleGreenOnLight, GoogleYellowOnLight, GoogleRedOnLight)
            },
        )

        else -> null
    }

/**
 * 「Google Play」逐字母配色。
 *
 * 用户给的参考就是 Google logo 本身的做法：不是横向渐变，而是每个字母各自一个纯色 ——
 * G 蓝 / o 红 / o 黄 / g 蓝 / l 绿 / e 红。渐变会让相邻字母之间出现过渡色，
 * 既不是 logo 的观感，饱和度也会被稀释掉。
 *
 * 返回的颜色按「Google」六个字母依次取用；超出 6 个的字符（" Play"）统一用蓝。
 */
fun appSourceLetterColors(source: AppSource, dark: Boolean): List<Color>? =
    when (source) {
        AppSource.GOOGLE -> if (dark) {
            listOf(
                GoogleBlueOnDark,
                GoogleRedOnDark,
                GoogleYellowOnDark,
                GoogleBlueOnDark,
                GoogleGreenOnDark,
                GoogleRedOnDark,
            )
        } else {
            listOf(
                GoogleBlueOnLight,
                GoogleRedOnLight,
                GoogleYellowOnLight,
                GoogleBlueOnLight,
                GoogleGreenOnLight,
                GoogleRedOnLight,
            )
        }

        else -> null
    }

// 浅色主题下的深色版（原色直接压在白底上，黄和绿对比度不足）
private val GoogleBlueOnLight = Color(0xFF1A73E8)
private val GoogleGreenOnLight = Color(0xFF188038)
private val GoogleYellowOnLight = Color(0xFFB06000)
private val GoogleRedOnLight = Color(0xFFD93025)

// 深色主题下的亮色版
private val GoogleBlueOnDark = Color(0xFF8AB4F8)
private val GoogleGreenOnDark = Color(0xFF81C995)
private val GoogleYellowOnDark = Color(0xFFFDD663)
private val GoogleRedOnDark = Color(0xFFF28B82)

/**
 * 来源标签底色。
 *
 * Google Play 的标签要「字样和底色都突出」：底色给到肉眼能看出的品牌蓝（不是 8% 那种几乎透明的），
 * 文字用同一色系的高对比版本，两边一起提亮，标签才立得住。
 */
fun appSourceLabelBackground(source: AppSource, dark: Boolean): androidx.compose.ui.graphics.Color? =
    when (source) {
        AppSource.GOOGLE -> if (dark) {
            GoogleBlueOnDark.copy(alpha = 0.22f)
        } else {
            GoogleBlueOnLight.copy(alpha = 0.16f)
        }

        else -> null
    }

/**
 * 来源专属渐变底色；没有专属渐变的来源返回 null（继续用「卡片底色混品牌色」的单色方案）。
 *
 * Google Play 的品牌识别就是那四个颜色，单取蓝色等于丢掉了它最有辨识度的部分，
 * 所以这里用「蓝→绿→黄→红」的横向渐变，颜色都带低透明度，保证正文可读。
 */
fun appSourceBrandBrush(source: AppSource, base: Color): androidx.compose.ui.graphics.Brush? =
    when (source) {
        AppSource.GOOGLE -> androidx.compose.ui.graphics.Brush.horizontalGradient(
            listOf(
                // 全部预先与卡片底色混合成不透明色：半透明色在渐变里会透出页面背景，深浅主题下发灰
                androidx.compose.ui.graphics.lerp(base, GoogleBlue, 0.22f),
                androidx.compose.ui.graphics.lerp(base, GoogleGreen, 0.22f),
                androidx.compose.ui.graphics.lerp(base, GoogleYellow, 0.18f),
                androidx.compose.ui.graphics.lerp(base, GoogleRed, 0.22f),
            ),
        )

        else -> null
    }

/** 自更新卡片底色：卡片底色 → 品牌色的横向渐变。 */
fun appMarketBrandBrush(
    base: Color,
): androidx.compose.ui.graphics.Brush =
    androidx.compose.ui.graphics.Brush.horizontalGradient(
        listOf(base, AppMarketBrandColor.copy(alpha = 0.22f)),
    )
