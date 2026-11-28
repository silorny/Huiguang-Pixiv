package moe.huiguang.pixiv.data.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import moe.huiguang.pixiv.data.model.BookmarkItem
import moe.huiguang.pixiv.data.model.HomeFeed
import moe.huiguang.pixiv.data.model.HistoryEntry
import moe.huiguang.pixiv.data.model.IllustDetail
import moe.huiguang.pixiv.data.model.IllustPage
import moe.huiguang.pixiv.data.model.NovelDetail
import moe.huiguang.pixiv.data.model.NovelImage
import moe.huiguang.pixiv.data.model.NovelSeries
import moe.huiguang.pixiv.data.model.PixivisionDetail
import moe.huiguang.pixiv.data.model.PixivisionSummary
import moe.huiguang.pixiv.data.model.RankingMode
import moe.huiguang.pixiv.data.model.RelatedIllustsPage
import moe.huiguang.pixiv.data.model.SearchCategory
import moe.huiguang.pixiv.data.model.SearchItem
import moe.huiguang.pixiv.data.model.SearchMode
import moe.huiguang.pixiv.data.model.SearchPageResult
import moe.huiguang.pixiv.data.model.SearchRatingFilter
import moe.huiguang.pixiv.data.model.SearchSortOrder
import moe.huiguang.pixiv.data.model.SeriesContent
import moe.huiguang.pixiv.data.model.SeriesLink
import moe.huiguang.pixiv.data.model.SeriesNav
import moe.huiguang.pixiv.data.model.TagMetadata
import moe.huiguang.pixiv.data.model.UserProfile
import moe.huiguang.pixiv.data.model.UserSearchItem
import moe.huiguang.pixiv.data.model.UserSearchResult
import moe.huiguang.pixiv.data.model.uncropThumb
import moe.huiguang.pixiv.data.model.parseSeriesNav
import moe.huiguang.pixiv.data.model.uncropThumb
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Raised for transport / server failures (as opposed to "not found"). */
class PixivException(message: String, val code: Int = -1) : IOException(message)

/**
 * Thin client over the public Pixiv web ("ajax") endpoints.
 *
 * Three things make this work from a plain HTTP client:
 *  - `https://www.pixiv.net/ajax/...` answers without any login cookie for
 *    all-ages works.
 *  - images live on `i.pximg.net`, which refuses (403) any request whose
 *    `Referer` is not exactly `https://www.pixiv.net/`, so every request going
 *    through [client] carries that header.
 *  - R-18 works are only ever returned to an authenticated session, so the
 *    cookie captured at login is attached to every request as well.
 *
 * `baseUrl` and [cookieProvider] are constructor parameters so the whole
 * protocol can be exercised against a local MockWebServer without touching the
 * real site.
 */
