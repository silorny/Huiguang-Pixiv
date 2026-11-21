package moe.huiguang.pixiv.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the home feed across process death.
 *
 * Rotating the device is handled by a ViewModel; being killed in the background
 * (which is what aggressive OEM task killers do when the screen turns off) is
 * not. A compact snapshot (~30 KB) lets the next cold start paint the previous
 * feed immediately and refresh quietly behind it.
 */
object HomeFeedCodec {

    fun encode(feed: HomeFeed): String {
        val root = JSONObject()
        root.put("v", 2)
        root.put("fallback", feed.isFallback)
        root.put("novelFallback", feed.novelFollowingFallback)
        root.put("tags", JSONArray(feed.tags))
        root.put("recommended", itemsToJson(feed.recommended))
        root.put("ranking", itemsToJson(feed.ranking))
        root.put("following", itemsToJson(feed.following))
        root.put("pixivision", pixivisionToJson(feed.pixivision))
        return root.toString()
    }

    fun decode(raw: String?): HomeFeed? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            HomeFeed(
                recommended = itemsFromJson(root.optJSONArray("recommended")),
                ranking = itemsFromJson(root.optJSONArray("ranking")),
                following = itemsFromJson(root.optJSONArray("following")),
                pixivision = pixivisionFromJson(root.optJSONArray("pixivision")),
                tags = root.optJSONArray("tags")?.let { array ->
                    buildList {
                        for (i in 0 until array.length()) {
                            array.optString(i).takeIf { it.isNotEmpty() }?.let(::add)
                        }
                    }
                }.orEmpty(),
                isFallback = root.optBoolean("fallback"),
                novelFollowingFallback = root.optBoolean("novelFallback"),
            )
        }.getOrNull()
    }

    private fun pixivisionToJson(items: List<PixivisionSummary>): JSONArray {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("cat", item.category)
                    put("slug", item.categorySlug)
                    put("cover", item.coverUrl)
                    put("date", item.date)
                },
            )
        }
        return array
    }

    private fun pixivisionFromJson(array: JSONArray?): List<PixivisionSummary> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isEmpty()) continue
                add(
                    PixivisionSummary(
                        id = id,
                        title = o.optString("title"),
                        category = o.optString("cat"),
                        categorySlug = o.optString("slug"),
                        coverUrl = o.optString("cover"),
                        date = o.optString("date"),
                    ),
                )
            }
        }
    }

    private fun itemsToJson(items: List<SearchItem>): JSONArray {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("cat", item.category.name)
                    put("title", item.title)
                    put("thumb", item.thumbUrl)
                    put("full", item.fullUrl)
                    put("uid", item.userId)
                    put("user", item.userName)
                    put("x", item.xRestrict)
                    put("w", item.width)
                    put("h", item.height)
                    put("pages", item.pageCount)
                    put("text", item.textCount)
                    put("tags", JSONArray(item.tags))
                    item.seriesTitle?.let { put("series", it) }
                    if (item.description.isNotEmpty()) put("desc", item.description)
                    if (item.readingTime > 0) put("read", item.readingTime)
                },
            )
        }
        return array
    }

    private fun itemsFromJson(array: JSONArray?): List<SearchItem> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isEmpty()) continue
                add(
                    SearchItem(
                        id = id,
                        category = runCatching {
                            SearchCategory.valueOf(o.optString("cat"))
                        }.getOrDefault(SearchCategory.ILLUST),
                        title = o.optString("title"),
                        thumbUrl = o.optString("thumb"),
                        fullUrl = o.optString("full"),
                        userId = o.optString("uid"),
                        userName = o.optString("user"),
                        xRestrict = o.optInt("x"),
                        width = o.optInt("w"),
                        height = o.optInt("h"),
                        pageCount = o.optInt("pages", 1),
                        textCount = o.optInt("text"),
                        tags = o.optJSONArray("tags")?.let { tags ->
                            buildList {
                                for (t in 0 until tags.length()) {
                                    tags.optString(t).takeIf { it.isNotEmpty() }?.let(::add)
                                }
                            }
                        }.orEmpty(),
                        seriesTitle = o.optString("series").takeIf { it.isNotEmpty() },
                        description = o.optString("desc"),
                        readingTime = o.optInt("read"),
                    ),
                )
            }
        }
    }
}
