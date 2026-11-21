package moe.huiguang.pixiv.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import kotlinx.coroutines.launch
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.HistoryEntry
import moe.huiguang.pixiv.data.model.SeriesLink
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.design.components.SecondaryButton
import moe.huiguang.pixiv.design.components.WorkRow
import moe.huiguang.pixiv.ui.common.FloatingSecondaryTopBar
import moe.huiguang.pixiv.ui.common.workMeta

/**
 * Series listing. Pixiv has no list endpoint, so [PixivApi.seriesEpisodes]
 * rebuilds it by walking prev/next — the list fills in progressively.
 */
@Composable
fun SeriesScreen(
    kind: String,
    seriesId: String,
    startId: String,
    api: PixivApi,
    onBack: () -> Unit,
    onOpen: (SeriesLink) -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    val backdrop = rememberCouiBackdrop()
    val overscroll = com.itos.coui.scroll.rememberCouiOverscrollEffect()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isNovel = kind == "novel"
    var title by remember(seriesId) { mutableStateOf<String?>(null) }
    var episodes by remember(seriesId) { mutableStateOf<List<SeriesLink>>(emptyList()) }
    var loading by remember(seriesId) { mutableStateOf(true) }
    var loadingMore by remember(seriesId) { mutableStateOf(false) }
    var ended by remember(seriesId) { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(seriesId, startId) {
        loading = true
        if (isNovel) {
            runCatching { api.novelSeries(seriesId)?.title }.getOrNull()?.let { title = it }
            val content = runCatching { api.novelSeriesContent(seriesId) }.getOrNull()
            if (!content?.episodes.isNullOrEmpty()) {
                episodes = content.episodes
            } else {
                // If the batch endpoint refuses (limit/region), fall back to the
                // prev/next walk so the list still opens.
                episodes = runCatching {
                    api.seriesEpisodes(
                        seriesId = seriesId,
                        startId = startId,
                        isNovel = true,
                        cap = 20,
                    )
                }.getOrDefault(emptyList())
            }
        } else {
            runCatching { api.illust(startId)?.series?.title }.getOrNull()?.let { title = it }
            runCatching {
                api.seriesEpisodes(
                    seriesId = seriesId,
                    startId = startId,
                    isNovel = false,
                    onProgress = { partial -> episodes = partial },
                )
            }.onSuccess { episodes = it }
        }
        loading = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .couiBackdrop(backdrop)
                .background(colors.background),
        ) {
            if (episodes.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(
                            if (loading) R.string.search_loading else R.string.series_empty,
                        ),
                        kind = CouiTextKind.ItemSummary,
                        color = colors.summary,
                    )
                }
            } else {
                val rowKind = if (isNovel) HistoryEntry.KIND_NOVEL else HistoryEntry.KIND_ILLUST
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .overscroll(overscroll),
                    contentPadding = PaddingValues(
                        top = statusTop + 56.dp + 8.dp,
                        bottom = navBottom + 24.dp,
                    ),
                ) {
                    item(key = "count") {
                        Text(
                            text = stringResource(R.string.series_count, episodes.size),
                            kind = CouiTextKind.GroupTitle,
                            color = colors.summary,
                            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp),
                        )
                    }

                    items(episodes, key = { it.id }) { link ->
                        WorkRow(
                            thumbUrl = link.thumbUrl,
                            title = link.title.ifBlank { link.id },
                            author = link.userName,
                            meta = stringResource(R.string.detail_series_order, link.order) +
                                " · " + workMeta(rowKind, link.illustType, link.id),
                            highlighted = link.id == startId,
                            onClick = { onOpen(link) },
                        )
                    }

                    if (episodes.size >= 20) {
                        item(key = "more") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (ended) {
                                    Text(
                                        text = stringResource(R.string.series_end),
                                        kind = CouiTextKind.ItemSummary,
                                        color = colors.summary,
                                    )
                                } else {
                                    SecondaryButton(
                                        text = stringResource(
                                            if (loadingMore) R.string.search_loading else R.string.series_load_more,
                                        ),
                                        onClick = {
                                            if (!loadingMore) {
                                                loadingMore = true
                                                scope.launch {
                                                    val last = episodes.last()
                                                    val more = if (isNovel) {
                                                        runCatching {
                                                            api.novelSeriesContent(
                                                                seriesId = seriesId,
                                                                limit = 30,
                                                                lastOrder = last.order,
                                                            )?.episodes.orEmpty()
                                                        }.getOrDefault(emptyList())
                                                            .filter { ep -> episodes.none { it.id == ep.id } }
                                                            .ifEmpty {
                                                                runCatching {
                                                                    api.seriesMore(last.id, true, 10)
                                                                }.getOrDefault(emptyList())
                                                            }
                                                    } else {
                                                        runCatching {
                                                            api.seriesMore(last.id, false, 10)
                                                        }.getOrDefault(emptyList())
                                                    }
                                                    if (more.isEmpty()) ended = true else episodes = episodes + more
                                                    loadingMore = false
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingSecondaryTopBar(
            title = title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.series_title),
            backdrop = backdrop,
            onBack = onBack,
        )
    }
}
