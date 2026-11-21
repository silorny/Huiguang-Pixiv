package moe.huiguang.pixiv.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.HistoryEntry

/** Short localized type label shared by history, bookmarks, search and detail. */
@Composable
fun workTypeLabel(kind: String, illustType: Int): String = when {
    kind == HistoryEntry.KIND_NOVEL -> stringResource(R.string.filter_novel)
    kind == HistoryEntry.KIND_AUTHOR -> stringResource(R.string.work_type_author)
    illustType == 1 -> stringResource(R.string.filter_manga)
    illustType == 2 -> stringResource(R.string.work_type_ugoira)
    else -> stringResource(R.string.filter_illust)
}

/** `插画 · PID 123` / `Novel · PID 123` / `Author · UID 456`. */
@Composable
fun workMeta(kind: String, illustType: Int, id: String): String {
    val prefix = if (kind == HistoryEntry.KIND_AUTHOR) "UID" else "PID"
    return "${workTypeLabel(kind, illustType)} · $prefix $id"
}
