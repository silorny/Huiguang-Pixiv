package moe.huiguang.pixiv.nav

/**
 * Every page the app can show. Kept as a sealed hierarchy so the tab bar and
 * the page host never deal with string keys and `when` blocks stay exhaustive.
 */
sealed interface Route {

    // Root tabs, hosted by MainActivity.
    data object Home : Route
    data object Search : Route
    data object Profile : Route

    // Secondary pages, each hosted by its own PageActivity.
    data object Login : Route
    data object Settings : Route
    data object ShareFormat : Route
    data object History : Route
    data object Bookmarks : Route
    data object About : Route
    data class AboutDoc(val doc: String) : Route

    // Work pages.
    /** Search results for [query]; [mode] is `keyword` or `tag`. */
    data class SearchResults(val query: String, val mode: String) : Route
    /** [kind] is `illust`, `manga` or `novel`. */
    data class Detail(val kind: String, val id: String) : Route
    data class Author(val id: String) : Route
    data class Series(val kind: String, val seriesId: String, val startId: String) : Route
    data class Reader(val novelId: String) : Route
    data class Viewer(val illustId: String, val page: Int) : Route
    data class Pixivision(val id: String) : Route
    data class ProfileList(val uid: String, val kind: String) : Route
    data class UserBookmarks(val uid: String) : Route

    companion object {
        val tabs: List<Route> = listOf(Home, Search, Profile)

        fun isTab(route: Route): Boolean = route in tabs

        /** `true` for routes that belong in a [PageActivity], not in the tab shell. */
        fun isPage(route: Route): Boolean = route !in tabs
    }
}

/**
 * Encodes a [Route] into a single string. This is the page identity carried by
 * `PageActivity`'s Intent extra (the COUI demo passes one page key the same
 * way); the system keeps the Activity stack, so no stack codec is needed.
 */
object RouteCodec {

    fun encode(route: Route): String = when (route) {
        Route.Home -> "home"
        Route.Search -> "search"
        Route.Profile -> "profile"
        Route.Login -> "login"
        Route.Settings -> "settings"
        Route.ShareFormat -> "shareformat"
        Route.History -> "history"
        Route.Bookmarks -> "bookmarks"
        Route.About -> "about"
        is Route.AboutDoc -> "aboutdoc|${encodeComponent(route.doc)}"
        is Route.Detail -> "detail|${encodeComponent(route.kind)}|${encodeComponent(route.id)}"
        is Route.SearchResults -> "results|${encodeComponent(route.query)}|${encodeComponent(route.mode)}"
        is Route.Author -> "author|${encodeComponent(route.id)}"
        is Route.Series ->
            "series|${encodeComponent(route.kind)}|${encodeComponent(route.seriesId)}|${encodeComponent(route.startId)}"
        is Route.Reader -> "reader|${encodeComponent(route.novelId)}"
        is Route.Viewer -> "viewer|${encodeComponent(route.illustId)}|${route.page}"
        is Route.Pixivision -> "pixivision|${encodeComponent(route.id)}"
        is Route.ProfileList -> "profilelist|${encodeComponent(route.uid)}|${encodeComponent(route.kind)}"
        is Route.UserBookmarks -> "userbookmarks|${encodeComponent(route.uid)}"
    }

    fun decode(raw: String): Route? {
        val parts = raw.split('|')
        return when (parts.firstOrNull()) {
            "home" -> Route.Home
            "search" -> Route.Search
            "profile" -> Route.Profile
            "login" -> Route.Login
            "settings" -> Route.Settings
            "shareformat" -> Route.ShareFormat
            "history" -> Route.History
            "bookmarks" -> Route.Bookmarks
            "about" -> Route.About
            "aboutdoc" -> if (parts.size == 2) {
                val doc = decodeComponent(parts[1])
                if (isValidAboutDoc(doc)) Route.AboutDoc(doc) else null
            } else {
                null
            }
            "detail" -> if (parts.size == 3) {
                val kind = decodeComponent(parts[1])
                val id = decodeComponent(parts[2])
                if (isValidWorkKind(kind) && isNumericId(id)) Route.Detail(kind, id) else null
            } else {
                null
            }
            "results" -> if (parts.size == 3) {
                val query = decodeComponent(parts[1])
                val mode = decodeComponent(parts[2])
                if (query.isNotEmpty() && (mode == "keyword" || mode == "tag")) {
                    Route.SearchResults(query, mode)
                } else {
                    null
                }
            } else {
                null
            }
            "author" -> if (parts.size == 2) {
                val id = decodeComponent(parts[1])
                if (isNumericId(id)) Route.Author(id) else null
            } else {
                null
            }
            "series" -> if (parts.size == 4) {
                val kind = decodeComponent(parts[1])
                val seriesId = decodeComponent(parts[2])
                val startId = decodeComponent(parts[3])
                if (isValidWorkKind(kind) && isNumericId(seriesId) && isNumericId(startId)) {
                    Route.Series(kind, seriesId, startId)
                } else {
                    null
                }
            } else {
                null
            }
            "reader" -> if (parts.size == 2) {
                val id = decodeComponent(parts[1])
                if (isNumericId(id)) Route.Reader(id) else null
            } else {
                null
            }
            "viewer" -> if (parts.size == 3) {
                val id = decodeComponent(parts[1])
                val page = parts[2].toIntOrNull()?.takeIf { it >= 0 }
                if (isNumericId(id) && page != null) Route.Viewer(id, page) else null
            } else {
                null
            }
            "pixivision" -> if (parts.size == 2) {
                val id = decodeComponent(parts[1])
                if (isNumericId(id)) Route.Pixivision(id) else null
            } else {
                null
            }
            "profilelist" -> if (parts.size == 3) {
                val uid = decodeComponent(parts[1])
                val kind = decodeComponent(parts[2])
                if (isNumericId(uid) && (kind == "following" || kind == "mypixiv" || kind == "works")) {
                    Route.ProfileList(uid, kind)
                } else {
                    null
                }
            } else {
                null
            }
            "userbookmarks" -> if (parts.size == 2) {
                val uid = decodeComponent(parts[1])
                if (isNumericId(uid)) Route.UserBookmarks(uid) else null
            } else {
                null
            }
            else -> null
        }
    }

    private fun isNumericId(value: String): Boolean =
        value.isNotEmpty() && value.length <= 32 && value.all { it in '0'..'9' }

    private fun isValidWorkKind(value: String): Boolean =
        value == "illust" || value == "manga" || value == "novel"

    private fun isValidAboutDoc(value: String): Boolean =
        value.isNotEmpty() && value.length <= 64 &&
            value.all { it.isLetterOrDigit() || it == ':' || it == '-' || it == '_' || it == '.' }

    private fun encodeComponent(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private fun decodeComponent(value: String): String =
        runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}
