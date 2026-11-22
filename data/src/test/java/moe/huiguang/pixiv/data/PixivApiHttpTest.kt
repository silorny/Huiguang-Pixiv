package moe.huiguang.pixiv.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import moe.huiguang.pixiv.data.model.RankingMode
import moe.huiguang.pixiv.data.model.SearchCategory
import moe.huiguang.pixiv.data.model.SearchItem
import moe.huiguang.pixiv.data.model.SearchMode
import moe.huiguang.pixiv.data.model.SearchRatingFilter
import moe.huiguang.pixiv.data.model.SearchSortOrder
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.net.extractTrendingTags
import moe.huiguang.pixiv.data.net.normalizeCookieHeader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Exercises the real HTTP path against a local JDK HttpServer.
 *
 * No MockWebServer dependency: `com.sun.net.httpserver` ships with the JDK, so
 * the test stays offline and still covers headers, status handling and retries.
 */
class PixivApiHttpTest {

    private lateinit var server: HttpServer
    private var baseUrl = ""
    private val hits = ConcurrentHashMap<String, AtomicInteger>()
    private val lastReferer = ConcurrentHashMap<String, String>()
    private val lastCookie = ConcurrentHashMap<String, String>()
    private val lastCsrf = ConcurrentHashMap<String, String>()
    private val lastRequestBody = ConcurrentHashMap<String, String>()
    private val lastQuery = ConcurrentHashMap<String, String>()
    private var touchReturnsHtml = false

