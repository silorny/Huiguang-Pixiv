package moe.huiguang.pixiv.data

import moe.huiguang.pixiv.data.model.HomeFeed
import moe.huiguang.pixiv.data.model.HomeFeedCodec
import moe.huiguang.pixiv.data.model.SearchCategory
import moe.huiguang.pixiv.data.model.SearchItem
import moe.huiguang.pixiv.data.store.HomeFeedCache
import moe.huiguang.pixiv.data.store.InMemoryKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedCacheTest {

    private fun item(id: String, title: String = "作品$id", category: SearchCategory = SearchCategory.ILLUST) =
        SearchItem(
            id = id,
            category = category,
            title = title,
            thumbUrl = "https://i.pximg.net/thumb$id.jpg",
            fullUrl = "https://i.pximg.net/full$id.jpg",
            userId = "u$id",
            userName = "作者$id",
            xRestrict = if (id == "2") 1 else 0,
            width = 800,
            height = 1200,
            pageCount = 2,
            textCount = 0,
            tags = listOf("tagA", "tagB"),
            seriesTitle = null,
        )

    private val feed = HomeFeed(
        recommended = listOf(item("1"), item("2", category = SearchCategory.MANGA)),
        ranking = listOf(item("3")),
        following = listOf(item("4", category = SearchCategory.NOVEL)),
        tags = listOf("オリジナル", "初音ミク"),
    )

    @Test
    fun `feed survives encode and decode`() {
        val decoded = HomeFeedCodec.decode(HomeFeedCodec.encode(feed))
        assertEquals(feed, decoded)
        assertEquals(SearchCategory.MANGA, decoded!!.recommended[1].category)
        assertEquals(1, decoded.recommended[1].xRestrict)
        assertTrue(decoded.recommended[0].tags.contains("tagB"))
    }

    @Test
    fun `cache is scoped to the account that wrote it`() {
        val store = InMemoryKeyValueStore()
        val cache = HomeFeedCache(store)
        cache.save("12345", feed)

        assertEquals(feed, HomeFeedCache(store).load("12345"))
        assertNull(HomeFeedCache(store).load("99999"))
        assertNull(HomeFeedCache(store).load(null))
    }

    @Test
    fun `guest feed does not leak into a signed-in session`() {
        val store = InMemoryKeyValueStore()
        val cache = HomeFeedCache(store)
        cache.save(null, feed)

        assertEquals(feed, HomeFeedCache(store).load(null))
        assertNull(HomeFeedCache(store).load("12345"))
    }

    @Test
    fun `empty feed is not persisted`() {
        val store = InMemoryKeyValueStore()
        HomeFeedCache(store).save("12345", HomeFeed())
        assertNull(HomeFeedCache(store).load("12345"))
        assertTrue(store.snapshot().isEmpty())
    }

    @Test
    fun `rebuilding the cache from a fresh store keeps the feed`() {
        val store = InMemoryKeyValueStore()
        HomeFeedCache(store).save("12345", feed)
        assertEquals(feed, HomeFeedCache(store).load("12345"))
    }
}
