package moe.huiguang.pixiv.data

import moe.huiguang.pixiv.data.store.FavoriteEntry
import moe.huiguang.pixiv.data.store.FavoriteStore
import moe.huiguang.pixiv.data.store.InMemoryKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteStoreTest {

    private fun entry(id: String) = FavoriteEntry(
        id = id,
        title = "作品$id",
        thumbUrl = "",
        userName = "作者",
        userId = "u1",
        kind = "illust",
        savedAt = id.toLongOrNull() ?: 0,
    )

    @Test
    fun `toggle adds then removes`() {
        val store = FavoriteStore(InMemoryKeyValueStore())
        assertTrue(store.toggle(entry("1")))
        assertTrue(store.isFavorite("1"))
        assertFalse(store.toggle(entry("1")))
        assertFalse(store.isFavorite("1"))
    }

    @Test
    fun `favourites survive a new instance`() {
        val kv = InMemoryKeyValueStore()
        FavoriteStore(kv).toggle(entry("1"))
        FavoriteStore(kv).toggle(entry("2"))
        val reloaded = FavoriteStore(kv)
        assertEquals(listOf("2", "1"), reloaded.items.value.map { it.id })
    }

    @Test
    fun `newest first and capped`() {
        val store = FavoriteStore(InMemoryKeyValueStore())
        repeat(FavoriteStore.MAX_ENTRIES + 10) { index ->
            store.toggle(entry((1000 + index).toString()))
        }
        assertEquals(FavoriteStore.MAX_ENTRIES, store.items.value.size)
        assertEquals((FavoriteStore.MAX_ENTRIES + 9 + 1000).toString(), store.items.value.first().id)
    }

    @Test
    fun `re-adding does not duplicate`() {
        val store = FavoriteStore(InMemoryKeyValueStore())
        store.toggle(entry("1"))
        store.toggle(entry("1"))
        store.toggle(entry("1"))
        assertEquals(1, store.items.value.size)
    }

    @Test
    fun `remove works and persists`() {
        val kv = InMemoryKeyValueStore()
        val store = FavoriteStore(kv)
        store.toggle(entry("1"))
        store.remove("1")
        assertFalse(FavoriteStore(kv).isFavorite("1"))
    }
}
