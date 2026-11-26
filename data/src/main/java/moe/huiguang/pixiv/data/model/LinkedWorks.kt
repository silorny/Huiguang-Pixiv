package moe.huiguang.pixiv.data.model

import java.net.URLDecoder

/**
 * A work or author referenced from an artwork/novel description.
 *
 * Pixiv authors paste both real anchors (`<a href="/artworks/97689382">前回</a>`)
 * and bare URLs/ids, so the parser accepts either and keeps the anchor text as
 * the display label.
 */
data class LinkedWork(
    /** `illust`, `novel`, or `author`. */
    val kind: String,
    val id: String,
    /** Anchor text such as 「前回」; falls back to 「关联信息」. */
    val label: String,
)

sealed interface CaptionTarget {
    data class Illust(val id: String) : CaptionTarget
    data class Novel(val id: String) : CaptionTarget
    data class User(val id: String) : CaptionTarget
    data class External(val url: String) : CaptionTarget
}

data class CaptionLink(
    val start: Int,
    val end: Int,
    val target: CaptionTarget,
    val displayUrl: String,
)

data class CaptionContent(
    val text: String,
    val links: List<CaptionLink>,
)

private val ANCHOR = Regex(
    """<a\b[^>]*?href\s*=\s*["']([^"']+)["'][^>]*?>(.*?)</a>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val ILLUST_PATH = Regex(
    """(?i)(?:https?://(?:(?:www|touch)\.)?pixiv\.net)?(?:/[a-z]{2})?/?(?:artworks|illust|i)/(\d+)(?!\d)""",
)
private val ILLUST_ID = Regex("""(?i)\billust_id=(\d+)(?!\d)""")
private val NOVEL_SHOW = Regex("""(?i)novel/show\.php\?[^"'\s]*?\bid=(\d+)(?!\d)""")
private val NOVEL_PATH = Regex(
    """(?i)(?:https?://(?:(?:www|touch)\.)?pixiv\.net)?(?:/[a-z]{2})?/?novel/(?!series/)(\d+)(?!\d)""",
)
private val USER_PATH = Regex(
    """(?i)(?:https?://(?:(?:www|touch)\.)?pixiv\.net)?(?:/[a-z]{2})?/?(?:users|user|u)/(\d+)(?!\d)""",
)
private val MEMBER_ID = Regex("""(?i)member\.php\?[^"'\s]*?\bid=(\d+)(?!\d)""")
private val INLINE_SHORTHAND = Regex(
    """(?i)(?<![A-Za-z0-9_.:/-])/?(?:[a-z]{2}/)?(?:(artworks|illust|i)/(\d+)|(novel)/(\d+)|(users|user|u)/(\d+)|novel/show\.php\?id=(\d+)|member\.php\?id=(\d+)|member_illust\.php\?[^\s<>"']*\billust_id=(\d+))(?!\d)""",
)
private val INLINE_URL = Regex("""(?i)https?://[^\s<>"'\]」】』）]+""")
private val PIXIV_HTTP_HOST = Regex("""(?i)^https?://(?:(?:www|touch)\.)?pixiv\.net(?:/|\?|$)""")
private val ANY_HTTP_SCHEME = Regex("""(?i)^https?://""")
private val TAGS = Regex("<[^>]+>")
private val BR_TAG = Regex("(?i)<br\\s*/?>")
private val P_CLOSE_TAG = Regex("(?i)</p\\s*>")
private val SPACES = Regex("\\s+")
private val ENTITY_NUMBER = Regex("&#(\\d+);")
private val URL_LIKE = Regex(
    """(?i)(?:https?://|(?:artworks|illust|novel|users?|u)/\d+|illust_id=|member\.php\?id=)""",
)

/**
 * Extracts work and author links from a raw Pixiv description, de-duplicated by
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
        val target = targetOf(unwrapPixivJump(decodeEntities(match.groupValues[1]))) ?: return@forEach
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
    // Collect matches paired with their start index so output preserves source order.
    val plain = ANCHOR.replace(html, " ")
    val plainMatches = mutableListOf<Triple<Int, String, String>>()
    INLINE_URL.findAll(plain).forEach { match ->
        val target = targetOf(match.value) ?: return@forEach
        plainMatches += Triple(match.range.first, target.first, target.second)
    }
    // Run against `plain` excluding spans covered by INLINE_URL to keep true `plain` indices.
    val urlRanges = INLINE_URL.findAll(plain).map { it.range }.toList()
    fun insideUrl(start: Int, end: Int): Boolean =
        urlRanges.any { r -> start <= r.last && end >= r.first }
    INLINE_SHORTHAND.findAll(plain).forEach { match ->
        if (insideUrl(match.range.first, match.range.last)) return@forEach
        val target = targetOf(match.value) ?: return@forEach
        plainMatches += Triple(match.range.first, target.first, target.second)
    }
    ILLUST_ID.findAll(plain).forEach { match ->
        if (insideUrl(match.range.first, match.range.last)) return@forEach
        plainMatches += Triple(match.range.first, "illust", match.groupValues[1])
    }
    plainMatches.sortedBy { it.first }.forEach { (_, kind, id) ->
        add(kind, id, "")
    }

    return found.values.toList()
}

/**
 * Parses a raw Pixiv caption HTML fragment into plain display text plus clickable
 * link spans distinguishing internal Pixiv targets from external web URLs.
 */
fun parseCaptionContent(rawHtml: String): CaptionContent {
    if (rawHtml.isBlank()) return CaptionContent("", emptyList())
    val builder = StringBuilder()
    val anchorLinks = mutableListOf<CaptionLink>()
    var cursor = 0

    ANCHOR.findAll(rawHtml).forEach { match ->
        if (match.range.first > cursor) {
            builder.append(renderPlainSegment(rawHtml.substring(cursor, match.range.first)))
        }
        val display = renderPlainSegment(match.groupValues[2])
        if (display.isNotEmpty()) {
            val rawHref = decodeEntities(match.groupValues[1]).trim()
            val unwrappedHref = unwrapPixivJump(rawHref)
            val target = resolveCaptionTarget(unwrappedHref) ?: resolveCaptionTarget(display)
            val start = builder.length
            builder.append(display)
            val end = builder.length
            if (target != null) {
                anchorLinks += CaptionLink(
                    start = start,
                    end = end,
                    target = target,
                    displayUrl = when (target) {
                        is CaptionTarget.External -> target.url
                        else -> unwrappedHref.ifBlank { display }
                    },
                )
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < rawHtml.length) {
        builder.append(renderPlainSegment(rawHtml.substring(cursor)))
    }

    val rawBuilt = builder.toString()
    val leadingTrim = rawBuilt.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) rawBuilt.length else it }
    val trimmed = rawBuilt.trim()
    if (trimmed.isEmpty()) return CaptionContent("", emptyList())

    val allLinks = mutableListOf<CaptionLink>()
    anchorLinks.forEach { link ->
        val start = (link.start - leadingTrim).coerceIn(0, trimmed.length)
        val end = (link.end - leadingTrim).coerceIn(0, trimmed.length)
        if (end > start) {
            allLinks += link.copy(start = start, end = end)
        }
    }

    fun overlaps(start: Int, end: Int): Boolean =
        allLinks.any { existing -> start < existing.end && end > existing.start }

    // Bare inline HTTP(S) URLs outside <a> tags
    INLINE_URL.findAll(trimmed).forEach { match ->
        val url = trimTrailingUrlPunctuation(match.value)
        if (url.isEmpty()) return@forEach
        val start = match.range.first
        val end = start + url.length
        if (overlaps(start, end)) return@forEach
        val target = resolveCaptionTarget(url) ?: CaptionTarget.External(url)
        allLinks += CaptionLink(
            start = start,
            end = end,
            target = target,
            displayUrl = (target as? CaptionTarget.External)?.url ?: url,
        )
    }

    // Bare inline Pixiv shorthands like illust/4034019, user/92806916, novel/29726268
    INLINE_SHORTHAND.findAll(trimmed).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        if (overlaps(start, end)) return@forEach
        val target = resolveCaptionTarget(match.value) ?: return@forEach
        allLinks += CaptionLink(
            start = start,
            end = end,
            target = target,
            displayUrl = match.value,
        )
    }

    return CaptionContent(
        text = trimmed,
        links = allLinks.sortedBy { it.start },
    )
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
    if (ANY_HTTP_SCHEME.containsMatchIn(url) && !PIXIV_HTTP_HOST.containsMatchIn(url)) {
        return null
    }
    if (url.contains("novel/series/", ignoreCase = true)) return null
    NOVEL_SHOW.find(url)?.let { return "novel" to it.groupValues[1] }
    NOVEL_PATH.find(url)?.let { return "novel" to it.groupValues[1] }
    ILLUST_ID.find(url)?.let { return "illust" to it.groupValues[1] }
    ILLUST_PATH.find(url)?.let { return "illust" to it.groupValues[1] }
    MEMBER_ID.find(url)?.let { return "author" to it.groupValues[1] }
    USER_PATH.find(url)?.let { return "author" to it.groupValues[1] }
    return null
}

