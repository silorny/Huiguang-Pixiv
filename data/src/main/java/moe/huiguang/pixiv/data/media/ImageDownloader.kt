package moe.huiguang.pixiv.data.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.net.PixivException
import java.net.URLDecoder

/**
 * Saves artwork and exported novels into either the phone's public Downloads
 * folder (`/storage/emulated/0/Download/`) via `MediaStore.Downloads`, or a
 * user-chosen SAF directory tree (`downloadTreeUriProvider`).
 */
class ImageDownloader(
    private val client: OkHttpClient = PixivApi().client,
    private val downloadTreeUriProvider: () -> String = { "" },
) {

    /** @return the human-readable destination path. */
    suspend fun download(context: Context, url: String, fileName: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).get().build()
            val bytes = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw PixivException("HTTP ${response.code}")
                response.body.bytes()
            }
            if (bytes.isEmpty()) throw PixivException("empty body")
            write(context, fileName, mimeOf(fileName), bytes)
        }

    /** Writes plain text (a novel) alongside the images, as UTF-8. */
    suspend fun saveText(context: Context, fileName: String, text: String): String =
        withContext(Dispatchers.IO) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (bytes.isEmpty()) throw PixivException("empty text")
            write(context, fileName, "text/plain", bytes)
        }

    private fun write(context: Context, fileName: String, mime: String, bytes: ByteArray): String {
        val customTreeUri = downloadTreeUriProvider().trim()
        if (customTreeUri.isNotEmpty()) {
            return writeToTree(context, Uri.parse(customTreeUri), fileName, mime, bytes)
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw PixivException("MediaStore refused the insert")

        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw PixivException("could not open the destination")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            // Never leave a half-written pending entry behind.
            resolver.delete(uri, null, null)
            throw e
        }
        return "$DEFAULT_DOWNLOAD_PATH$fileName"
    }

    private fun writeToTree(
        context: Context,
        treeUri: Uri,
        fileName: String,
        mime: String,
        bytes: ByteArray,
    ): String {
        val resolver = context.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
        val destUri = DocumentsContract.createDocument(resolver, parentDocUri, mime, fileName)
            ?: throw PixivException("Could not create file in selected folder")
        try {
            resolver.openOutputStream(destUri)?.use { it.write(bytes) }
                ?: throw PixivException("could not open the destination")
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, destUri) }
            throw e
        }
        return "${formatTreeUriPath(treeUri.toString())}$fileName"
    }

    companion object {
        const val DEFAULT_DOWNLOAD_PATH = "/storage/emulated/0/Download/"

        internal fun decodeTreeDocumentId(uriString: String): String? {
            if (uriString.isBlank()) return null
            val raw = uriString.substringAfter("/tree/", "").substringBefore('/')
            if (raw.isEmpty()) return null
            return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull()
        }

        fun isDefaultDownloadUri(uriString: String): Boolean {
            val docId = decodeTreeDocumentId(uriString)?.trimEnd('/') ?: return false
            return docId == "primary:Download" || docId == "downloads"
        }

        fun formatTreeUriPath(uriString: String): String {
            if (uriString.isBlank() || isDefaultDownloadUri(uriString)) return DEFAULT_DOWNLOAD_PATH
            val docId = decodeTreeDocumentId(uriString) ?: return uriString
            if (docId.startsWith("primary:")) {
                val sub = docId.removePrefix("primary:").trim('/')
                return if (sub.isEmpty()) "/storage/emulated/0/" else "/storage/emulated/0/$sub/"
            }
            val cleaned = docId.replace(':', '/').trim('/')
            return if (cleaned.isEmpty()) DEFAULT_DOWNLOAD_PATH else "$cleaned/"
        }

        private fun mimeOf(fileName: String): String = when {
            fileName.endsWith(".png", true) -> "image/png"
            fileName.endsWith(".gif", true) -> "image/gif"
            fileName.endsWith(".webp", true) -> "image/webp"
            fileName.endsWith(".txt", true) -> "text/plain"
            else -> "image/jpeg"
        }

        /** Strips characters that are illegal in a file name, and caps the length. */
        fun safeName(raw: String, fallback: String): String {
            val cleaned = raw
                .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_")
                .trim()
                .take(80)
            return cleaned.ifEmpty { fallback }
        }

        /** `https://i.pximg.net/.../100000000_p0.jpg` -> `pixiv_100000000_p0.jpg` */
        fun fileNameFor(url: String, fallbackId: String, page: Int): String {
            val last = url.substringAfterLast('/').substringBefore('?')
            val safe = last.replace(Regex("[^A-Za-z0-9._-]"), "_")
            return if (safe.isNotEmpty() && safe.contains('.')) {
                "pixiv_$safe"
            } else {
                "pixiv_${fallbackId}_p$page.jpg"
            }
        }
    }
}
