package moe.huiguang.pixiv.ui.author

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorLinksTest {

    @Test
    fun extractsAndClassifiesCommonArtistLinksWithPunctuationTrimmed() {
        val comment = """
            イラスト描いてます。
            X: https://x.com/example_artist/
            Skeb（https://skeb.jp/@example_artist）
            FANBOX: https://example_artist.fanbox.cc/、
            BOOTH -> https://example.booth.pm/items/123。
        """.trimIndent()

        val links = extractProfileLinks(comment)

        assertEquals(4, links.size)
        assertEquals("https://x.com/example_artist/", links[0].url)
        assertEquals(AuthorLinkPlatform.X, links[0].platform)
        assertEquals("X · @example_artist", links[0].label)

        assertEquals("https://skeb.jp/@example_artist", links[1].url)
        assertEquals(AuthorLinkPlatform.SKEB, links[1].platform)
        assertEquals("Skeb · @example_artist", links[1].label)

        assertEquals("https://example_artist.fanbox.cc/", links[2].url)
        assertEquals(AuthorLinkPlatform.FANBOX, links[2].platform)
        assertEquals("FANBOX · example_artist", links[2].label)

        assertEquals("https://example.booth.pm/items/123", links[3].url)
        assertEquals(AuthorLinkPlatform.BOOTH, links[3].platform)
        assertEquals("BOOTH · example", links[3].label)
    }

    @Test
    fun deduplicatesWebpageAndCommentUrlsAndRejectsUnsafeSchemes() {
        val comment = """
            ignore javascript:alert(1) and file:///etc/passwd
            https://twitter.com/pixiv_user
            wiki: https://en.wikipedia.org/wiki/Foo_(bar))
        """.trimIndent()

        val links = extractProfileLinks(
            comment = comment,
            webpage = "https://www.twitter.com/pixiv_user/",
        )

        assertEquals(2, links.size)
        assertEquals(AuthorLinkPlatform.X, links[0].platform)
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", links[1].url)
        assertTrue(links.none { it.url.startsWith("javascript:") || it.url.startsWith("file:") })
    }

    @Test
    fun extractsWebpageEvenWhenCommentIsBlank() {
        val links = extractProfileLinks(
            comment = "",
            webpage = "https://lit.link/sampleartist",
        )

        assertEquals(1, links.size)
        assertEquals(AuthorLinkPlatform.LINKHUB, links[0].platform)
        assertEquals("lit.link", links[0].label)
    }

    @Test
    fun classifiesAfdianAndEnsuresDarkModeXReadability() {
        val comment = """
            X: https://x.com/example_artist
            爱发电: https://afdian.com/a/example_artist
            爱发电新域名: https://www.ifdian.net/a/gulingyu
            Unifans: https://app.unifans.io/c/abc
        """.trimIndent()
        val links = extractProfileLinks(comment)

        assertEquals(4, links.size)
        assertEquals(AuthorLinkPlatform.X, links[0].platform)
        assertEquals(AuthorLinkPlatform.AFDIAN, links[1].platform)
        assertEquals("爱发电 · example_artist", links[1].label)
        assertEquals(AuthorLinkPlatform.AFDIAN, links[2].platform)
        assertEquals("爱发电 · gulingyu", links[2].label)
        assertEquals(AuthorLinkPlatform.UNIFANS, links[3].platform)
        assertEquals("Unifans", links[3].label)

        val fallback = Color(0xFF0096FA)
        val xLight = platformChipColor(AuthorLinkPlatform.X, dark = false, fallbackAccent = fallback)
        val xDark = platformChipColor(AuthorLinkPlatform.X, dark = true, fallbackAccent = fallback)
        val afdianColor = platformChipColor(AuthorLinkPlatform.AFDIAN, dark = false, fallbackAccent = fallback)

        assertNotEquals(xLight, xDark)
        assertTrue(xLight.red < 0.15f && xLight.green < 0.15f && xLight.blue < 0.15f)
        assertTrue(xDark.red > xLight.red && xDark.blue > xLight.blue)
        assertTrue(afdianColor.blue > afdianColor.green && afdianColor.red > 0.4f)
    }
}
