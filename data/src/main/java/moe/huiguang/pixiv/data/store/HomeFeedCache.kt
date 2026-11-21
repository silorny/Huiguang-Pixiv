package moe.huiguang.pixiv.data.store

import moe.huiguang.pixiv.data.model.HomeFeed
import moe.huiguang.pixiv.data.model.HomeFeedCodec
import org.json.JSONObject

/**
 * Last home feed, keyed by the account it belongs to.
 *
 * The owner check matters: after logging out the cached personalised feed must
 * not leak back into the guest view.
 */
class HomeFeedCache(private val store: KeyValueStore) {

    fun load(ownerId: String?): HomeFeed? {
        val raw = store.getString(KEY) ?: return null
        val owner = runCatching { JSONObject(raw).optString("owner") }.getOrNull()
        if (owner != (ownerId ?: GUEST)) return null
        return HomeFeedCodec.decode(runCatching { JSONObject(raw).optString("feed") }.getOrNull())
    }

    fun save(ownerId: String?, feed: HomeFeed) {
        if (feed.isEmpty) return
        val payload = JSONObject()
            .put("owner", ownerId ?: GUEST)
            .put("feed", HomeFeedCodec.encode(feed))
        store.putString(KEY, payload.toString())
    }

    fun clear() {
        store.remove(KEY)
    }

    private companion object {
        const val KEY = "home_feed_cache"
        const val GUEST = "guest"
    }
}
