package moe.huiguang.pixiv.data.model

/**
 * One renderable unit of a novel body.
 *
 * Pixiv returns the whole body as plain text with lightweight markup:
 * - `[chapter:Title]` for chapter headings
 * - `[newpage]` for page breaks
 * - `[uploadedimage:ID]` / `[pixivimage:ID]` for embedded illustrations
 * - `[[rb:Text > Ruby]]` and `[[jumpuri:Label > URL]]` inline tags
 */
sealed interface NovelBlock {

    /** A `[chapter:Title]` section heading. */
    data class Chapter(val title: String) : NovelBlock

    /** [first] marks the first chunk of a paragraph — only that one gets the indent. */
    data class Paragraph(val text: String, val first: Boolean) : NovelBlock

    /** [image] is `null` when the id has no entry in `textEmbeddedImages`. */
    data class Image(val imageId: String, val image: NovelImage?) : NovelBlock

    /** A `[newpage]` separator between sections. */
    data object PageBreak : NovelBlock
}

object NovelBlocks {

    private val imageMarker = Regex("""\[(?:uploadedimage|pixivimage):(\d+)]""")
    private val chapterMarker = Regex("""^\[chapter:(.+)]$""")
    private val newPageMarker = Regex("""^\[newpage]$""")
    private val jumpMarker = Regex("""\[jump:\d+]""")
    private val rubyMarker = Regex("""\[\[rb:([^>\]]+?)\s*>\s*([^\]]+?)]]""")
    private val jumpUriMarker = Regex("""\[\[jumpuri:([^>\]]+?)\s*>\s*[^\]]+?]]""")

    /**
     * @param chunkSize long paragraphs are cut so the reader never lays out a
     *   10 000-character paragraph in one go (that was the old jank source).
     */
    fun build(
        content: String,
        images: Map<String, NovelImage>,
        chunkSize: Int = 1000,
    ): List<NovelBlock> {
        if (content.isEmpty()) return emptyList()
        val blocks = ArrayList<NovelBlock>()
        content.split('\n').forEach { rawLine ->
            val line = rawLine.trimEnd('\r').trim()
            if (line.isEmpty()) return@forEach

            if (newPageMarker.matches(line)) {
                blocks += NovelBlock.PageBreak
                return@forEach
            }

            chapterMarker.matchEntire(line)?.let { match ->
                val title = cleanInline(match.groupValues[1].trim())
                if (title.isNotEmpty()) blocks += NovelBlock.Chapter(title)
                return@forEach
            }

            val matches = imageMarker.findAll(line).toList()
            if (matches.isEmpty()) {
                appendParagraph(blocks, cleanInline(line), chunkSize)
                return@forEach
            }

            var cursor = 0
            matches.forEach { match ->
                if (match.range.first > cursor) {
                    val before = cleanInline(line.substring(cursor, match.range.first).trim())
                    if (before.isNotEmpty()) appendParagraph(blocks, before, chunkSize)
                }
                val id = match.groupValues[1]
                blocks += NovelBlock.Image(id, images[id])
                cursor = match.range.last + 1
            }
            if (cursor < line.length) {
                val tail = cleanInline(line.substring(cursor).trim())
                if (tail.isNotEmpty()) appendParagraph(blocks, tail, chunkSize)
            }
        }
        return blocks
    }

    private fun appendParagraph(
        blocks: MutableList<NovelBlock>,
        text: String,
        chunkSize: Int,
    ) {
        if (text.isEmpty()) return
        var offset = 0
        var first = true
        while (offset < text.length) {
            val end = minOf(offset + chunkSize, text.length)
            blocks += NovelBlock.Paragraph(text.substring(offset, end), first)
            first = false
            offset = end
        }
    }

    /** Formats Pixiv inline ruby/link markup into readable plain text. */
    internal fun cleanInline(text: String): String = text
        .replace(rubyMarker) { m ->
            val base = m.groupValues[1].trim()
            val ruby = m.groupValues[2].trim()
            if (ruby.isEmpty() || ruby == base) base else "$base（$ruby）"
        }
        .replace(jumpUriMarker) { m -> m.groupValues[1].trim() }
        .replace(jumpMarker, "")
        .trim()

    /** Plain text for the TXT export: chapters and paragraphs kept, markers dropped. */
    fun toPlainText(blocks: List<NovelBlock>): String = buildString {
        blocks.forEach { block ->
            when (block) {
                is NovelBlock.Chapter -> {
                    append(block.title)
                    append("\n\n")
                }

                is NovelBlock.Paragraph -> {
                    append(block.text)
                    append("\n\n")
                }

                is NovelBlock.PageBreak -> append("---\n\n")
                is NovelBlock.Image -> Unit
            }
        }
    }.trimEnd()
}
