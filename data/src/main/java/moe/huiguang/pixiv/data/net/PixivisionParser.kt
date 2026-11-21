package moe.huiguang.pixiv.data.net

import moe.huiguang.pixiv.data.model.PixivisionDetail
import moe.huiguang.pixiv.data.model.PixivisionSummary
import moe.huiguang.pixiv.data.model.PixivisionWork

/**
 * Parses Pixivision editorial lists and article pages from `https://www.pixivision.net/zh/`.
 */
object PixivisionParser {

    private val ARTICLE_CARD_RE = Regex(
        """<article\s+class="_article-(?:eyecatch-)?card[^"]*"[^>]*>(.*?)</article>""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    private val ARTICLE_ID_RE = Regex("""href="(?:https://www\.pixivision\.net)?/[^/"]+/a/(\d+)"""")
    private val BG_IMAGE_RE = Regex("""background-image:\s*url\(([^)]+)\)""")
    private val CATEGORY_SLUG_RE = Regex("""data-gtm-action="ClickCategory"\s*data-gtm-label="([^"]+)"""")
    private val CATEGORY_LABEL_RE = Regex("""<span[^>]*_category-label[^>]*>(.*?)</span>""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val LIST_TITLE_RE = Regex("""<h2[^>]*>(.*?)</h2>""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val DATE_RE = Regex("""<time[^>]*>(.*?)</time>""", setOf(RegexOption.DOT_MATCHES_ALL))

    private val DETAIL_TITLE_RE = Regex("""<h1[^>]*class="am__title"[^>]*>(.*?)</h1>""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val OG_TITLE_RE = Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""")
    private val OG_IMAGE_RE = Regex("""<meta\s+property="og:image"\s+content="([^"]+)"""")
    private val EYECATCH_IMAGE_RE = Regex("""<img[^>]*class="aie__image"[^>]*src="([^"]+)"""")
    private val HEADER_TAGS_UL_RE = Regex("""<ul[^>]*am__header-tags[^>]*>(.*?)</ul>""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val HEADER_TAG_ITEM_RE = Regex("""<a[^>]*class="tls__list-item"[^>]*>(.*?)</a>""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val INTRO_RE = Regex("""<div[^>]*class="fab__paragraph[^"]*"[^>]*>(.*?)</div>""", setOf(RegexOption.DOT_MATCHES_ALL))

    private val USER_ID_RE = Regex("""https?://www\.pixiv\.net/users/(\d+)""")
    private val WORK_TITLE_RE = Regex("""<h3[^>]*class="am__work__title"[^>]*>\s*<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val WORK_AUTHOR_NAME_RE = Regex("""<p[^>]*class="am__work__user-name"[^>]*>.*?<a[^>]*>(.*?)</a>""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val WORK_AVATAR_RE = Regex("""<img[^>]*src="([^"]+)"[^>]*class="am__work__uesr-icon"""")
    private val WORK_AVATAR_ALT_RE = Regex("""am__work__user-icon-container.*?<img[^>]*src="([^"]+)"""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val WORK_MAIN_IMAGE_RE = Regex("""am__work__main.*?<img[^>]*src="([^"]+)"""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val ARTWORK_ID_RE = Regex("""/artworks/(\d+)|illust_id=(\d+)""")
    private val NOVEL_ID_RE = Regex("""/novel/show\.php\?id=(\d+)|/novels/(\d+)""")

    private val HTML_TAG_RE = Regex("""<[^>]+>""")
    private val NUMERIC_ENTITY_RE = Regex("""&#(\d+);""")
    private val HEX_ENTITY_RE = Regex("""&#x([0-9a-fA-F]+);""")

    fun parseList(html: String, lang: String = "zh"): List<PixivisionSummary> {
        val seen = HashSet<String>()
        val out = ArrayList<PixivisionSummary>()
        for (match in ARTICLE_CARD_RE.findAll(html)) {
            val block = match.groupValues[1]
            val id = ARTICLE_ID_RE.find(block)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() } ?: continue
            if (!seen.add(id)) continue
            val rawCover = BG_IMAGE_RE.find(block)?.groupValues?.get(1)?.trim('"', '\'', ' ').orEmpty()
            val cover = if (rawCover.isEmpty() || "/img-original/" in rawCover) {
                "https://embed.pixiv.net/pixivision/$lang/a/$id/ogimage.jpg"
            } else {
                rawCover
            }
            val title = cleanText(LIST_TITLE_RE.find(block)?.groupValues?.get(1).orEmpty())
            if (title.isEmpty()) continue
            val category = cleanText(CATEGORY_LABEL_RE.find(block)?.groupValues?.get(1).orEmpty())
            val slug = CATEGORY_SLUG_RE.find(block)?.groupValues?.get(1).orEmpty()
            val date = cleanText(DATE_RE.find(block)?.groupValues?.get(1).orEmpty())
            out.add(
                PixivisionSummary(
                    id = id,
                    title = title,
                    category = category,
                    categorySlug = slug,
                    coverUrl = cover,
                    date = date,
                ),
            )
        }
        return out
    }

    fun parseDetail(articleId: String, html: String, lang: String = "zh"): PixivisionDetail? {
        val title = cleanText(DETAIL_TITLE_RE.find(html)?.groupValues?.get(1).orEmpty())
            .ifEmpty { unescapeHtml(OG_TITLE_RE.find(html)?.groupValues?.get(1).orEmpty()).trim() }
        if (title.isEmpty()) return null

        val category = cleanText(CATEGORY_LABEL_RE.find(html)?.groupValues?.get(1).orEmpty())
        val slug = CATEGORY_SLUG_RE.find(html)?.groupValues?.get(1).orEmpty()
        val date = cleanText(DATE_RE.find(html)?.groupValues?.get(1).orEmpty())
        val coverUrl = EYECATCH_IMAGE_RE.find(html)?.groupValues?.get(1)
            ?: OG_IMAGE_RE.find(html)?.groupValues?.get(1)
            ?: ""

        val tagsBlock = HEADER_TAGS_UL_RE.find(html)?.groupValues?.get(1).orEmpty()
        val tags = HEADER_TAG_ITEM_RE.findAll(tagsBlock)
            .map { cleanText(it.groupValues[1]) }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

        val introRaw = INTRO_RE.find(html)?.groupValues?.get(1).orEmpty()
        val intro = introRaw
            .replace(Regex("""</p\s*>|<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .let(::cleanMultilineText)

        val workBlocks = html.split("""<div class="am__work">""").drop(1)
        val works = workBlocks.mapNotNull { rawBlock ->
            // Trim at the next major article-item boundary so regexes stay local to one work.
            val block = rawBlock.substringBefore("""<div class="article-item""")
            val titleMatch = WORK_TITLE_RE.find(block) ?: return@mapNotNull null
            val workUrl = titleMatch.groupValues[1]
            val workTitle = cleanText(titleMatch.groupValues[2])
            val novelMatch = NOVEL_ID_RE.find(workUrl)
            val artworkMatch = ARTWORK_ID_RE.find(workUrl)
            val workId: String
            val kind: String
            if (novelMatch != null) {
                workId = novelMatch.groupValues[1].ifEmpty { novelMatch.groupValues[2] }
                kind = "novel"
            } else if (artworkMatch != null) {
                workId = artworkMatch.groupValues[1].ifEmpty { artworkMatch.groupValues[2] }
                kind = if (slug == "manga") "manga" else "illust"
            } else {
                return@mapNotNull null
            }

            val authorId = USER_ID_RE.find(block)?.groupValues?.get(1).orEmpty()
            val authorName = cleanText(WORK_AUTHOR_NAME_RE.find(block)?.groupValues?.get(1).orEmpty())
            val avatarUrl = WORK_AVATAR_RE.find(block)?.groupValues?.get(1)
                ?: WORK_AVATAR_ALT_RE.find(block)?.groupValues?.get(1)
                ?: ""
            val imageUrl = WORK_MAIN_IMAGE_RE.find(block)?.groupValues?.get(1).orEmpty()

            PixivisionWork(
                workId = workId,
                kind = kind,
                title = workTitle,
                imageUrl = imageUrl,
                authorId = authorId,
                authorName = authorName,
                authorAvatarUrl = avatarUrl,
            )
        }

        return PixivisionDetail(
            id = articleId,
            title = title,
            category = category,
            categorySlug = slug,
            date = date,
            coverUrl = coverUrl,
            tags = tags,
            intro = intro,
            works = works,
            articleUrl = "https://www.pixivision.net/$lang/a/$articleId",
        )
    }

    private fun cleanText(raw: String): String =
        unescapeHtml(HTML_TAG_RE.replace(raw, " "))
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun cleanMultilineText(raw: String): String =
        unescapeHtml(HTML_TAG_RE.replace(raw, ""))
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")

    private fun unescapeHtml(input: String): String {
        if ('&' !in input) return input
        var out = input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
        out = NUMERIC_ENTITY_RE.replace(out) { m ->
            m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
        }
        out = HEX_ENTITY_RE.replace(out) { m ->
            m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
        }
        return out
    }
}
