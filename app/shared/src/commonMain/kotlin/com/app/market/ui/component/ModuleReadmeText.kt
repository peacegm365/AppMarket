package com.app.market.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * LSPosed 模块 README 渲染（对齐 GitHub 的显示方式）。
 *
 * 仓库返回的 `readmeHTML` 是 GitHub 从 README.md 渲染出的富文本，这里实现一个**面向 README 的
 * 极小 HTML→富文本** 渲染器：标题分级、正文、列表、引用、代码块、分隔线、**图片**、**表格（真网格）**，
 * 行内支持加粗/斜体/行内代码/链接；链接点击交给 [onOpenLink]，锚点（#xxx）就地忽略。
 */
@Composable
fun ModuleReadmeText(
    html: String,
    modifier: Modifier = Modifier,
    onOpenLink: (String) -> Unit = {},
    /**
     * 仓库的 raw 根地址，例如 https://raw.githubusercontent.com/tiann/KernelSU/main/
     * 用来补全 README 里的相对链接（GitHub 返回的 HTML 里相对链接没有前缀，
     * `/repos/../readme` 接口又不带 docBase，不补就整排语言链接都点不了）。
     */
    linkBaseUrl: String = "",
) {
    val blocks = remember(html, linkBaseUrl) { parseReadmeBlocks(html, linkBaseUrl) }
    if (blocks.isEmpty()) return
    val linkColor = MiuixTheme.colorScheme.primary
    val codeSurface = MiuixTheme.colorScheme.surfaceContainerHigh
    val borderColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val bodyStyle = MiuixTheme.textStyles.body2.copy(lineHeight = 22.sp)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            val text = remember(block, linkColor, onOpenLink) { block.toAnnotated(linkColor, onOpenLink) }
            when (block.kind) {
                ReadmeBlockKind.CODE -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(codeSurface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = text,
                        style = bodyStyle.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        ),
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }

                ReadmeBlockKind.BULLET -> Row(modifier = Modifier.padding(start = 2.dp)) {
                    Text(
                        text = "\u2022",
                        style = bodyStyle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = text,
                        style = bodyStyle,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }

                ReadmeBlockKind.QUOTE -> Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .padding(end = 10.dp)
                            .height(if (text.length > 0) 20.dp else 0.dp)
                            .width(3.dp)
                            .background(borderColor),
                    )
                    Text(
                        text = text,
                        style = bodyStyle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.weight(1f),
                    )
                }

                ReadmeBlockKind.TITLE, ReadmeBlockKind.HEADING1 -> Text(
                    text = text,
                    style = bodyStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 27.sp),
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )

                ReadmeBlockKind.HEADING2 -> Column {
                    Text(
                        text = text,
                        style = bodyStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp),
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(codeSurface),
                    )
                }

                ReadmeBlockKind.HEADING3 -> Text(
                    text = text,
                    style = bodyStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp),
                    color = MiuixTheme.colorScheme.onSurface,
                )

                ReadmeBlockKind.PARAGRAPH -> Text(
                    text = text,
                    style = bodyStyle,
                    color = MiuixTheme.colorScheme.onSurface,
                )

                // <hr>：README 里常用的分节线
                ReadmeBlockKind.DIVIDER -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .height(1.dp)
                        .background(codeSurface),
                )

                // 图片：README 里的截图（GitHub 已经把地址补成绝对 URL）
                ReadmeBlockKind.IMAGE -> {
                    val url = block.imageUrl.orEmpty()
                    if (url.isNotBlank()) {
                        AppAsyncImage(
                            url = url,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(codeSurface.copy(alpha = 0.25f)),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }

                // 表格：真网格（等宽列 + 表头底色 + 行分隔线），和 GitHub 一致
                ReadmeBlockKind.TABLE -> {
                    val table = block.table ?: return@forEach
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(codeSurface.copy(alpha = 0.35f)),
                    ) {
                        table.rows.forEachIndexed { rowIndex, row ->
                            val isHeader = table.headerRows.contains(rowIndex)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isHeader) codeSurface else Color.Transparent),
                                verticalAlignment = Alignment.Top,
                            ) {
                                row.forEach { cell ->
                                    val cellImage = cell.firstOrNull { it.imageUrl != null }?.imageUrl
                                    if (cellImage != null) {
                                        AppAsyncImage(
                                            url = cellImage,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 6.dp, vertical = 6.dp)
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.FillWidth,
                                        )
                                        return@forEach
                                    }
                                    Text(
                                        text = remember(cell, linkColor, onOpenLink) { cell.toAnnotated(linkColor, onOpenLink) },
                                        style = bodyStyle.copy(
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
                                        ),
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                    )
                                }
                            }
                            if (rowIndex != table.rows.lastIndex) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(borderColor.copy(alpha = 0.35f)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal enum class ReadmeBlockKind {
    TITLE, HEADING1, HEADING2, HEADING3, PARAGRAPH, BULLET, CODE, QUOTE, DIVIDER, TABLE, IMAGE,
}

internal data class ReadmeSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val mono: Boolean = false,
    val link: String? = null,
    val imageUrl: String? = null,
)

