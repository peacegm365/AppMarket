package com.app.market.ui.util

/**
 * 极简 Markdown → HTML 转换。
 *
 * 用途：发布说明（Release notes）通常是 `.md` 写的，直接当纯文本显示会满屏 `**`、`-`；
 * 而详情页已经有按 HTML 渲染富文本的 [com.app.market.ui.component.ModuleReadmeText]，
 * 所以这里只做「够用」的转换：标题 / 段落 / 无序有序列表 / 引用 / 分割线 / 代码块 /
 * 行内代码 / 粗体 / 斜体 / 链接 / 图片 / 简单表格。
 */
fun markdownToHtml(md: String): String {
    if (md.isBlank()) return ""
    val lines = md.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val out = StringBuilder()
    var i = 0

    while (i < lines.size) {
        val line = lines[i].trimEnd()
        val trimmed = line.trim()

        // 空行：段落分隔
        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 代码块 ```lang
        if (trimmed.startsWith("```")) {
            i++
            val code = StringBuilder()
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                code.append(lines[i]).append('\n')
                i++
            }
            if (i < lines.size) i++ // 跳过结束的 ```
            out.append("<pre><code>").append(escapeHtml(code.toString().trimEnd())).append("</code></pre>\n")
            continue
        }

        // 标题 #..######
        val heading = Regex("^(#{1,6})\\s+(.*)$").find(trimmed)
        if (heading != null) {
            val level = heading.groupValues[1].length
            out.append("<h").append(level).append('>')
                .append(inline(heading.groupValues[2]))
                .append("</h").append(level).append(">\n")
            i++
            continue
        }

        // 分割线
        if (Regex("^(-{3,}|\\*{3,}|_{3,})$").matches(trimmed)) {
            out.append("<hr/>\n")
            i++
            continue
        }

        // 表格：| a | b |  +  | --- | --- |
        if (trimmed.startsWith("|") && i + 1 < lines.size && isTableDivider(lines[i + 1].trim())) {
            val header = splitRow(trimmed)
            i += 2
            out.append("<table><tr>")
            header.forEach { out.append("<th>").append(inline(it)).append("</th>") }
            out.append("</tr>")
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                val cells = splitRow(lines[i].trim())
                out.append("<tr>")
                cells.forEach { out.append("<td>").append(inline(it)).append("</td>") }
                out.append("</tr>")
                i++
            }
            out.append("</table>\n")
            continue
        }

        // 引用
        if (trimmed.startsWith(">")) {
            val quote = StringBuilder()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                if (quote.isNotEmpty()) quote.append(' ')
                quote.append(lines[i].trim().removePrefix(">").trim())
                i++
            }
            out.append("<blockquote>").append(inline(quote.toString())).append("</blockquote>\n")
            continue
        }

        // 无序列表
        val bullet = Regex("^[-*+]\\s+(.*)$")
        if (bullet.matches(trimmed)) {
            out.append("<ul>")
            while (i < lines.size) {
                val m = bullet.find(lines[i].trim()) ?: break
                out.append("<li>").append(inline(m.groupValues[1])).append("</li>")
                i++
            }
            out.append("</ul>\n")
            continue
        }

        // 有序列表
        val ordered = Regex("^\\d+[.)]\\s+(.*)$")
        if (ordered.matches(trimmed)) {
            out.append("<ol>")
            while (i < lines.size) {
                val m = ordered.find(lines[i].trim()) ?: break
                out.append("<li>").append(inline(m.groupValues[1])).append("</li>")
                i++
            }
            out.append("</ol>\n")
            continue
        }

        // 普通段落：连续非空行合并
        val para = StringBuilder()
        while (i < lines.size) {
            val t = lines[i].trim()
            if (t.isEmpty()) break
            if (t.startsWith("#") || t.startsWith("```") || t.startsWith(">") ||
                bullet.matches(t) || ordered.matches(t) || t.startsWith("|")
            ) break
            if (para.isNotEmpty()) para.append(' ')
            para.append(t)
            i++
        }
        out.append("<p>").append(inline(para.toString())).append("</p>\n")
    }
    return out.toString()
}

private fun isTableDivider(line: String): Boolean =
    line.startsWith("|") && line.replace("|", "").replace(":", "").replace("-", "").isBlank()

private fun splitRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

/** 行内标记：图片 / 链接 / 粗体 / 斜体 / 行内代码。先转义再套标签。 */
private fun inline(text: String): String {
    var s = escapeHtml(text)
    // 行内代码优先，避免里面的 * _ 被当成强调
    val codes = ArrayList<String>()
    s = Regex("`([^`]+)`").replace(s) { m ->
        codes += "<code>" + m.groupValues[1] + "</code>"
        "\u0000CODE${codes.lastIndex}\u0000"
    }
    // 图片
    s = Regex("!\\[([^\\]]*)]\\(([^)\\s]+)\\)").replace(s) {
        val alt = it.groupValues[1]
        "<img src=\"${it.groupValues[2]}\" alt=\"$alt\"/>"
    }
    // 链接
    s = Regex("\\[([^\\]]+)]\\(([^)\\s]+)\\)").replace(s) {
        "<a href=\"${it.groupValues[2]}\">${it.groupValues[1]}</a>"
    }
    // 裸链接
    s = Regex("(?<![\"(=])(https?://[^\\s<]+)").replace(s) { "<a href=\"${it.groupValues[1]}\">${it.groupValues[1]}</a>" }
    // 粗体 / 斜体
    s = Regex("\\*\\*([^*]+)\\*\\*").replace(s) { "<strong>${it.groupValues[1]}</strong>" }
    s = Regex("__([^_]+)__").replace(s) { "<strong>${it.groupValues[1]}</strong>" }
    s = Regex("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)").replace(s) { "<em>${it.groupValues[1]}</em>" }
    // 还原行内代码
    s = Regex("\u0000CODE(\\d+)\u0000").replace(s) { m ->
        codes.getOrNull(m.groupValues[1].toIntOrNull() ?: -1) ?: ""
    }
    return s
}

private fun escapeHtml(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")