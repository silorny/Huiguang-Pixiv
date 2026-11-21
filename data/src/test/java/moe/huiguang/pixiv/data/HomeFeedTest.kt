package moe.huiguang.pixiv.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import moe.huiguang.pixiv.data.model.SearchCategory
import moe.huiguang.pixiv.data.net.PixivApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/**
 * Home feed parsing against the *real* payload shape:
 * `body.thumbnails.illust` is a work cache, the sections are id lists.
 */
class HomeFeedTest {

    private lateinit var server: HttpServer
    private var baseUrl = ""
    private var status = 200

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val code = if (status == 200) 200 else status
            val bytes = (if (code == 200) FEED_JSON else "{}").toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
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

    private fun api() = PixivApi(baseUrl = baseUrl, cookieProvider = { "PHPSESSID=abc" })

    @Test
    fun `logged out feed returns null`() = runBlocking {
        status = 400
        assertNull(api().homeFeed())
    }

    @Test
    fun `feed resolves ids through the thumbnail cache`() = runBlocking {
        val feed = api().homeFeed()
        assertNotNull(feed)
        feed!!
        assertEquals(listOf("101", "102"), feed.recommended.map { it.id })
        assertEquals(listOf("103"), feed.ranking.map { it.id })
        assertEquals(listOf("102"), feed.following.map { it.id })
        assertEquals(listOf("オリジナル", "初音ミク"), feed.tags)
        assertEquals("作品101", feed.recommended[0].title)
        assertEquals(SearchCategory.ILLUST, feed.recommended[0].category)
        assertEquals(SearchCategory.MANGA, feed.recommended[1].category)
        assertTrue(feed.recommended[0].fullUrl.contains("_master1200"))
    }

    private companion object {
        const val FEED_JSON = """
        {"error":false,"body":{
          "thumbnails":{"illust":[
            {"id":"101","title":"作品101","illustType":0,"xRestrict":0,
             "url":"https://i.pximg.net/c/250x250_80_a2/img-master/img/100_p0_square1200.jpg",
             "userId":"1","userName":"作者A","width":800,"height":1200,"pageCount":2,"tags":["tag1"]},
            {"id":"102","title":"作品102","illustType":1,"xRestrict":1,
             "url":"https://i.pximg.net/c/250x250_80_a2/img-master/img/101_p0_square1200.jpg",
             "userId":"2","userName":"作者B","width":1200,"height":800,"pageCount":5,"tags":["tag2"]},
            {"id":"103","title":"作品103","illustType":0,"xRestrict":0,
             "url":"https://i.pximg.net/c/250x250_80_a2/img-master/img/102_p0_square1200.jpg",
             "userId":"3","userName":"作者C","width":900,"height":900,"pageCount":1,"tags":[]}
          ]},
          "page":{
            "recommend":{"ids":["101","102","404"],"details":{}},
            "ranking":{"items":[{"rank":"1","id":"103"}],"date":"2026-11-15"},
            "follow":["102"],
            "trendingTags":[{"tag":"オリジナル","ids":["101"]},{"tag":"初音ミク","ids":["102"]}]
          }
        }}
        """
    }
}
