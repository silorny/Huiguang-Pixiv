package moe.huiguang.pixiv.ui.novel

import moe.huiguang.pixiv.data.model.NovelDetail

/** LRU of recently opened novels; detail → reader → back never refetches. */
class NovelCache(private val capacity: Int = 4) {

    private val entries = object : LinkedHashMap<String, NovelDetail>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NovelDetail>?): Boolean =
            size > capacity
    }

    @Synchronized
    fun get(id: String): NovelDetail? = entries[id]

    @Synchronized
    fun put(id: String, novel: NovelDetail) {
        entries[id] = novel
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}
