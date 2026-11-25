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
        is Route.AboutDoc -> "aboutdoc|${route.doc}"
        is Route.Detail -> "detail|${route.kind}|${route.id}"
        is Route.SearchResults -> "results|${encodeComponent(route.query)}|${route.mode}"
        is Route.Author -> "author|${route.id}"
        is Route.Series -> "series|${route.kind}|${route.seriesId}|${route.startId}"
        is Route.Reader -> "reader|${route.novelId}"
        is Route.Viewer -> "viewer|${route.illustId}|${route.page}"
        is Route.Pixivision -> "pixivision|${route.id}"
        is Route.ProfileList -> "profilelist|${route.uid}|${route.kind}"
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
            "aboutdoc" -> if (parts.size == 2) Route.AboutDoc(parts[1]) else null
            "detail" -> if (parts.size == 3) Route.Detail(parts[1], parts[2]) else null
            "results" -> if (parts.size == 3) {
                Route.SearchResults(decodeComponent(parts[1]), parts[2])
            } else {
                null
            }
            "author" -> if (parts.size == 2) Route.Author(parts[1]) else null
            "series" -> if (parts.size == 4) Route.Series(parts[1], parts[2], parts[3]) else null
            "reader" -> if (parts.size == 2) Route.Reader(parts[1]) else null
            "viewer" -> if (parts.size == 3) parts[2].toIntOrNull()?.let { Route.Viewer(parts[1], it) } else null
            "pixivision" -> if (parts.size == 2) Route.Pixivision(parts[1]) else null
            "profilelist" -> if (parts.size == 3) Route.ProfileList(parts[1], parts[2]) else null
            else -> null
        }
    }

    private fun encodeComponent(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private fun decodeComponent(value: String): String =
        runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}
