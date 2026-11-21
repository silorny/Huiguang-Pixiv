package moe.huiguang.pixiv.data.net

/** A signed-in Pixiv account: the session cookie plus the id it belongs to. */
data class Session(val cookie: String, val userId: String)

/**
 * Read by [PixivApi]'s interceptor on every request.
 *
 * Deliberately Android-free so the network layer needs no `Context`, and so it
 * can be exercised on a plain JVM.
 */
object SessionHolder {
    @Volatile
    var cookie: String? = null
}

/**
 * Deduplicates a raw `Cookie` header string by cookie name so an earlier
 * anonymous `PHPSESSID` never shadows the signed-in `PHPSESSID` (`<uid>_...`).
 */
fun normalizeCookieHeader(raw: String): String {
    if (raw.isBlank()) return ""
    val ordered = LinkedHashMap<String, String>()
    var loggedInPhpSessId: String? = null
    for (part in raw.split(';')) {
        val trimmed = part.trim()
        val eq = trimmed.indexOf('=')
        if (eq <= 0) continue
        val name = trimmed.substring(0, eq).trim()
        val value = trimmed.substring(eq + 1).trim()
        if (name == "PHPSESSID" && value.contains('_')) {
            loggedInPhpSessId = value
        }
        ordered[name] = value
    }
    if (loggedInPhpSessId != null) {
        ordered["PHPSESSID"] = loggedInPhpSessId
    }
    return ordered.entries.joinToString("; ") { "${it.key}=${it.value}" }
}
