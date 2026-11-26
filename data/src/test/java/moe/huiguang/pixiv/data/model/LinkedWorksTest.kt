package moe.huiguang.pixiv.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkedWorksTest {

    @Test
    fun parsesPixivAnchorAndKeepsSurroundingLabel() {
        val html = """前回<strong><a href="https://www.pixiv.net/artworks/97653050">illust/97653050</a></strong>の続き"""
        val links = parseLinkedWorks(html, currentKind = "illust", currentId = "97689382")
        assertEquals(1, links.size)
        assertEquals("illust", links[0].kind)
        assertEquals("97653050", links[0].id)
        assertEquals("前回", links[0].label)
    }

    @Test
    fun deDuplicatesAndExcludesTheCurrentWork() {
        val html = """
            <a href="/artworks/123">前回</a>
            <a href="https://www.pixiv.net/novel/show.php?id=456">次回</a>
            /artworks/123 /novel/456
        """.trimIndent()
        val links = parseLinkedWorks(html, currentKind = "illust", currentId = "123")
        assertEquals(1, links.size)
        assertEquals("novel", links[0].kind)
        assertEquals("456", links[0].id)
        assertEquals("次回", links[0].label)
    }

    @Test
    fun acceptsPlainIllustAndNovelLinks() {
        val html = "前回 illust/97689382、続きは https://www.pixiv.net/novel/show.php?id=555"
        val links = parseLinkedWorks(html, currentKind = "illust", currentId = "1")
        assertEquals(2, links.size)
        assertEquals("illust", links[0].kind)
        assertEquals("97689382", links[0].id)
        assertEquals("novel", links[1].kind)
        assertEquals("555", links[1].id)
    }

    @Test
    fun ignoresSeriesLinks() {
        val links = parseLinkedWorks(
            """<a href="https://www.pixiv.net/novel/series/16632482">系列</a>""",
            currentKind = "novel",
            currentId = "1",
        )
        assertEquals(0, links.size)
    }

    @Test
    fun noSeriesKeepsEveryLinkedWork() {
        val links = listOf(
            LinkedWork("illust", "10", "前回"),
            LinkedWork("illust", "11", "次回"),
        )
        assertEquals(
            links,
            visibleLinkedWorks(links, "illust", setOf("11"), hasSeries = false),
        )
    }

    @Test
    fun seriesRemovesItsMembersAndKeepsOutsideLinks() {
        val links = listOf(
            LinkedWork("illust", "10", "前回"),
            LinkedWork("illust", "99", "番外"),
            LinkedWork("novel", "10", "同号小说"),
        )
        val visible = visibleLinkedWorks(
            linked = links,
            currentKind = "illust",
            seriesIds = setOf("10"),
            hasSeries = true,
        )
        assertEquals(listOf("99", "10"), visible.map { it.id })
        assertEquals(listOf("illust", "novel"), visible.map { it.kind })
    }

    @Test
    fun allLinksInsideSeriesLeavesAnEmptyList() {
        val links = listOf(LinkedWork("novel", "10", "前回"))
        assertEquals(
            emptyList<LinkedWork>(),
            visibleLinkedWorks(links, "novel", setOf("10"), hasSeries = true),
        )
    }

    @Test
    fun parsesAuthorLinksAndPreservesAcrossSeriesFilter() {
        val html = """合作：<a href="https://www.pixiv.net/users/92806916">某画师</a>，副号 user/12345，旧链接 member.php?id=556677，外链 https://x.com/user/999"""
        val parsed = parseLinkedWorks(html, "illust", "4034019")
        assertEquals(
            listOf(
                LinkedWork("author", "92806916", "某画师"),
                LinkedWork("author", "12345", ""),
                LinkedWork("author", "556677", ""),
            ),
            parsed,
        )
        assertEquals(
            parsed,
            visibleLinkedWorks(parsed, "illust", setOf("92806916"), hasSeries = true),
        )
    }

    @Test
    fun parsesCaptionContentInternalAndExternalLinks() {
        val html = """参考 illust/4034019 与 user/92806916 还有 <a href="/novel/29726268">后续小说</a>，外链 <a href="/jump.php?https%3A%2F%2Fx.com%2Fpixiv_artist%3Fs%3D20">X</a> 及 https://afdian.com/a/demo。"""
        val content = parseCaptionContent(html)
        assertEquals(
            listOf(
                CaptionTarget.Illust("4034019"),
                CaptionTarget.User("92806916"),
                CaptionTarget.Novel("29726268"),
                CaptionTarget.External("https://x.com/pixiv_artist?s=20"),
                CaptionTarget.External("https://afdian.com/a/demo"),
            ),
            content.links.map { it.target },
        )
        content.links.forEach { link ->
            assertTrue(link.start in 0 until link.end && link.end <= content.text.length)
        }
        assertEquals("illust/4034019", content.text.substring(content.links[0].start, content.links[0].end))
        assertEquals("user/92806916", content.text.substring(content.links[1].start, content.links[1].end))
        assertEquals("后续小说", content.text.substring(content.links[2].start, content.links[2].end))
        assertEquals("X", content.text.substring(content.links[3].start, content.links[3].end))
        assertEquals("https://afdian.com/a/demo", content.text.substring(content.links[4].start, content.links[4].end))
    }
}
