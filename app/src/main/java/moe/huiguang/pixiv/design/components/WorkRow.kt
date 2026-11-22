package moe.huiguang.pixiv.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

private val WorkRowThumbShape = CouiShape(12.dp, 3f)
internal val WorkRowCardShape = CouiShape(18.dp, 3f)

/** Thumbnail row used by history, bookmarks and the series list. */
@Composable
fun WorkRow(
    thumbUrl: String,
    title: String,
    author: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    /** `false` renders the same content without its own card, for use inside a Group. */
    surface: Boolean = true,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val cardShape = WorkRowCardShape
    val clickModifier = Modifier
        .couiButtonFeedback(dark, interaction, enabled, list = !surface)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        )
    val container = if (surface) {
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(cardShape)
            .background(colors.card)
            .then(clickModifier)
            .padding(10.dp)
    } else {
        Modifier
            .then(clickModifier)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    }
    WorkRowBody(
        thumbUrl = thumbUrl,
        title = title,
        author = author,
        meta = meta,
        highlighted = highlighted,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .then(container),
    )
}

/** Shared thumbnail + text body used by [WorkRow] and [SwipeDeleteRow]. */
@Composable
internal fun WorkRowBody(
    thumbUrl: String,
    title: String,
    author: String,
    meta: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = CouiPageDefaults.themeColors()
    val thumbShape = WorkRowThumbShape
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumbUrl.isBlank()) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(thumbShape)
                    .background(colors.background),
            )
        } else {
            RetryableImage(
                url = thumbUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(thumbShape)
                    .background(colors.background),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title.ifBlank { meta },
                kind = CouiTextKind.ItemTitle,
                color = when {
                    !enabled -> colors.summary
                    highlighted -> colors.accent
                    else -> colors.title
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (author.isNotBlank()) {
                Text(
                    text = author,
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = meta,
                kind = CouiTextKind.ItemSummary,
                color = colors.summary,
                maxLines = 1,
            )
        }
    }
}