internal data class ReadmeTable(
    val rows: List<List<List<ReadmeSpan>>>,
    val headerRows: Set<Int>,
)

internal data class ReadmeBlock(
    val kind: ReadmeBlockKind,
    val spans: List<ReadmeSpan> = emptyList(),
    val table: ReadmeTable? = null,
    val imageUrl: String? = null,
) {
    fun toAnnotated(linkColor: Color, onOpenLink: (String) -> Unit): AnnotatedString =
        spans.toAnnotated(linkColor, onOpenLink)
}

internal fun List<ReadmeSpan>.toAnnotated(
    linkColor: Color,
    onOpenLink: (String) -> Unit = {},
): AnnotatedString = buildAnnotatedString {
    forEach { span ->
        val start = length
        append(span.text)
        if (span.text.isEmpty()) return@forEach
        if (span.bold) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), start, length)
        if (span.italic) addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
        if (span.mono) addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, length)
        val href = span.link
        if (href != null) {
            addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), start, length)
            addLink(
                LinkAnnotation.Clickable(
                    tag = href,
                    styles = TextLinkStyles(
                        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    ),
                    linkInteractionListener = { onOpenLink(href) },
                ),
                start,
                length,
            )
        }
    }
}

private val TagRegex = Regex("(?s)<[^>]*>")
private val BlockNoiseRegex = Regex("(?is)<(script|style|svg|head|template)[^>]*>.*?</\\1>")
private val AnchorPermalinkRegex = Regex("(?is)<a[^>]*class=\"anchor\"[^>]*>.*?</a>")
private val ImageTagRegex = Regex("(?is)<img[^>]*>")
private val ImageSrcRegex = Regex("(?is)src=\"([^\"]*)\"")
private val WhitespaceRegex = Regex("[ \t\r\n\u00a0]+")
private val TableRegex = Regex("(?is)<table[^>]*>(.*?)</table>")
private val RowRegex = Regex("(?is)<tr[^>]*>(.*?)</tr>")
private val CellRegex = Regex("(?is)<t([hd])[^>]*>(.*?)</t\\1>")
private val CheckedInputRegex = Regex("(?is)<input[^>]*checked[^>]*>")
private val InputRegex = Regex("(?is)<input[^>]*>")
private val LinkHrefRegex = Regex("href=\"([^\"]*)\"")
/** assets.lsposed.org 的仓库根（含 commit sha），用来把 README 里的相对链接补全。 */
private val DocBaseRegex = Regex("(https://[^/\"']+/raw/[^/\"']+/[^/\"']+/[0-9a-fA-F]{7,}/)")

/** HTML 实体还原（README 里 &amp; &lt; 很常见，不还原会显示成乱码）。 */
private fun unescapeEntities(text: String): String = text
    .replace("&nbsp;", " ")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&apos;", "'")
    .replace("&amp;", "&")

