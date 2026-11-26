package moe.huiguang.pixiv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.HistoryEntry
import moe.huiguang.pixiv.data.model.SeriesLink
import moe.huiguang.pixiv.data.model.SeriesNav
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.design.components.WorkRow

/**
 * Official series block: title, previous/next episode and the "view series"
 * entry. Neighbours use the same thumbnail row as history/bookmarks.
 */
@Composable
fun SeriesSection(
    series: SeriesNav,
    currentId: String,
    isNovel: Boolean,
    api: PixivApi,
    onOpenWork: (String) -> Unit,
    onOpenSeries: () -> Unit,
    insetInGrid: Boolean = false,
) {
    val colors = CouiPageDefaults.themeColors()
    val kind = if (isNovel) HistoryEntry.KIND_NOVEL else HistoryEntry.KIND_ILLUST
    var prevLink by remember(series.seriesId, series.prev?.id) { mutableStateOf(series.prev) }
    var nextLink by remember(series.seriesId, series.next?.id) { mutableStateOf(series.next) }

    LaunchedEffect(series.seriesId, series.prev?.id, series.next?.id, isNovel) {
        if (isNovel) {
            val episodes = runCatching { api.novelSeriesContent(series.seriesId)?.episodes }
                .getOrNull()
                .orEmpty()
                .associateBy { it.id }
            episodes[series.prev?.id]?.let { prevLink = it }
            episodes[series.next?.id]?.let { nextLink = it }
        } else {
            series.prev?.let { link ->
                runCatching { api.illust(link.id) }.getOrNull()?.let { detail ->
                    prevLink = link.copy(
                        thumbUrl = detail.thumbUrl,
                        userName = detail.userName,
                        illustType = detail.illustType,
                    )
                }
            }
            series.next?.let { link ->
                runCatching { api.illust(link.id) }.getOrNull()?.let { detail ->
                    nextLink = link.copy(
                        thumbUrl = detail.thumbUrl,
                        userName = detail.userName,
                        illustType = detail.illustType,
                    )
                }
            }
        }
    }

    val seriesBody: @Composable ColumnScope.() -> Unit = {
        Item(
            summary = {
                if (series.order > 0) {
                    Text(
                        text = stringResource(R.string.detail_series_order, series.order),
                        kind = CouiTextKind.ItemSummary,
                        color = colors.summary,
                    )
                }
            },
        ) {
            Text(
                text = series.title.ifBlank { series.seriesId },
                kind = CouiTextKind.ItemTitle,
                color = colors.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Divider()
        SeriesNeighbourRow(
            label = stringResource(R.string.series_prev),
            link = prevLink,
            fallback = stringResource(R.string.series_first),
            kind = kind,
            isNovel = isNovel,
            onOpen = onOpenWork,
        )
        Divider()
        SeriesNeighbourRow(
            label = stringResource(R.string.series_next),
            link = nextLink,
            fallback = stringResource(R.string.series_last),
            kind = kind,
            isNovel = isNovel,
            onOpen = onOpenWork,
        )
        Divider()
        Item(
            summary = {
                Text(
                    text = stringResource(R.string.series_view_summary),
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                )
            },
            onClick = onOpenSeries,
        ) {
            Text(
                text = stringResource(R.string.series_view),
                kind = CouiTextKind.ItemTitle,
                color = colors.accent,
            )
        }
    }

    if (insetInGrid) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.detail_series),
                kind = CouiTextKind.GroupTitle,
                color = colors.summary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(CouiPageDefaults.shape)
                    .background(colors.card),
                content = seriesBody,
            )
        }
    } else {
        Group(title = stringResource(R.string.detail_series), content = seriesBody)
    }
}

@Composable
private fun SeriesNeighbourRow(
    label: String,
    link: SeriesLink?,
    fallback: String,
    kind: String,
    isNovel: Boolean,
    onOpen: (String) -> Unit,
) {
    val usable = link?.takeIf { it.available }
    WorkRow(
        thumbUrl = usable?.thumbUrl.orEmpty(),
        title = usable?.let { it.title.ifBlank { it.id } } ?: fallback,
        author = usable?.userName.orEmpty(),
        meta = if (usable != null) {
            "$label · ${workMeta(kind, if (isNovel) -1 else usable.illustType, usable.id)}"
        } else {
            label
        },
        onClick = { usable?.let { onOpen(it.id) } },
        enabled = usable != null,
        surface = false,
    )
}
