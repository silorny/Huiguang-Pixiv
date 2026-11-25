package moe.huiguang.pixiv.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.HistoryEntry
import moe.huiguang.pixiv.design.shape.CachedCouiShape
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CategoryBadgeShape = CachedCouiShape(10.dp, 3f)
private val PidChipShape = CachedCouiShape(6.dp, 3f)
private val WorkDateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.ROOT)
private val DateFallbackRegex = Regex("""(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})""")

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

/** Soft blue capsule badge shared by Pixivision and work detail subtitle rows. */
@Composable
fun CategoryBadge(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .background(accent.copy(alpha = 0.12f), CategoryBadgeShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

/**
 * Unified work detail metadata row matching Pixivision's header style:
 * blue type badge + tappable copyable `PID <id>` on the left, publish date (`yyyy.MM.dd`) on the right.
 */
@Composable
fun WorkMetaHeader(
    typeLabel: String,
    id: String,
    createDate: String,
    modifier: Modifier = Modifier,
) {
    val colors = CouiPageDefaults.themeColors()
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val formattedDate = remember(createDate) { formatWorkDate(createDate) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryBadge(text = typeLabel, accent = colors.accent)
            Text(
                text = "PID $id",
                kind = CouiTextKind.ItemSummary,
                color = colors.summary,
                modifier = Modifier
                    .clip(PidChipShape)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(ClipData.newPlainText("pid", id))
                        Toast.makeText(
                            context,
                            resources.getString(R.string.detail_pid_copied),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        if (formattedDate.isNotEmpty()) {
            Text(
                text = formattedDate,
                kind = CouiTextKind.ItemSummary,
                color = colors.summary,
            )
        }
    }
}

internal fun formatWorkDate(raw: String): String {
    if (raw.isBlank()) return ""
    runCatching {
        return OffsetDateTime.parse(raw).toLocalDate().format(WorkDateFormatter)
    }
    val match = DateFallbackRegex.find(raw) ?: return ""
    val year = match.groupValues[1]
    val month = match.groupValues[2].padStart(2, '0')
    val day = match.groupValues[3].padStart(2, '0')
    return "$year.$month.$day"
}
