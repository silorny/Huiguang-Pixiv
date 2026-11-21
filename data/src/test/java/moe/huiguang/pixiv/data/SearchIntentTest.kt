package moe.huiguang.pixiv.data

import moe.huiguang.pixiv.data.search.SearchIntent
import moe.huiguang.pixiv.data.search.SearchIntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIntentTest {

    private fun direct(raw: String): SearchIntent.Direct {
        val intent = SearchIntentParser.parse(raw)
        assertTrue("expected Direct for $raw, got $intent", intent is SearchIntent.Direct)
        return intent as SearchIntent.Direct
    }

    @Test
    fun `bare digits probe every kind`() {
        val intent = direct("150930584")
        assertEquals(SearchIntent.Kind.AUTO, intent.kind)
        assertEquals("150930584", intent.id)
    }

    @Test
    fun `artwork link`() {
        assertEquals("150930584", direct("https://www.pixiv.net/artworks/150930584").id)
    }

    @Test
    fun `artwork link with query keeps the id`() {
        assertEquals("150930584", direct("https://www.pixiv.net/artworks/150930584?from=home").id)
    }

    @Test
    fun `legacy member_illust link`() {
        assertEquals(
            "150930584",
            direct("https://www.pixiv.net/member_illust.php?mode=medium&illust_id=150930584").id,
        )
    }

    @Test
    fun `novel link is a novel, not an artwork`() {
        val intent = direct("https://www.pixiv.net/novel/show.php?id=29404946")
        assertEquals(SearchIntent.Kind.NOVEL, intent.kind)
        assertEquals("29404946", intent.id)
    }

    @Test
    fun `short novel link`() {
        assertEquals(SearchIntent.Kind.NOVEL, direct("https://www.pixiv.net/novel/29404946").kind)
    }

    @Test
    fun `user link is an author`() {
        assertEquals(SearchIntent.Kind.AUTHOR, direct("https://www.pixiv.net/users/3215185").kind)
    }

    @Test
    fun `short user link is an author`() {
        assertEquals(SearchIntent.Kind.AUTHOR, direct("https://www.pixiv.net/u/3215185").kind)
    }

    @Test
    fun `member link is an author`() {
        assertEquals(SearchIntent.Kind.AUTHOR, direct("https://www.pixiv.net/member.php?id=3215185").kind)
    }

    @Test
    fun `plain text is a search`() {
        val intent = SearchIntentParser.parse("初音ミク")
        assertTrue(intent is SearchIntent.Text)
        assertEquals("初音ミク", (intent as SearchIntent.Text).query)
    }

    @Test
    fun `blank input is ignored`() {
        assertNull(SearchIntentParser.parse("   "))
        assertNull(SearchIntentParser.parse(null))
    }

    @Test
    fun `unrelated url stays text`() {
        assertTrue(SearchIntentParser.parse("https://example.com/foo") is SearchIntent.Text)
    }
}
