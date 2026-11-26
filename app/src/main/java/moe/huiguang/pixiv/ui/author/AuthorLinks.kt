package moe.huiguang.pixiv.ui.author

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import java.net.URL
import java.util.Locale

enum class AuthorLinkPlatform {
    X,
    AFDIAN,
    PIXIV,
    FANBOX,
    SKEB,
    BOOTH,
    YOUTUBE,
    GITHUB,
    BILIBILI,
    INSTAGRAM,
    TWITCH,
    PATREON,
    FANTIA,
    UNIFANS,
    BLUESKY,
    KOFI,
    WEIBO,
    MARSHMALLOW,
    LINKHUB,
    WEBSITE,
}

data class AuthorLink(
    val url: String,
    val label: String,
    val host: String,
    val platform: AuthorLinkPlatform,
    val range: IntRange? = null,
)

private val URL_REGEX = Regex("""https?://[^\s<>"'\u3000]+""", RegexOption.IGNORE_CASE)
private val TRAILING_PUNCTUATION = setOf(
    '.', ',', ';', ':', '!', '?', '\'', '"', ']', '}', '>',
    '、', '。', '，', '！', '？', '」', '』', '】', '》', '）',
)
private val RESERVED_X_SEGMENTS = setOf("i", "intent", "share", "search", "explore", "home", "hashtag")

/**
 * Extracts validated `http`/`https` links from an author's bio [comment] and optional [webpage],
 * deduplicating repeated targets while keeping text ranges for inline link annotation.
 */
fun extractProfileLinks(
    comment: String,
    webpage: String? = null,
    webpageFallbackLabel: String = "主页",
): List<AuthorLink> {
    val result = mutableListOf<AuthorLink>()
    val seen = HashSet<String>()

    webpage?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        normalizeSafeHttpUrl(raw)?.let { cleanUrl ->
            if (seen.add(dedupKey(cleanUrl))) {
                result += buildAuthorLink(cleanUrl, range = null, webpageFallbackLabel = webpageFallbackLabel)
            }
        }
    }

    if (comment.isNotBlank()) {
        for (match in URL_REGEX.findAll(comment)) {
            val trimmed = trimTrailingUrlPunctuation(match.value)
            val cleanUrl = normalizeSafeHttpUrl(trimmed) ?: continue
            val endInclusive = match.range.first + trimmed.length - 1
            if (endInclusive < match.range.first) continue
            val range = match.range.first..endInclusive
            if (seen.add(dedupKey(cleanUrl))) {
                result += buildAuthorLink(cleanUrl, range = range, webpageFallbackLabel = webpageFallbackLabel)
            } else {
                val existingIndex = result.indexOfFirst { dedupKey(it.url) == dedupKey(cleanUrl) && it.range == null }
                if (existingIndex >= 0) {
                    result[existingIndex] = result[existingIndex].copy(range = range)
                }
            }
        }
    }

    return result
}

/**
 * Collects every clickable URL span in [comment] (even if the same URL appears multiple times)
 * so inline link highlighting covers all occurrences.
 */
fun extractInlineLinkSpans(
    comment: String,
    webpageFallbackLabel: String = "主页",
): List<AuthorLink> {
    if (comment.isBlank()) return emptyList()
    return URL_REGEX.findAll(comment).mapNotNull { match ->
        val trimmed = trimTrailingUrlPunctuation(match.value)
        val cleanUrl = normalizeSafeHttpUrl(trimmed) ?: return@mapNotNull null
        val endInclusive = match.range.first + trimmed.length - 1
        if (endInclusive < match.range.first) return@mapNotNull null
        buildAuthorLink(cleanUrl, range = match.range.first..endInclusive, webpageFallbackLabel = webpageFallbackLabel)
    }.toList()
}

fun buildLinkedBioAnnotatedString(
    text: String,
    inlineLinks: List<AuthorLink>,
    linkColor: Color,
    onLinkClick: (AuthorLink) -> Unit,
): AnnotatedString = buildAnnotatedString {
    append(text)
    val linkStyle = TextLinkStyles(
        style = SpanStyle(
            color = linkColor,
            textDecoration = TextDecoration.Underline,
        ),
    )
    for (link in inlineLinks) {
        val range = link.range ?: continue
        if (range.first < 0 || range.last >= text.length || range.first > range.last) continue
        addLink(
            clickable = LinkAnnotation.Clickable(
                tag = link.url,
                styles = linkStyle,
                linkInteractionListener = { onLinkClick(link) },
            ),
            start = range.first,
            end = range.last + 1,
        )
    }
}

/**
 * Brand accent color for each recognized platform pill, tuned for white label text
 * and crisp separation against both light and dark page backgrounds.
 */
