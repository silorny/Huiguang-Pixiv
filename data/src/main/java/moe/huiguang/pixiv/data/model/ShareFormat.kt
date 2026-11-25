package moe.huiguang.pixiv.data.model

/**
 * Formats user-customizable share / copy templates for works and authors.
 *
 * Keeps default templates lightweight when no override is configured, and
 * substitutes `{...}` placeholders cleanly for both live copy/share actions
 * and preview rendering.
 */
object ShareFormatter {

    const val FALLBACK_WORK_TEMPLATE =
        "标题:{title}\n画师:{user_name}({user_id})\nPID:{illust_id}\n{url}"

    const val FALLBACK_AUTHOR_TEMPLATE =
        "画师:{user_name}({user_id})\n{url}"

    const val SAMPLE_TITLE = "夕焼けの空"
    const val SAMPLE_USER_NAME = "絵師さん"
    const val SAMPLE_USER_ID = "12345678"
    const val SAMPLE_ILLUST_ID = "100000000"
    const val SAMPLE_WORK_URL = "https://www.pixiv.net/artworks/100000000"
    const val SAMPLE_AUTHOR_URL = "https://www.pixiv.net/users/12345678"
    val SAMPLE_TAGS: List<String> = listOf("夕焼け", "風景")

    fun formatWork(
        template: String,
        title: String,
        userName: String,
        userId: String,
        illustId: String,
        url: String,
        tags: List<String> = emptyList(),
        fallbackTemplate: String = FALLBACK_WORK_TEMPLATE,
    ): String {
        val active = template.ifBlank { fallbackTemplate }
        val formattedTags = tags
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ") { "#$it" }
        return active
            .replace("{title}", title)
            .replace("{user_name}", userName)
            .replace("{user_id}", userId)
            .replace("{illust_id}", illustId)
            .replace("{url}", url)
            .replace("{tags}", formattedTags)
    }

    fun formatAuthor(
        template: String,
        userName: String,
        userId: String,
        url: String,
        fallbackTemplate: String = FALLBACK_AUTHOR_TEMPLATE,
    ): String {
        val active = template.ifBlank { fallbackTemplate }
        return active
            .replace("{user_name}", userName)
            .replace("{user_id}", userId)
            .replace("{url}", url)
    }

    fun insertToken(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        token: String,
    ): Pair<String, Int> {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val updated = buildString(text.length + token.length) {
            append(text, 0, start)
            append(token)
            append(text, end, text.length)
        }
        return updated to (start + token.length)
    }
}
