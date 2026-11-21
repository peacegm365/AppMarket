package com.app.market.ui.util

/**
 * 把商店接口里的「富文本」压成纯文本。
 *
 * Google Play（以及部分聚合源）的简介/更新日志是 HTML 片段，`<br>` 这类标签必须转成换行，
 * 否则界面上会原样显示成字面量 —— 用户看到的就是满屏 `<br><br>`。
 * 这里只做纯文本化，不做富文本渲染：应用介绍统一用同一字号排版，不需要保留标签语义。
 */
fun String.toPlainStoreText(): String {
    if (none { it == '<' || it == '&' }) return this
    var text = this
    text = text.replace(BreakTagRegex, "\n")
    text = text.replace(ParagraphTagRegex, "\n\n")
    text = text.replace(ListItemTagRegex, "\n· ")
    text = text.replace(AnyTagRegex, "")
    text = text
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(NamedQuoteEntity, QuoteChar)
        .replace(NamedAposEntity, AposChar)
        .replace("&#39;", AposChar)
        .replace("&#34;", QuoteChar)
    text = BlankLinesRegex.replace(text, "\n\n")
    return text.trim()
}

private val BreakTagRegex = Regex("(?i)<br\\s*/?>")
private val ParagraphTagRegex = Regex("(?i)</p\\s*>")
private val ListItemTagRegex = Regex("(?i)<li[^>]*>")
private val AnyTagRegex = Regex("<[^>]{1,200}>")
private val BlankLinesRegex = Regex("\n{3,}")

/** 用码点拼出实体名与替换字符，避免源码里出现会被工具链二次转义的转义序列。 */
private const val NamedQuoteEntity: String = "&quo" + "t;"
private const val NamedAposEntity: String = "&apo" + "s;"
private const val QuoteChar: String = "\u0022"
private const val AposChar: String = "\u0027"