package moe.huiguang.pixiv.data.model

/**
 * A work referenced from an artwork/novel description.
 *
 * Pixiv authors paste both real anchors (`<a href="/artworks/97689382">前回</a>`)
 * and bare URLs/ids, so the parser accepts either and keeps the anchor text as
 * the display label.
 */
data class LinkedWork(
    /** `illust` or `novel`. */
    val kind: String,
    val id: String,
    /** Anchor text such as 「前回」; falls back to 「关联作品」. */
    val label: String,
)

private val ANCHOR = Regex(
    """<a\b[^>]*?href\s*=\s*["']([^"']+)["'][^>]*?>(.*?)</a>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val ILLUST_PATH = Regex(
    """(?i)(?:https?://(?:www\.)?pixiv\.net)?(?:/[a-z]{2})?/?(?:artworks|illust|i)/(\d+)""",
)
private val ILLUST_ID = Regex("""(?i)illust_id=(\d+)""")
private val NOVEL_SHOW = Regex("""(?i)novel/show\.php\?id=(\d+)""")
private val NOVEL_PATH = Regex(
    """(?i)(?:https?://(?:www\.)?pixiv\.net)?(?:/[a-z]{2})?/?novel/(?!series/)(\d+)""",
)
private val TAGS = Regex("<[^>]+>")
private val SPACES = Regex("\\s+")
private val ENTITY_NUMBER = Regex("&#(\\d+);")
private val URL_LIKE = Regex(
    """(?i)(?:https?://|(?:artworks|illust|novel)/\d+|illust_id=)""",
)

/**
 * Extracts work links from a raw Pixiv description, de-duplicated by
 * `(kind, id)` and with the current work removed.
 */
fun parseLinkedWorks(
    html: String,
    currentKind: String,
    currentId: String,
): List<LinkedWork> {
    if (html.isBlank()) return emptyList()
    val found = LinkedHashMap<String, LinkedWork>()

    fun add(kind: String, id: String, label: String) {
        if (kind == currentKind && id == currentId) return
        val key = "$kind:$id"
        if (found.containsKey(key)) return
        found[key] = LinkedWork(
            kind = kind,
            id = id,
            label = cleanLabel(label),
        )
    }

    ANCHOR.findAll(html).forEach { match ->
        val target = targetOf(match.groupValues[1]) ?: return@forEach
        // Pixiv often wraps the bare URL as the anchor text and puts the real
        // label ("前回") immediately before it:
        // 前回<strong><a href=".../97653050">illust/97653050</a></strong>の続き
        val anchorLabel = cleanLabel(match.groupValues[2])
        val label = if (URL_LIKE.containsMatchIn(anchorLabel)) {
            cleanLabel(TAGS.replace(html.substring(0, match.range.first), " "))
                .replace(URL_LIKE, " ")
                .replace(SPACES, " ")
                .trim()
                .takeLast(16)
                .ifBlank { anchorLabel }
        } else {
            anchorLabel
        }
        add(target.first, target.second, label)
    }

    // Plain URLs/ids outside an anchor; the anchor pass already owns labels.
    val plain = ANCHOR.replace(html, " ")
    ILLUST_PATH.findAll(plain).forEach { add("illust", it.groupValues[1], "") }
    ILLUST_ID.findAll(plain).forEach { add("illust", it.groupValues[1], "") }
    NOVEL_SHOW.findAll(plain).forEach { add("novel", it.groupValues[1], "") }
    NOVEL_PATH.findAll(plain).forEach { add("novel", it.groupValues[1], "") }

    return found.values.toList()
}

/**
 * Rule 4: when an official series exists it wins; description links that point
 * into that series are removed. Links outside the series stay visible.
 */
fun visibleLinkedWorks(
    linked: List<LinkedWork>,
    currentKind: String,
    seriesIds: Set<String>,
    hasSeries: Boolean,
): List<LinkedWork> {
    if (!hasSeries) return linked
    return linked.filterNot { it.kind == currentKind && it.id in seriesIds }
}

private fun targetOf(raw: String): Pair<String, String>? {
    val url = raw.trim()
    if (url.contains("novel/series/", ignoreCase = true)) return null
    NOVEL_SHOW.find(url)?.let { return "novel" to it.groupValues[1] }
    NOVEL_PATH.find(url)?.let { return "novel" to it.groupValues[1] }
    ILLUST_ID.find(url)?.let { return "illust" to it.groupValues[1] }
    ILLUST_PATH.find(url)?.let { return "illust" to it.groupValues[1] }
    return null
}

private fun cleanLabel(raw: String): String =
    decodeEntities(TAGS.replace(raw, " "))
        .replace(SPACES, " ")
        .trim()
        .take(48)

private fun decodeEntities(raw: String): String {
    val basic = raw
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
    return ENTITY_NUMBER.replace(basic) { match ->
        match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
    }
}
