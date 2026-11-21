package moe.huiguang.pixiv.data

import moe.huiguang.pixiv.data.model.HistoryEntry
import moe.huiguang.pixiv.data.model.ReadingMark
import moe.huiguang.pixiv.data.model.uncropThumb
import moe.huiguang.pixiv.data.media.ImageDownloader
import moe.huiguang.pixiv.data.store.AppLanguage
import moe.huiguang.pixiv.data.store.InMemoryKeyValueStore
import moe.huiguang.pixiv.data.store.PaperTheme
import moe.huiguang.pixiv.data.store.ReadingStore
import moe.huiguang.pixiv.data.store.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class StoreTest {

    private fun history(pid: String, kind: String = HistoryEntry.KIND_ILLUST, at: Long = 0) =
        HistoryEntry(pid = pid, kind = kind, title = "作品 $pid", thumbUrl = "", at = at)

    private fun mark(id: String, at: Long, block: Int = 3, total: Int = 10) =
        ReadingMark(novelId = id, title = "小説 $id", blockIndex = block, totalBlocks = total, updatedAt = at, bookmarked = false)

    // ------------------------------------------------------------- settings

    @Test
    fun `settings survive a new instance`() {
        val kv = InMemoryKeyValueStore()
        SettingsStore(kv).apply {
            saveSession("PHPSESSID=abc", "12345")
            setRevealR18(false)
            setLanguage(AppLanguage.TRADITIONAL_CHINESE_HK)
        }

        val reloaded = SettingsStore(kv).current
        assertEquals("PHPSESSID=abc", reloaded.session?.cookie)
        assertEquals("12345", reloaded.session?.userId)
        assertFalse(reloaded.revealR18)
        assertEquals(AppLanguage.TRADITIONAL_CHINESE_HK, reloaded.language)
    }

    @Test
    fun `logout clears the stored session`() {
        val kv = InMemoryKeyValueStore()
        val store = SettingsStore(kv)
        store.saveSession("PHPSESSID=abc", "12345")
        store.logout()
        assertNull(store.current.session)
        assertNull(SettingsStore(kv).current.session)
    }

    @Test
    fun `history is newest first and de-duplicated by pid and kind`() {
        val store = SettingsStore(InMemoryKeyValueStore())
        store.addHistory(history("1"))
        store.addHistory(history("2"))
        store.addHistory(history("1"))
        val list = store.current.history
        assertEquals(listOf("1", "2"), list.map { it.pid })
        assertEquals(2, list.size)
    }

    @Test
    fun `same pid as illust and novel are two entries`() {
        val store = SettingsStore(InMemoryKeyValueStore())
        store.addHistory(history("21962805", HistoryEntry.KIND_ILLUST))
        store.addHistory(history("21962805", HistoryEntry.KIND_NOVEL))
        assertEquals(2, store.current.history.size)
    }

    @Test
    fun `history is capped at sixty`() {
        val store = SettingsStore(InMemoryKeyValueStore())
        repeat(70) { store.addHistory(history(it.toString())) }
        assertEquals(60, store.current.history.size)
        assertEquals("69", store.current.history.first().pid)
    }

    @Test
    fun `reader style survives a new instance`() {
        val kv = InMemoryKeyValueStore()
        SettingsStore(kv).apply {
            setReaderFontSize(21)
            setReaderWeight(700)
            setReaderIndent(false)
            setReaderPaperTheme(PaperTheme.SAGE)
            setReaderLineSpacing(2.2f)
        }

        val reader = SettingsStore(kv).current.reader
        assertEquals(21, reader.fontSize)
        assertEquals(700, reader.weight)
        assertFalse(reader.indent)
        assertEquals(PaperTheme.SAGE, reader.paperTheme)
        assertEquals(2.2f, reader.lineSpacing, 0.001f)
    }

    @Test
    fun `reader font size is clamped to the supported range`() {
        val store = SettingsStore(InMemoryKeyValueStore())
        store.setReaderFontSize(99)
        assertEquals(23, store.current.reader.fontSize)
        store.setReaderFontSize(1)
        assertEquals(13, store.current.reader.fontSize)
    }

    @Test
    fun `left handed preference persists across store reloads`() {
        val kv = InMemoryKeyValueStore()
        val store = SettingsStore(kv)
        assertFalse(store.current.leftHanded)
        store.setLeftHanded(true)
        assertTrue(store.current.leftHanded)
        assertTrue(SettingsStore(kv).current.leftHanded)
    }

    @Test
    fun `welcome consent persists across store reloads`() {
        val kv = InMemoryKeyValueStore()
        val store = SettingsStore(kv)
        assertFalse(store.current.welcomeAccepted)
        store.setWelcomeAccepted(true)
        assertTrue(store.current.welcomeAccepted)
        assertTrue(SettingsStore(kv).current.welcomeAccepted)
    }

    @Test
    fun `reader paged mode persists across store reloads`() {
        val kv = InMemoryKeyValueStore()
        val store = SettingsStore(kv)
        assertFalse(store.current.reader.paged)
        store.setReaderPaged(true)
        assertTrue(store.current.reader.paged)
        assertTrue(SettingsStore(kv).current.reader.paged)
    }

    @Test
    fun `system language falls back to traditional for hong kong`() {        assertEquals(
            AppLanguage.TRADITIONAL_CHINESE_HK,
            SettingsStore.systemDefaultLanguage(Locale.forLanguageTag("zh-HK")),
        )
        assertEquals(
            AppLanguage.SIMPLIFIED_CHINESE,
            SettingsStore.systemDefaultLanguage(Locale.forLanguageTag("zh-CN")),
        )
        assertEquals(
            AppLanguage.SIMPLIFIED_CHINESE,
            SettingsStore.systemDefaultLanguage(Locale.forLanguageTag("ja-JP")),
        )
    }

    // -------------------------------------------------------------- reading

    @Test
    fun `auto save keeps an existing bookmark flag`() {
        val store = ReadingStore(InMemoryKeyValueStore())
        val first = mark("1", at = 100)
        assertTrue(store.toggleBookmark(first))

        store.saveAuto(first.copy(blockIndex = 7, updatedAt = 200))
        val saved = store.mark("1")
        assertNotNull(saved)
        assertEquals(7, saved!!.blockIndex)
        assertTrue(saved.bookmarked)
    }

    @Test
    fun `bookmark toggles on and off`() {
        val store = ReadingStore(InMemoryKeyValueStore())
        val entry = mark("1", at = 100)
        assertTrue(store.toggleBookmark(entry))
        assertFalse(store.toggleBookmark(entry))
    }

    @Test
    fun `reading marks survive a new instance and can be removed`() {
        val kv = InMemoryKeyValueStore()
        ReadingStore(kv).saveAuto(mark("1", at = 100))

        val reloaded = ReadingStore(kv)
        assertEquals(100L, reloaded.mark("1")?.updatedAt)
        reloaded.remove("1")
        assertNull(reloaded.mark("1"))
        assertNull(ReadingStore(kv).mark("1"))
    }

    @Test
    fun `reading marks keep only the newest sixty`() {
        val store = ReadingStore(InMemoryKeyValueStore())
        repeat(65) { store.saveAuto(mark(it.toString(), at = it.toLong())) }
        val marks = store.marks.value
        assertEquals(60, marks.size)
        assertNull(marks["0"])
        assertNotNull(marks["64"])
    }
}

