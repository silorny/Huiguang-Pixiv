package moe.huiguang.pixiv.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PixivisionParserTest {

    @Test
    fun `parseList extracts eyecatch and standard cards`() {
        val html = """
            <article class="_article-eyecatch-card spotlight">
              <div class="aec__thumbnail-container">
                <a href="/zh/a/12056"><div class="_thumbnail aec__thumbnail" style="background-image: url(https://i.pximg.net/c/1200x630/146265066_p0.jpg)"></div></a>
                <a href="/zh/c/illustration" data-gtm-action="ClickCategory" data-gtm-label="illustration"><span class="_category-label large spotlight">插画</span></a>
              </div>
              <div class="aec__title-container"><h2 class="aec__title"><a href="/zh/a/12056">迷你裙插画特辑</a></h2></div>
              <div class="aec__footer-container"><time class="_date gray large" datetime="2026-11-19">2026.11.19</time></div>
            </article>
            <article class="_article-card spotlight">
              <div class="arc__thumbnail-container">
                <a href="https://www.pixivision.net/zh/a/12143"><div class="_thumbnail" style="background-image: url(https://i.pximg.net/c/1200x630/151112846_p0.png)"></div></a>
                <a href="https://www.pixivision.net/zh/c/manga" data-gtm-action="ClickCategory" data-gtm-label="manga"><span class="_category-label large spotlight">漫画</span></a>
              </div>
              <div class="arc__title-container"><h2 class="arc__title"><a href="https://www.pixivision.net/zh/a/12143">晚霞特辑 &amp; 故事</a></h2></div>
              <div class="arc__footer-container"><div class="arc__footer-date-pr"><time class="_date small light-gray">2026.11.18</time></div></div>
            </article>
        """.trimIndent()

        val list = PixivisionParser.parseList(html)
        assertEquals(2, list.size)
        assertEquals("12056", list[0].id)
        assertEquals("插画", list[0].category)
        assertEquals("illustration", list[0].categorySlug)
        assertEquals("迷你裙插画特辑", list[0].title)
        assertEquals("https://i.pximg.net/c/1200x630/146265066_p0.jpg", list[0].coverUrl)
        assertEquals("2026.11.19", list[0].date)

        assertEquals("12143", list[1].id)
        assertEquals("漫画", list[1].category)
        assertEquals("晚霞特辑 & 故事", list[1].title)
    }

    @Test
    fun `parseDetail extracts header, intro, tags and works`() {
        val html = """
            <header class="am__header">
              <div class="am__sub-info">
                <div class="am__categoty-pr"><a href="https://www.pixivision.net/zh/c/illustration" data-gtm-action="ClickCategory" data-gtm-label="illustration"><span class="_category-label large spotlight">插画</span></a></div>
                <time class="_date am__sub-info__date large light-gray" datetime="2026-11-19">2026.11.19</time>
              </div>
              <h1 class="am__title">迷你裙插画特辑</h1>
              <ul class="am__header-tags _tag-list">
                <li class="tls__list-item-container"><a href="/zh/t/255" class="tls__list-item">腿部</a></li>
                <li class="tls__list-item-container"><a href="/zh/t/1039" class="tls__list-item">迷你裙</a></li>
              </ul>
            </header>
            <div class="am__body">
              <div class="_feature-article-body">
                <div class="article-item _feature-article-body__article_thumbnail">
                  <div class="am__eyecatch-container"><div class="_article-illust-eyecatch"><img class="aie__image" src="https://embed.pixiv.net/pixivision/zh/a/12056/ogimage.jpg" alt="迷你裙"></div></div>
                </div>
                <div class="article-item _feature-article-body__paragraph">
                  <div class="fab__paragraph _medium-editor-text"><p>第一段简介。</p><p>第二段简介。</p></div>
                </div>
                <div class="article-item _feature-article-body__pixiv_illust">
                  <div class="am__work">
                    <div class="am__work__info">
                      <a href="https://www.pixiv.net/users/93583" class="am__work__user-icon-container"><img src="https://i.pximg.net/user-profile/9416144_170.jpg" class="am__work__uesr-icon"></a>
                      <div class="am__work__title-container">
                        <h3 class="am__work__title"><a href="https://www.pixiv.net/artworks/137701458">サトネちゃん</a></h3>
                        <p class="am__work__user-name">by <a href="https://www.pixiv.net/users/93583">92M</a></p>
                      </div>
                    </div>
                    <div class="am__work__main">
                      <a href="https://www.pixiv.net/artworks/137701458"><img src="https://i.pximg.net/c/768x1200_80/137701458_p0_master1200.jpg" class="am__work__illust"></a>
                    </div>
                  </div>
                </div>
              </div>
            </div>
        """.trimIndent()

        val detail = PixivisionParser.parseDetail("12056", html)
        assertNotNull(detail)
        detail!!
        assertEquals("迷你裙插画特辑", detail.title)
        assertEquals("插画", detail.category)
        assertEquals("2026.11.19", detail.date)
        assertEquals("https://embed.pixiv.net/pixivision/zh/a/12056/ogimage.jpg", detail.coverUrl)
        assertEquals(listOf("腿部", "迷你裙"), detail.tags)
        assertEquals("第一段简介。\n\n第二段简介。", detail.intro)
        assertEquals(1, detail.works.size)
        assertEquals("137701458", detail.works[0].workId)
        assertEquals("illust", detail.works[0].kind)
        assertEquals("サトネちゃん", detail.works[0].title)
        assertEquals("93583", detail.works[0].authorId)
        assertEquals("92M", detail.works[0].authorName)
        assertEquals("https://i.pximg.net/user-profile/9416144_170.jpg", detail.works[0].authorAvatarUrl)
        assertEquals("https://i.pximg.net/c/768x1200_80/137701458_p0_master1200.jpg", detail.works[0].imageUrl)
    }
}
