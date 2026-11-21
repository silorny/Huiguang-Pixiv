package moe.huiguang.pixiv.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.repo.EngagementEvent
import moe.huiguang.pixiv.data.repo.EngagementKind
import moe.huiguang.pixiv.data.repo.EngagementRepository
import moe.huiguang.pixiv.data.store.FavoriteEntry
import moe.huiguang.pixiv.data.store.FavoriteStore
import moe.huiguang.pixiv.data.store.InMemoryKeyValueStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

class EngagementRepositoryTest {

    private lateinit var server: HttpServer
    private var baseUrl = ""
    private var failNext = false
    private val touchHits = AtomicInteger()

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            exchange.requestBody.readBytes()
            val (code, body) = when (path) {
                "/" -> 200 to """<meta name="global-data" id="meta-global-data" content="{&quot;token&quot;:&quot;0123456789abcdef0123456789abcdef&quot;}">"""
                "/touch/ajax_api/ajax_api.php" -> {
                    touchHits.incrementAndGet()
                    if (failNext) 500 to "error" else 200 to """{"isSucceed":true}"""
                }
                "/ajax/illusts/bookmarks/add" ->
                    if (failNext) 500 to "error" else 200 to """{"error":false,"body":{"last_bookmark_id":"55501"}}"""
                "/ajax/illusts/bookmarks/delete" ->
                    if (failNext) 500 to "error" else 200 to """{"error":false,"body":[]}"""
                else -> 404 to "{}"
            }
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

    private fun repo(): EngagementRepository {
        val api = PixivApi(baseUrl = baseUrl, cookieProvider = { "PHPSESSID=123_ok" })
        val fav = FavoriteStore(InMemoryKeyValueStore())
        return EngagementRepository(api = api, favorites = fav, minGapMs = 0L, cooldownMs = 100L)
    }

    @Test
    fun `optimistic follow succeeds and keeps state`() = runBlocking {
        val r = repo()
        val ev = async { r.events.first() }
        r.toggleFollow("42")
        assertTrue(r.isFollowed("42"))
        val event = ev.await()
        assertTrue(event is EngagementEvent.Succeeded && event.active)
        assertTrue(r.isFollowed("42"))
    }

    @Test
    fun `bookmark failure rolls back optimistic state`() = runBlocking {
        failNext = true
        val r = repo()
        val ev = async { r.events.first() }
        r.toggleBookmark(
            FavoriteEntry(
                id = "777",
                title = "art",
                thumbUrl = "",
                userName = "u",
                userId = "42",
                kind = "illust",
                savedAt = 1L,
            ),
        )
        assertTrue(r.isBookmarked("777", novel = false))
        val event = ev.await()
        assertTrue(event is EngagementEvent.Failed && event.kind == EngagementKind.ILLUST)
        assertFalse(r.isBookmarked("777", novel = false))
    }

    @Test
    fun `rapid taps coalesce to final state`() = runBlocking {
        val r = repo()
        // 3 rapid toggles: false -> true -> false -> true
        r.toggleFollow("99")
        r.toggleFollow("99")
        r.toggleFollow("99")
        delay(400)
        assertTrue(r.isFollowed("99"))
        assertTrue(touchHits.get() <= 2)
        assertEquals(true, r.followedAuthors.value["99"])
    }
}
