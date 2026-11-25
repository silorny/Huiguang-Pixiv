package moe.huiguang.pixiv.data

import moe.huiguang.pixiv.data.media.ImageDownloader
import moe.huiguang.pixiv.data.model.ShareFormatter
import moe.huiguang.pixiv.data.store.InMemoryKeyValueStore
import moe.huiguang.pixiv.data.store.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareFormatAndStorageTest {

    @Test
    fun `formatWork substitutes all placeholders including tags`() {
        val result = ShareFormatter.formatWork(
            template = "标题:{title}\n画师:{user_name}({user_id})\nPID:{illust_id}\n{tags}\n{url}",
            title = ShareFormatter.SAMPLE_TITLE,
            userName = ShareFormatter.SAMPLE_USER_NAME,
            userId = ShareFormatter.SAMPLE_USER_ID,
            illustId = ShareFormatter.SAMPLE_ILLUST_ID,
            url = ShareFormatter.SAMPLE_WORK_URL,
            tags = ShareFormatter.SAMPLE_TAGS,
        )
        assertEquals(
            "标题:夕焼けの空\n画师:絵師さん(12345678)\nPID:100000000\n#夕焼け #風景\nhttps://www.pixiv.net/artworks/100000000",
            result,
        )
    }

    @Test
    fun `formatAuthor substitutes author placeholders and insertToken inserts at cursor`() {
        val (inserted, nextCursor) = ShareFormatter.insertToken(
            text = "画师：{user_name}()",
            selectionStart = 15,
            selectionEnd = 15,
            token = "{user_id}",
        )
        assertEquals("画师：{user_name}({user_id})", inserted)
        assertEquals(24, nextCursor)

        val formatted = ShareFormatter.formatAuthor(
            template = inserted + "\n{url}",
            userName = ShareFormatter.SAMPLE_USER_NAME,
            userId = ShareFormatter.SAMPLE_USER_ID,
            url = ShareFormatter.SAMPLE_AUTHOR_URL,
        )
        assertEquals("画师：絵師さん(12345678)\nhttps://www.pixiv.net/users/12345678", formatted)
    }

    @Test
    fun `SettingsStore persists downloadTreeUri and share formats with reset`() {
        val kv = InMemoryKeyValueStore()
        val store = SettingsStore(kv)
        store.setDownloadTreeUri("content://com.android.externalstorage.documents/tree/primary%3APictures%2FPixiv")
        store.setWorkShareFormat("{title} {url}")
        store.setAuthorShareFormat("{user_name} {url}")

        val reloaded = SettingsStore(kv)
        assertEquals("content://com.android.externalstorage.documents/tree/primary%3APictures%2FPixiv", reloaded.current.downloadTreeUri)
        assertEquals("{title} {url}", reloaded.current.workShareFormat)
        assertEquals("{user_name} {url}", reloaded.current.authorShareFormat)

        reloaded.resetWorkShareFormat()
        reloaded.resetAuthorShareFormat()
        assertEquals("", reloaded.current.workShareFormat)
        assertEquals("", reloaded.current.authorShareFormat)
    }

    @Test
    fun `ImageDownloader formats SAF tree URIs into readable paths`() {
        assertEquals("/storage/emulated/0/Download/", ImageDownloader.formatTreeUriPath(""))
        assertEquals(
            "/storage/emulated/0/Download/",
            ImageDownloader.formatTreeUriPath("content://com.android.externalstorage.documents/tree/primary%3ADownload"),
        )
        assertTrue(ImageDownloader.isDefaultDownloadUri("content://com.android.externalstorage.documents/tree/primary%3ADownload"))
        assertFalse(ImageDownloader.isDefaultDownloadUri("content://com.android.externalstorage.documents/tree/primary%3APictures%2FPixiv"))
        assertEquals(
            "/storage/emulated/0/Pictures/Pixiv/",
            ImageDownloader.formatTreeUriPath("content://com.android.externalstorage.documents/tree/primary%3APictures%2FPixiv"),
        )
    }
}