fun platformChipColor(platform: AuthorLinkPlatform, dark: Boolean, fallbackAccent: Color): Color = when (platform) {
    // Pure X black in light mode; lifted graphite-black in dark mode so edge glow and pill stay distinct on black.
    AuthorLinkPlatform.X -> if (dark) Color(0xEB2D323B) else Color(0xEB111418)
    AuthorLinkPlatform.AFDIAN -> Color(0xEB8B5CF6)
    AuthorLinkPlatform.PIXIV -> fallbackAccent
    AuthorLinkPlatform.FANBOX -> Color(0xEBE58E00)
    AuthorLinkPlatform.SKEB -> Color(0xEB129A8E)
    AuthorLinkPlatform.BOOTH -> Color(0xEBE8484D)
    AuthorLinkPlatform.YOUTUBE -> Color(0xEBDC2626)
    AuthorLinkPlatform.GITHUB -> if (dark) Color(0xEB3B424D) else Color(0xEB24292F)
    AuthorLinkPlatform.BILIBILI -> Color(0xEBE65584)
    AuthorLinkPlatform.INSTAGRAM -> Color(0xEBD62976)
    AuthorLinkPlatform.TWITCH -> Color(0xEB7C3AED)
    AuthorLinkPlatform.PATREON -> Color(0xEBE64F38)
    AuthorLinkPlatform.FANTIA -> Color(0xEBD94670)
    AuthorLinkPlatform.UNIFANS -> Color(0xEB6366F1)
    AuthorLinkPlatform.BLUESKY -> Color(0xEB1185FE)
    AuthorLinkPlatform.KOFI -> Color(0xEB1FA2DA)
    AuthorLinkPlatform.WEIBO -> Color(0xEBD91E32)
    AuthorLinkPlatform.MARSHMALLOW -> Color(0xEBE4648E)
    AuthorLinkPlatform.LINKHUB -> Color(0xEB5B5CE6)
    AuthorLinkPlatform.WEBSITE -> fallbackAccent
}

private fun trimTrailingUrlPunctuation(raw: String): String {
    var end = raw.length
    while (end > 0) {
        val ch = raw[end - 1]
        if (ch == ')') {
            val prefix = raw.substring(0, end - 1)
            val openCount = prefix.count { it == '(' }
            val closeCount = prefix.count { it == ')' }
            if (openCount > closeCount) break
            end--
        } else if (ch in TRAILING_PUNCTUATION) {
            end--
        } else {
            break
        }
    }
    return raw.substring(0, end)
}

private fun normalizeSafeHttpUrl(raw: String): String? {
    val parsed = runCatching { URL(raw) }.getOrNull() ?: return null
    val scheme = parsed.protocol?.lowercase(Locale.ROOT) ?: return null
    if (scheme != "http" && scheme != "https") return null
    if (!parsed.userInfo.isNullOrEmpty()) return null
    val host = parsed.host?.takeIf { it.isNotBlank() } ?: return null
    if (!host.contains('.')) return null
    return raw
}

private fun dedupKey(url: String): String {
    val parsed = runCatching { URL(url) }.getOrNull() ?: return url.lowercase(Locale.ROOT)
    val scheme = parsed.protocol?.lowercase(Locale.ROOT).orEmpty()
    val host = parsed.host?.lowercase(Locale.ROOT).orEmpty().removePrefix("www.")
    val path = parsed.path?.trimEnd('/').orEmpty()
    val query = parsed.query?.let { "?$it" }.orEmpty()
    return "$scheme://$host$path$query"
}

