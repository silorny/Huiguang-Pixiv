package moe.huiguang.pixiv.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Parsed release metadata for an app update check. */
data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val publishedDate: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?,
    val apkSizeBytes: Long,
    val htmlUrl: String,
    val isForceUpdate: Boolean = false,
    val localizedReleaseNotes: Map<String, String> = emptyMap(),
) {
    val formattedSize: String
        get() = if (apkSizeBytes > 0L) {
            String.format(Locale.US, "%.1f MB", apkSizeBytes / (1024.0 * 1024.0))
        } else {
            ""
        }

    /**
     * Picks the changelog matching [languageTag] (`zh-CN`, `zh-HK`, `en`, etc.),
     * falling back cleanly across Chinese variants and then [releaseNotes].
     */
    fun releaseNotesFor(languageTag: String): String {
        if (localizedReleaseNotes.isEmpty()) return releaseNotes
        val normalized = AppUpdateService.canonicalLanguageKey(languageTag)
        localizedReleaseNotes[normalized]?.takeIf { it.isNotBlank() }?.let { return it }
        if (normalized == "zh-HK") {
            localizedReleaseNotes["zh-CN"]?.takeIf { it.isNotBlank() }?.let { return it }
        } else if (normalized == "zh-CN") {
            localizedReleaseNotes["zh-HK"]?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return localizedReleaseNotes["en"]?.takeIf { it.isNotBlank() }
            ?: localizedReleaseNotes.values.firstOrNull { it.isNotBlank() }
            ?: releaseNotes
    }
}

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: AppReleaseInfo) : UpdateCheckResult
    data class UpToDate(val release: AppReleaseInfo?) : UpdateCheckResult
}

/**
 * Isolated GitHub Releases client for explicit user-triggered update checks
 * and APK downloads. Strips all Pixiv cookies/Referer and restricts connections
 * strictly to GitHub hosts (plus loopback in HTTP unit tests).
 */
