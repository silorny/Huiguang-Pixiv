package moe.huiguang.pixiv.ui.profile

import moe.huiguang.pixiv.data.model.UserProfile

/** Cached "me" card data, so switching tabs does not refetch the profile. */
class SelfProfileCache(private val capacity: Int = 2) {

    data class Entry(val profile: UserProfile, val worksCount: Int)

    private val entries = object : LinkedHashMap<String, Entry>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > capacity
    }

    @Synchronized
    fun get(uid: String): Entry? = entries[uid]

    @Synchronized
    fun put(uid: String, entry: Entry) {
        entries[uid] = entry
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}
