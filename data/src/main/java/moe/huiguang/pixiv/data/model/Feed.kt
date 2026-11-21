package moe.huiguang.pixiv.data.model

/**
 * The personalised home feed, assembled from `/ajax/top/illust` plus
 * `/ajax/top/novel`.
 *
 * Each response ships one big payload: `thumbnails.illust` / `thumbnails.novel`
 * is a work cache and the sections (`page.recommend.ids`, `page.ranking.items`,
 * `page.follow` / `page.newNovels`) are id lists pointing into it. Parsing both
 * caches once and resolving ids keeps the feed to two requests.
 */
data class HomeFeed(
    val recommended: List<SearchItem> = emptyList(),
    val ranking: List<SearchItem> = emptyList(),
    val following: List<SearchItem> = emptyList(),
    val pixivision: List<PixivisionSummary> = emptyList(),
    val tags: List<String> = emptyList(),
    /** `true` when this is the logged-out fallback built from curated tags. */
    val isFallback: Boolean = false,
    /** `true` when Pixiv returned no novel follow feed and ranking is shown instead. */
    val novelFollowingFallback: Boolean = false,
) {
    val isEmpty: Boolean
        get() = recommended.isEmpty() && ranking.isEmpty() && following.isEmpty() && pixivision.isEmpty()
}
