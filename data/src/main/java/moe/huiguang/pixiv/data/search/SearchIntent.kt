package moe.huiguang.pixiv.data.search

/**
 * What the single search box should do with its input.
 *
 * Mirrors how the web client behaves: a bare number or a pixiv link opens that
 * work directly, anything else becomes a text search.
 */
sealed interface SearchIntent {

    /** Which id sequence the digits belong to. */
    enum class Kind { ILLUST, NOVEL, AUTHOR, AUTO }

    /** A PID or link: open it directly. [Kind.AUTO] means "probe every kind". */
    data class Direct(val kind: Kind, val id: String) : SearchIntent

    /** Free text: title / caption / tag search. */
    data class Text(val query: String) : SearchIntent
}

/**
 * Parses the lookup box.
 *
 * Link shapes handled (all on `pixiv.net`):
 *  - `/artworks/123`, `/illusts/123`, `/i/123`, `illust_id=123` -> AUTO
 *  - `/novel/show.php?id=123`, `/novel/123`                     -> NOVEL
 *  - `/users/123`, `/u/123`, `member.php?id=123`                -> AUTHOR
 */
object SearchIntentParser {

    private val artwork = Regex("(?:/artworks/|/illusts/|/i/|illust_id=)(\\d+)")
    private val novel = Regex("(?:/novel/show\\.php\\?id=|/novel/)(\\d+)")
    private val author = Regex("(?:/users/|/u/|member\\.php\\?id=)(\\d+)")
    private val digits = Regex("^\\d{1,12}$")

    fun parse(raw: String?): SearchIntent? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (digits.matches(text)) return SearchIntent.Direct(SearchIntent.Kind.AUTO, text)

        val lower = text.lowercase()
        val mentionsPixiv = lower.contains("pixiv.net") || lower.contains("pixiv.me")
        if (mentionsPixiv || lower.contains("artworks/") || lower.contains("illust_id=")) {
            novel.find(text)?.let {
                return SearchIntent.Direct(SearchIntent.Kind.NOVEL, it.groupValues[1])
            }
            author.find(text)?.let {
                return SearchIntent.Direct(SearchIntent.Kind.AUTHOR, it.groupValues[1])
            }
            artwork.find(text)?.let {
                return SearchIntent.Direct(SearchIntent.Kind.AUTO, it.groupValues[1])
            }
        }
        return SearchIntent.Text(text)
    }
}