private fun resolveCaptionTarget(raw: String): CaptionTarget? {
    val candidate = unwrapPixivJump(raw.trim())
    if (candidate.isEmpty()) return null
    targetOf(candidate)?.let { (kind, id) ->
        return when (kind) {
            "illust" -> CaptionTarget.Illust(id)
            "novel" -> CaptionTarget.Novel(id)
            "author" -> CaptionTarget.User(id)
            else -> null
        }
    }
    return when {
        candidate.startsWith("https://", ignoreCase = true) ||
            candidate.startsWith("http://", ignoreCase = true) -> CaptionTarget.External(candidate)
        candidate.startsWith("/") -> {
            // Relative Pixiv path that isn't a direct illust/novel/user target (e.g. series)
            CaptionTarget.External("https://www.pixiv.net$candidate")
        }
        else -> null
    }
}

private fun unwrapPixivJump(raw: String): String {
    val trimmed = raw.trim()
    val jumpIdx = trimmed.indexOf("jump.php?", ignoreCase = true)
    if (jumpIdx < 0) return trimmed
    val prefix = trimmed.substring(0, jumpIdx)
    val isPixivJump = prefix.isEmpty() || prefix == "/" || PIXIV_HTTP_HOST.containsMatchIn(prefix)
    if (!isPixivJump) return trimmed
    val query = trimmed.substring(jumpIdx + "jump.php?".length)
    val encoded = if (query.startsWith("url=", ignoreCase = true)) {
        query.substringAfter('=').substringBefore('&')
    } else {
        query.split('&').firstOrNull { it.startsWith("url=", ignoreCase = true) }
            ?.substringAfter('=')
            ?: query.substringBefore('&')
    }
    if (encoded.isBlank()) return trimmed
    return runCatching { URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8") }
        .getOrDefault(trimmed)
}

private fun trimTrailingUrlPunctuation(url: String): String {
    var end = url.length
    while (end > 0) {
        val ch = url[end - 1]
        if (ch == '.' || ch == ',' || ch == ';' || ch == ':' || ch == '!' || ch == '?' ||
            ch == '。' || ch == '、' || ch == '；' || ch == '：' || ch == '！' || ch == '？'
        ) {
            end--
        } else if (ch == ')') {
            val openCount = url.substring(0, end).count { it == '(' }
            val closeCount = url.substring(0, end).count { it == ')' }
            if (closeCount > openCount) end-- else break
        } else {
            break
        }
    }
    return url.substring(0, end)
}

private fun renderPlainSegment(raw: String): String {
    if (raw.isEmpty()) return ""
    val normalized = raw
        .replace(BR_TAG, "\n")
        .replace(P_CLOSE_TAG, "\n\n")
        .replace(TAGS, "")
    return decodeEntities(normalized)
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
