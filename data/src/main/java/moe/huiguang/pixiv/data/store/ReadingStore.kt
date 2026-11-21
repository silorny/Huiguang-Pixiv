package moe.huiguang.pixiv.data.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.huiguang.pixiv.data.model.ReadingMark
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers where the reader left off in each novel.
 *
 * One mark per novel: scrolling updates it automatically, and the user can also
 * pin it as an explicit bookmark (which survives later auto-saves).
 */
class ReadingStore(
    private val store: KeyValueStore,
    private val key: String = KEY_MARKS,
) {

    private val _marks = MutableStateFlow(load())
    val marks: StateFlow<Map<String, ReadingMark>> = _marks.asStateFlow()

    fun mark(novelId: String): ReadingMark? = _marks.value[novelId]

    /** Auto-save while reading; keeps an existing bookmark flag. */
    fun saveAuto(mark: ReadingMark) {
        val existing = _marks.value[mark.novelId]
        persist(mark.copy(bookmarked = existing?.bookmarked ?: false))
    }

    /** Pin or unpin the current position. */
    fun toggleBookmark(mark: ReadingMark): Boolean {
        val nowBookmarked = !(_marks.value[mark.novelId]?.bookmarked ?: false)
        persist(mark.copy(bookmarked = nowBookmarked))
        return nowBookmarked
    }

    fun remove(novelId: String) {
        val next = _marks.value.toMutableMap()
        next.remove(novelId)
        write(next)
    }

    private fun persist(mark: ReadingMark) {
        write(_marks.value + (mark.novelId to mark))
    }

    private fun write(next: Map<String, ReadingMark>) {
        // Keep the file small: newest 60 novels only.
        val trimmed = next.entries
            .sortedByDescending { it.value.updatedAt }
            .take(MAX_ENTRIES)
            .associate { it.key to it.value }

        val array = JSONArray()
        trimmed.values.forEach { m ->
            array.put(
                JSONObject().apply {
                    put("id", m.novelId)
                    put("title", m.title)
                    put("block", m.blockIndex)
                    put("total", m.totalBlocks)
                    put("at", m.updatedAt)
                    put("bookmarked", m.bookmarked)
                },
            )
        }
        store.putString(key, array.toString())
        _marks.value = trimmed
    }

    private fun load(): Map<String, ReadingMark> {
        val raw = store.getString(key) ?: return emptyMap()
        return try {
            val array = JSONArray(raw)
            buildMap {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isEmpty()) continue
                    put(
                        id,
                        ReadingMark(
                            novelId = id,
                            title = o.optString("title"),
                            blockIndex = o.optInt("block"),
                            totalBlocks = o.optInt("total"),
                            updatedAt = o.optLong("at"),
                            bookmarked = o.optBoolean("bookmarked"),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    companion object {
        const val KEY_MARKS = "reader_marks"
        const val MAX_ENTRIES = 60
    }
}
