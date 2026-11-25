package moe.huiguang.pixiv.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import moe.huiguang.pixiv.data.net.AppUpdateService
import moe.huiguang.pixiv.data.net.UpdateCheckResult
import moe.huiguang.pixiv.data.store.InMemoryKeyValueStore
import moe.huiguang.pixiv.data.store.PaperTheme
import moe.huiguang.pixiv.data.store.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress

class AppUpdateServiceTest {

    @Test
    fun `compareVersions orders stable and prerelease versions accurately`() {
        assertTrue(AppUpdateService.compareVersions("0.1.3", "0.1.2-beta.10") > 0)
        assertTrue(AppUpdateService.compareVersions("0.1.2", "0.1.2-beta.10") > 0)
        assertTrue(AppUpdateService.compareVersions("0.1.2-beta.11", "0.1.2-beta.10") > 0)
        assertTrue(AppUpdateService.compareVersions("v0.1.2-beta.10", "0.1.2-beta.10") == 0)
        assertTrue(AppUpdateService.compareVersions("0.1.2-beta.9", "0.1.2-beta.10") < 0)
    }

    @Test
    fun `checkForUpdate parses release json and downloads apk over isolated client`() = runBlocking {
        val fakeApkBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3, 4)
        lateinit var serverUrl: String
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/releases") { ex ->
                val json = """
                    [{
                      "tag_name":"v0.1.3",
                      "published_at":"2026-11-25T08:00:00Z",
                      "body":"- 新增自訂分享格式\n- 優化效能",
                      "html_url":"https://github.com/silorny/Huiguang-Pixiv/releases/tag/v0.1.3",
                      "assets":[{"name":"huiguang-v0.1.3.apk","size":8,"browser_download_url":"$serverUrl/download.apk"}]
                    }]
                """.trimIndent().toByteArray(Charsets.UTF_8)
                ex.sendResponseHeaders(200, json.size.toLong())
                ex.responseBody.use { it.write(json) }
            }
            createContext("/download.apk") { ex ->
                ex.sendResponseHeaders(200, fakeApkBytes.size.toLong())
                ex.responseBody.use { it.write(fakeApkBytes) }
            }
            start()
        }
        serverUrl = "http://127.0.0.1:${server.address.port}"
        try {
            val service = AppUpdateService(baseApiUrl = "$serverUrl/releases", allowLoopbackForTest = true)
            val result = service.checkForUpdate("0.1.2-beta.10")
            assertTrue(result is UpdateCheckResult.UpdateAvailable)
            val release = (result as UpdateCheckResult.UpdateAvailable).release
            assertEquals("0.1.3", release.versionName)
            assertEquals("2026.11.25", release.publishedDate)
            assertEquals("• 新增自訂分享格式\n• 優化效能", release.releaseNotes)

            val tempDir = File(System.getProperty("java.io.tmpdir"), "huiguang_update_test_${System.nanoTime()}")
            val target = File(tempDir, "update.apk")
            try {
                service.downloadApk(release.apkDownloadUrl!!, target) { _, _ -> }
                assertTrue(target.isFile)
                assertEquals(8L, target.length())
            } finally {
                tempDir.deleteRecursively()
            }

            val upToDate = service.checkForUpdate("0.1.3")
            assertTrue(upToDate is UpdateCheckResult.UpToDate)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `resetReaderStyle restores typography defaults and skippedUpdateTag persists`() {
        val store = SettingsStore(InMemoryKeyValueStore())
        store.setReaderFontSize(22)
        store.setReaderWeight(700)
        store.setReaderIndent(false)
        store.setReaderPaperTheme(PaperTheme.SAGE)
        store.setReaderLineSpacing(2.2f)
        store.setSkippedUpdateTag("v0.1.3")

        assertEquals("v0.1.3", store.current.skippedUpdateTag)
        store.resetReaderStyle()
        assertEquals(17, store.current.reader.fontSize)
        assertEquals(400, store.current.reader.weight)
        assertEquals(true, store.current.reader.indent)
        assertEquals(PaperTheme.SYSTEM, store.current.reader.paperTheme)
        assertEquals(1.9f, store.current.reader.lineSpacing, 0.001f)
    }

    @Test
    fun `isAllowedReleaseHtmlUrl accepts only repo github prefix`() {
        assertTrue(AppUpdateService.isAllowedReleaseHtmlUrl("https://github.com/silorny/Huiguang-Pixiv/releases/tag/v0.1.3"))
        assertFalse(AppUpdateService.isAllowedReleaseHtmlUrl("https://github.com/evil/repo/releases"))
        assertFalse(AppUpdateService.isAllowedReleaseHtmlUrl("http://github.com/silorny/Huiguang-Pixiv/releases"))
        assertFalse(AppUpdateService.isAllowedReleaseHtmlUrl("javascript:alert(1)"))
    }

    @Test
    fun `checkForUpdate falls back to web atom and expanded assets when API returns 403`() = runBlocking {
        lateinit var serverUrl: String
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/releases") { ex ->
                val msg = """{"message":"API rate limit exceeded"}""".toByteArray(Charsets.UTF_8)
                ex.sendResponseHeaders(403, msg.size.toLong())
                ex.responseBody.use { it.write(msg) }
            }
            createContext("/web/releases.atom") { ex ->
                val atom = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <feed xmlns="http://www.w3.org/2005/Atom">
                      <entry>
                        <id>tag:github.com,2008:Repository/1/v0.1.4</id>
                        <updated>2026-11-25T10:00:00Z</updated>
                        <link rel="alternate" type="text/html" href="https://github.com/silorny/Huiguang-Pixiv/releases/tag/v0.1.4"/>
                        <title>Huiguang v0.1.4</title>
                        <content type="html">&lt;h2&gt;更新说明&lt;/h2&gt;&lt;ul&gt;&lt;li&gt;修复 403 限流&lt;/li&gt;&lt;/ul&gt;</content>
                      </entry>
                    </feed>
                """.trimIndent().toByteArray(Charsets.UTF_8)
                ex.sendResponseHeaders(200, atom.size.toLong())
                ex.responseBody.use { it.write(atom) }
            }
            createContext("/web/releases/expanded_assets/v0.1.4") { ex ->
                val padding = "0".repeat(2000)
                val html = """
                    <ul>
                      <li>
                        <a href="/web/releases/download/v0.1.4/Huiguang-v0.1.4.apk">Huiguang-v0.1.4.apk</a>
                        <span data-sha="$padding">sha256</span>
                        <span class="color-fg-muted">4.20 MB</span>
                      </li>
                    </ul>
                """.trimIndent().toByteArray(Charsets.UTF_8)
                ex.sendResponseHeaders(200, html.size.toLong())
                ex.responseBody.use { it.write(html) }
            }
            start()
        }
        serverUrl = "http://127.0.0.1:${server.address.port}"
        try {
            val service = AppUpdateService(
                baseApiUrl = "$serverUrl/api/releases",
                allowLoopbackForTest = true,
                webBaseUrl = "$serverUrl/web",
            )
            val result = service.checkForUpdate("0.1.3")
            assertTrue(result is UpdateCheckResult.UpdateAvailable)
            val release = (result as UpdateCheckResult.UpdateAvailable).release
            assertEquals("0.1.4", release.versionName)
            assertEquals("2026.11.25", release.publishedDate)
            assertEquals("更新说明\n• 修复 403 限流", release.releaseNotes)
            assertEquals("$serverUrl/web/releases/download/v0.1.4/Huiguang-v0.1.4.apk", release.apkDownloadUrl)
            assertEquals("4.2 MB", release.formattedSize)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `clearDownloadedUpdates deletes updates directory`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "huiguang_clear_updates_${System.nanoTime()}")
        val updatesDir = File(tempDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "huiguang-update.apk").apply { writeText("test") }
        assertTrue(apkFile.isFile)
        AppUpdateService.clearDownloadedUpdates(tempDir)
        assertFalse(updatesDir.exists())
        tempDir.deleteRecursively()
    }
}