    private fun pathOf(exchange: HttpExchange): String = exchange.requestURI.path

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = pathOf(exchange)
            hits.getOrPut(path) { AtomicInteger() }.incrementAndGet()
            exchange.requestURI.rawQuery?.let { lastQuery[path] = it }
            exchange.requestHeaders.getFirst("Referer")?.let { lastReferer[path] = it }
            exchange.requestHeaders.getFirst("Cookie")?.let { lastCookie[path] = it }
            exchange.requestHeaders.getFirst("X-Csrf-Token")?.let { lastCsrf[path] = it }
            val reqBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).readText()
            if (reqBody.isNotEmpty()) lastRequestBody[path] = reqBody
            // First call to /drop dies without a response, like a pooled socket
            // the server closed between requests.
            if (path == "/ajax/illust/drop" && hits[path]!!.get() == 1) {
                exchange.close()
                return@createContext
            }
            val (code, body) = route(path)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun route(path: String): Pair<Int, String> = when {
        path == "/" -> 200 to """<html><meta name="global-data" id="meta-global-data" content="{&quot;token&quot;:&quot;0123456789abcdef0123456789abcdef&quot;}"></html>"""
        path == "/touch/ajax_api/ajax_api.php" ->
            if (touchReturnsHtml) 200 to "<!DOCTYPE html><html></html>"
            else 200 to """{"isSucceed":true}"""
        path == "/ajax/illusts/bookmarks/add" -> 200 to """{"error":false,"body":{"last_bookmark_id":"999111"}}"""
        path == "/ajax/illusts/bookmarks/delete" -> 200 to """{"error":false,"body":[]}"""
        path == "/ajax/novels/bookmarks/add" -> 200 to """{"error":false,"body":"888222"}"""
        path == "/ajax/novels/bookmarks/delete" -> 200 to """{"error":false,"body":[]}"""
        path == "/ajax/illust/404" -> 404 to "{\"error\":true}"
        path == "/ajax/illust/777" -> 200 to ILLUST_JSON
        path == "/ajax/illust/drop" -> 200 to ILLUST_JSON
        path == "/ajax/illust/503" ->
            if (hits["/ajax/illust/503"]!!.get() == 1) 503 to "busy" else 200 to ILLUST_JSON
        path == "/ajax/novel/888" -> 200 to NOVEL_JSON
        path == "/ajax/user/self" -> 200 to SELF_JSON
        path.startsWith("/ajax/search/artworks/") -> 200 to SEARCH_ART_JSON
        path.startsWith("/ajax/search/novels/") -> 200 to SEARCH_NOVEL_JSON
        path.startsWith("/ajax/search/tags/") -> 200 to SEARCH_TAG_INFO_JSON
        path == "/ajax/search/users" ->
            if (lastCookie[path] == "PHPSESSID=abc") 200 to SEARCH_USERS_JSON else 400 to "{\"error\":true}"
        path == "/ajax/illust/discovery" -> 200 to DISCOVERY_ILLUST_JSON
        path == "/ajax/novel/discovery" -> 200 to DISCOVERY_NOVEL_JSON
        path == "/ranking.php" -> 200 to RANKING_JSON
        path.contains("/bookmarks") -> 400 to "{\"error\":true,\"message\":\"不正なリクエストです\"}"
        else -> 404 to "{}"
    }

    private fun api(cookie: String? = null) = PixivApi(
        baseUrl = baseUrl,
        cookieProvider = { cookie },
    )

    @Test
    fun `missing illust returns null`() = runBlocking {
        assertNull(api().illust("404"))
    }

    @Test
    fun `illust parses fields and series`() = runBlocking {
        val detail = api().illust("777")
        assertNotNull(detail)
        detail!!
        assertEquals("777", detail.id)
        assertEquals("テスト絵", detail.title)
        assertEquals(listOf("初音ミク", "オリジナル"), detail.tags)
        assertEquals(2, detail.pageCount)
        assertEquals("https://i.pximg.net/master.jpg", detail.previewUrl)
        val series = detail.series
        assertNotNull(series)
        assertEquals("900", series!!.seriesId)
        assertEquals(2, series.order)
        assertEquals("776", series.prev?.id)
        assertNull(series.next)
    }

    @Test
    fun `server error is retried once`() = runBlocking {
        val detail = api().illust("503")
        assertNotNull(detail)
        assertEquals(2, hits["/ajax/illust/503"]!!.get())
    }

    @Test
    fun `dropped connection is retried once`() = runBlocking {
        val detail = api().illust("drop")
        assertNotNull(detail)
        assertEquals("テスト絵", detail!!.title)
        assertEquals(2, hits["/ajax/illust/drop"]!!.get())
    }

    @Test
    fun `referer is always sent`() = runBlocking {
        api().illust("777")
        assertEquals("https://www.pixiv.net/", lastReferer["/ajax/illust/777"])
    }

    @Test
    fun `session cookie is attached`() = runBlocking {
        api(cookie = "PHPSESSID=abc").illust("777")
        assertEquals("PHPSESSID=abc", lastCookie["/ajax/illust/777"])
    }

    @Test
    fun `novel parses body and embedded images`() = runBlocking {
        val novel = api().novel("888")
        assertNotNull(novel)
        novel!!
        assertEquals(1, novel.xRestrict)
        assertTrue(novel.content.contains("[uploadedimage:555]"))
        val image = novel.embeddedImages["555"]
        assertNotNull(image)
        assertEquals("https://i.pximg.net/480.jpg", image!!.displayUrl)
        assertEquals("https://i.pximg.net/1200.jpg", image.originalUrl)
    }

    @Test
    fun `search merges artwork and novel results`() = runBlocking {
        val items = api().search("初音ミク", mode = moe.huiguang.pixiv.data.model.SearchMode.KEYWORD)
        assertEquals(2, items.size)
        val manga = items.first { it.category == SearchCategory.MANGA }
        assertEquals("100", manga.id)
        assertTrue(manga.fullUrl.contains("_master1200"))
        assertTrue(!manga.fullUrl.contains("/c/"))
        val novel = items.first { it.category == SearchCategory.NOVEL }
        assertEquals(1200, novel.textCount)
        assertEquals(0, novel.width)
    }

    @Test
    fun `bookmarks without login degrade to empty`() = runBlocking {
        assertTrue(api().bookmarks("1").isEmpty())
    }

    @Test
    fun `current user id comes from userData`() = runBlocking {
        assertEquals("12345", api(cookie = "PHPSESSID=abc").currentUserId())
    }

    @Test
    fun `searchUsers returns users when signed in and signals requiresLogin otherwise`() = runBlocking {
        val anon = api().searchUsers("miku")
        assertTrue(anon.requiresLogin)
        assertTrue(anon.users.isEmpty())

        val signed = api(cookie = "PHPSESSID=abc").searchUsers("miku")
        assertTrue(!signed.requiresLogin)
        assertEquals(1, signed.users.size)
        assertEquals("99", signed.users[0].userId)
        assertEquals("絵師A", signed.users[0].userName)
        assertEquals(1, signed.users[0].recentWorks.size)
    }

    @Test
    fun `discovery and ranking endpoints parse mixed categories`() = runBlocking {
        val illusts = api().illustDiscovery()
        val novels = api().novelDiscovery()
        val ranked = api().rankingPage(mode = RankingMode.WEEKLY, category = SearchCategory.MANGA)
        assertEquals(1, illusts.size)
        assertEquals(SearchCategory.MANGA, illusts[0].category)
        assertEquals(1, novels.size)
        assertEquals("短篇 简介", novels[0].description)
        assertEquals(1, ranked.size)
        assertEquals("301", ranked[0].id)
        val rankingQuery = lastQuery["/ranking.php"].orEmpty()
        assertTrue(rankingQuery.contains("mode=weekly"))
        assertTrue(rankingQuery.contains("content=manga"))
    }

    @Test
    fun `search forwards sort order and age rating filters`() = runBlocking {
        api().search(
            word = "初音ミク",
            mode = SearchMode.TAG,
            order = SearchSortOrder.OLDEST,
            rating = SearchRatingFilter.SAFE,
        )
        val q = lastQuery.entries.firstOrNull { it.key.startsWith("/ajax/search/artworks/") }?.value.orEmpty()
        assertTrue(q.contains("order=date"))
        assertTrue(q.contains("mode=safe"))
        assertTrue(q.contains("s_mode=s_tag"))
    }

    @Test
    fun `extractTrendingTags ranks non-adult topic tags and filters milestone tags`() {
        val sample = listOf(
            SearchItem("1", SearchCategory.ILLUST, "A", "", "", "1", "u", 0, 10, 10, 1, 0, listOf("初音ミク", "1000users入り", "オリジナル"), null),
            SearchItem("2", SearchCategory.ILLUST, "B", "", "", "2", "v", 0, 10, 10, 1, 0, listOf("オリジナル", "風景", "R-18"), null),
            SearchItem("3", SearchCategory.ILLUST, "C", "", "", "3", "w", 1, 10, 10, 1, 0, listOf("SecretAdultTag", "風景"), null),
        )
        val safeTags = extractTrendingTags(sample, limit = 5, includeAdult = false)
        assertEquals(listOf("オリジナル", "初音ミク", "風景"), safeTags)
        val allTags = extractTrendingTags(sample, limit = 5, includeAdult = true)
        assertTrue(allTags.contains("SecretAdultTag"))
    }

    @Test
    fun `normalizeCookieHeader deduplicates anonymous PHPSESSID`() {
        val raw = "PHPSESSID=anon123; __cf_bm=abc; PHPSESSID=12345_realhash; __cf_bm=def"
        val normalized = normalizeCookieHeader(raw)
        assertEquals("PHPSESSID=12345_realhash; __cf_bm=def", normalized)
    }

    @Test
    fun `followUser uses touch api and rejects html 200`() = runBlocking {
        val client = api("PHPSESSID=12345_real")
        assertTrue(client.followUser("42"))
        assertEquals("0123456789abcdef0123456789abcdef", lastCsrf["/touch/ajax_api/ajax_api.php"])
        assertTrue(lastRequestBody["/touch/ajax_api/ajax_api.php"].orEmpty().contains("add_bookmark_user"))

        touchReturnsHtml = true
        assertFalse(client.followUser("42"))
        touchReturnsHtml = false
        assertTrue(client.unfollowUser("42"))
        assertTrue(lastRequestBody["/touch/ajax_api/ajax_api.php"].orEmpty().contains("delete_bookmark_user"))
    }

    @Test
    fun `addBookmark and removeBookmark send work referer and parse bookmark id`() = runBlocking {
        val client = api("PHPSESSID=12345_real")
        val bid = client.addBookmark("777", novel = false)
        assertEquals("999111", bid)
        assertEquals("$baseUrl/artworks/777", lastReferer["/ajax/illusts/bookmarks/add"])
        assertTrue(client.removeBookmark("777", bookmarkId = bid, novel = false))
        assertEquals("$baseUrl/artworks/777", lastReferer["/ajax/illusts/bookmarks/delete"])

        val nBid = client.addBookmark("888", novel = true)
        assertEquals("888222", nBid)
        assertEquals("$baseUrl/novel/show.php?id=888", lastReferer["/ajax/novels/bookmarks/add"])
        assertTrue(client.removeBookmark("888", bookmarkId = nBid, novel = true))
    }

    @Test
    fun `production api never sends session cookie to loopback hosts`() {
        val prodApi = PixivApi(cookieProvider = { "PHPSESSID=secret_token" })
        val req = okhttp3.Request.Builder().url("$baseUrl/ajax/illust/777").get().build()
        prodApi.client.newCall(req).execute().use { resp ->
            assertEquals(200, resp.code)
        }
        assertNull(lastCookie["/ajax/illust/777"])
    }

    @Test
    fun `searchPage parses tag work totals and english translation`() = runBlocking {
        val result = api().searchPage("風景", SearchMode.TAG)
        assertEquals(2, result.items.size)
        val meta = result.tagMetadata
        assertNotNull(meta)
        assertEquals("風景", meta!!.tag)
        assertEquals("scenery", meta.englishName)
        assertEquals(4200, meta.illustTotal)
        assertEquals(180, meta.novelTotal)
        assertEquals(4380, meta.totalWorks)
        assertEquals("https://i.pximg.net/c/540x540_70/img-master/img/tag_cover.jpg", meta.officialCoverUrl)
    }

    @Test
    fun `cookie and referer are stripped on cross-host redirect`() {
        var redirectedCookie: String? = "UNSET"
        var redirectedReferer: String? = "UNSET"
        val externalServer = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/target") { ex ->
                redirectedCookie = ex.requestHeaders.getFirst("Cookie")
                redirectedReferer = ex.requestHeaders.getFirst("Referer")
                val bytes = "ok".toByteArray(Charsets.UTF_8)
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val redirectServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/hop") { ex ->
                ex.responseHeaders.add("Location", "http://localhost:${externalServer.address.port}/target")
                ex.sendResponseHeaders(302, -1)
                ex.close()
            }
            start()
        }
        try {
            val testApi = PixivApi(
                baseUrl = "http://127.0.0.1:${redirectServer.address.port}",
                cookieProvider = { "PHPSESSID=secret_token" },
            )
            val req = okhttp3.Request.Builder()
                .url("http://127.0.0.1:${redirectServer.address.port}/hop")
                .get()
                .build()
            testApi.client.newCall(req).execute().use { resp ->
                assertEquals(200, resp.code)
            }
            assertNull(redirectedCookie)
            assertNull(redirectedReferer)
        } finally {
            redirectServer.stop(0)
            externalServer.stop(0)
        }
    }

    private companion object {
        const val ILLUST_JSON = """
        {"error":false,"body":{
          "illustId":"777","illustTitle":"テスト絵","userId":"42","userName":"作者",
          "urls":{"thumb":"https://i.pximg.net/thumb.jpg","small":"https://i.pximg.net/small.jpg",
                  "regular":"https://i.pximg.net/master.jpg","original":"https://i.pximg.net/original.jpg"},
          "pageCount":2,"xRestrict":0,"width":800,"height":1200,
          "bookmarkCount":10,"likeCount":3,"viewCount":100,"createDate":"2026-01-01T00:00:00+09:00",
          "tags":{"tags":[{"tag":"初音ミク"},{"tag":"オリジナル"}]},
          "seriesNavData":{"seriesId":"900","title":"連作","order":2,
                           "prev":{"id":"776","title":"前","order":1},"next":null}
        }}
        """

        const val NOVEL_JSON = """
        {"error":false,"body":{
          "id":"888","title":"小説","userId":"42","userName":"作者",
          "coverUrl":"https://i.pximg.net/cover.jpg","description":"あらすじ",
          "content":"一行目\n[uploadedimage:555]\n二行目",
          "textEmbeddedImages":{"555":{"novelImageId":"555","urls":{
            "480mw":"https://i.pximg.net/480.jpg",
            "1200x1200":"https://i.pximg.net/1200.jpg",
            "original":"https://i.pximg.net/orig.jpg"}}},
          "tags":{"tags":[{"tag":"夢小説"}]},
          "xRestrict":1,"isLoginOnly":false,"characterCount":10,"readingTime":1,
          "bookmarkCount":2,"likeCount":1,"viewCount":5,"createDate":"2026-01-01T00:00:00+09:00"
        }}
        """

        const val SELF_JSON = """
        {"error":false,"body":{"userData":{"id":"12345","name":"me"}}}
        """

        const val SEARCH_ART_JSON = """
        {"error":false,"body":{"illustManga":{"total":4200,"data":[{
          "id":"100","title":"作品A","illustType":1,
          "url":"https://i.pximg.net/c/250x250_80_a2/img-master/img/2026/01/01/00/00/00/100_p0_square1200.jpg",
          "userId":"1","userName":"u","xRestrict":0,"width":600,"height":800,
          "pageCount":3,"tags":["tag1","tag2"]
        }]},"tagTranslation":{"風景":{"en":"scenery"}}}}
        """

        const val SEARCH_NOVEL_JSON = """
        {"error":false,"body":{"novel":{"total":180,"data":[{
          "id":"200","title":"小説B","url":"https://i.pximg.net/c/250x250_80_a2/img-master/img/2026/01/01/00/00/00/200_p0_square1200.jpg",
          "userId":"2","userName":"v","xRestrict":0,"textCount":1200,"tags":["小説"],
          "seriesTitle":"連作"
        }]}}}
        """

        const val SEARCH_TAG_INFO_JSON = """
        {"error":false,"body":{"tag":"風景","pixpedia":{"image":"https://i.pximg.net/c/540x540_70/img-master/img/tag_cover.jpg","id":38474246}}}
        """

        const val SEARCH_USERS_JSON = """
        {"error":false,"body":{"user":{"data":[{
          "userId":"99","userName":"絵師A",
          "profileImg":"https://i.pximg.net/user/99.png",
          "comment":"简介<br />第二行",
          "recentWorks":[{"id":"501","title":"近作","illustType":0,"url":"https://i.pximg.net/501.jpg","userId":"99","userName":"絵師A","width":800,"height":1000}]
        }]}}}
        """

        const val DISCOVERY_ILLUST_JSON = """
        {"error":false,"body":{"illusts":[{
          "id":"401","title":"漫画D","illustType":1,"xRestrict":0,
          "url":"https://i.pximg.net/c/360x360_70/custom-thumb/img/401_p0_custom1200.jpg",
          "userId":"10","userName":"漫画家","width":800,"height":1200,"pageCount":4,"tags":["漫画"]
        }]}}
        """

        const val DISCOVERY_NOVEL_JSON = """
        {"error":false,"body":{"novels":[{
          "id":"402","title":"小说D","xRestrict":0,
          "url":"https://i.pximg.net/c/600x600/novel-cover-master/402.jpg",
          "userId":"11","userName":"作者N","textCount":8500,"readingTime":1200,
          "description":"短篇<br>简介","tags":["原创"]
        }]}}
        """

        const val RANKING_JSON = """
        {"contents":[{
          "illust_id":301,"title":"榜单作品","illust_type":"0","illust_page_count":"2",
          "url":"https://i.pximg.net/c/480x960/img-master/301_p0_master1200.jpg",
          "user_id":12,"user_name":"作者R","width":1000,"height":1400,"tags":["tag"],
          "illust_content_type":{"sexual":0}
        }]}
        """
    }
}