class AppUpdateService private constructor(
    private val baseApiUrl: String,
    private val webBaseUrl: String,
    private val allowLoopbackForTest: Boolean,
    private val customClient: OkHttpClient?,
) {
    constructor() : this(GITHUB_RELEASES_API, GITHUB_WEB_BASE, false, publicHttp)

    internal constructor(
        baseApiUrl: String,
        allowLoopbackForTest: Boolean = false,
        webBaseUrl: String = GITHUB_WEB_BASE,
    ) : this(baseApiUrl, webBaseUrl, allowLoopbackForTest, null)

    val http: OkHttpClient by lazy {
        customClient ?: buildGitHubClient(allowLoopbackForTest)
    }

    suspend fun checkForUpdate(currentVersionName: String): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(baseApiUrl)
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()
            val (code, body) = http.newCall(req).execute().use { resp ->
                resp.code to resp.body.string()
            }
            if (code == 404) {
                return@withContext UpdateCheckResult.UpToDate(null)
            }
            val releases = when (code) {
                200 -> parseReleases(body)
                403, 429 -> fetchReleasesFromWeb()
                    ?: throw PixivException("HTTP $code", code)
                else -> throw PixivException("HTTP $code", code)
            }
            if (releases.isEmpty()) {
                return@withContext UpdateCheckResult.UpToDate(null)
            }
            val newest = releases.maxWithOrNull { a, b ->
                compareVersions(a.versionName, b.versionName)
            } ?: return@withContext UpdateCheckResult.UpToDate(null)

            if (compareVersions(newest.versionName, currentVersionName) > 0) {
                UpdateCheckResult.UpdateAvailable(enrichAssetFromWebIfNeeded(newest))
            } else {
                val matchingCurrent = releases.firstOrNull {
                    compareVersions(it.versionName, currentVersionName) == 0
                } ?: newest
                UpdateCheckResult.UpToDate(enrichAssetFromWebIfNeeded(matchingCurrent))
            }
        }

    private fun fetchReleasesFromWeb(): List<AppReleaseInfo>? {
        val base = webBaseUrl.trimEnd('/')
        if (base.isEmpty()) return null

        val atomReq = Request.Builder()
            .url("$base/releases.atom")
            .header("Accept", "application/atom+xml, application/xml, text/xml")
            .get()
            .build()
        val atomReleases = runCatching {
            http.newCall(atomReq).execute().use { resp ->
                if (!resp.isSuccessful) emptyList()
                else parseAtomReleases(resp.body.string(), base)
            }
        }.getOrDefault(emptyList())
        if (atomReleases.isNotEmpty()) return atomReleases

        // Fallback to /releases/latest redirect if Atom feed is unavailable.
        val latestReq = Request.Builder()
            .url("$base/releases/latest")
            .get()
            .build()
        return runCatching {
            http.newCall(latestReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val finalUrl = resp.request.url.toString()
                val tag = finalUrl.substringAfterLast("/releases/tag/", "")
                    .substringBefore('?')
                    .substringBefore('#')
                    .trim()
                if (tag.isEmpty()) return@use null
                listOf(
                    AppReleaseInfo(
                        tagName = tag,
                        versionName = tag.removePrefix("v").removePrefix("V"),
                        publishedDate = "",
                        releaseNotes = "",
                        apkDownloadUrl = null,
                        apkSizeBytes = 0L,
                        htmlUrl = "$base/releases/tag/$tag",
                    ),
                )
            }
        }.getOrNull()
    }

    private fun enrichAssetFromWebIfNeeded(release: AppReleaseInfo): AppReleaseInfo {
        if (!release.apkDownloadUrl.isNullOrBlank() && release.apkSizeBytes > 0L) {
            return release
        }
        val base = webBaseUrl.trimEnd('/')
        if (base.isEmpty() || release.tagName.isBlank()) return release
        val encodedTag = URLEncoder.encode(release.tagName, Charsets.UTF_8.name())
        val req = Request.Builder()
            .url("$base/releases/expanded_assets/$encodedTag")
            .header("Accept", "text/html")
            .get()
            .build()
        val parsed = runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null
                else parseExpandedAssets(resp.body.string(), base)
            }
        }.getOrNull() ?: return release
        return release.copy(
            apkDownloadUrl = release.apkDownloadUrl ?: parsed.first,
            apkSizeBytes = if (release.apkSizeBytes > 0L) release.apkSizeBytes else parsed.second,
        )
    }

    suspend fun downloadApk(
        url: String,
        destFile: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        if (!allowLoopbackForTest && !isAllowedApkDownloadUrl(url)) {
            throw IOException("Blocked untrusted APK download URL")
        }
        destFile.parentFile?.mkdirs()
        val tempFile = File(destFile.parentFile, "${destFile.name}.part")
        if (tempFile.exists()) tempFile.delete()
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw PixivException("HTTP ${resp.code}", resp.code)
            val total = resp.body.contentLength()
            var downloaded = 0L
            val buffer = ByteArray(16 * 1024)
            resp.body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
            if (downloaded == 0L || (total > 0L && downloaded != total)) {
                tempFile.delete()
                throw IOException("Incomplete download ($downloaded/$total)")
            }
        }
        // Validate APK (ZIP container) header magic before exposing to PackageInstaller.
        val validApk = tempFile.inputStream().use { stream ->
            val header = ByteArray(2)
            stream.read(header) == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
        }
        if (!validApk) {
            tempFile.delete()
            throw IOException("Invalid APK package")
        }
        if (destFile.exists()) destFile.delete()
        if (!tempFile.renameTo(destFile)) {
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()
        }
        destFile
    }

    companion object {
        const val GITHUB_RELEASES_API =
            "https://api.github.com/repos/silorny/Huiguang-Pixiv/releases?per_page=10"
        const val GITHUB_WEB_BASE =
            "https://github.com/silorny/Huiguang-Pixiv"
        const val GITHUB_RELEASES_HTML =
            "https://github.com/silorny/Huiguang-Pixiv/releases"

        private val APK_HREF_RE = Regex(
            """href=["']([^"']+/releases/download/[^"']+\.apk)["']""",
            RegexOption.IGNORE_CASE,
        )
        private val ASSET_SIZE_RE = Regex(
            """>(\d+(?:\.\d+)?)\s*(Bytes|KB|MB|GB)\s*<""",
            RegexOption.IGNORE_CASE,
        )
        private val HTML_TAG_RE = Regex("""<[^>]+>""")
        private val HTML_BLOCK_BREAK_RE = Regex(
            """</(?:h[1-6]|p|div|li|tr)>|<br\s*/?>""",
            RegexOption.IGNORE_CASE,
        )
        private val HTML_LI_OPEN_RE = Regex("""<li(?:\s[^>]*)?>""", RegexOption.IGNORE_CASE)
        private val HTML_LANG_COMMENT_RE = Regex(
            """<!--\s*(?:lang\s*[:=]\s*)?([a-zA-Z_-]+|/lang)\s*-->""",
            RegexOption.IGNORE_CASE,
        )
        private val MARKDOWN_RULE_RE = Regex("""^(?:-{3,}|\*{3,}|_{3,})$""")

        val publicHttp: OkHttpClient by lazy { buildGitHubClient(allowLoopbackForTest = false) }

        fun isAllowedReleaseHtmlUrl(url: String): Boolean =
            url.startsWith("https://github.com/silorny/Huiguang-Pixiv/")

        fun isAllowedApkDownloadUrl(url: String): Boolean =
            url.startsWith("https://github.com/silorny/Huiguang-Pixiv/releases/download/")

        fun clearDownloadedUpdates(cacheDir: File) {
            runCatching { File(cacheDir, "updates").deleteRecursively() }
        }

        private fun buildGitHubClient(allowLoopbackForTest: Boolean): OkHttpClient =
            OkHttpClient.Builder()
                .cookieJar(CookieJar.NO_COOKIES)
                .addNetworkInterceptor { chain ->
                    val req = chain.request()
                    val host = req.url.host.lowercase()
                    val isAllowedGitHub = req.url.scheme == "https" && (
                        host == "api.github.com" ||
                            host == "github.com" ||
                            host.endsWith(".githubusercontent.com")
                        )
                    val isAllowedLoopback = allowLoopbackForTest &&
                        (host == "127.0.0.1" || host == "localhost")
                    if (!isAllowedGitHub && !isAllowedLoopback) {
                        throw IOException("Blocked non-GitHub host: $host")
                    }
                    val cleaned = req.newBuilder()
                        .removeHeader("Referer")
                        .removeHeader("Cookie")
                        .removeHeader("Accept-Language")
                        .header("User-Agent", "Huiguang")
                        .build()
                    chain.proceed(cleaned)
                }
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(180, TimeUnit.SECONDS)
                .build()

        internal fun parseReleases(rawJson: String): List<AppReleaseInfo> {
            val trimmed = rawJson.trim()
            val array = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> JSONArray().put(JSONObject(trimmed))
                else -> return emptyList()
            }
            return buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    if (obj.optBoolean("draft", false)) continue
                    val tag = obj.optString("tag_name").trim()
                    if (tag.isEmpty()) continue
                    val rawBody = obj.optString("body")
                    val isForce = rawBody.contains("[force-update]", ignoreCase = true)
                    val localized = parseLocalizedChangelogs(rawBody)
                    val notes = formatChangelog(rawBody)
                    val assets = obj.optJSONArray("assets")
                    var apkUrl: String? = null
                    var apkSize = 0L
                    if (assets != null) {
                        for (j in 0 until assets.length()) {
                            val asset = assets.optJSONObject(j) ?: continue
                            val assetName = asset.optString("name")
                            if (assetName.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url").takeIf { it.isNotEmpty() }
                                apkSize = asset.optLong("size", 0L)
                                break
                            }
                        }
                    }
                    add(
                        AppReleaseInfo(
                            tagName = tag,
                            versionName = tag.removePrefix("v").removePrefix("V"),
                            publishedDate = formatReleaseDate(obj.optString("published_at")),
                            releaseNotes = notes,
                            apkDownloadUrl = apkUrl,
                            apkSizeBytes = apkSize,
                            htmlUrl = obj.optString("html_url"),
                            isForceUpdate = isForce,
                            localizedReleaseNotes = localized,
                        ),
                    )
                }
            }
        }

        internal fun formatReleaseDate(raw: String): String {
            val datePart = raw.substringBefore('T').trim()
            val parts = datePart.split('-')
            if (parts.size != 3) return ""
            val y = parts[0].toIntOrNull() ?: return ""
            val m = parts[1].toIntOrNull() ?: return ""
            val d = parts[2].toIntOrNull() ?: return ""
            return String.format(Locale.US, "%04d.%02d.%02d", y, m, d)
        }

        internal fun formatChangelog(rawBody: String): String =
            HTML_LANG_COMMENT_RE.replace(rawBody, "")
                .replace("[force-update]", "", ignoreCase = true)
                .lines()
                .map { it.trim() }
                .filter {
                    it.isNotEmpty() &&
                        !(it.startsWith("[__LANG_MARKER__:") && it.endsWith("]")) &&
                        !MARKDOWN_RULE_RE.matches(it)
                }
                .joinToString("\n") { line ->
                    val stripped = line.removePrefix("#").trimStart('#', ' ')
                    when {
                        stripped.startsWith("- ") -> "• ${stripped.removePrefix("- ").trim()}"
                        stripped.startsWith("* ") -> "• ${stripped.removePrefix("* ").trim()}"
                        else -> stripped
                    }
                }

        internal fun canonicalLanguageKey(languageTag: String): String {
            val normalized = languageTag.trim().replace('_', '-').lowercase(Locale.US)
            return when {
                normalized.startsWith("en") -> "en"
                normalized == "zh-hant" ||
                    normalized.startsWith("zh-hant-") ||
                    normalized.startsWith("zh-hk") ||
                    normalized.startsWith("zh-tw") ||
                    normalized.startsWith("zh-mo") -> "zh-HK"
                normalized.startsWith("zh") -> "zh-CN"
                else -> normalized.ifEmpty { "zh-CN" }
            }
        }

        internal fun parseLocalizedChangelogs(rawBody: String): Map<String, String> {
            val prepared = HTML_LANG_COMMENT_RE.replace(rawBody) { match ->
                "\n[__LANG_MARKER__:${match.groupValues[1]}]\n"
            }
            val buckets = linkedMapOf<String, MutableList<String>>()
            var currentLang: String? = null
            for (rawLine in prepared.lines()) {
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty()) {
                    currentLang?.let { buckets.getOrPut(it) { mutableListOf() }.add("") }
                    continue
                }
                if (trimmed.startsWith("[__LANG_MARKER__:") && trimmed.endsWith("]")) {
                    val token = trimmed.removePrefix("[__LANG_MARKER__:").removeSuffix("]")
                    currentLang = if (token.equals("/lang", ignoreCase = true)) {
                        null
                    } else {
                        matchLanguageMarker(token)?.also {
                            buckets.getOrPut(it) { mutableListOf() }
                        }
                    }
                    continue
                }
                val detectedHeaderLang = detectLanguageHeading(trimmed)
                if (detectedHeaderLang != null) {
                    currentLang = detectedHeaderLang
                    buckets.getOrPut(detectedHeaderLang) { mutableListOf() }
                    continue
                }
                if (MARKDOWN_RULE_RE.matches(trimmed)) continue
                currentLang?.let { lang ->
                    buckets.getOrPut(lang) { mutableListOf() }.add(rawLine)
                }
            }
            if (buckets.isEmpty()) return emptyMap()
            return buildMap {
                for ((lang, lines) in buckets) {
                    val formatted = formatChangelog(lines.joinToString("\n"))
                    if (formatted.isNotBlank()) put(lang, formatted)
                }
            }
        }

        private fun detectLanguageHeading(trimmedLine: String): String? {
            if (trimmedLine.length > 64) return null
            var candidate = trimmedLine.trimStart('#', ' ')
            if (candidate.startsWith("**") && candidate.endsWith("**") && candidate.length > 4) {
                candidate = candidate.removeSurrounding("**").trim()
            } else if (candidate.startsWith("__") && candidate.endsWith("__") && candidate.length > 4) {
                candidate = candidate.removeSurrounding("__").trim()
            }
            if (candidate.startsWith("[") && candidate.endsWith("]") && candidate.length > 2) {
                candidate = candidate.removeSurrounding("[", "]").trim()
            }
            candidate = candidate.trimEnd(':', '：').trim()
            return matchLanguageMarker(candidate)
        }

        private fun matchLanguageMarker(rawToken: String): String? {
            val token = rawToken
                .removePrefix("lang:")
                .removePrefix("LANG:")
                .trim()
                .lowercase(Locale.US)
                .replace('_', '-')
            return when (token) {
                "zh-cn", "zh-hans", "zh-sg",
                "简体中文", "中文（简体）", "中文 (简体)",
                "更新说明（简体中文）", "更新说明 (简体中文)",
                "更新日志（简体中文）", "更新日志 (简体中文)",
                "simplified chinese",
                -> "zh-CN"

                "zh-hk", "zh-tw", "zh-mo", "zh-hant",
                "繁體中文", "繁体中文",
                "繁體中文（香港）", "繁體中文 (香港)",
                "繁體中文（台灣）", "繁體中文 (台灣)",
                "中文（繁體）", "中文 (繁體)",
                "更新說明（繁體中文）", "更新說明 (繁體中文)",
                "更新日誌（繁體中文）", "更新日誌 (繁體中文)",
                "traditional chinese",
                -> "zh-HK"

                "en", "en-us", "en-gb",
                "english",
                "changelog (english)",
                "release notes (english)",
                "what's new (english)",
                -> "en"

                else -> null
            }
        }

        internal fun parseAtomReleases(atomXml: String, webBaseUrl: String): List<AppReleaseInfo> {
            val factory = DocumentBuilderFactory.newInstance().apply {
                runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                isExpandEntityReferences = false
            }
            val doc = factory.newDocumentBuilder()
                .parse(ByteArrayInputStream(atomXml.toByteArray(Charsets.UTF_8)))
            val entries = doc.getElementsByTagName("entry")
            val base = webBaseUrl.trimEnd('/')
            return buildList {
                for (i in 0 until entries.length) {
                    val entry = entries.item(i) as? Element ?: continue
                    val linkNodes = entry.getElementsByTagName("link")
                    var htmlUrl = ""
                    for (j in 0 until linkNodes.length) {
                        val el = linkNodes.item(j) as? Element ?: continue
                        val href = el.getAttribute("href").trim()
                        if (href.contains("/releases/tag/")) {
                            htmlUrl = href
                            break
                        }
                    }
                    val idText = entry.getElementsByTagName("id").item(0)?.textContent?.trim().orEmpty()
                    val tag = htmlUrl.substringAfterLast("/releases/tag/", "")
                        .substringBefore('?')
                        .substringBefore('#')
                        .trim()
                        .ifEmpty { idText.substringAfterLast('/').trim() }
                    if (tag.isEmpty()) continue
                    if (htmlUrl.isEmpty()) htmlUrl = "$base/releases/tag/$tag"
                    val updated = entry.getElementsByTagName("updated").item(0)?.textContent?.trim().orEmpty()
                    val rawHtml = entry.getElementsByTagName("content").item(0)?.textContent.orEmpty()
                    val isForce = rawHtml.contains("[force-update]", ignoreCase = true)
                    val normalizedAtom = normalizeAtomHtml(rawHtml)
                    val localized = parseLocalizedChangelogs(normalizedAtom)
                    val notes = formatChangelog(normalizedAtom)
                    add(
                        AppReleaseInfo(
                            tagName = tag,
                            versionName = tag.removePrefix("v").removePrefix("V"),
                            publishedDate = formatReleaseDate(updated),
                            releaseNotes = notes,
                            apkDownloadUrl = null,
                            apkSizeBytes = 0L,
                            htmlUrl = htmlUrl,
                            isForceUpdate = isForce,
                            localizedReleaseNotes = localized,
                        ),
                    )
                }
            }
        }

        internal fun normalizeAtomHtml(rawHtml: String): String {
            if (rawHtml.isBlank()) return ""
            val withCommentsPreserved = HTML_LANG_COMMENT_RE.replace(rawHtml) { match ->
                "\n[__LANG_MARKER__:${match.groupValues[1]}]\n"
            }
            return withCommentsPreserved
                .replace(HTML_LI_OPEN_RE, "- ")
                .replace(HTML_BLOCK_BREAK_RE, "\n")
                .replace(HTML_TAG_RE, "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")
        }

        internal fun formatAtomHtmlChangelog(rawHtml: String): String =
            formatChangelog(normalizeAtomHtml(rawHtml))

        internal fun parseExpandedAssets(html: String, webBaseUrl: String): Pair<String?, Long> {
            val match = APK_HREF_RE.find(html) ?: return null to 0L
            val href = match.groupValues[1].replace("&amp;", "&").trim()
            val resolvedUrl = when {
                href.startsWith("https://") -> href
                href.startsWith("http://") -> if (webBaseUrl.startsWith("http://")) href else null
                else -> {
                    val baseHttpUrl = webBaseUrl.toHttpUrlOrNull()
                    if (baseHttpUrl != null) {
                        val portSuffix = if ((baseHttpUrl.scheme == "https" && baseHttpUrl.port == 443) ||
                            (baseHttpUrl.scheme == "http" && baseHttpUrl.port == 80)
                        ) {
                            ""
                        } else {
                            ":${baseHttpUrl.port}"
                        }
                        val path = if (href.startsWith("/")) href else "/$href"
                        "${baseHttpUrl.scheme}://${baseHttpUrl.host}$portSuffix$path"
                    } else {
                        null
                    }
                }
            }
            val rowEnd = html.indexOf("</li>", match.range.last + 1)
                .takeIf { it > match.range.last }
                ?: html.length
            val tailWindow = html.substring(
                match.range.last + 1,
                minOf(rowEnd, match.range.last + 1 + 4096),
            )
            val sizeMatch = ASSET_SIZE_RE.find(tailWindow)
            val sizeBytes = if (sizeMatch != null) {
                parseHumanSizeBytes(sizeMatch.groupValues[1], sizeMatch.groupValues[2])
            } else {
                0L
            }
            return resolvedUrl to sizeBytes
        }

        private fun parseHumanSizeBytes(valueStr: String, unit: String): Long {
            val value = valueStr.toDoubleOrNull() ?: return 0L
            return when (unit.uppercase(Locale.US)) {
                "BYTES" -> value.toLong()
                "KB" -> (value * 1024.0).toLong()
                "MB" -> (value * 1024.0 * 1024.0).toLong()
                "GB" -> (value * 1024.0 * 1024.0 * 1024.0).toLong()
                else -> 0L
            }
        }

        /**
         * SemVer comparison supporting `v0.1.2-beta.10` vs `0.1.2` vs `0.1.3`.
         * Returns `< 0` if [a] < [b], `0` if equal, `> 0` if [a] > [b].
         */
        fun compareVersions(a: String, b: String): Int {
            val pa = parseVersion(a)
            val pb = parseVersion(b)
            val maxCore = maxOf(pa.core.size, pb.core.size)
            for (i in 0 until maxCore) {
                val va = pa.core.getOrElse(i) { 0 }
                val vb = pb.core.getOrElse(i) { 0 }
                if (va != vb) return va.compareTo(vb)
            }
            if (pa.pre.isEmpty() && pb.pre.isEmpty()) return 0
            if (pa.pre.isEmpty()) return 1
            if (pb.pre.isEmpty()) return -1
            val maxPre = maxOf(pa.pre.size, pb.pre.size)
            for (i in 0 until maxPre) {
                val sa = pa.pre.getOrNull(i) ?: return -1
                val sb = pb.pre.getOrNull(i) ?: return 1
                val na = sa.toIntOrNull()
                val nb = sb.toIntOrNull()
                val cmp = when {
                    na != null && nb != null -> na.compareTo(nb)
                    na != null -> -1
                    nb != null -> 1
                    else -> sa.compareTo(sb, ignoreCase = true)
                }
                if (cmp != 0) return cmp
            }
            return 0
        }

        private data class ParsedVersion(val core: List<Int>, val pre: List<String>)

        private fun parseVersion(raw: String): ParsedVersion {
            val clean = raw.trim().removePrefix("v").removePrefix("V")
            val plusIdx = clean.indexOf('+')
            val withoutBuild = if (plusIdx >= 0) clean.substring(0, plusIdx) else clean
            val dashIdx = withoutBuild.indexOf('-')
            val coreStr = if (dashIdx >= 0) withoutBuild.substring(0, dashIdx) else withoutBuild
            val preStr = if (dashIdx >= 0) withoutBuild.substring(dashIdx + 1) else ""
            val core = coreStr.split('.').map { it.toIntOrNull() ?: 0 }
            val pre = if (preStr.isEmpty()) emptyList() else preStr.split('.').filter { it.isNotEmpty() }
            return ParsedVersion(core, pre)
        }
    }
}
