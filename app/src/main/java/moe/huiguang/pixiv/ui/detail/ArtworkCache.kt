package moe.huiguang.pixiv.ui.detail

import moe.huiguang.pixiv.data.model.IllustDetail
import moe.huiguang.pixiv.data.model.IllustPage

/**
 * Small LRU cache for the artwork detail + its page list.
 *
 * The detail screen re-composes on rotation and every time the user comes back
 * from the viewer; both used to refetch. Keeping the last few works makes those
 * transitions instant and keeps the pager's saved position meaningful.
 */
class ArtworkCache(private val capacity: Int = 6) {

    data class Entry(
        val detail: IllustDetail,
        val pages: List<IllustPage>,
    )

    private val entries = object : LinkedHashMap<String, Entry>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > capacity
    }

    @Synchronized
    fun get(id: String): Entry? = entries[id]

    @Synchronized
    fun put(id: String, entry: Entry) {
        entries[id] = entry
    }

    // Avatars are tiny and shared across a whole author's works.
    private val avatars = object : LinkedHashMap<String, String>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > 64
    }

    @Synchronized
    fun avatar(userId: String): String? = avatars[userId]

    @Synchronized
    fun putAvatar(userId: String, url: String) {
        if (url.isNotEmpty()) avatars[userId] = url
    }

    @Synchronized
    fun clear() {
        entries.clear()
        avatars.clear()
    }
}
