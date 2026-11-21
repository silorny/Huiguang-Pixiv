package moe.huiguang.pixiv.ui.search

import moe.huiguang.pixiv.data.model.SearchItem

/**
 * Process-level cache for search result pages.
 *
 * `remember` inside the results screen meant a rotation (or coming back from
 * the viewer) refetched page 1 and dropped every appended page. This keeps the
 * last few queries so the screen re-renders instantly; the LRU bound stops a
 * long session from holding every result forever.
 */
class SearchResultsCache(private val capacity: Int = 8) {

    data class Entry(
        val items: List<SearchItem>,
        val nextPage: Int,
        val endReached: Boolean,
    )

    private val entries = object : LinkedHashMap<String, Entry>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > capacity
    }

    @Synchronized
    fun get(key: String): Entry? = entries[key]

    @Synchronized
    fun put(key: String, entry: Entry) {
        entries[key] = entry
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}