class UrlTest {

    @Test
    fun `crop segment and square suffix are removed`() {
        val cropped = "https://i.pximg.net/c/250x250_80_a2/img-master/img/2026/01/01/00/00/00/100_p0_square1200.jpg"
        assertEquals(
            "https://i.pximg.net/img-master/img/2026/01/01/00/00/00/100_p0_master1200.jpg",
            uncropThumb(cropped),
        )
    }

    @Test
    fun `already uncropped url is untouched`() {
        val clean = "https://i.pximg.net/img-master/img/2026/01/01/00/00/00/100_p0_master1200.jpg"
        assertEquals(clean, uncropThumb(clean))
    }

    @Test
    fun `discovery custom thumb crop segment is removed`() {
        val cropped = "https://i.pximg.net/c/360x360_70/custom-thumb/img/401_p0_custom1200.jpg"
        assertEquals(
            "https://i.pximg.net/custom-thumb/img/401_p0_custom1200.jpg",
            uncropThumb(cropped),
        )
    }

    @Test
    fun `file names are sanitised`() {
        assertEquals("a_b", ImageDownloader.safeName("a/b", "fallback"))
        assertEquals("fallback", ImageDownloader.safeName("   ", "fallback"))
        assertEquals(
            "pixiv_100_p0.jpg",
            ImageDownloader.fileNameFor("https://i.pximg.net/img/100_p0.jpg?x=1", "100", 0),
        )
    }
}
