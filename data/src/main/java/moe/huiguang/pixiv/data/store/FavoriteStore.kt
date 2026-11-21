package moe.huiguang.pixiv.data.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device favourites.
 *
 * Pixiv's *add bookmark* endpoint is a CSRF-protected POST that needs the page's
 * token; that is not implemented yet, so the star on the detail page stores the
 * work locally. The cloud bookmark list is still read from the API (P7).
 */
data class FavoriteEntry(
    val id: String,
    val title: String,
    val thumbUrl: String,
    val userName: String,
    val userId: String,
    /** `illust` or `novel`. */
    val kind: String,
    val savedAt: Long,
    /** 0 = illustration, 1 = manga, 2 = ugoira; novels use -1. */
    val illustType: Int = 0,
)

class FavoriteStore(private val store: KeyValueStore) {

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<FavoriteEntry>> = _items.asStateFlow()

    private val _followedAuthors = MutableStateFlow(loadFollowed())
    val followedAuthors: StateFlow<Set<String>> = _followedAuthors.asStateFlow()

    fun isFavorite(id: String): Boolean = _items.value.any { it.id == id }

    fun isFollowed(authorId: String): Boolean = authorId in _followedAuthors.value

    /** @return `true` when the author ended up followed. */
    fun toggleFollow(authorId: String): Boolean {
        if (authorId.isEmpty()) return false
        val target = authorId !in _followedAuthors.value
        setFollowed(authorId, target)
        return target
    }

    fun setFollowed(authorId: String, followed: Boolean) {
        if (authorId.isEmpty()) return
        val exists = authorId in _followedAuthors.value
        if (followed == exists) return
        val next = if (followed) _followedAuthors.value + authorId else _followedAuthors.value - authorId
        val array = JSONArray()
        next.forEach { array.put(it) }
        store.putString(KEY_FOLLOWED, array.toString())
        _followedAuthors.value = next
    }

    /** @return `true` when the work ended up favourited. */
    fun toggle(entry: FavoriteEntry): Boolean {
        val exists = isFavorite(entry.id)
        val next = if (exists) {
            _items.value.filterNot { it.id == entry.id }
        } else {
            (listOf(entry) + _items.value.filterNot { it.id == entry.id }).take(MAX_ENTRIES)
        }
        persist(next)
        return !exists
    }

    fun setBookmarked(entry: FavoriteEntry, bookmarked: Boolean) {
        val exists = isFavorite(entry.id)
        if (bookmarked == exists) return
        val next = if (bookmarked) {
            (listOf(entry) + _items.value.filterNot { it.id == entry.id }).take(MAX_ENTRIES)
        } else {
            _items.value.filterNot { it.id == entry.id }
        }
        persist(next)
    }

    fun remove(id: String) {
        persist(_items.value.filterNot { it.id == id })
    }

    private fun persist(items: List<FavoriteEntry>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("thumb", item.thumbUrl)
                    put("user", item.userName)
                    put("uid", item.userId)
                    put("kind", item.kind)
                    put("at", item.savedAt)
                    put("type", item.illustType)
                },
            )
        }
        store.putString(KEY, array.toString())
        _items.value = items
    }

    private fun load(): List<FavoriteEntry> {
        val raw = store.getString(KEY) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isEmpty()) continue
                    add(
                        FavoriteEntry(
                            id = id,
                            title = o.optString("title"),
                            thumbUrl = o.optString("thumb"),
                            userName = o.optString("user"),
                            userId = o.optString("uid"),
                            kind = o.optString("kind", "illust"),
                            savedAt = o.optLong("at"),
                            illustType = o.optInt("type", 0),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadFollowed(): Set<String> {
        val raw = store.getString(KEY_FOLLOWED) ?: return emptySet()
        return try {
            val array = JSONArray(raw)
            buildSet {
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    companion object {
        const val KEY = "favorites"
        const val KEY_FOLLOWED = "followed_authors"
        const val MAX_ENTRIES = 200
    }
}
