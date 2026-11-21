package moe.huiguang.pixiv.data

import moe.huiguang.pixiv.data.model.NovelBlock
import moe.huiguang.pixiv.data.model.NovelBlocks
import moe.huiguang.pixiv.data.model.NovelImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelBlocksTest {

    private val images = mapOf(
        "555" to NovelImage("555", "https://i.pximg.net/480.jpg", "https://i.pximg.net/1200.jpg"),
    )

    @Test
    fun `blank lines are dropped and paragraphs kept in order`() {
        val blocks = NovelBlocks.build("一行目\n\n二行目\n   \n三行目", emptyMap())
        assertEquals(3, blocks.size)
        assertEquals("一行目", (blocks[0] as NovelBlock.Paragraph).text)
        assertTrue(blocks.all { it is NovelBlock.Paragraph && it.first })
    }

    @Test
    fun `image marker becomes an image block`() {
        val blocks = NovelBlocks.build("前\n[uploadedimage:555]\n後", images)
        assertEquals(3, blocks.size)
        val image = blocks[1] as NovelBlock.Image
        assertEquals("555", image.imageId)
        assertEquals("https://i.pximg.net/480.jpg", image.image?.displayUrl)
    }

    @Test
    fun `unknown image id keeps an empty block so the reader can report it`() {
        val blocks = NovelBlocks.build("[uploadedimage:999]", images)
        val image = blocks.single() as NovelBlock.Image
        assertEquals("999", image.imageId)
        assertEquals(null, image.image)
    }

    @Test
    fun `long paragraph is chunked and only the first chunk is indented`() {
        val long = "あ".repeat(2_500)
        val blocks = NovelBlocks.build(long, emptyMap(), chunkSize = 1_000)
        assertEquals(3, blocks.size)
        val chunks = blocks.map { it as NovelBlock.Paragraph }
        assertEquals(listOf(true, false, false), chunks.map { it.first })
        assertEquals(2_500, chunks.sumOf { it.text.length })
    }

    @Test
    fun `crlf is tolerated`() {
        val blocks = NovelBlocks.build("一行目\r\n二行目\r\n", emptyMap())
        assertEquals(listOf("一行目", "二行目"), blocks.map { (it as NovelBlock.Paragraph).text })
    }

    @Test
    fun `plain text export drops image markers`() {
        val blocks = NovelBlocks.build("前\n[uploadedimage:555]\n後", images)
        assertEquals("前\n\n後", NovelBlocks.toPlainText(blocks))
    }

    @Test
    fun `empty body yields no blocks`() {
        assertTrue(NovelBlocks.build("", images).isEmpty())
    }

    @Test
    fun `chapter newpage pixivimage and ruby markers are parsed`() {
        val body = "[chapter:第一章 序幕]\n[[rb:彼方 > かなた]]へ[[jumpuri:参考 > https://pixiv.net]]\n[newpage]\n前文[pixivimage:555]后文"
        val blocks = NovelBlocks.build(body, images)
        assertEquals(6, blocks.size)
        assertEquals(NovelBlock.Chapter("第一章 序幕"), blocks[0])
        assertEquals("彼方（かなた）へ参考", (blocks[1] as NovelBlock.Paragraph).text)
        assertEquals(NovelBlock.PageBreak, blocks[2])
        assertEquals("前文", (blocks[3] as NovelBlock.Paragraph).text)
        assertEquals("555", (blocks[4] as NovelBlock.Image).imageId)
        assertEquals("后文", (blocks[5] as NovelBlock.Paragraph).text)
    }
}
