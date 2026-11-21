package moe.huiguang.pixiv.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCodecTest {

    @Test
    fun `every route survives a round trip`() {
        val routes = listOf(
            Route.Home, Route.Search, Route.Profile, Route.Login, Route.Settings,
            Route.History, Route.Bookmarks, Route.About, Route.AboutDoc("licenses"),
            Route.AboutDoc("license:coui-notice"),
            Route.Detail("illust", "150930584"),
            Route.Detail("novel", "29404946"),
            Route.Author("3215185"),
            Route.Series("illust", "900", "776"),
            Route.Reader("29404946"),
            Route.Viewer("150930584", 3),
        )
        routes.forEach { route ->
            assertEquals(route, RouteCodec.decode(RouteCodec.encode(route)))
        }
    }

    @Test
    fun `query with separators survives a round trip`() {
        val route = Route.SearchResults("初音 ミク|a&b", "keyword")
        assertEquals(route, RouteCodec.decode(RouteCodec.encode(route)))
    }

    @Test
    fun `garbage decodes to null`() {
        assertNull(RouteCodec.decode("nope"))
        assertNull(RouteCodec.decode(""))
        assertNull(RouteCodec.decode("detail|illust"))
    }
}

class RouteTest {

    @Test
    fun `only the three tabs are tab routes`() {
        assertEquals(listOf(Route.Home, Route.Search, Route.Profile), Route.tabs)
        Route.tabs.forEach { assertTrue(Route.isTab(it)); assertFalse(Route.isPage(it)) }
    }

    @Test
    fun `secondary routes are pages`() {
        val pages = listOf(
            Route.Login, Route.Settings, Route.History, Route.Bookmarks, Route.About, Route.AboutDoc("ack"),
            Route.Detail("illust", "1"), Route.Author("2"), Route.Series("illust", "3", "4"),
            Route.Reader("5"), Route.Viewer("6", 0), Route.SearchResults("q", "tag"),
        )
        pages.forEach { assertTrue(Route.isPage(it)); assertFalse(Route.isTab(it)) }
    }
}
