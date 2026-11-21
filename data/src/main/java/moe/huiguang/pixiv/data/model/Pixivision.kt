package moe.huiguang.pixiv.data.model

/** Summary card for a Pixivision editorial feature shown on the home ranking tab. */
data class PixivisionSummary(
    val id: String,
    val title: String,
    val category: String,
    val categorySlug: String,
    val coverUrl: String,
    val date: String,
)

/** A single featured artwork or novel inside a Pixivision article. */
data class PixivisionWork(
    val workId: String,
    val kind: String,
    val title: String,
    val imageUrl: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String,
)

/** Full Pixivision editorial article parsed from `https://www.pixivision.net/zh/a/{id}`. */
data class PixivisionDetail(
    val id: String,
    val title: String,
    val category: String,
    val categorySlug: String,
    val date: String,
    val coverUrl: String,
    val tags: List<String>,
    val intro: String,
    val works: List<PixivisionWork>,
    val articleUrl: String,
)
