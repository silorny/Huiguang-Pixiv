package moe.huiguang.pixiv.data.model

/**
 * One saved bookmark.
 *
 * Pixiv keeps artworks and novels in two separate bookmark endpoints, so this
 * normalises both into a single row shape.
 */
data class BookmarkItem(
    val id: String,
    /** [HistoryEntry.KIND_ILLUST] or [HistoryEntry.KIND_NOVEL]. */
    val kind: String,
    val title: String,
    val thumbUrl: String,
    val userName: String,
    val pageCount: Int,
    /** Novels only: character count. */
    val textCount: Int,
    val isAdult: Boolean,
    /** 0 = illustration, 1 = manga, 2 = ugoira; novels use -1. */
    val illustType: Int = 0,
)

/** Where the reader left off in one novel (or an explicit bookmark). */
data class ReadingMark(
    val novelId: String,
    val title: String,
    val blockIndex: Int,
    val totalBlocks: Int,
    val updatedAt: Long,
    /** `true` when the user pinned it explicitly rather than auto-saved. */
    val bookmarked: Boolean,
) {
    val fraction: Float
        get() = if (totalBlocks <= 1) 0f else blockIndex.toFloat() / (totalBlocks - 1)
}