/** 单元格 / 行内文本：去标签 + 压空白。 */
private fun plainText(html: String): String =
    unescapeEntities(html.replace(TagRegex, " ")).replace(WhitespaceRegex, " ").trim()

/** HTML → 块列表。只为 README 设计。 */
private val DataPathRegex = Regex("(?is)data-path=\"([^\"]+)\"")

internal fun parseReadmeBlocks(html: String, linkBaseUrl: String = ""): List<ReadmeBlock> {
    if (html.isBlank()) return emptyList()
    val docBase = DocBaseRegex.find(html)?.groupValues?.get(1)
    // README 在子目录里（如 docs/README.md）时，相对链接要带上这个目录
    val docDir = DataPathRegex.find(html)?.groupValues?.get(1)
        ?.let { path -> path.substringBeforeLast('/', "").let { if (it.isBlank()) "" else "$it/" } }
        .orEmpty()
    val repoBase = linkBaseUrl.takeIf { it.isNotBlank() }?.let { base ->
        if (base.endsWith("/")) base + docDir else "$base/$docDir"
    }

    fun resolve(url: String): String? {
        val raw = unescapeEntities(url).trim()
        if (raw.isEmpty()) return null
        if (raw.startsWith("#")) return null // 页内锚点：不跳转
        // assets.lsposed.org/raw/<owner>/<repo>/<sha>/ 对文档请求返回 403，
        // 换成 raw.githubusercontent.com 的同路径才能真的取到正文。
        if (raw.startsWith("https://assets.lsposed.org/raw/")) {
            return "https://raw.githubusercontent.com/" +
                raw.removePrefix("https://assets.lsposed.org/raw/")
        }
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        if (raw.startsWith("//")) return "https:$raw"
        if (raw.startsWith("mailto:") || raw.startsWith("tel:")) return raw
        val base = docBase ?: repoBase ?: return null
        if (raw.startsWith("/") && base.startsWith("https://raw.githubusercontent.com/")) {
            val parts = base.removePrefix("https://raw.githubusercontent.com/").split('/')
            if (parts.size >= 3) return "https://raw.githubusercontent.com/${parts[0]}/${parts[1]}/${parts[2]}/" + raw.trimStart('/')
        }
        return base + raw.trimStart('/')
    }

    // ① 图片：抽出来换成占位符，交给 flush 生成 IMAGE 块（原来是被整体丢弃的）
    val images = ArrayList<String>()
    val withImages = StringBuilder()
    var imgCursor = 0
    ImageTagRegex.findAll(html).forEach { match ->
        withImages.append(html, imgCursor, match.range.first)
        val src = ImageSrcRegex.find(match.value)?.groupValues?.get(1)
        val url = src?.let(::resolve)
        if (url != null) {
            images += url
            withImages.append("\n\n@@IMG${images.lastIndex}@@\n\n")
        }
        imgCursor = match.range.last + 1
    }
    withImages.append(html, imgCursor, html.length)
    val source = withImages.toString()

    // ② 再把表格整块抽出来，换成占位符，交给 flush 生成 TABLE 块
    val tables = ArrayList<ReadmeTable>()
    val withoutTables = StringBuilder()
    var cursor = 0
    TableRegex.findAll(source).forEach { match ->
        withoutTables.append(source, cursor, match.range.first)
        val body = match.groupValues[1]
        val rows = ArrayList<List<List<ReadmeSpan>>>()
        val headerRows = HashSet<Int>()
        RowRegex.findAll(body).forEachIndexed { rowIndex, rowMatch ->
            val cells = ArrayList<List<ReadmeSpan>>()
            var sawHeader = false
            CellRegex.findAll(rowMatch.groupValues[1]).forEach { cellMatch ->
                if (cellMatch.groupValues[1].equals("h", ignoreCase = true)) sawHeader = true
                val cellSpans = parseInlineSpans(cellMatch.groupValues[2], images)
                cells += cellSpans
            }
            if (cells.isNotEmpty()) {
                if (sawHeader) headerRows += rows.size
                rows += cells
            }
        }
        if (rows.isNotEmpty()) {
            tables += ReadmeTable(rows = rows, headerRows = headerRows)
            withoutTables.append("\n\n@@TABLE${tables.lastIndex}@@\n\n")
        }
        cursor = match.range.last + 1
    }
    withoutTables.append(source, cursor, source.length)

    val cleaned = withoutTables.toString()
        .replace(BlockNoiseRegex, "")
        .replace(AnchorPermalinkRegex, "")
        // 任务列表：GitHub 会把 [ ] / [x] 渲染成复选框，纯文本里用符号表达
        .replace(CheckedInputRegex, "\u2611 ")
        .replace(InputRegex, "\u2610 ")
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)<hr[^>]*>"), "\n\n----\n\n")
        .replace(Regex("(?i)</p>|</div>|</section>|</tr>"), "\n\n")

    val blocks = ArrayList<ReadmeBlock>()
    var spans = ArrayList<ReadmeSpan>()
    var headingLevel = 0
    var bullet = false
    var quote = false
    var inCode = false
    var bold = 0
    var italic = 0
    var mono = false
    var link: String? = null

    fun flush() {
        val merged = ArrayList<ReadmeSpan>()
        spans.forEach { span ->
            if (span.text.isEmpty()) return@forEach
            val last = merged.lastOrNull()
            if (last != null && last.bold == span.bold && last.italic == span.italic &&
                last.mono == span.mono && last.link == span.link
            ) {
                merged[merged.lastIndex] = last.copy(text = last.text + span.text)
            } else {
                merged += span
            }
        }
        spans = ArrayList()
        val raw = merged.joinToString("") { it.text }
        if (raw.isBlank()) return
        val trimmedText = raw.trim()

        // 表格占位符
        val tableMatch = Regex("@@TABLE(\\d+)@@").find(trimmedText)
        if (tableMatch != null) {
            val index = tableMatch.groupValues[1].toIntOrNull()
            val table = index?.let { tables.getOrNull(it) }
            if (table != null) blocks += ReadmeBlock(kind = ReadmeBlockKind.TABLE, table = table)
            return
        }
        // 图片占位符
        val imageMatch = Regex("@@IMG(\\d+)@@").find(trimmedText)
        if (imageMatch != null) {
            val index = imageMatch.groupValues[1].toIntOrNull()
            val url = index?.let { images.getOrNull(it) }
            if (url != null) blocks += ReadmeBlock(kind = ReadmeBlockKind.IMAGE, imageUrl = url)
            return
        }

        val kind = when {
            !inCode && trimmedText.all { it == '-' } && trimmedText.length >= 3 -> ReadmeBlockKind.DIVIDER
            inCode -> ReadmeBlockKind.CODE
            headingLevel == 1 -> ReadmeBlockKind.TITLE
            headingLevel == 2 -> ReadmeBlockKind.HEADING2
            headingLevel >= 3 -> ReadmeBlockKind.HEADING3
            bullet -> ReadmeBlockKind.BULLET
            quote -> ReadmeBlockKind.QUOTE
            else -> ReadmeBlockKind.PARAGRAPH
        }
        val trimmed = merged
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotEmpty() }
        if (trimmed.isNotEmpty()) blocks += ReadmeBlock(kind, trimmed)
        if (!inCode) {
            headingLevel = 0
            bullet = false
            quote = false
        }
    }

    fun addText(raw: String) {
        val text = if (inCode) unescapeEntities(raw) else unescapeEntities(raw).replace(WhitespaceRegex, " ")
        if (text.isEmpty()) return
        spans += ReadmeSpan(text = text, bold = bold > 0, italic = italic > 0, mono = mono || inCode, link = link)
    }

    var index = 0
    TagRegex.findAll(cleaned).forEach { match ->
        if (match.range.first > index) addText(cleaned.substring(index, match.range.first))
        index = match.range.last + 1
        val tag = match.value
        val name = Regex("<\\s*/?\\s*([a-zA-Z][a-zA-Z0-9]*)").find(tag)
            ?.groupValues?.get(1)?.lowercase() ?: return@forEach
        val closing = tag.startsWith("</")
        when (name) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                flush()
                if (!closing) headingLevel = name.drop(1).toIntOrNull() ?: 1
            }

            "p", "div", "section", "article", "ul", "ol", "table", "tr", "tbody", "details", "summary" -> flush()
            "li" -> flush().also { bullet = !closing }
            "blockquote" -> {
                flush()
                quote = !closing
            }

            "pre" -> {
                flush()
                inCode = !closing
                mono = !closing
            }

            "code" -> mono = if (inCode) true else !closing
            "strong", "b" -> bold += if (closing) -1 else 1
            "em", "i" -> italic += if (closing) -1 else 1
            "a" -> {
                link = if (closing) {
                    null
                } else {
                    LinkHrefRegex.find(tag)?.groupValues?.get(1)?.let(::resolve)
                }
            }

            "br" -> addText("\n")
            "hr" -> flush()
            else -> Unit
        }
    }
    if (index < cleaned.length) addText(cleaned.substring(index))
    flush()
    return blocks
}

