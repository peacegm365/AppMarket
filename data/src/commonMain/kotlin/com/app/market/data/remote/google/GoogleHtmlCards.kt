package com.app.market.data.remote.google

/** Extracts the visible cards, not incidental package-looking strings in scripts. */
internal object GoogleHtmlCards {
    private val anchor = Regex("<a\\b[^>]*href=\"/store/apps/details\\?id=([A-Za-z0-9._]+)[^\"]*\"[^>]*>", RegexOption.IGNORE_CASE)
    private val image = Regex("<img\\b[^>]*src=\"(https://play-lh\\.googleusercontent\\.com/[^\"]+)\"", RegexOption.IGNORE_CASE)
    private val videoThumb = Regex("<img\\b[^>]*src=\"(https://i\\.ytimg\\.com/[^\"]+)\"", RegexOption.IGNORE_CASE)
    /** 预注册类卡片的小图标（alt=“缩略图”），优先于横幅截图。 */
    private val iconThumb = Regex("<img\\b[^>]*src=\"(https://play-lh\\.googleusercontent\\.com/[^\"]+)\"[^>]*alt=\"(?:缩略图|[Tt]humbnail)\"", RegexOption.IGNORE_CASE)
    /** 视频卡片的名字只在 aria-label=“播放《X》”里（中文），英文是 “Play “X””。 */
    private val bracketName = Regex("aria-label=\"[^\"]*?[《“\"]([^》”\"]{1,120})[》”\"]\"")
    /** 普通卡片的图标 alt：“图标图片“X”” / “Icon image for “X””。 */
    private val iconAlt = Regex("alt=\"[^\"]*?[“\"]([^”\"]{1,120})[”\"]\"")
    private val aria = Regex("aria-label=\"([^\"]+)\"")
    private val title = Regex("<(?:div|span)\\b[^>]*(?:class=\"[^\"]*?(?:Epkrse|DdYX5|vWM94c)[^\"]*\"|title=\"([^\"]+)\")[^>]*>([^<]*)", RegexOption.IGNORE_CASE)
    private val developer = Regex("<(?:div|span)\\b[^>]*class=\"(?:wMUdtb|LbQbAe)[^\"]*\"[^>]*>([^<]*)", RegexOption.IGNORE_CASE)
    private val rating = Regex("(?:aria-label=\"[^\"]*?([0-5][.,][0-9])[^\"]*\"|class=\"(?:w2kbF|LrNMN)[^\"]*\"[^>]*>\\s*([0-5][.,][0-9]))")
    internal fun unescape(s: String): String = s.replace("&amp;", "&").replace("&" + "quot;", 34.toChar().toString())
        .replace("&#39;", "'").replace("&#x27;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").trim()
    fun collections(html: String): List<Pair<String, String>> {
        val pattern = Regex("\\[\"([^\"]{1,120})\",(?:null|\"[^\"]*\"),\\[null,null,null,null,\\[null,null,\"(/store/apps/collection/cluster\\?gsr[^\"]+)\"")
        return pattern.findAll(html).mapNotNull { match ->
            val name = unescape(match.groupValues[1])
            val path = match.groupValues[2].replace("\\u003d", "=").replace("\\u0026", "&")
            if (path.startsWith("/store/apps/collection/cluster?gsr=") && !path.contains('&')) path to name else null
        }.distinctBy { it.first }.toList()
    }

    fun parse(html: String): List<GoogleAppRecord> {
        val seen = HashSet<String>()
        val result = ArrayList<GoogleAppRecord>()
        for (m in anchor.findAll(html)) {
            val pkg = m.groupValues[1]
            if (!seen.add(pkg.lowercase())) continue
            val start = m.range.last + 1
            val endTag = html.indexOf("</a>", start).let { if (it < 0) start else it }
            // Featured search hits use an empty anchor followed by a sibling card.
            val end = if (endTag == start) minOf(html.length, start + 5000) else minOf(endTag, start + 12000)
            val part = html.substring(start, end)
            // 名字多级回退：卡片 title 属性/标题类 → 视频卡 aria-label“播放《X》” → 图标 alt “X” → 包名兜底。
            // 取不到名字绝不能丢卡片：视频样式卡片整页都取不到旧规则的名字，丢弃会把分类变成空列表。
            val rawName = aria.find(m.value)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                ?: title.find(part)?.let { it.groupValues[1].ifBlank { it.groupValues[2] } }?.takeIf { it.isNotBlank() }
                ?: bracketName.find(part)?.groupValues?.get(1)
                ?: iconAlt.find(part)?.groupValues?.get(1)
            val name = rawName?.let(::unescape)?.takeIf { it.isNotBlank() } ?: pkg
            val score = rating.find(part)?.let { it.groupValues[1].ifBlank { it.groupValues[2] }.replace(',', '.') }.orEmpty()
            result.add(GoogleAppRecord(packageName = pkg, title = name,
                publisher = developer.find(part)?.groupValues?.get(1)?.let(::unescape).orEmpty(),
                icon = (iconThumb.find(part)?.groupValues?.get(1) ?: image.find(part)?.groupValues?.get(1) ?: videoThumb.find(part)?.groupValues?.get(1))?.let(::unescape).orEmpty(),
                versionCode = 0L, versionString = "", offerType = 1, fileSize = 0L,
                description = "", subtitle = "", recentChanges = "", ratingText = score,
                downloadCountText = "", screenshots = emptyList()))
        }
        return result
    }
}