class PixivApi(
    private val baseUrl: String = DEFAULT_BASE,
    private val cookieProvider: () -> String? = { SessionHolder.cookie },
    private val debugLogging: Boolean = false,
) {

    companion object {
        const val DEFAULT_BASE = "https://www.pixiv.net"
        const val REFERER = "https://www.pixiv.net/"
        const val PAGE_URL = "https://www.pixiv.net/artworks/"
        const val NOVEL_PAGE_URL = "https://www.pixiv.net/novel/show.php?id="

        private const val UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private const val SERIES_CACHE_MAX = 12
        private const val RETRY_DELAY_MS = 700L
    }

    private val ua = UA
    private val baseHost: String? = baseUrl.toHttpUrlOrNull()?.host?.lowercase()
    private val allowLoopbackTestHost: Boolean =
        baseUrl.startsWith("http://127.0.0.1") || baseUrl.startsWith("http://localhost")

    private fun isValidPathId(id: String): Boolean {
        if (id.isEmpty()) return false
        return if (allowLoopbackTestHost) {
            id.length <= 64 && id.all { it.isLetterOrDigit() || it == '_' || it == '-' }
        } else {
            id.length <= 32 && id.all { it in '0'..'9' }
        }
    }

    private fun isPixivCookieHost(host: String): Boolean =
        host == "pixiv.net" || host.endsWith(".pixiv.net") ||
            host == "pixiv.org" || host.endsWith(".pixiv.org")

    private fun isAllowedPixivHost(request: Request): Boolean {
        val host = request.url.host.lowercase()
        if (isPixivCookieHost(host) ||
            host == "pximg.net" || host.endsWith(".pximg.net") ||
            host == "pixivision.net" || host.endsWith(".pixivision.net")
        ) {
            return request.isHttps
        }
        return allowLoopbackTestHost && baseHost != null && host == baseHost
    }

    private fun isCookieAllowed(request: Request): Boolean {
        val host = request.url.host.lowercase()
        if (isPixivCookieHost(host)) {
            return request.isHttps
        }
        return allowLoopbackTestHost && baseHost != null && host == baseHost
    }

    /** Shared by the API calls and by Coil's image loading. */
    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
            if (isAllowedPixivHost(original)) {
                builder.header("Accept-Language", "zh-CN,zh;q=0.9,ja;q=0.8,en;q=0.7")
                if (original.header("Referer") == null) {
                    builder.header("Referer", REFERER)
                }
            }
            if (original.header("User-Agent") == null) {
                builder.header("User-Agent", ua)
            }
            chain.proceed(builder.build())
        }
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val host = request.url.host.lowercase()
            val isLoopback = host == "127.0.0.1" || host == "localhost" || host == "::1"
            if (!request.isHttps && !isLoopback) {
                throw IOException("Blocked cleartext HTTP request")
            }
            val builder = request.newBuilder()
            if (!isAllowedPixivHost(request)) {
                builder.removeHeader("Referer")
                builder.removeHeader("Accept-Language")
                builder.removeHeader("Origin")
                builder.removeHeader("X-Csrf-Token")
                builder.removeHeader("X-Requested-With")
            }
            // Enforce cookie scope on every network hop (including 30x redirects)
            // so session cookies never leak to non-Pixiv hosts or over cleartext.
            if (isCookieAllowed(request)) {
                val explicitCookie = request.header("Cookie")
                val raw = explicitCookie ?: cookieProvider()
                val normalized = raw?.let(::normalizeCookieHeader).orEmpty()
                if (normalized.isNotEmpty()) {
                    builder.header("Cookie", normalized)
                } else {
                    builder.removeHeader("Cookie")
                }
            } else {
                builder.removeHeader("Cookie")
            }
            chain.proceed(builder.build())
        }
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 12
            },
        )
        .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
        .connectTimeout(20, TimeUnit.SECONDS)
        // A heavily illustrated novel is a ~1 MB JSON response and an original
        // artwork can be several MB; a 20 s read window times those out.
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    // ---------------------------------------------------------------- illust

    /**
     * Raw response of the personalised top page.
     *
     * The JSON is a deep, section-based payload whose exact shape has changed
     * several times, so the parser is written against the *raw* body and this
     * entry is kept public for diagnostics.
     */
    suspend fun homeFeedRaw(): Pair<Int, String> =
        getJson("$baseUrl/ajax/top/illust?mode=all&lang=zh")

    /** Novel counterpart of [homeFeedRaw]; same `page` / `thumbnails` shape. */
    suspend fun homeNovelRaw(): Pair<Int, String> =
        getJson("$baseUrl/ajax/top/novel?mode=all&lang=zh")

    /**
     * The signed-in home feed, or `null` when the illust endpoint refuses the
     * session (it answers **400** without one). Novels are fetched in parallel
     * from `/ajax/top/novel` and merged into the same three sections; a novel
     * failure never hides the illustration feed.
     */
    suspend fun homeFeed(): HomeFeed? {
        val illustPair: Pair<Int, String>
        val novelPair: Pair<Int, String>?
        val features: List<PixivisionSummary>
        coroutineScope {
            val illust = async {
                try {
                    homeFeedRaw()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    throw PixivException(e.message ?: "network")
                }
            }
            val novel = async {
                runCatching { homeNovelRaw() }.getOrNull()
            }
            val pixivision = async {
                runCatching { pixivisionList() }.getOrDefault(emptyList())
            }
            illustPair = illust.await()
            novelPair = novel.await()
            features = pixivision.await()
        }

        val (code, body) = illustPair
        if (code == 400 || code == 401) return null
        if (code != 200) throw PixivException("HTTP $code", code)
        val illustFeed = parseHomePayload(body, novel = false) ?: return null
        val novelFeed = novelPair?.takeIf { it.first == 200 }?.let { (_, raw) ->
            runCatching { parseHomePayload(raw, novel = true) }.getOrNull()
        }
        return illustFeed.copy(
            novelRecommended = novelFeed?.recommended.orEmpty(),
            novelFollowing = novelFeed?.following.orEmpty(),
            pixivision = features,
        )
    }

    private fun parseHomePayload(body: String, novel: Boolean): HomeFeed? {
        val root = JSONObject(body)
        if (root.optBoolean("error")) return null
        val payload = root.optJSONObject("body") ?: return null
        val page = payload.optJSONObject("page") ?: return null
        val cache = payload.optJSONObject("thumbnails")
            ?.optJSONArray(if (novel) "novel" else "illust")

        val byId = HashMap<String, SearchItem>()
        if (cache != null) {
            for (i in 0 until cache.length()) {
                val o = cache.optJSONObject(i) ?: continue
                val item = if (novel) parseThumbNovel(o) else parseThumbIllust(o)
                if (item.id.isNotEmpty()) byId[item.id] = item
            }
        }

        fun resolveIds(ids: JSONArray?, limit: Int): List<SearchItem> {
            if (ids == null) return emptyList()
            val out = ArrayList<SearchItem>(minOf(ids.length(), limit))
            for (i in 0 until ids.length()) {
                if (out.size >= limit) break
                val id = ids.opt(i)?.toString().orEmpty()
                byId[id]?.let { out.add(it) }
            }
            return out
        }

        val recommendIds = page.optJSONObject("recommend")?.optJSONArray("ids")
        val recommended = resolveIds(recommendIds, 60)

        val rankingItems = page.optJSONObject("ranking")?.opt("items") as? JSONArray
        val ranking = if (rankingItems == null) {
            emptyList()
        } else {
            buildList {
                for (i in 0 until rankingItems.length()) {
                    if (size >= 30) break
                    val id = rankingItems.optJSONObject(i)?.optString("id").orEmpty()
                    byId[id]?.let { add(it) }
                }
            }
        }

        // Illustrations use `follow`; novels expose the same idea as `newNovels`.
        val followingIds = if (novel) page.optJSONArray("newNovels") else page.optJSONArray("follow")
        val following = resolveIds(followingIds, 30)

        // `tags` is the per-account recommendation list; `trendingTags` is global,
        // so prefer the former for the home chips.
        val tagArray = page.optJSONArray("tags") ?: page.optJSONArray("trendingTags")
        val tags = buildList {
            if (tagArray != null) {
                for (i in 0 until tagArray.length()) {
                    if (size >= 12) break
                    val tag = tagArray.optJSONObject(i)?.optString("tag").orEmpty()
                    if (tag.isNotEmpty()) add(tag)
                }
            }
        }

        return HomeFeed(
            recommended = recommended,
            ranking = ranking,
            following = following,
            tags = tags,
        )
    }

    private fun parseThumbNovel(o: JSONObject): SearchItem {
        val thumb = o.optString("url")
        val tags = o.optJSONArray("tags")?.let { array ->
            buildList {
                for (i in 0 until minOf(array.length(), 6)) {
                    val tag = array.optString(i)
                    if (tag.isNotEmpty()) add(tag)
                }
            }
        } ?: o.optString("tags")
            .split(' ')
            .filter { it.isNotBlank() }
            .take(6)
        return SearchItem(
            id = o.optString("id"),
            category = SearchCategory.NOVEL,
            title = o.optString("title"),
            // Novel covers are already master-size; don't un-crop them.
            thumbUrl = thumb,
            fullUrl = thumb,
            userId = o.optString("userId"),
            userName = o.optString("userName"),
            xRestrict = o.optInt("xRestrict", 0),
            width = 0,
            height = 0,
            pageCount = 0,
            textCount = o.optInt("textCount"),
            tags = tags,
            seriesTitle = o.optString("seriesTitle").takeIf { it.isNotEmpty() },
            description = plainSnippet(o.optString("description")),
            readingTime = o.optInt("readingTime"),
        )
    }

    /**
     * Logged-out home: Pixiv hides the personalised page, so fall back to public
     * discovery feeds plus curated tag searches.
     */
    suspend fun fallbackFeed(): HomeFeed {
        val tags = listOf("オリジナル", "初音ミク", "風景", "女の子")
        val (artDisc, novelDisc, features) = coroutineScope {
            val a = async { runCatching { illustDiscovery(limit = 24) }.getOrDefault(emptyList()) }
            val n = async { runCatching { novelDiscovery(limit = 16) }.getOrDefault(emptyList()) }
            val p = async { runCatching { pixivisionList() }.getOrDefault(emptyList()) }
            Triple(a.await(), n.await(), p.await())
        }
        val artworks = LinkedHashMap<String, SearchItem>()
        artDisc.forEach { artworks.putIfAbsent(it.id, it) }
        if (artworks.size < 12) {
            tags.forEach { tag ->
                runCatching { search(tag, SearchMode.TAG, page = 1, limit = 12) }
                    .getOrNull()
                    ?.filter { it.category != SearchCategory.NOVEL }
                    ?.forEach { artworks.putIfAbsent(it.id, it) }
            }
        }
        val rankingArt = runCatching { rankingPage(page = 1, category = null) }.getOrDefault(emptyList())
        val extractedTags = extractTrendingTags(rankingArt, limit = 12)
        return HomeFeed(
            recommended = artworks.values.take(48),
            ranking = rankingArt,
            novelRecommended = novelDisc,
            pixivision = features,
            tags = extractedTags.ifEmpty { tags },
            isFallback = true,
        )
    }

    /**
     * Loads the next page for the home waterfall (section: 0=recommend, 1=ranking,
     * 2=following; category: 0=all, 1=illust, 2=manga, 3=novel on section 0/2,
     * or ranking tab index on section 1).
     */
    suspend fun homeSectionMore(
        section: Int,
        category: Int,
        page: Int,
        rankingMode: RankingMode = RankingMode.DAILY,
    ): List<SearchItem> = when (section) {
        1 -> when (category) {
            1 -> rankingPage(page = page, category = null, mode = RankingMode.WEEKLY)
            2 -> rankingPage(page = page, category = null, mode = RankingMode.MONTHLY)
            3 -> rankingPage(page = page, category = null, mode = RankingMode.ROOKIE)
            4 -> rankingPage(page = page, category = SearchCategory.ILLUST, mode = rankingMode)
            5 -> rankingPage(page = page, category = SearchCategory.MANGA, mode = rankingMode)
            else -> rankingPage(page = page, category = null, mode = rankingMode)
        }
        2 -> followLatestPage(novel = category == 3, page = page)
        else -> if (category == 3) novelDiscovery(limit = 24) else illustDiscovery(limit = 30)
    }

    /** Fetches live popular search tags from public daily ranking items. */
    suspend fun trendingTags(limit: Int = 12, includeAdult: Boolean = false): List<String> {
        val ranked = runCatching { rankingPage(page = 1, category = null, mode = RankingMode.DAILY) }
            .getOrDefault(emptyList())
        return extractTrendingTags(ranked, limit = limit, includeAdult = includeAdult)
    }

    /** Fetches the latest Pixivision editorial features in the active app language. */
    suspend fun pixivisionList(): List<PixivisionSummary> {
        val lang = pixivisionLang()
        val (code, html) = get(
            url = "https://www.pixivision.net/$lang/",
            accept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            cookie = null,
            userAgent = DESKTOP_UA,
        )
        if (code != 200) return emptyList()
        return PixivisionParser.parseList(html, lang)
    }

    /** Fetches a single Pixivision editorial article in the active app language. */
    suspend fun pixivisionDetail(id: String): PixivisionDetail? {
        if (!isValidPathId(id)) return null
        val lang = pixivisionLang()
        val (code, html) = get(
            url = "https://www.pixivision.net/$lang/a/$id",
            accept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            cookie = null,
            userAgent = DESKTOP_UA,
        )
        if (code != 200) throw PixivException("HTTP $code", code)
        return PixivisionParser.parseDetail(id, html, lang)
    }

    private fun pixivisionLang(): String {
        val locale = java.util.Locale.getDefault()
        return when {
            locale.language.equals("en", ignoreCase = true) -> "en"
            locale.language.equals("ja", ignoreCase = true) -> "ja"
            locale.language.equals("zh", ignoreCase = true) &&
                (locale.country in listOf("HK", "TW", "MO") || locale.script == "Hant") -> "zh-tw"
            else -> "zh"
        }
    }

    /** Public/logged-in illustration discovery stream (`/ajax/illust/discovery`). */
    suspend fun illustDiscovery(limit: Int = 24): List<SearchItem> {
        val (code, body) = getJson("$baseUrl/ajax/illust/discovery?mode=all&limit=$limit&lang=zh")
        if (code != 200) return emptyList()
        val root = JSONObject(body)
        if (root.optBoolean("error")) return emptyList()
        val arr = root.optJSONObject("body")?.optJSONArray("illusts") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val item = parseThumbIllust(o)
                if (item.id.isNotEmpty()) add(item)
            }
        }
    }

    /** Public/logged-in novel discovery stream (`/ajax/novel/discovery`). */
    suspend fun novelDiscovery(limit: Int = 20): List<SearchItem> {
        val (code, body) = getJson("$baseUrl/ajax/novel/discovery?mode=all&limit=$limit&lang=zh")
        if (code != 200) return emptyList()
        val root = JSONObject(body)
        if (root.optBoolean("error")) return emptyList()
        val arr = root.optJSONObject("body")?.optJSONArray("novels") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val item = parseThumbNovel(o)
                if (item.id.isNotEmpty()) add(item)
            }
        }
    }

    /** Paginated ranking via `/ranking.php?mode=...&p=...&format=json`. */
    suspend fun rankingPage(
        page: Int = 1,
        category: SearchCategory? = null,
        mode: RankingMode = RankingMode.DAILY,
    ): List<SearchItem> {
        val content = when (category) {
            SearchCategory.ILLUST -> "&content=illust"
            SearchCategory.MANGA -> "&content=manga"
            else -> ""
        }
        val (code, body) = getJson(
            url = "$baseUrl/ranking.php?mode=${mode.apiMode}$content&p=$page&format=json",
            userAgent = DESKTOP_UA,
        )
        if (code != 200) throw PixivException("HTTP $code", code)
        val trimmed = body.trimStart()
        if (!trimmed.startsWith("{")) throw PixivException("HTTP $code: ${trimmed.take(220)}")
        val root = JSONObject(trimmed)
        if (root.has("error") && !root.isNull("error")) {
            val err = root.optString("error")
            if (err.isNotEmpty() && err != "false") throw PixivException(err)
        }
        val contents = root.optJSONArray("contents") ?: return emptyList()
        return buildList {
            for (i in 0 until contents.length()) {
                val o = contents.optJSONObject(i) ?: continue
                val id = o.optString("illust_id")
                if (id.isEmpty()) continue
                val thumb = o.optString("url")
                val illustType = o.optString("illust_type").toIntOrNull() ?: 0
                val xRestrict = o.optJSONObject("illust_content_type")?.optInt("sexual") ?: 0
                add(
                    SearchItem(
                        id = id,
                        category = if (illustType == 1) SearchCategory.MANGA else SearchCategory.ILLUST,
                        title = o.optString("title"),
                        thumbUrl = thumb,
                        fullUrl = uncropThumb(thumb),
                        userId = o.optString("user_id"),
                        userName = o.optString("user_name"),
                        xRestrict = xRestrict,
                        width = o.optInt("width"),
                        height = o.optInt("height"),
                        pageCount = o.optString("illust_page_count").toIntOrNull() ?: 1,
                        textCount = 0,
                        tags = stringArray(o.optJSONArray("tags")),
                        seriesTitle = null,
                    ),
                )
            }
        }
    }

    /** Paginated following latest works (`/ajax/follow_latest/{illust|novel}`). */
    private suspend fun followLatestPage(novel: Boolean, page: Int): List<SearchItem> {
        val kind = if (novel) "novel" else "illust"
        val (code, body) = getJson("$baseUrl/ajax/follow_latest/$kind?p=$page&mode=all&lang=zh")
        if (code != 200) return emptyList()
        val root = JSONObject(body)
        if (root.optBoolean("error")) return emptyList()
        val payload = root.optJSONObject("body") ?: return emptyList()
        val ids = payload.optJSONObject("page")?.optJSONArray("ids") ?: return emptyList()
        val thumbs = payload.optJSONObject("thumbnails")?.optJSONArray(kind) ?: return emptyList()
        val byId = HashMap<String, SearchItem>()
        for (i in 0 until thumbs.length()) {
            val o = thumbs.optJSONObject(i) ?: continue
            val item = if (novel) parseThumbNovel(o) else parseThumbIllust(o)
            if (item.id.isNotEmpty()) byId[item.id] = item
        }
        return buildList {
            for (i in 0 until ids.length()) {
                val id = ids.opt(i)?.toString().orEmpty()
                byId[id]?.let(::add)
            }
        }
    }

    private fun parseThumbIllust(o: JSONObject): SearchItem {
        val thumb = o.optString("url")
        return SearchItem(
            id = o.optString("id"),
            category = if (o.optInt("illustType") == 1) SearchCategory.MANGA else SearchCategory.ILLUST,
            title = o.optString("title"),
            thumbUrl = thumb,
            fullUrl = uncropThumb(thumb),
            userId = o.optString("userId"),
            userName = o.optString("userName"),
            xRestrict = o.optInt("xRestrict", 0),
            width = o.optInt("width"),
            height = o.optInt("height"),
            pageCount = o.optInt("pageCount", 1),
            textCount = 0,
            tags = stringArray(o.optJSONArray("tags")),
            seriesTitle = null,
        )
    }

    /** Returns the illustration, or `null` when Pixiv has no such id (HTTP 404). */
    suspend fun illust(id: String): IllustDetail? {
        if (!isValidPathId(id)) return null
        val (code, body) = getJson("$baseUrl/ajax/illust/$id")
        if (code == 404) return null
        if (code != 200) throw PixivException("HTTP $code", code)
        val json = JSONObject(body)
        if (json.optBoolean("error")) return null
        val payload = json.optJSONObject("body") ?: return null
        return parseIllust(payload)
    }

    /** Per-page URLs: the ~1200px render plus the full-resolution original. */
    suspend fun illustPages(id: String): List<IllustPage> {
        if (!isValidPathId(id)) return emptyList()
        val (code, body) = try {
            getJson("$baseUrl/ajax/illust/$id/pages")
        } catch (e: IOException) {
            return emptyList()
        }
        if (code != 200) return emptyList()
        val json = JSONObject(body)
        if (json.optBoolean("error")) return emptyList()
        val arr = json.optJSONArray("body") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val page = arr.optJSONObject(i) ?: continue
                val urls = page.optJSONObject("urls") ?: continue
                val regular = urls.optString("regular").ifEmpty { urls.optString("original") }
                val original = urls.optString("original").ifEmpty { regular }
                if (regular.isEmpty()) continue
                add(
                    IllustPage(
                        regularUrl = regular,
                        originalUrl = original,
                        width = page.optInt("width"),
                        height = page.optInt("height"),
                    ),
                )
            }
        }
    }

    // ----------------------------------------------------------------- novel

    /** Returns the novel (body text included), or `null` when the id is unknown. */
    suspend fun novel(id: String): NovelDetail? {
        if (!isValidPathId(id)) return null
        val (code, body) = getJson("$baseUrl/ajax/novel/$id")
        if (code == 404) return null
        if (code != 200) throw PixivException("HTTP $code", code)
        val json = JSONObject(body)
        if (json.optBoolean("error")) return null
        val payload = json.optJSONObject("body") ?: return null
        return parseNovel(payload)
    }

    private fun parseNovel(b: JSONObject): NovelDetail {
        val tagArray = b.optJSONObject("tags")?.optJSONArray("tags")
        val tags = buildList {
            if (tagArray != null) {
                for (i in 0 until tagArray.length()) {
                    val t = tagArray.optJSONObject(i)?.optString("tag").orEmpty()
                    if (t.isNotEmpty()) add(t)
                }
            }
        }
        // Images referenced from the body text as [uploadedimage:ID].
        val embedded = LinkedHashMap<String, NovelImage>()
        b.optJSONObject("textEmbeddedImages")?.let { images ->
            val keys = images.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val entry = images.optJSONObject(key) ?: continue
                val urls = entry.optJSONObject("urls") ?: continue
                // Inline rendering uses the ~480px variant: the 1200px file is
                // ~1 MB each, and a novel can carry dozens of them. The full-size
                // URL is kept for "view original" instead of being displayed.
                val preview = urls.optString("480mw")
                    .ifEmpty { urls.optString("240mw") }
                    .ifEmpty { urls.optString("1200x1200") }
                    .ifEmpty { urls.optString("original") }
                if (preview.isEmpty()) continue
                val full = urls.optString("1200x1200")
                    .ifEmpty { urls.optString("original") }
                    .ifEmpty { preview }
                embedded[key] = NovelImage(
                    imageId = entry.optString("novelImageId").ifEmpty { key },
                    displayUrl = preview,
                    originalUrl = full,
                )
            }
        }
        return NovelDetail(
            id = b.optString("id"),
            title = b.optString("title"),
            userId = b.optString("userId"),
            userName = b.optString("userName"),
            coverUrl = b.optString("coverUrl"),
            description = b.optString("description"),
            content = b.optString("content"),
            embeddedImages = embedded,
            series = parseSeriesNav(b),
            tags = tags,
            xRestrict = b.optInt("xRestrict", 0),
            isLoginOnly = b.optBoolean("isLoginOnly"),
            characterCount = b.optInt("characterCount"),
            readingTime = b.optInt("readingTime"),
            bookmarkCount = b.optInt("bookmarkCount"),
            likeCount = b.optInt("likeCount"),
            viewCount = b.optInt("viewCount"),
            createDate = b.optString("createDate"),
            bookmarkId = b.optJSONObject("bookmarkData")?.optString("id")?.takeIf { it.isNotEmpty() },
        )
    }

    // ------------------------------------------------------------- bookmarks

    /**
     * One page of a user's bookmarks — artworks **and** novels merged.
     *
     * Pixiv keeps these in two endpoints, `/illusts/bookmarks` and
     * `/novels/bookmarks`. Querying only the first is why novel bookmarks were
     * invisible.
     *
     * Both answer **HTTP 400** (not 401/403) without a session, byte-identical
     * to the `following` endpoint, so an empty list normally means "not signed
     * in" — the UI asks for a login rather than reporting a failure.
     */
    suspend fun bookmarks(
        userId: String,
        limit: Int = 24,
        offset: Int = 0,
        privateOnly: Boolean = false,
    ): List<BookmarkItem> {
        if (!isValidPathId(userId)) return emptyList()
        val rest = if (privateOnly) "hide" else "show"
        val artworks = bookmarkPage("$baseUrl/ajax/user/$userId/illusts/bookmarks", limit, offset, rest) { w ->
            val thumb = w.optString("url").ifEmpty { w.optString("thumbnailUrl") }
            val xRestrict = w.optInt("xRestrict", 0)
            BookmarkItem(
                id = w.optString("id"),
                kind = HistoryEntry.KIND_ILLUST,
                title = w.optString("title"),
                thumbUrl = thumb,
                userName = w.optString("userName"),
                pageCount = w.optInt("pageCount", 1),
                textCount = 0,
                isAdult = xRestrict > 0,
                illustType = w.optInt("illustType", 0),
                userId = w.optString("userId"),
                width = w.optInt("width", 0),
                height = w.optInt("height", 0),
                fullUrl = if (thumb.isNotEmpty()) uncropThumb(thumb) else "",
                xRestrict = xRestrict,
                tags = parseBookmarkTags(w.optJSONArray("tags")),
                bookmarkId = w.optJSONObject("bookmarkData")?.optString("id").orEmpty(),
            )
        }
        val novels = bookmarkPage("$baseUrl/ajax/user/$userId/novels/bookmarks", limit, offset, rest) { w ->
            val thumb = w.optString("url").ifEmpty { w.optString("thumbnailUrl") }
            val xRestrict = w.optInt("xRestrict", 0)
            BookmarkItem(
                id = w.optString("id"),
                kind = HistoryEntry.KIND_NOVEL,
                title = w.optString("title"),
                thumbUrl = thumb,
                userName = w.optString("userName"),
                pageCount = 1,
                textCount = w.optInt("textCount"),
                isAdult = xRestrict > 0,
                illustType = -1,
                userId = w.optString("userId"),
                fullUrl = thumb,
                xRestrict = xRestrict,
                tags = parseBookmarkTags(w.optJSONArray("tags")),
                bookmarkId = w.optJSONObject("bookmarkData")?.optString("id").orEmpty(),
            )
        }
        return artworks + novels
    }

    @Volatile
    private var cachedCsrfEntry: Pair<String?, String>? = null

    fun clearCsrfCache() {
        cachedCsrfEntry = null
    }

    private fun currentCookieKey(): String? =
        cookieProvider()?.let(::normalizeCookieHeader)?.takeIf { it.isNotEmpty() }

    private fun logApi(msg: String) {
        if (!debugLogging) return
        runCatching { android.util.Log.d("HuiguangApi", msg) }
    }

    private suspend fun csrfToken(forceRefresh: Boolean = false): String? {
        val cookieKey = currentCookieKey()
        if (!forceRefresh) {
            cachedCsrfEntry?.let { (cachedKey, token) ->
                if (cachedKey == cookieKey) return token
            }
        }
        val candidates = listOf(
            "$baseUrl/",
            "$baseUrl/users/11",
        )
        for (pageUrl in candidates) {
            val (code, html) = getText(pageUrl)
            if (code != 200) {
                logApi("csrf fetch $pageUrl -> HTTP $code")
                continue
            }
            val token = extractCsrfToken(html)
            if (!token.isNullOrEmpty()) {
                cachedCsrfEntry = cookieKey to token
                return token
            }
        }
        logApi("csrf token unavailable")
        return null
    }

    private fun extractCsrfToken(html: String): String? {
        val globalMeta = Regex("""id="meta-global-data"[^>]*content="([^"]+)"""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("&quot;", "\"")
        if (!globalMeta.isNullOrEmpty()) {
            Regex(""""token"\s*:\s*"([0-9a-fA-F]{32,})"""")
                .find(globalMeta)
                ?.groupValues
                ?.getOrNull(1)
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        val normalized = html
            .replace("\\\"", "\"")
            .replace("&quot;", "\"")
        val patterns = listOf(
            Regex("""pixiv\.context\.token\s*=\s*"([0-9a-fA-F]{32,})""""),
            Regex("""name="tt"\s+value="([0-9a-fA-F]{32,})""""),
            Regex("""value="([0-9a-fA-F]{32,})"\s+name="tt""""),
            Regex(""""(?:token|csrfToken|csrf_token)"\s*:\s*"([0-9a-fA-F]{32,})""""),
        )
        for (pattern in patterns) {
            val match = pattern.find(normalized)?.groupValues?.getOrNull(1)
            if (!match.isNullOrEmpty()) return match
        }
        return null
    }

    /**
     * Bookmarks an illustration/manga or novel on Pixiv (`restrict = 0` public).
     * @return the created Pixiv bookmark id, or `null` if the request failed.
     */
    suspend fun addBookmark(id: String, novel: Boolean = false): String? {
        if (!isValidPathId(id)) return null
        val url = if (novel) {
            "$baseUrl/ajax/novels/bookmarks/add"
        } else {
            "$baseUrl/ajax/illusts/bookmarks/add"
        }
        val referer = if (novel) "$baseUrl/novel/show.php?id=$id" else "$baseUrl/artworks/$id"
        val payload = JSONObject().apply {
            val numericId = id.toLongOrNull()
            if (numericId != null) {
                put(if (novel) "novel_id" else "illust_id", numericId)
            } else {
                put(if (novel) "novel_id" else "illust_id", id)
            }
            put("restrict", 0)
            put("comment", "")
            put("tags", JSONArray())
        }.toString()
        val (code, body) = postWithCsrf(url, referer = referer) {
            payload.toRequestBody("application/json; charset=utf-8".toMediaType())
        }
        logApi("addBookmark(novel=$novel) -> HTTP $code")
        if (code == 429) throw PixivException("HTTP 429", 429)
        if (code !in 200..299) return null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (json.optBoolean("error")) return null
        val b = json.opt("body")
        return when (b) {
            is JSONObject -> b.optString("last_bookmark_id").ifEmpty { id }
            is String -> b.ifEmpty { id }
            else -> id
        }
    }

    /**
     * Removes an illustration/manga or novel bookmark from Pixiv.
     * If [bookmarkId] is `null`, fetches the work detail first to resolve it.
     */
    suspend fun removeBookmark(
        id: String,
        bookmarkId: String? = null,
        novel: Boolean = false,
    ): Boolean {
        if (!isValidPathId(id)) return false
        val resolvedId = bookmarkId?.takeIf { it.isNotEmpty() } ?: run {
            if (novel) novel(id)?.bookmarkId else illust(id)?.bookmarkId
        } ?: return true
        val url = if (novel) {
            "$baseUrl/ajax/novels/bookmarks/delete"
        } else {
            "$baseUrl/ajax/illusts/bookmarks/delete"
        }
        val referer = if (novel) "$baseUrl/novel/show.php?id=$id" else "$baseUrl/artworks/$id"
        val (code, body) = postWithCsrf(url, referer = referer) {
            FormBody.Builder().apply {
                if (novel) {
                    add("del", "1")
                    add("book_id", resolvedId)
                } else {
                    add("bookmark_id", resolvedId)
                }
            }.build()
        }
        logApi("removeBookmark(novel=$novel) -> HTTP $code")
        if (code == 429) throw PixivException("HTTP 429", 429)
        if (code == 404) return true
        if (code !in 200..299) return false
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return false
        return !json.optBoolean("error", true)
    }

    /**
     * Switches an existing bookmark's privacy between public and private via
     * `/ajax/{illusts|novels}/bookmarks/edit_restrict`.
     */
    suspend fun setBookmarkVisibility(
        id: String,
        novel: Boolean = false,
        bookmarkId: String? = null,
        makePrivate: Boolean,
        userId: String? = null,
    ): Boolean {
        if (!isValidPathId(id)) return false
        if (userId != null && !isValidPathId(userId)) return false
        val resolvedId = bookmarkId?.takeIf { it.isNotEmpty() } ?: run {
            if (novel) novel(id)?.bookmarkId else illust(id)?.bookmarkId
        } ?: return false
        val url = if (novel) {
            "$baseUrl/ajax/novels/bookmarks/edit_restrict"
        } else {
            "$baseUrl/ajax/illusts/bookmarks/edit_restrict"
        }
        val referer = if (!userId.isNullOrEmpty()) {
            val section = if (novel) "novels" else "artworks"
            "$baseUrl/users/$userId/bookmarks/$section"
        } else {
            if (novel) "$baseUrl/novel/show.php?id=$id" else "$baseUrl/artworks/$id"
        }
        val payload = JSONObject().apply {
            put("bookmarkIds", JSONArray().put(resolvedId))
            put("bookmarkRestrict", if (makePrivate) "private" else "public")
        }.toString()
        val (code, body) = postWithCsrf(url, referer = referer) {
            payload.toRequestBody("application/json; charset=utf-8".toMediaType())
        }
        logApi("setBookmarkVisibility(novel=$novel, private=$makePrivate) -> HTTP $code")
        if (code == 429) throw PixivException("HTTP 429", 429)
        if (code !in 200..299) return false
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return false
        return !json.optBoolean("error", true)
    }

    private suspend fun postWithCsrf(
        url: String,
        referer: String = REFERER,
        bodyFactory: (token: String) -> RequestBody,
    ): Pair<Int, String> {
        var token = csrfToken() ?: return -1 to ""
        repeat(2) { attempt ->
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Origin", baseUrl)
                .header("Referer", referer)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-Csrf-Token", token)
                .post(bodyFactory(token))
                .build()
            val response = client.newCall(request).await()
            val code = response.code
            val respBody = response.use { it.body.string() }
            if ((code == 400 || code == 403) && attempt == 0) {
                logApi("postWithCsrf $url -> HTTP $code, refreshing CSRF token")
                token = csrfToken(forceRefresh = true) ?: return code to respBody
            } else {
                return code to respBody
            }
        }
        return -1 to ""
    }

    /** Follows a Pixiv author publicly via `/touch/ajax_api/ajax_api.php`. */
    suspend fun followUser(userId: String): Boolean {
        if (!isValidPathId(userId)) return false
        val (code, body) = postWithCsrf(
            url = "$baseUrl/touch/ajax_api/ajax_api.php",
            referer = "$baseUrl/users/$userId",
        ) {
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("mode", "add_bookmark_user")
                .addFormDataPart("user_id", userId)
                .addFormDataPart("restrict", "0")
                .build()
        }
        logApi("followUser -> HTTP $code")
        if (code == 429) throw PixivException("HTTP 429", 429)
        if (code !in 200..299) return false
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return false
        return json.optBoolean("isSucceed", false)
    }

    /** Unfollows a Pixiv author via `/touch/ajax_api/ajax_api.php`. */
    suspend fun unfollowUser(userId: String): Boolean {
        if (!isValidPathId(userId)) return false
        val (code, body) = postWithCsrf(
            url = "$baseUrl/touch/ajax_api/ajax_api.php",
            referer = "$baseUrl/users/$userId",
        ) {
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("mode", "delete_bookmark_user")
                .addFormDataPart("user_id", userId)
                .build()
        }
        logApi("unfollowUser -> HTTP $code")
        if (code == 429) throw PixivException("HTTP 429", 429)
        if (code == 404) return true
        if (code !in 200..299) return false
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return false
        return json.optBoolean("isSucceed", false)
    }

    private suspend fun bookmarkPage(
        base: String,
        limit: Int,
        offset: Int,
        rest: String,
        map: (JSONObject) -> BookmarkItem,
    ): List<BookmarkItem> {
        val (code, body) = getJson("$base?tag=&offset=$offset&limit=$limit&rest=$rest&lang=ja")
        if (code == 429) throw PixivException("HTTP 429", 429)
        if (code != 200) return emptyList()
        val json = JSONObject(body)
        if (json.optBoolean("error")) return emptyList()
        val works = json.optJSONObject("body")?.optJSONArray("works") ?: return emptyList()
        return buildList {
            for (i in 0 until works.length()) {
                val work = works.optJSONObject(i) ?: continue
                val item = map(work)
                if (item.id.isNotEmpty()) add(item)
            }
        }
    }

    /**
     * Returns an author's public bookmark count (`illusts` + `novels`), or
     * `null` when unavailable (e.g. not signed in, where Pixiv returns HTTP 400).
     */
    suspend fun userBookmarksTotal(id: String): Int? {
        if (!isValidPathId(id)) return null
        return coroutineScope {
            val illustDeferred = async(Dispatchers.IO) {
                bookmarkTotal("$baseUrl/ajax/user/$id/illusts/bookmarks")
            }
            val novelDeferred = async(Dispatchers.IO) {
                bookmarkTotal("$baseUrl/ajax/user/$id/novels/bookmarks")
            }
            val illustTotal = illustDeferred.await()
            val novelTotal = novelDeferred.await()
            when {
                illustTotal != null && novelTotal != null -> illustTotal + novelTotal
                illustTotal != null -> illustTotal
                novelTotal != null -> novelTotal
                else -> null
            }
        }
    }

    private suspend fun bookmarkTotal(base: String): Int? {
        val (code, body) = getJson("$base?tag=&offset=0&limit=1&rest=show&lang=zh")
        if (code != 200) return null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (json.optBoolean("error")) return null
        val total = json.optJSONObject("body")?.optInt("total", -1) ?: -1
        return total.takeIf { it >= 0 }
    }

    private fun parseBookmarkTags(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                array.optString(i).takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
    }

    // ------------------------------------------------------------------ user

    /** Returns the author profile, or `null` when Pixiv has no such user (HTTP 404). */
    suspend fun user(id: String): UserProfile? {
        if (!isValidPathId(id)) return null
        val (code, body) = getJson("$baseUrl/ajax/user/$id?full=1")
        if (code == 404) return null
        if (code != 200) throw PixivException("HTTP $code", code)
        val json = JSONObject(body)
        if (json.optBoolean("error")) return null
        val b = json.optJSONObject("body") ?: return null
        val name = b.optString("name")
        if (name.isEmpty() && b.optString("userId").isEmpty()) return null
        return UserProfile(
            id = b.optString("userId", id),
            name = name,
            avatarUrl = b.optString("imageBig").ifEmpty { b.optString("image") },
            backgroundUrl = b.optJSONObject("background")?.optString("url").orEmpty(),
            comment = b.optString("comment"),
            following = b.optInt("following"),
            mypixivCount = b.optInt("mypixivCount"),
            premium = b.optBoolean("premium"),
            webpage = b.optString("webpage").takeIf { it.isNotEmpty() },
            isFollowed = b.optBoolean("isFollowed", false),
        )
    }

    /** Newest-first ids of a user's illustrations (`body.illusts`). */
    suspend fun userIllustIds(id: String): List<String> =
        profileIds(id, listOf("illusts"))

    /** Newest-first ids of a user's manga works (`body.manga`). */
    suspend fun userMangaIds(id: String): List<String> =
        profileIds(id, listOf("manga"))

    /**
     * Newest-first ids of a user's works. `/profile/all` only returns ids, so the
     * caller decides how many to hydrate into full [IllustDetail]s.
     */
    suspend fun userWorkIds(id: String): List<String> =
        profileIds(id, listOf("illusts", "manga"))

    /** Newest-first novel ids; hydrated through `/profile/novels` in one call. */
    suspend fun userNovelIds(id: String): List<String> =
        profileIds(id, listOf("novels"))

    /** Fetches a user's public following list (`/ajax/user/{id}/following`). */
    suspend fun userFollowing(id: String, offset: Int = 0, limit: Int = 24): List<UserSearchItem> {
        if (!isValidPathId(id)) return emptyList()
        return userRelationList("$baseUrl/ajax/user/$id/following?offset=$offset&limit=$limit&rest=show&lang=zh")
    }

    /** Fetches a user's MyPixiv (好P友) list (`/ajax/user/{id}/mypixiv`). */
    suspend fun userMyPixiv(id: String, offset: Int = 0, limit: Int = 24): List<UserSearchItem> {
        if (!isValidPathId(id)) return emptyList()
        return userRelationList("$baseUrl/ajax/user/$id/mypixiv?offset=$offset&limit=$limit&lang=zh")
    }

    private suspend fun userRelationList(url: String): List<UserSearchItem> {
        val (code, body) = getJson(url)
        if (code != 200) throw PixivException("HTTP $code")
        val root = JSONObject(body)
        if (root.optBoolean("error")) return emptyList()
        val users = root.optJSONObject("body")?.optJSONArray("users") ?: return emptyList()
        return buildList {
            for (i in 0 until users.length()) {
                val u = users.optJSONObject(i) ?: continue
                val uid = u.optString("userId").ifEmpty { u.optString("user_id") }
                if (uid.isEmpty()) continue
                add(
                    UserSearchItem(
                        userId = uid,
                        userName = u.optString("userName").ifEmpty { u.optString("user_name") },
                        avatarUrl = u.optString("profileImageUrl").ifEmpty { u.optString("profile_image_url") },
                        comment = u.optString("userComment"),
                    ),
                )
            }
        }
    }

    private suspend fun profileIds(id: String, keys: List<String>): List<String> {
        if (!isValidPathId(id)) return emptyList()
        val (code, body) = getJson("$baseUrl/ajax/user/$id/profile/all")
        if (code == 404) return emptyList()
        if (code != 200) throw PixivException("HTTP $code", code)
        val json = JSONObject(body)
        if (json.optBoolean("error")) return emptyList()
        val b = json.optJSONObject("body") ?: return emptyList()
        val ids = LinkedHashSet<String>()
        for (key in keys) {
            val obj = b.optJSONObject(key) ?: continue
            val it = obj.keys()
            while (it.hasNext()) ids.add(it.next())
        }
        return ids.sortedByDescending { it.toLongOrNull() ?: 0L }
    }

    /** Hydrates up to [limit] of the user's newest works, newest first. */
    suspend fun userWorks(id: String, limit: Int = 9): List<IllustDetail> =
        userWorksPage(id = id, offset = 0, limit = limit)

    /** Hydrates a page of the user's artworks starting at [offset]. */
    suspend fun userWorksPage(id: String, offset: Int, limit: Int = 18): List<IllustDetail> {
        val ids = userWorkIds(id).drop(offset).take(limit)
        if (ids.isEmpty()) return emptyList()
        val gate = Semaphore(4)
        return coroutineScope {
            ids.map { workId ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        try {
                            illust(workId)
                        } catch (e: CancellationException) {
                            // Never swallow cancellation: a newer keystroke
                            // relies on this coroutine actually stopping.
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /**
     * Batch-loads a page of the user's illustrations or manga starting at [offset]
     * via `/ajax/user/{id}/profile/illusts`, avoiding N+1 `/ajax/illust/{id}` calls
     * and keeping illusts and manga strictly separated by category.
     */
    suspend fun userArtworkItemsPage(
        id: String,
        category: SearchCategory,
        offset: Int,
        limit: Int = 18,
    ): List<SearchItem> {
        if (!isValidPathId(id)) return emptyList()
        val ids = when (category) {
            SearchCategory.MANGA -> userMangaIds(id)
            else -> userIllustIds(id)
        }.drop(offset).take(limit)
        if (ids.isEmpty()) return emptyList()
        val workCategory = if (category == SearchCategory.MANGA) "manga" else "illust"
        val query = ids.joinToString("") { "&ids%5B%5D=$it" }
        val (code, body) = getJson(
            "$baseUrl/ajax/user/$id/profile/illusts?is_first_page=0&work_category=$workCategory&lang=zh$query",
        )
        if (code == 404) return emptyList()
        if (code == 200) {
            val json = JSONObject(body)
            if (!json.optBoolean("error")) {
                val works = json.optJSONObject("body")?.optJSONObject("works")
                if (works != null) {
                    return ids.mapNotNull { workId ->
                        works.optJSONObject(workId)?.let(::parseThumbIllust)?.let { item ->
                            if (category == SearchCategory.MANGA) item.copy(category = SearchCategory.MANGA) else item
                        }
                    }
                }
            }
            return emptyList()
        }
        // Fall back to per-work hydration only if the batch endpoint itself failed.
        val gate = Semaphore(4)
        return coroutineScope {
            ids.map { workId ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        try {
                            illust(workId)?.let { d ->
                                SearchItem(
                                    id = d.id,
                                    category = if (category == SearchCategory.MANGA || d.illustType == 1) {
                                        SearchCategory.MANGA
                                    } else {
                                        SearchCategory.ILLUST
                                    },
                                    title = d.title,
                                    thumbUrl = d.thumbUrl,
                                    fullUrl = d.previewUrl,
                                    userId = d.userId,
                                    userName = d.userName,
                                    xRestrict = d.xRestrict,
                                    width = d.width,
                                    height = d.height,
                                    pageCount = d.pageCount,
                                    textCount = 0,
                                    tags = d.tags,
                                    seriesTitle = d.series?.title,
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /**
     * Fetches neighboring works by [userId] centered around [currentId]
     * (newest first), using `/profile/all` + batch `/profile/illusts`.
     */
    suspend fun authorNeighborWorks(
        userId: String,
        currentId: String,
        radius: Int = 6,
    ): List<SearchItem> {
        if (!isValidPathId(userId) || !isValidPathId(currentId)) return emptyList()
        val (code, body) = getJson("$baseUrl/ajax/user/$userId/profile/all")
        if (code == 404) return emptyList()
        if (code != 200) throw PixivException("HTTP $code", code)
        val json = JSONObject(body)
        if (json.optBoolean("error")) return emptyList()
        val b = json.optJSONObject("body") ?: return emptyList()

        val illustIds = collectObjectKeys(b.optJSONObject("illusts"))
        val mangaIds = collectObjectKeys(b.optJSONObject("manga"))
        val allIds = (illustIds + mangaIds)
            .distinct()
            .sortedByDescending { it.toLongOrNull() ?: 0L }
        if (allIds.isEmpty()) return emptyList()

        val centerIdx = allIds.indexOf(currentId)
        val windowIds = if (centerIdx >= 0) {
            val start = (centerIdx - radius).coerceAtLeast(0)
            val end = (centerIdx + radius + 1).coerceAtMost(allIds.size)
            allIds.subList(start, end)
        } else {
            allIds.take(radius * 2 + 1)
        }
        val windowIllusts = windowIds.filter { it in illustIds }
        val windowManga = windowIds.filter { it in mangaIds && it !in illustIds }

        val hydrated = HashMap<String, SearchItem>()
        if (windowIllusts.isNotEmpty()) {
            fetchProfileIllustBatch(userId, windowIllusts, SearchCategory.ILLUST)
                .forEach { hydrated[it.id] = it }
        }
        if (windowManga.isNotEmpty()) {
            fetchProfileIllustBatch(userId, windowManga, SearchCategory.MANGA)
                .forEach { hydrated[it.id] = it }
        }
        return windowIds.mapNotNull { hydrated[it] }
    }

    private fun collectObjectKeys(obj: JSONObject?): Set<String> {
        if (obj == null) return emptySet()
        val out = LinkedHashSet<String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (isValidPathId(key)) out += key
        }
        return out
    }

    private suspend fun fetchProfileIllustBatch(
        userId: String,
        ids: List<String>,
        category: SearchCategory,
    ): List<SearchItem> {
        val workCategory = if (category == SearchCategory.MANGA) "manga" else "illust"
        val query = ids.joinToString("") { "&ids%5B%5D=$it" }
        val (code, body) = getJson(
            "$baseUrl/ajax/user/$userId/profile/illusts?is_first_page=0&work_category=$workCategory&lang=zh$query",
        )
        if (code != 200) return emptyList()
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        if (json.optBoolean("error")) return emptyList()
        val works = json.optJSONObject("body")?.optJSONObject("works") ?: return emptyList()
        return ids.mapNotNull { workId ->
            works.optJSONObject(workId)?.let(::parseThumbIllust)?.let { item ->
                if (category == SearchCategory.MANGA) item.copy(category = SearchCategory.MANGA) else item
            }
        }
    }

    /**
     * Novel cards for an author. `/profile/novels` accepts a batch of ids and
     * returns lightweight thumbnails (not the novel body), so 18 works cost one
     * request instead of 18 full novel downloads.
     */
    suspend fun userNovelWorks(id: String, limit: Int = 9): List<SearchItem> =
        userNovelWorksPage(id = id, offset = 0, limit = limit)

    /** Batch-loads a page of the user's novels starting at [offset]. */
    suspend fun userNovelWorksPage(id: String, offset: Int, limit: Int = 18): List<SearchItem> {
        if (!isValidPathId(id)) return emptyList()
        val ids = userNovelIds(id).drop(offset).take(limit)
        if (ids.isEmpty()) return emptyList()
        val query = ids.joinToString("") { "&ids%5B%5D=$it" }
        val (code, body) =
            getJson("$baseUrl/ajax/user/$id/profile/novels?is_first_page=0&lang=zh$query")
        if (code == 404) return emptyList()
        if (code != 200) throw PixivException("HTTP $code", code)
        val json = JSONObject(body)
        if (json.optBoolean("error")) return emptyList()
        val works = json.optJSONObject("body")?.optJSONObject("works") ?: return emptyList()
        return ids.mapNotNull { workId ->
            works.optJSONObject(workId)?.let(::parseThumbNovel)
        }
    }

    // --------------------------------------------------------------- session

    /**
     * Id of the signed-in account, or `null` when the session is anonymous.
     *
     * `/ajax/user/self` answers **200** with `"userData": null` for a guest, so
     * the field itself is the login test. That matters because Pixiv hands a
     * `PHPSESSID` to anonymous visitors too — the cookie existing proves nothing.
     *
     * @param cookie explicit cookie to test with, used right after a WebView
     *   login before it has been promoted to the stored session.
     */
    suspend fun currentUserId(cookie: String? = null): String? {
        try {
            val (code, body) = getJson("$baseUrl/ajax/user/self?full=1", cookie)
            if (code == 200) {
                // Pixiv has answered with the payload in three different places
                // over the years (`userData`, `body.userData`, `body` itself), so
                // accept all of them instead of betting on one shape.
                val json = JSONObject(body)
                val data = json.optJSONObject("userData")
                    ?: json.optJSONObject("body")?.optJSONObject("userData")
                    ?: json.optJSONObject("body")
                if (data != null) {
                    val id = data.optString("id").ifEmpty { data.optString("userId") }
                    if (id.isNotEmpty() && id != "null") return id
                }
            }
            // Fallback: the signed-in home page embeds the id in its state blob.
            val (homeCode, html) = getText("$baseUrl/", cookie)
            if (homeCode == 200) {
                val match = Regex("\"userId\"\\s*:\\s*\"(\\d+)\"").find(html)
                if (match != null) return match.groupValues[1]
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return null
    }

    // --------------------------------------------------------------- parsing

    private fun parseIllust(b: JSONObject): IllustDetail {
        val urls = b.optJSONObject("urls")
        val tagArray = b.optJSONObject("tags")?.optJSONArray("tags")
        val tags = buildList {
            if (tagArray != null) {
                for (i in 0 until tagArray.length()) {
                    val t = tagArray.optJSONObject(i)?.optString("tag").orEmpty()
                    if (t.isNotEmpty()) add(t)
                }
            }
        }
        val regular = urls?.optString("regular").orEmpty()
        val small = urls?.optString("small").orEmpty()
        val currentId = b.optString("illustId")
        return IllustDetail(
            id = currentId,
            title = b.optString("illustTitle"),
            userId = b.optString("userId"),
            userName = b.optString("userName"),
            thumbUrl = urls?.optString("thumb").orEmpty().ifEmpty { small },
            previewUrl = regular.ifEmpty { small },
            series = parseSeriesNav(b),
            originalUrl = urls?.optString("original").orEmpty(),
            pageCount = b.optInt("pageCount", 1),
            illustType = b.optInt("illustType", 0),
            xRestrict = b.optInt("xRestrict", 0),
            width = b.optInt("width"),
            height = b.optInt("height"),
            tags = tags,
            bookmarkCount = b.optInt("bookmarkCount"),
            likeCount = b.optInt("likeCount"),
            viewCount = b.optInt("viewCount"),
            createDate = b.optString("createDate"),
            description = b.optString("description"),
            userAvatarUrl = b.optString("userImage"),
            bookmarkId = b.optJSONObject("bookmarkData")?.optString("id")?.takeIf { it.isNotEmpty() },
            authorNeighborWorks = parseUserIllustNeighbors(b.optJSONObject("userIllusts"), currentId),
        )
    }

    private fun parseUserIllustNeighbors(
        userIllusts: JSONObject?,
        currentId: String,
    ): List<SearchItem> {
        if (userIllusts == null) return emptyList()
        val keys = collectObjectKeys(userIllusts)
            .sortedByDescending { it.toLongOrNull() ?: 0L }
        val nonNullItems = keys.mapNotNull { key ->
            val obj = userIllusts.optJSONObject(key) ?: return@mapNotNull null
            val item = parseThumbIllust(obj)
            val resolvedId = item.id.ifEmpty { key }
            if (!isValidPathId(resolvedId)) return@mapNotNull null
            if (item.id.isEmpty()) item.copy(id = resolvedId) else item
        }
        if (nonNullItems.isEmpty()) return emptyList()
        val idx = nonNullItems.indexOfFirst { it.id == currentId }
        if (idx < 0) return nonNullItems.take(13)
        val start = (idx - 6).coerceAtLeast(0)
        val end = (idx + 7).coerceAtMost(nonNullItems.size)
        return nonNullItems.subList(start, end)
    }

    /** Initial page of related works + queue of `nextIds` for `/ajax/illust/{id}/recommend/init`. */
    suspend fun relatedIllustsInit(id: String, limit: Int = 18): RelatedIllustsPage {
        if (!isValidPathId(id)) return RelatedIllustsPage()
        val safeLimit = limit.coerceIn(1, 50)
        val (code, body) = getJson("$baseUrl/ajax/illust/$id/recommend/init?limit=$safeLimit&lang=zh")
        if (code == 404) return RelatedIllustsPage()
        if (code != 200) throw PixivException("HTTP $code")
        val json = JSONObject(body)
        if (json.optBoolean("error")) return RelatedIllustsPage()
        val payload = json.optJSONObject("body") ?: return RelatedIllustsPage()
        val items = parseRecommendIllustArray(payload.optJSONArray("illusts"))
        val nextArr = payload.optJSONArray("nextIds")
        val nextIds = buildList {
            if (nextArr != null) {
                for (i in 0 until nextArr.length()) {
                    val nextId = nextArr.optString(i).trim()
                    if (nextId.isNotEmpty() && nextId.length <= 32 && nextId.all { it in '0'..'9' }) {
                        add(nextId)
                    }
                }
            }
        }
        return RelatedIllustsPage(items = items, nextIds = nextIds)
    }

    /** Hydrates subsequent pages of related works from `nextIds`. */
    suspend fun relatedIllustsByIds(ids: List<String>): List<SearchItem> {
        val validIds = ids.distinct().filter(::isValidPathId).take(18)
        if (validIds.isEmpty()) return emptyList()
        val query = validIds.joinToString("&") { "illust_ids%5B%5D=$it" }
        val (code, body) = getJson("$baseUrl/ajax/illust/recommend/illusts?lang=zh&$query")
        if (code == 404) return emptyList()
        if (code != 200) throw PixivException("HTTP $code", code)
        val json = JSONObject(body)
        if (json.optBoolean("error")) return emptyList()
        val payload = json.optJSONObject("body") ?: return emptyList()
        return parseRecommendIllustArray(payload.optJSONArray("illusts"))
    }

    private fun parseRecommendIllustArray(array: JSONArray?): List<SearchItem> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                if (o.optBoolean("isAdContainer", false)) continue
                val item = parseThumbIllust(o)
                if (isValidPathId(item.id)) add(item)
            }
        }
    }

    // ----------------------------------------------------------------- search

    /**
     * Tag or keyword search.
     *
     * Pixiv splits this across two endpoints and reports illustrations *and*
     * manga together under `illustManga`, so both are queried and the caller
     * filters by [SearchCategory].
     */
    suspend fun search(
        word: String,
        mode: SearchMode,
        page: Int = 1,
        limit: Int = 30,
        order: SearchSortOrder = SearchSortOrder.NEWEST,
        rating: SearchRatingFilter = SearchRatingFilter.ALL,
    ): List<SearchItem> = searchPage(
        word = word,
        mode = mode,
        page = page,
        limit = limit,
        order = order,
        rating = rating,
    ).items

    suspend fun searchPage(
        word: String,
        mode: SearchMode,
        page: Int = 1,
        limit: Int = 30,
        order: SearchSortOrder = SearchSortOrder.NEWEST,
        rating: SearchRatingFilter = SearchRatingFilter.ALL,
    ): SearchPageResult {
        if (word.isBlank()) return SearchPageResult(emptyList())
        val enc = URLEncoder.encode(word, "UTF-8")
        val sm = mode.sMode
        val ord = order.apiOrder
        val rMode = rating.apiMode

        val (artCode, artBody) = getJson(
            "$baseUrl/ajax/search/artworks/$enc?word=$enc&order=$ord&mode=$rMode&p=$page&s_mode=$sm&type=all&lang=en",
        )
        if (artCode == 429) throw PixivException("HTTP 429", 429)
        val (novelCode, novelBody) = getJson(
            "$baseUrl/ajax/search/novels/$enc?word=$enc&order=$ord&mode=$rMode&p=$page&s_mode=$sm&lang=en",
        )
        if (novelCode == 429) throw PixivException("HTTP 429", 429)
        if (artCode != 200 && novelCode != 200) throw PixivException("HTTP $artCode", artCode)

        val items = ArrayList<SearchItem>()
        if (artCode == 200) items += parseArtworkSearch(artBody, limit)
        if (novelCode == 200) items += parseNovelSearch(novelBody, limit)
        val tagMeta = if (mode == SearchMode.TAG) {
            val illustTotal = if (artCode == 200) parseSearchTotal(artBody, "illustManga") else 0
            val novelTotal = if (novelCode == 200) parseSearchTotal(novelBody, "novel") else 0
            val english = (if (artCode == 200) parseTagEnglishTranslation(artBody, word) else null)
                ?: (if (novelCode == 200) parseTagEnglishTranslation(novelBody, word) else null)
            val officialCoverUrl = if (page == 1) {
                val (tagCode, tagBody) = getJson("$baseUrl/ajax/search/tags/$enc")
                if (tagCode == 429) throw PixivException("HTTP 429", 429)
                if (tagCode == 200) parseTagOfficialCover(tagBody) else null
            } else {
                null
            }
            TagMetadata(
                tag = word,
                englishName = english,
                illustTotal = illustTotal,
                novelTotal = novelTotal,
                officialCoverUrl = officialCoverUrl,
            )
        } else {
            null
        }
        return SearchPageResult(items = items, tagMetadata = tagMeta)
    }

    /**
     * Searches creators by nickname (`/ajax/search/users`).
     *
     * Pixiv refuses anonymous user searches with HTTP 400; when that happens,
     * [UserSearchResult.requiresLogin] is returned so the UI can guide the user
     * instead of showing a generic failure.
     */
    suspend fun searchUsers(nick: String, page: Int = 1): UserSearchResult {
        if (nick.isBlank()) return UserSearchResult(emptyList())
        val enc = URLEncoder.encode(nick, "UTF-8")
        val (code, body) = getJson(
            "$baseUrl/ajax/search/users?nick=$enc&s_mode=s_usr&i=1&p=$page&lang=zh",
        )
        if (code == 400 || code == 401 || code == 403) {
            return UserSearchResult(emptyList(), requiresLogin = true)
        }
        if (code != 200) throw PixivException("HTTP $code", code)
        val root = JSONObject(body)
        if (root.optBoolean("error")) return UserSearchResult(emptyList())
        val payload = root.optJSONObject("body") ?: return UserSearchResult(emptyList())
        val arr = payload.optJSONObject("user")?.optJSONArray("data")
            ?: payload.optJSONArray("users")
            ?: return UserSearchResult(emptyList())
        val users = buildList {
            for (i in 0 until arr.length()) {
                val u = arr.optJSONObject(i) ?: continue
                val uid = u.optString("userId").ifEmpty { u.optString("id") }
                if (uid.isEmpty()) continue
                val worksArr = u.optJSONArray("recentWorks") ?: u.optJSONArray("illusts")
                val previews = buildList {
                    if (worksArr != null) {
                        for (j in 0 until minOf(worksArr.length(), 3)) {
                            val w = worksArr.optJSONObject(j) ?: continue
                            val item = parseThumbIllust(w)
                            if (item.id.isNotEmpty()) add(item)
                        }
                    }
                }
                add(
                    UserSearchItem(
                        userId = uid,
                        userName = u.optString("userName").ifEmpty { u.optString("name") },
                        avatarUrl = u.optString("profileImg").ifEmpty { u.optString("imageBig") },
                        comment = plainSnippet(u.optString("comment")),
                        recentWorks = previews,
                    ),
                )
            }
        }
        return UserSearchResult(users = users)
    }

    // ----------------------------------------------------------------- series

    /**
     * Series listings are memoised.
     *
     * A listing cannot be fetched in one request — it is rebuilt by walking
     * `prev`/`next`, which is sequential and therefore slow. Caching turns
     * re-entering a series from several seconds into instant.
     */
    private val seriesCache = LinkedHashMap<String, List<SeriesLink>>()
    private val seriesMetaCache = HashMap<String, NovelSeries>()

    private fun seriesKey(seriesId: String, isNovel: Boolean): String =
        (if (isNovel) "n:" else "i:") + seriesId

    /** Novel series metadata; this endpoint answers without a session. */
    suspend fun novelSeries(seriesId: String): NovelSeries? {
        if (!isValidPathId(seriesId)) return null
        synchronized(seriesMetaCache) { seriesMetaCache[seriesId] }?.let { return it }

        val (code, body) = getJson("$baseUrl/ajax/novel/series/$seriesId?lang=zh")
        if (code != 200) return null
        val json = JSONObject(body)
        if (json.optBoolean("error")) return null
        val b = json.optJSONObject("body") ?: return null
        val published = b.optInt("publishedContentCount")
        val result = NovelSeries(
            id = b.optString("id").ifEmpty { seriesId },
            title = b.optString("title"),
            userId = b.optString("userId"),
            userName = b.optString("userName"),
            caption = b.optString("caption"),
            coverUrl = b.optJSONObject("cover")?.optString("url").orEmpty(),
            total = if (published > 0) published else b.optInt("total"),
            isConcluded = b.optBoolean("isConcluded"),
        )
        synchronized(seriesMetaCache) { seriesMetaCache[seriesId] = result }
        return result
    }

    /**
     * Rebuilds a series listing by walking its `prev`/`next` links.
     *
     * Pixiv exposes no list endpoint for a series — the artwork one 404s and the
     * novel one returns metadata only — but every entry's `seriesNavData` points
     * at its neighbours, so the list is assembled one step at a time.
     * [onProgress] fires after each step so the UI can fill in progressively, and
     * [cap] bounds how many requests a deep series can cost.
     */
    /**
     * Novel-series contents in one request. Unlike [seriesEpisodes], this does
     * not download each novel body: the endpoint returns lightweight covers and
     * the `page.seriesContents` order for the whole batch.
     */
    suspend fun novelSeriesContent(
        seriesId: String,
        limit: Int = 30,
        lastOrder: Int = 0,
    ): SeriesContent? {
        if (!isValidPathId(seriesId)) return null
        val (code, body) = getJson(
            "$baseUrl/ajax/novel/series_content/$seriesId" +
                "?limit=$limit&last_order=$lastOrder&order_by=asc&lang=zh",
        )
        if (code == 404) return null
        if (code != 200) throw PixivException("HTTP $code", code)
        val json = JSONObject(body)
        if (json.optBoolean("error")) return null
        val payload = json.optJSONObject("body") ?: return null
        val meta = payload.optJSONObject("page")?.optJSONArray("seriesContents")
        val works = payload.optJSONObject("thumbnails")?.optJSONArray("novel")

        val byId = HashMap<String, JSONObject>()
        if (works != null) {
            for (i in 0 until works.length()) {
                val work = works.optJSONObject(i) ?: continue
                val workId = work.optString("id")
                if (workId.isNotEmpty()) byId[workId] = work
            }
        }

        val allIds = LinkedHashSet<String>(byId.keys)
        val episodes = ArrayList<SeriesLink>()
        if (meta != null) {
            for (i in 0 until meta.length()) {
                val entry = meta.optJSONObject(i) ?: continue
                val id = entry.optString("id")
                if (id.isEmpty()) continue
                allIds.add(id)
                val work = byId[id] ?: continue
                val order = work.optInt(
                    "seriesContentOrder",
                    entry.optJSONObject("series")?.optInt("contentOrder") ?: (i + 1),
                )
                episodes.add(
                    SeriesLink(
                        id = id,
                        title = work.optString("title"),
                        order = order,
                        available = true,
                        thumbUrl = work.optString("url"),
                        userName = work.optString("userName"),
                        illustType = -1,
                        pageCount = 0,
                    ),
                )
            }
        }
        return SeriesContent(
            episodes = episodes.sortedBy { it.order },
            allIds = allIds,
        )
    }

    suspend fun seriesEpisodes(
        seriesId: String,
        startId: String,
        isNovel: Boolean,
        cap: Int = 20,
        onProgress: (List<SeriesLink>) -> Unit = {},
    ): List<SeriesLink> {
        val key = seriesKey(seriesId, isNovel)
        synchronized(seriesCache) { seriesCache[key] }?.takeIf { it.isNotEmpty() }?.let { cached ->
            onProgress(cached)
            return cached
        }

        val startWork = seriesWork(startId, isNovel) ?: return emptyList()
        val startNav = startWork.series ?: return emptyList()
        val startTitle = startWork.title.ifEmpty { startId }

        val visited = HashSet<String>().apply { add(startId) }
        val before = ArrayList<SeriesLink>()
        val after = ArrayList<SeriesLink>()
        val me = SeriesLink(
            id = startId,
            title = startTitle,
            order = startNav.order,
            available = true,
            thumbUrl = startWork.thumbUrl,
            userName = startWork.userName,
            illustType = startWork.illustType,
            pageCount = startWork.pageCount,
        )

        fun emit() = onProgress(before.asReversed().toList() + me + after)

        var cursor = startNav.prev?.takeIf { it.available }
        while (cursor != null && visited.size < cap) {
            if (!visited.add(cursor.id)) break
            val work = seriesWork(cursor.id, isNovel) ?: break
            val nav = work.series ?: break
            before.add(
                SeriesLink(
                    id = cursor.id,
                    title = cursor.title.ifEmpty { work.title },
                    order = cursor.order,
                    available = true,
                    thumbUrl = work.thumbUrl,
                    userName = work.userName,
                    illustType = work.illustType,
                    pageCount = work.pageCount,
                ),
            )
            emit()
            cursor = nav.prev?.takeIf { it.available }
        }

        cursor = startNav.next?.takeIf { it.available }
        while (cursor != null && visited.size < cap) {
            if (!visited.add(cursor.id)) break
            val work = seriesWork(cursor.id, isNovel) ?: break
            val nav = work.series ?: break
            after.add(
                SeriesLink(
                    id = cursor.id,
                    title = cursor.title.ifEmpty { work.title },
                    order = cursor.order,
                    available = true,
                    thumbUrl = work.thumbUrl,
                    userName = work.userName,
                    illustType = work.illustType,
                    pageCount = work.pageCount,
                ),
            )
            emit()
            cursor = nav.next?.takeIf { it.available }
        }

        val listing = before.asReversed().toList() + me + after
        if (listing.isNotEmpty()) {
            synchronized(seriesCache) {
                seriesCache.remove(key)
                seriesCache[key] = listing
                while (seriesCache.size > SERIES_CACHE_MAX) {
                    val oldest = seriesCache.keys.firstOrNull() ?: break
                    seriesCache.remove(oldest)
                }
            }
        }
        return listing
    }

    /**
     * Walks the `next` chain starting after [fromId], returning up to [count]
     * episodes. Used by the series page to append beyond the first page instead
     * of stopping at the initial cap.
     */
    suspend fun seriesMore(fromId: String, isNovel: Boolean, count: Int): List<SeriesLink> {
        val out = ArrayList<SeriesLink>()
        val visited = HashSet<String>().apply { add(fromId) }
        var cursor = seriesWork(fromId, isNovel)?.series?.next?.takeIf { it.available }
        while (cursor != null && out.size < count) {
            if (!visited.add(cursor.id)) break
            val work = seriesWork(cursor.id, isNovel) ?: break
            val nav = work.series ?: break
            out.add(
                SeriesLink(
                    id = cursor.id,
                    title = cursor.title.ifEmpty { work.title },
                    order = cursor.order,
                    available = true,
                    thumbUrl = work.thumbUrl,
                    userName = work.userName,
                    illustType = work.illustType,
                    pageCount = work.pageCount,
                ),
            )
            cursor = nav.next?.takeIf { it.available }
        }
        return out
    }

    /** Everything the series list needs, from the single detail request per episode. */
    private data class SeriesWork(
        val title: String,
        val thumbUrl: String,
        val userName: String,
        val illustType: Int,
        val pageCount: Int,
        val series: SeriesNav?,
    )

    private suspend fun seriesWork(id: String, isNovel: Boolean): SeriesWork? =
        if (isNovel) {
            novel(id)?.let {
                SeriesWork(it.title, it.coverUrl, it.userName, -1, 0, it.series)
            }
        } else {
            illust(id)?.let {
                SeriesWork(it.title, it.thumbUrl, it.userName, it.illustType, it.pageCount, it.series)
            }
        }
    /** Drops cached series listings, e.g. after signing in or out. */
    fun clearSeriesCache() {
        synchronized(seriesCache) { seriesCache.clear() }
        synchronized(seriesMetaCache) { seriesMetaCache.clear() }
    }

    private fun parseArtworkSearch(body: String, limit: Int): List<SearchItem> {
        val json = JSONObject(body)
        if (json.optBoolean("error")) return emptyList()
        val data = json.optJSONObject("body")
            ?.optJSONObject("illustManga")
            ?.optJSONArray("data") ?: return emptyList()

        return buildList {
            for (i in 0 until minOf(data.length(), limit)) {
                val o = data.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isEmpty()) continue
                val thumb = o.optString("url")
                add(
                    SearchItem(
                        id = id,
                        // illustType: 0 = illustration, 1 = manga, 2 = ugoira.
                        category = if (o.optInt("illustType") == 1) {
                            SearchCategory.MANGA
                        } else {
                            SearchCategory.ILLUST
                        },
                        title = o.optString("title"),
                        thumbUrl = thumb,
                        fullUrl = uncropThumb(thumb),
                        userId = o.optString("userId"),
                        userName = o.optString("userName"),
                        xRestrict = o.optInt("xRestrict", 0),
                        width = o.optInt("width"),
                        height = o.optInt("height"),
                        pageCount = o.optInt("pageCount", 1),
                        textCount = 0,
                        tags = stringArray(o.optJSONArray("tags")),
                        seriesTitle = null,
                    ),
                )
            }
        }
    }

    private fun parseNovelSearch(body: String, limit: Int): List<SearchItem> {
        val json = JSONObject(body)
        if (json.optBoolean("error")) return emptyList()
        val data = json.optJSONObject("body")
            ?.optJSONObject("novel")
            ?.optJSONArray("data") ?: return emptyList()

        return buildList {
            for (i in 0 until minOf(data.length(), limit)) {
                val o = data.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isEmpty()) continue
                val thumb = o.optString("url")
                add(
                    SearchItem(
                        id = id,
                        category = SearchCategory.NOVEL,
                        title = o.optString("title"),
                        thumbUrl = thumb,
                        fullUrl = uncropThumb(thumb),
                        userId = o.optString("userId"),
                        userName = o.optString("userName"),
                        xRestrict = o.optInt("xRestrict", 0),
                        width = 0,
                        height = 0,
                        pageCount = 1,
                        textCount = o.optInt("textCount"),
                        tags = stringArray(o.optJSONArray("tags")),
                        seriesTitle = o.optString("seriesTitle").takeIf { it.isNotEmpty() },
                        description = plainSnippet(o.optString("description")),
                        readingTime = o.optInt("readingTime"),
                    ),
                )
            }
        }
    }

    private fun parseSearchTotal(body: String, sectionKey: String): Int {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return 0
        if (json.optBoolean("error")) return 0
        return json.optJSONObject("body")
            ?.optJSONObject(sectionKey)
            ?.optInt("total", 0)
            ?: 0
    }

    private fun parseTagEnglishTranslation(body: String, word: String): String? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (json.optBoolean("error")) return null
        val translations = json.optJSONObject("body")?.optJSONObject("tagTranslation") ?: return null
        val entry = translations.optJSONObject(word) ?: run {
            var matched: JSONObject? = null
            val keys = translations.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.equals(word, ignoreCase = true)) {
                    matched = translations.optJSONObject(key)
                    break
                }
            }
            matched
        } ?: return null
        val en = entry.optString("en").trim()
        return en.takeIf { it.isNotEmpty() && !it.equals(word, ignoreCase = true) }
    }

    private fun parseTagOfficialCover(body: String): String? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (json.optBoolean("error")) return null
        return json.optJSONObject("body")
            ?.optJSONObject("pixpedia")
            ?.optString("image")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    private fun plainSnippet(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun stringArray(array: org.json.JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                if (value.isNotEmpty()) add(value)
            }
        }
    }

    // -------------------------------------------------------------- transport

    private suspend fun getJson(
        url: String,
        cookie: String? = null,
        userAgent: String? = null,
    ): Pair<Int, String> = get(url, "application/json", cookie, userAgent)

    private suspend fun getText(url: String, cookie: String? = null): Pair<Int, String> =
        get(url, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", cookie)

    private suspend fun get(
        url: String,
        accept: String,
        cookie: String?,
        userAgent: String? = null,
    ): Pair<Int, String> {
        // Two flaky behaviours observed on mobile networks:
        //  - pixiv intermittently answers 503 when busy;
        //  - a pooled keep-alive socket may be closed by the server between
        //    requests ("connection closed"), which surfaces as IOException.
        // One short retry turns both into a success instead of a hard failure.
        var code = -1
        var body = ""
        repeat(2) { attempt ->
            val builder = Request.Builder()
                .url(url)
                .header("Accept", accept)
                .get()
            if (cookie != null) builder.header("Cookie", cookie)
            if (userAgent != null) builder.header("User-Agent", userAgent)
            try {
                val response = client.newCall(builder.build()).await()
                response.use {
                    code = it.code
                    body = it.body.string()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (attempt == 1) throw e
                delay(RETRY_DELAY_MS)
                return@repeat
            }
            if (code !in 500..599) return code to body
            if (attempt == 0) delay(RETRY_DELAY_MS)
        }
        return code to body
    }
}

private val USERS_MILESTONE_REGEX = Regex("\\d+users入り$", RegexOption.IGNORE_CASE)

/** Aggregates popular topic tags from ranked items for search discovery. */
fun extractTrendingTags(
    items: List<SearchItem>,
    limit: Int = 12,
    includeAdult: Boolean = false,
): List<String> {
    if (items.isEmpty() || limit <= 0) return emptyList()
    val counts = LinkedHashMap<String, Int>()
    for (item in items) {
        if (!includeAdult && item.isAdult) continue
        for (rawTag in item.tags) {
            val tag = rawTag.trim()
            if (tag.isEmpty()) continue
            if (!includeAdult && (tag.equals("R-18", ignoreCase = true) || tag.equals("R-18G", ignoreCase = true))) {
                continue
            }
            if (USERS_MILESTONE_REGEX.containsMatchIn(tag)) continue
            counts[tag] = (counts[tag] ?: 0) + 1
        }
    }
    return counts.entries
        .sortedByDescending { it.value }
        .take(limit)
        .map { it.key }
}

/** OkHttp `enqueue` bridged to coroutines so cancelled lookups abort the call. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            // If the lookup was cancelled meanwhile, `resume` drops the value,
            // so close the body ourselves rather than leaking the connection.
            cont.resume(response) { _, _, _ -> response.close() }
        }

        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
}
