package moe.huiguang.pixiv.data.media

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.net.PixivException

/**
 * Saves artwork into the phone's public Downloads folder
 * (`/storage/emulated/0/Download/`).
 *
 * minSdk 34 means `MediaStore.Downloads` is always available, so no storage
 * permission and no legacy direct write path is needed.
 */
class ImageDownloader(private val client: OkHttpClient = PixivApi().client) {

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
        return "${Environment.DIRECTORY_DOWNLOADS}/$fileName"
    }

    companion object {

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
