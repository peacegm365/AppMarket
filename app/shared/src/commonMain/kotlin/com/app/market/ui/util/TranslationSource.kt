package com.app.market.ui.util
import com.app.market.domain.model.market.AppDetail
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.ModuleRelease
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** HTML → 纯文本：翻译接口只吃文本，标签/脚本先清掉。 */
fun htmlToPlainText(html: String): String {
    if (html.isBlank()) return ""
    var text = html
    text = text.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
    text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
    // 保留结构标记：标题用 #，列表用 -，段落空行。
    // 翻译只改文字，符号留着，译文再用 markdownToHtml 渲染回原来的层级/列表。
    for (level in 1..6) {
        text = text.replace(
            Regex("(?is)<h$level[^>]*>\\s*(.*?)\\s*</h$level>"),
            "\n\n" + "#".repeat(level) + " $1\n\n",
        )
    }
    text = text.replace(Regex("(?i)<li[^>]*>\\s*"), "\n- ")
    text = text.replace(Regex("(?i)</li>"), "")
    text = text.replace(Regex("(?i)</(p|div|tr)>"), "\n\n")
    text = text.replace(Regex("(?s)<[^>]+>"), " ")
    text = text
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;quot;", "\u0022")
        .replace("&#39;", "'")
    text = text.replace(Regex("[ \\t]+"), " ")
    text = text.replace(Regex(" *\n *"), "\n")
    text = text.replace(Regex("\n{3,}"), "\n\n")
    return text.trim()
}
/** 原地翻译的一段：keep=true 原样保留（标签/代码/链接地址），否则翻 [text]。 */
private class TransSeg(val keep: Boolean, var text: String)
/** 值得翻：至少一个字母/汉字；纯符号、数字、空白不动。 */
private fun hasTranslatableText(text: String): Boolean =
    Regex("[A-Za-z\u00C0-\u024F\u0400-\u04FF\u3040-\u30FF\u4E00-\u9FFF]").containsMatchIn(text)
/** 并发翻译可翻段；单段失败保留原文，不放大成整体失败。 */
private suspend fun translateSegs(segs: List<TransSeg>, translate: suspend (String) -> String) {
    coroutineScope {
        val gate = Semaphore(4)
        segs.mapNotNull { seg ->
            if (seg.keep || !hasTranslatableText(seg.text)) null
            else async {
                gate.withPermit {
                    val out = runCatching { translate(seg.text) }.getOrNull()
                    if (!out.isNullOrBlank()) seg.text = out
                }
            }
        }.awaitAll()
    }
}
/**
 * HTML 原地翻译：只翻文本节点，标签/图片/链接/颜色/排版原样保留。
 * script/style 整块跳过，code/pre 内容不翻（代码标识符翻了就废了）。
 */
suspend fun translateHtmlInPlace(html: String, translate: suspend (String) -> String): String {
    val segs = ArrayList<TransSeg>()
    var inCode = false
    for (m in Regex("(?is)<script[^>]*>.*?</script>|<style[^>]*>.*?</style>|<[^>]+>|[^<]+").findAll(html)) {
        val t = m.value
        if (t.startsWith("<")) {
            if (Regex("(?i)^<(code|pre)\\b").containsMatchIn(t)) inCode = true
            if (Regex("(?i)^</(code|pre)>").containsMatchIn(t)) inCode = false
            segs += TransSeg(keep = true, text = t)
        } else {
            segs += TransSeg(keep = inCode, text = t)
        }
    }
    translateSegs(segs, translate)
    return segs.joinToString("") { it.text }
}
/**
 * Markdown/轻 HTML 原地翻译：只翻正文文字。代码块、行内代码、图片、
 * 链接地址、裸链接、HTML 标签原样保留；链接的 [文字] 照翻。
 */
suspend fun translateMarkdownInPlace(md: String, translate: suspend (String) -> String): String {
    val segs = ArrayList<TransSeg>()
    val protected = Regex(
        "(?s)(```[\\s\\S]*?```)|(~~~[\\s\\S]*?~~~)|(`[^`\\n]*`)|(!\\[[^\\]]*\\]\\([^)]*\\))|(\\[[^\\]]*\\]\\([^)]*\\))|(https?://[^\\s)\\]>]+)|(<[^>]+>)",
    )
    var last = 0
    for (m in protected.findAll(md)) {
        if (m.range.first > last) segs += TransSeg(keep = false, text = md.substring(last, m.range.first))
        val v = m.value
        if (m.groups[5] != null) {
            // 链接 [文字](地址)：只翻文字，括号/地址原样
            val idx = v.lastIndexOf("](")
            if (idx > 0) {
                segs += TransSeg(keep = true, text = "[")
                segs += TransSeg(keep = false, text = v.substring(1, idx))
                segs += TransSeg(keep = true, text = v.substring(idx))
            } else {
                segs += TransSeg(keep = true, text = v)
            }
        } else {
            segs += TransSeg(keep = true, text = v)
        }
        last = m.range.last + 1
    }
    if (last < md.length) segs += TransSeg(keep = false, text = md.substring(last))
    translateSegs(segs, translate)
    return segs.joinToString("") { it.text }
}

/**
 * 详情页要翻译的内容：
 * - 仓库来源（GitHub / LSPosed）：Releases 页签翻版本说明，其余翻 README
 * - 普通来源：简介 + 详情 + 更新日志
 */
fun detailTranslationSource(
    detail: AppDetail,
    releases: List<ModuleRelease>,
): String {
    if (detail.app.source in AppSource.RepositorySources) {
        // README 与 Releases 一起翻：详情页两个页签的内容一次拿到，用户不用来回切
        val builder = StringBuilder()
        val readme = htmlToPlainText(detail.introductionHtml)
        if (readme.isNotBlank()) builder.append(readme)
        val releaseText = releases.take(6).joinToString("\n\n") { release ->
            buildString {
                append(release.tagName.ifBlank { release.name })
                val log = htmlToPlainText(release.changeLog)
                if (log.isNotBlank()) {
                    append('\n')
                    append(log)
                }
            }
        }
        if (releaseText.isNotBlank()) {
            if (builder.isNotEmpty()) builder.append("\n\n")
            builder.append(releaseText)
        }
        if (builder.isNotBlank()) return builder.toString()
    }
    return listOf(detail.brief, detail.introduction, detail.changeLog)
        .map { htmlToPlainText(it) }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
}