/** 单个表格单元格的简易行内解析：只保留加粗/斜体/行内代码/链接。 */
private fun parseInlineSpans(html: String, images: List<String> = emptyList()): List<ReadmeSpan> {
    val out = ArrayList<ReadmeSpan>()
    var bold = 0
    var italic = 0
    var mono = 0
    var link: String? = null
    var index = 0
    fun add(raw: String) {
        val text = unescapeEntities(raw).replace(WhitespaceRegex, " ")
        if (text.isEmpty()) return
        // 单元格里也可能是一张图（README 的“预览”表格）：占位符还原成图片 span
        val imageMatch = Regex("@@IMG(\\d+)@@").find(text)
        val imageUrl = imageMatch?.groupValues?.get(1)?.toIntOrNull()?.let { images.getOrNull(it) }
        if (imageUrl != null) {
            out += ReadmeSpan(text = "", imageUrl = imageUrl)
            return
        }
        out += ReadmeSpan(text = text, bold = bold > 0, italic = italic > 0, mono = mono > 0, link = link)
    }
    TagRegex.findAll(html).forEach { match ->
        if (match.range.first > index) add(html.substring(index, match.range.first))
        index = match.range.last + 1
        val tag = match.value
        val name = Regex("<\\s*/?\\s*([a-zA-Z][a-zA-Z0-9]*)").find(tag)
            ?.groupValues?.get(1)?.lowercase() ?: return@forEach
        val closing = tag.startsWith("</")
        when (name) {
            "strong", "b" -> bold += if (closing) -1 else 1
            "em", "i" -> italic += if (closing) -1 else 1
            "code" -> mono += if (closing) -1 else 1
            "a" -> link = if (closing) null else LinkHrefRegex.find(tag)?.groupValues?.get(1)
            "br" -> add("\n")
            else -> Unit
        }
    }
    if (index < html.length) add(html.substring(index))
    val merged = ArrayList<ReadmeSpan>()
    out.forEach { span ->
        val last = merged.lastOrNull()
        if (last != null && span.imageUrl == null && last.imageUrl == null &&
            last.bold == span.bold && last.italic == span.italic &&
            last.mono == span.mono && last.link == span.link
        ) {
            merged[merged.lastIndex] = last.copy(text = last.text + span.text)
        } else {
            merged += span
        }
    }
    return merged.map { it.copy(text = it.text.trim()) }
        .filter { it.text.isNotEmpty() || it.imageUrl != null }
        .ifEmpty { listOf(ReadmeSpan(plainText(html))) }
}