private fun buildAuthorLink(
    url: String,
    range: IntRange?,
    webpageFallbackLabel: String,
): AuthorLink {
    val parsed = runCatching { URL(url) }.getOrNull()
    val rawHost = parsed?.host?.lowercase(Locale.ROOT).orEmpty()
    val cleanHost = rawHost.removePrefix("www.")
    val segments = parsed?.path
        ?.split('/')
        ?.filter { it.isNotBlank() }
        .orEmpty()

    val platform = classifyPlatform(cleanHost)
    val label = when (platform) {
        AuthorLinkPlatform.X -> {
            val handle = segments.firstOrNull()?.removePrefix("@")
            if (!handle.isNullOrBlank() && handle.lowercase(Locale.ROOT) !in RESERVED_X_SEGMENTS) {
                "X · @$handle"
            } else {
                "X"
            }
        }
        AuthorLinkPlatform.AFDIAN -> {
            val user = when {
                segments.firstOrNull() == "a" && segments.size >= 2 -> segments[1]
                segments.firstOrNull()?.startsWith("@") == true -> segments.first()
                else -> null
            }
            if (!user.isNullOrBlank()) "爱发电 · $user" else "爱发电"
        }
        AuthorLinkPlatform.SKEB -> {
            val handle = segments.firstOrNull()?.removePrefix("@")
            if (!handle.isNullOrBlank()) "Skeb · @$handle" else "Skeb"
        }
        AuthorLinkPlatform.FANBOX -> {
            val sub = cleanHost.removeSuffix(".fanbox.cc").takeIf { it != cleanHost && it.isNotBlank() }
            val handle = segments.firstOrNull()?.takeIf { it.startsWith("@") }
            when {
                sub != null -> "FANBOX · $sub"
                handle != null -> "FANBOX · $handle"
                else -> "FANBOX"
            }
        }
        AuthorLinkPlatform.BOOTH -> {
            val sub = cleanHost.removeSuffix(".booth.pm").takeIf { it != cleanHost && it.isNotBlank() }
            if (sub != null) "BOOTH · $sub" else "BOOTH"
        }
        AuthorLinkPlatform.YOUTUBE -> "YouTube"
        AuthorLinkPlatform.GITHUB -> {
            val user = segments.firstOrNull()
            if (!user.isNullOrBlank()) "GitHub · $user" else "GitHub"
        }
        AuthorLinkPlatform.BILIBILI -> "Bilibili"
        AuthorLinkPlatform.INSTAGRAM -> {
            val handle = segments.firstOrNull()?.takeIf { it !in setOf("p", "reel", "stories") }
            if (!handle.isNullOrBlank()) "Instagram · @$handle" else "Instagram"
        }
        AuthorLinkPlatform.TWITCH -> "Twitch"
        AuthorLinkPlatform.PATREON -> "Patreon"
        AuthorLinkPlatform.FANTIA -> "Fantia"
        AuthorLinkPlatform.UNIFANS -> "Unifans"
        AuthorLinkPlatform.BLUESKY -> "Bluesky"
        AuthorLinkPlatform.KOFI -> "Ko-fi"
        AuthorLinkPlatform.WEIBO -> "微博"
        AuthorLinkPlatform.MARSHMALLOW -> "Marshmallow"
        AuthorLinkPlatform.PIXIV -> "Pixiv"
        AuthorLinkPlatform.LINKHUB -> cleanHost
        AuthorLinkPlatform.WEBSITE -> cleanHost.ifBlank { webpageFallbackLabel }
    }

    return AuthorLink(
        url = url,
        label = label,
        host = cleanHost,
        platform = platform,
        range = range,
    )
}

private fun classifyPlatform(host: String): AuthorLinkPlatform = when {
    host == "x.com" || host == "twitter.com" || host == "mobile.twitter.com" -> AuthorLinkPlatform.X
    host == "afdian.com" || host == "afdian.net" || host == "ifdian.net" ||
        host.endsWith(".afdian.com") || host.endsWith(".ifdian.net") -> AuthorLinkPlatform.AFDIAN
    host == "fanbox.cc" || host.endsWith(".fanbox.cc") -> AuthorLinkPlatform.FANBOX
    host == "skeb.jp" || host.endsWith(".skeb.jp") -> AuthorLinkPlatform.SKEB
    host == "booth.pm" || host.endsWith(".booth.pm") -> AuthorLinkPlatform.BOOTH
    host == "youtube.com" || host == "youtu.be" || host.endsWith(".youtube.com") -> AuthorLinkPlatform.YOUTUBE
    host == "github.com" -> AuthorLinkPlatform.GITHUB
    host == "bilibili.com" || host == "b23.tv" || host.endsWith(".bilibili.com") -> AuthorLinkPlatform.BILIBILI
    host == "instagram.com" || host.endsWith(".instagram.com") -> AuthorLinkPlatform.INSTAGRAM
    host == "twitch.tv" || host.endsWith(".twitch.tv") -> AuthorLinkPlatform.TWITCH
    host == "patreon.com" || host.endsWith(".patreon.com") -> AuthorLinkPlatform.PATREON
    host == "fantia.jp" || host.endsWith(".fantia.jp") -> AuthorLinkPlatform.FANTIA
    host == "unifans.io" || host.endsWith(".unifans.io") -> AuthorLinkPlatform.UNIFANS
    host == "bsky.app" -> AuthorLinkPlatform.BLUESKY
    host == "ko-fi.com" -> AuthorLinkPlatform.KOFI
    host == "weibo.com" || host == "weibo.cn" || host.endsWith(".weibo.com") -> AuthorLinkPlatform.WEIBO
    host == "marshmallow-qa.com" -> AuthorLinkPlatform.MARSHMALLOW
    host == "pixiv.net" || host.endsWith(".pixiv.net") -> AuthorLinkPlatform.PIXIV
    host == "lit.link" || host == "linktr.ee" || host == "potofu.me" || host == "profcard.info" -> AuthorLinkPlatform.LINKHUB
    else -> AuthorLinkPlatform.WEBSITE
}
