package moe.huiguang.pixiv.data.model

import org.junit.Assert.assertEquals
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
}
