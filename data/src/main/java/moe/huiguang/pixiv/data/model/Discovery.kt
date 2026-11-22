package moe.huiguang.pixiv.data.model

import org.json.JSONObject

/** One neighbouring entry in a series. */
data class SeriesLink(
    val id: String,
    val title: String,
    val order: Int,
    /** Novels report `false` for entries the viewer cannot open. */
    val available: Boolean,
    /** Row metadata for the series list; empty when a caller built a bare link. */
    val thumbUrl: String = "",
    val userName: String = "",
    /** 0 = illustration, 1 = manga, -1 = novel. */
    val illustType: Int = 0,
    val pageCount: Int = 0,
)

/** Series membership, as reported by `/ajax/illust/{id}` and `/ajax/novel/{id}`. */
data class SeriesNav(
    val seriesId: String,
    val title: String,
    val order: Int,
    val prev: SeriesLink?,
    val next: SeriesLink?,
)

/**
 * Which flavour of search to run.
 *
 * Pixiv distinguishes "title & caption" (`s_tc`) from an exact tag match
 * (`s_tag`); both are plain GETs against the same endpoint.
 */
enum class SearchMode(val sMode: String) {
    KEYWORD("s_tc"),
    TAG("s_tag"),
}

enum class SearchCategory { ILLUST, MANGA, NOVEL }

/** Metadata for a novel series, from `/ajax/novel/series/{id}`. */
data class NovelSeries(
    val id: String,
    val title: String,
    val userId: String,
    val userName: String,
    val caption: String,
    val coverUrl: String,
    val total: Int,
    val isConcluded: Boolean,
)

/** One search result, normalised across artworks and novels. */
data class SearchItem(
    val id: String,
    val category: SearchCategory,
    val title: String,
    /** The cropped thumbnail exactly as the endpoint returned it. */
    val thumbUrl: String,
    /** The same image without Pixiv's crop, used by the waterfall. */
    val fullUrl: String,
    val userId: String,
    val userName: String,
    val xRestrict: Int,
    val width: Int,
    val height: Int,
    val pageCount: Int,
    /** Novels only: character count. */
    val textCount: Int,
    val tags: List<String>,
    val seriesTitle: String?,
    val description: String = "",
    /** Novels only: estimated reading time in seconds. */
    val readingTime: Int = 0,
) {
    val isAdult: Boolean get() = xRestrict > 0

    /** height / width, or `null` when the endpoint reported no size (novels). */
    val aspectRatio: Float?
        get() = if (width > 0 && height > 0) height.toFloat() / width.toFloat() else null
}

/** One user returned by Pixiv author search. */
data class UserSearchItem(
    val userId: String,
    val userName: String,
    val avatarUrl: String,
    val comment: String = "",
    val recentWorks: List<SearchItem> = emptyList(),
)

/** Result of an author search page; [requiresLogin] is true when Pixiv refused anonymous access. */
data class UserSearchResult(
    val users: List<UserSearchItem>,
    val requiresLogin: Boolean = false,
)

/** One novel-series content page: viewable rows plus every member id. */
data class SeriesContent(
    val episodes: List<SeriesLink>,
    /** Includes entries the viewer cannot open, so description links can be de-duped. */
    val allIds: Set<String>,
)

/**
 * Reads `seriesNavData`, which both detail endpoints embed.
 *
 * Note the shapes differ: artwork series use a *string* `seriesId` and their
 * prev/next entries carry no `available` flag, while novel series use a number
 * and do set it. `optString` covers both, and a missing flag defaults to true.
 */
internal fun parseSeriesNav(b: JSONObject): SeriesNav? {
    val s = b.optJSONObject("seriesNavData") ?: return null
    val seriesId = s.optString("seriesId")
    if (seriesId.isEmpty() || seriesId == "null") return null

    fun link(key: String): SeriesLink? {
        val o = s.optJSONObject(key) ?: return null
        val id = o.optString("id")
        if (id.isEmpty() || id == "null") return null
        return SeriesLink(
            id = id,
            title = o.optString("title"),
            order = o.optInt("order"),
            available = o.optBoolean("available", true),
        )
    }

    return SeriesNav(
        seriesId = seriesId,
        title = s.optString("title"),
        order = s.optInt("order"),
        prev = link("prev"),
        next = link("next"),
    )
}

/** Dimension selector for `/ranking.php`. */
enum class RankingMode(val apiMode: String) {
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    ROOKIE("rookie"),
}

/** Sort order supported by Pixiv artwork/novel search for all accounts. */
enum class SearchSortOrder(val apiOrder: String) {
    NEWEST("date_d"),
    OLDEST("date"),
}

/** Age rating filter supported by Pixiv search. */
enum class SearchRatingFilter(val apiMode: String) {
    ALL("all"),
    SAFE("safe"),
    R18("r18"),
}

/** Aggregated metadata for a tag search query, extracted alongside page 1 results. */
data class TagMetadata(
    val tag: String,
    val englishName: String? = null,
    val illustTotal: Int = 0,
    val novelTotal: Int = 0,
    val officialCoverUrl: String? = null,
) {
    val totalWorks: Int get() = illustTotal + novelTotal
}

/** Search page result paired with optional tag metadata (populated on page 1). */
data class SearchPageResult(
    val items: List<SearchItem>,
    val tagMetadata: TagMetadata? = null,
)

/**
 * Undoes Pixiv's thumbnail crop.
 *
 * Search results carry `…/c/250x250_80_a2/img-master/…​_square1200.jpg`. Dropping
 * the `/c/WxH…/` resizer segment (and, for artworks, swapping `_square1200` for
 * `_master1200`) yields the uncropped file, whose real aspect ratio matches the
 * `width`/`height` the endpoint reported. Verified against the live CDN:
 * 250×250 cropped → 849×1200 uncropped, novel 427×600 → 640×900.
 */
internal fun uncropThumb(url: String): String = url
    .replace(Regex("/c/[0-9]+x[0-9]+(_[^/]+)?/"), "/")
    .replace("_square1200.jpg", "_master1200.jpg")
