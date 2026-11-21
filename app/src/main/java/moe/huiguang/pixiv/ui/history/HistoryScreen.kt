package moe.huiguang.pixiv.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.overscroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.HistoryEntry
import moe.huiguang.pixiv.data.store.SettingsStore
import moe.huiguang.pixiv.design.components.CircleIconButton
import moe.huiguang.pixiv.design.components.SlideSwitcher
import moe.huiguang.pixiv.design.components.WorkRow
import moe.huiguang.pixiv.ui.common.FloatingSecondaryTopBar
import moe.huiguang.pixiv.ui.common.workMeta

/** On-device lookup history, newest first, filtered by work type. */
@Composable
fun HistoryScreen(
    settings: SettingsStore,
    onBack: () -> Unit,
    onOpen: (HistoryEntry) -> Unit,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val backdrop = rememberCouiBackdrop()
    val windowBackdrop = rememberCouiWindowBackdrop()
    val state by settings.state.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val overscroll = com.itos.coui.scroll.rememberCouiOverscrollEffect()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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
            SlideSwitcher(
                index = filter,
                modifier = Modifier.fillMaxSize(),
            ) { selected ->
                val entries = remember(state.history, selected) {
                    state.history.filter { matchesHistoryFilter(it, selected) }
                }
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .overscroll(overscroll),
                    contentPadding = PaddingValues(
                        top = statusTop + 56.dp + 8.dp,
                        bottom = navBottom + 24.dp,
                    ),
                ) {
                    item(key = "filter") {
                        SegmentedControl(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            labels = listOf(
                                stringResource(R.string.filter_all),
                                stringResource(R.string.filter_illust),
                                stringResource(R.string.filter_manga),
                                stringResource(R.string.filter_novel),
                            ),
                            selectedIndex = filter,
                            onSelected = { filter = it },
                        )
                    }
                    if (entries.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 72.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.history_empty),
                                    kind = CouiTextKind.ItemSummary,
                                    color = colors.summary,
                                )
                            }
                        }
                    } else {
                        items(
                            items = entries,
                            key = { "${it.kind}-${it.pid}" },
                            contentType = { "historyRow" },
                        ) { entry ->
                            WorkRow(
                                thumbUrl = entry.thumbUrl,
                                title = entry.title,
                                author = if (entry.kind == HistoryEntry.KIND_AUTHOR) "" else entry.userName,
                                meta = workMeta(entry.kind, entry.illustType, entry.pid),
                                onClick = { onOpen(entry) },
                            )
                        }
                        item(key = "bottom-spacer") { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }

        FloatingSecondaryTopBar(
            title = stringResource(R.string.history_title),
            backdrop = backdrop,
            onBack = onBack,
            trailing = {
                Box {
                    CircleIconButton(
                        icon = CouiIcons.Filled.More,
                        contentDescription = stringResource(R.string.search_filter_menu),
                        onClick = { menuExpanded = true },
                        backdrop = backdrop,
                        dark = dark,
                        blurRadius = 20f,
                    )
                    Menu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        headerText = stringResource(R.string.search_menu_category_header),
                        headerTextAlign = TextAlign.Start,
                        modifier = Modifier.widthIn(min = 168.dp),
                        offset = DpOffset(0.dp, 8.dp),
                        items = listOf(
                            CouiMenuItem(
                                value = 0,
                                text = stringResource(R.string.filter_all),
                                selected = filter == 0,
                            ),
                            CouiMenuItem(
                                value = 1,
                                text = stringResource(R.string.filter_illust),
                                selected = filter == 1,
                            ),
                            CouiMenuItem(
                                value = 2,
                                text = stringResource(R.string.filter_manga),
                                selected = filter == 2,
                            ),
                            CouiMenuItem(
                                value = 3,
                                text = stringResource(R.string.filter_novel),
                                selected = filter == 3,
                            ),
                            CouiMenuItem(
                                value = 4,
                                text = stringResource(R.string.search_menu_clear_history),
                                dividerBefore = true,
                                enabled = state.history.isNotEmpty(),
                            ),
                        ),
                        onItemClick = { item ->
                            menuExpanded = false
                            if (item.value in 0..3) {
                                filter = item.value
                            } else if (item.value == 4) {
                                confirmClear = true
                            }
                        },
                    )
                }
            },
        )
    }

    Dialog(
        visible = confirmClear,
        onDismissRequest = { confirmClear = false },
        title = stringResource(R.string.history_clear),
        message = stringResource(R.string.history_clear_message),
        material = if (windowBackdrop != null) CouiDialogDefaults.material(dark) else null,
        backdrop = windowBackdrop,
        confirmAction = CouiDialogAction(
            text = stringResource(R.string.history_clear),
            onClick = {
                settings.clearHistory()
                confirmClear = false
            },
            style = CouiDialogActionStyle.Primary,
            colors = CouiDialogDefaults.primaryActionColors(CouiDialogDefaults.colors(dark).destructive),
        ),
        dismissAction = CouiDialogAction(
            text = stringResource(R.string.action_cancel),
            onClick = { confirmClear = false },
        ),
    )
}

/** 0 = all, 1 = illustration, 2 = manga, 3 = novel. Authors only show under "all". */
internal fun matchesHistoryFilter(entry: HistoryEntry, filter: Int): Boolean = when (filter) {
    1 -> entry.kind == HistoryEntry.KIND_ILLUST && entry.illustType != 1
    2 -> entry.kind == HistoryEntry.KIND_ILLUST && entry.illustType == 1
    3 -> entry.kind == HistoryEntry.KIND_NOVEL
    else -> true
}
