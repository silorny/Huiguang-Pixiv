package moe.huiguang.pixiv.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.HistoryEntry
import moe.huiguang.pixiv.data.model.LinkedWork
import moe.huiguang.pixiv.data.model.SeriesNav
import moe.huiguang.pixiv.data.model.parseLinkedWorks
import moe.huiguang.pixiv.data.model.visibleLinkedWorks
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.design.components.WorkRow

private data class LinkedMeta(
    val title: String,
    val thumbUrl: String,
    val userName: String,
    val illustType: Int,
)

/**
 * Parses works linked from the caption and removes anything that already
 * belongs to the official series. Full series membership is loaded lazily and
 * only when a link falls outside the immediate prev/next neighbours.
 *
 * Each remaining link is hydrated into the same thumbnail row used by
 * history/bookmarks; the row appears immediately with its label and PID, then
 * fills in the real cover/title.
 */
@Composable
fun LinkedWorksSection(
    descriptionHtml: String,
    currentKind: String,
    currentId: String,
    series: SeriesNav?,
    api: PixivApi,
    onOpen: (LinkedWork) -> Unit,
    onOpenAuthor: ((String) -> Unit)? = null,
    titleHorizontalPadding: Dp = 24.dp,
    cardHorizontalPadding: Dp = 16.dp,
) {
    val colors = CouiPageDefaults.themeColors()
    val fallbackLabel = stringResource(R.string.linked_work_fallback)
    val authorRoleLabel = stringResource(R.string.detail_linked_author_role)
    val linked = remember(descriptionHtml, currentKind, currentId) {
        parseLinkedWorks(descriptionHtml, currentKind, currentId)
    }
    val neighbourIds = remember(series, currentId) {
        buildSet {
            add(currentId)
            series?.prev?.id?.let(::add)
            series?.next?.id?.let(::add)
        }
    }
    var seriesIds by remember(series?.seriesId, currentId) { mutableStateOf(neighbourIds) }
    var metadata by remember(linked) { mutableStateOf<Map<String, LinkedMeta>>(emptyMap()) }

    LaunchedEffect(series?.seriesId, currentKind, currentId, linked) {
        val nav = series ?: return@LaunchedEffect
        if (linked.isEmpty()) return@LaunchedEffect
        val needsFullList = linked.any { it.kind == currentKind && it.id !in neighbourIds }
        if (!needsFullList) return@LaunchedEffect
        val ids = if (currentKind == "novel") {
            runCatching { api.novelSeriesContent(nav.seriesId)?.allIds }.getOrNull()
        } else {
            runCatching {
                api.seriesEpisodes(
                    seriesId = nav.seriesId,
                    startId = currentId,
                    isNovel = false,
                    cap = 20,
                ).map { it.id }.toSet()
            }.getOrNull()
        }
        if (!ids.isNullOrEmpty()) {
            seriesIds = neighbourIds + ids
        }
    }

    val visible = visibleLinkedWorks(
        linked = linked,
        currentKind = currentKind,
        seriesIds = seriesIds,
        hasSeries = series != null,
    )

    LaunchedEffect(visible) {
        val resolved = LinkedHashMap<String, LinkedMeta>()
        visible.forEach { work ->
            val meta = runCatching {
                when (work.kind) {
                    "novel" -> api.novel(work.id)?.let {
                        LinkedMeta(it.title, it.coverUrl, it.userName, -1)
                    }
                    "author" -> api.user(work.id)?.let {
                        LinkedMeta(it.name, it.avatarUrl, "", -2)
                    }
                    else -> api.illust(work.id)?.let {
                        LinkedMeta(it.title, it.thumbUrl, it.userName, it.illustType)
                    }
                }
            }.getOrNull()
            if (meta != null) resolved["${work.kind}:${work.id}"] = meta
        }
        metadata = resolved
    }

    if (visible.isEmpty()) return

    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.detail_linked_info),
        kind = CouiTextKind.GroupTitle,
        color = colors.summary,
        modifier = Modifier.padding(horizontal = titleHorizontalPadding, vertical = 6.dp),
    )
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        visible.forEach { work ->
            val meta = metadata["${work.kind}:${work.id}"]
            if (work.kind == "author") {
                val roleLabel = work.label.ifBlank { authorRoleLabel }
                WorkRow(
                    thumbUrl = meta?.thumbUrl.orEmpty(),
                    title = meta?.title?.ifBlank { roleLabel } ?: roleLabel,
                    author = "",
                    meta = "$roleLabel · UID ${work.id}",
                    onClick = { onOpenAuthor?.invoke(work.id) ?: onOpen(work) },
                    outerHorizontalPadding = cardHorizontalPadding,
                )
            } else {
                val kind = if (work.kind == "novel") HistoryEntry.KIND_NOVEL else HistoryEntry.KIND_ILLUST
                val illustType = meta?.illustType ?: if (work.kind == "novel") -1 else 0
                WorkRow(
                    thumbUrl = meta?.thumbUrl.orEmpty(),
                    title = meta?.title?.ifBlank { work.label } ?: work.label.ifBlank { fallbackLabel },
                    author = meta?.userName.orEmpty(),
                    meta = "${work.label.ifBlank { fallbackLabel }} · " +
                        workMeta(kind, illustType, work.id),
                    onClick = { onOpen(work) },
                    outerHorizontalPadding = cardHorizontalPadding,
                )
            }
        }
    }
}
