package moe.huiguang.pixiv.data.model

/** A Pixiv illustration (or manga) work. */
data class IllustDetail(
    val id: String,
    val title: String,
    val userId: String,
    val userName: String,
    /** Small square-ish thumbnail, used in grids. */
    val thumbUrl: String,
    /** ~1200px "master" render, used for the big preview. */
    val previewUrl: String,
    val originalUrl: String,
    val pageCount: Int,
    /** 0 = illustration, 1 = manga, 2 = ugoira. */
    val illustType: Int = 0,
    /** 0 = all ages, 1 = R-18, 2 = R-18G. */
    val xRestrict: Int,
    val width: Int,
    val height: Int,
    val tags: List<String>,
    val bookmarkCount: Int,
    val likeCount: Int,
    val viewCount: Int,
    val createDate: String,
    /** Author-written caption; empty when the artist left it blank. */
    val description: String = "",
    /** Avatar of the author, when the endpoint happens to include it. */
    val userAvatarUrl: String = "",
    /** The series this work belongs to, when it is part of one. */
    val series: SeriesNav? = null,
    /** Non-null when the signed-in user has bookmarked this work on Pixiv. */
    val bookmarkId: String? = null,
    /** Neighboring works by the same author, ordered newest-first around this work. */
    val authorNeighborWorks: List<SearchItem> = emptyList(),
) {
    val isAdult: Boolean get() = xRestrict > 0
}

data class RelatedIllustsPage(
    val items: List<SearchItem> = emptyList(),
    val nextIds: List<String> = emptyList(),
)

/** A Pixiv author / artist profile. */
data class UserProfile(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val backgroundUrl: String = "",
    val comment: String,
    val following: Int,
    val mypixivCount: Int,
    val premium: Boolean,
    val webpage: String?,
    val isFollowed: Boolean = false,
)

/**
 * One page of an illustration.
 *
 * [regularUrl] is the ~1200px "master" render used for the inline preview;
 * [originalUrl] is the untouched upload, used by the full-screen viewer and by
 * downloads.
 */
data class IllustPage(
    val regularUrl: String,
    val originalUrl: String,
    val width: Int,
    val height: Int,
)

/** An image embedded in a novel's body text via a `[uploadedimage:ID]` marker. */
data class NovelImage(
    val imageId: String,
    val displayUrl: String,
    val originalUrl: String,
)

/**
 * A Pixiv novel. Pixiv returns the **entire body text inline** in [content],
 * so the reader needs no second request.
 */
data class NovelDetail(
    val id: String,
    val title: String,
    val userId: String,
    val userName: String,
    val coverUrl: String,
    val description: String,
    /** Full body text; lines are separated by '\n'. */
    val content: String,
    /**
     * Images placed inside the text, keyed by the id used in the
     * `[uploadedimage:ID]` markers that appear in [content].
     */
    val embeddedImages: Map<String, NovelImage>,
    val tags: List<String>,
    val xRestrict: Int,
    val isLoginOnly: Boolean,
    val characterCount: Int,
    val readingTime: Int,
    val bookmarkCount: Int,
    val likeCount: Int,
    val viewCount: Int,
    val createDate: String,
    /** The series this novel belongs to, when it is part of one. */
    val series: SeriesNav? = null,
    /** Non-null when the signed-in user has bookmarked this novel on Pixiv. */
    val bookmarkId: String? = null,
) {
    val isAdult: Boolean get() = xRestrict > 0
}

/**
 * One possible interpretation of a numeric PID.
 *
 * Pixiv's work, novel and user id sequences are independent, so the same digits
 * can legitimately resolve to more than one of them; the UI then shows every
 * match and lets the user choose.
 */
data class LookupCandidate(
    val kind: String,
    val pid: String,
    val title: String,
    val subtitle: String,
    val thumbUrl: String,
)

/** One row of the on-device lookup history. */
data class HistoryEntry(
    val pid: String,
    /** [KIND_ILLUST], [KIND_NOVEL] or [KIND_AUTHOR]. */
    val kind: String,
    val title: String,
    val thumbUrl: String,
    val at: Long,
    val userName: String = "",
    /** 0 = illustration, 1 = manga, 2 = ugoira; novels and authors use -1. */
    val illustType: Int = 0,
) {
    companion object {
        const val KIND_ILLUST = "illust"
        const val KIND_NOVEL = "novel"
        const val KIND_AUTHOR = "author"
    }
}
