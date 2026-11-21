package moe.huiguang.pixiv.ui.detail

/**
 * Pixiv captions are HTML fragments (`<br />`, `<strong>`, `<a href=…>`).
 * The detail page renders plain text, so tags are stripped and the common
 * entities decoded; `<br>` becomes a real line break.
 */
fun plainCaption(raw: String): String {
    if (raw.isBlank()) return ""
    var text = raw
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p\\s*>"), "\n\n")
        .replace(Regex("<[^>]*>"), "")
    val entities = mapOf(
        "&nbsp;" to " ",
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&#39;" to "'",
        "&apos;" to "'",
    )
    entities.forEach { (entity, value) -> text = text.replace(entity, value) }
    return text.trim()
}
