package moe.huiguang.pixiv.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import moe.huiguang.pixiv.data.model.UserSearchItem

private val UserCardShape = RoundedCornerShape(18.dp)
private val UserPreviewThumbShape = RoundedCornerShape(12.dp)

/** Author card shown in the "作者" tab of search results. */
@Composable
fun UserCard(
    user: UserSearchItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outerPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val interaction = remember { MutableInteractionSource() }
    val cardIndication = rememberCouiCardIndication(UserCardShape, dark)
    val placeholderColor = colors.background.copy(alpha = 0.5f)
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(outerPadding)
            .background(colors.card, UserCardShape)
            .clickable(
                interactionSource = interaction,
                indication = cardIndication,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (user.avatarUrl.isNotBlank()) {
                RetryableImage(
                    url = user.avatarUrl,
                    contentDescription = user.userName,
                    contentScale = ContentScale.Crop,
                    placeholderColor = placeholderColor,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.background),
                )
            } else {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = user.userName.take(1).ifEmpty { "?" },
                        style = TextStyle(color = colors.accent, fontSize = 18.sp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = user.userName.ifBlank { user.userId },
                    kind = CouiTextKind.ItemTitle,
                    color = colors.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "UID ${user.userId}",
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                    maxLines = 1,
                )
                if (user.comment.isNotBlank()) {
                    Text(
                        text = user.comment,
                        kind = CouiTextKind.ItemSummary,
                        color = colors.summary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (user.recentWorks.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                user.recentWorks.take(3).forEach { work ->
                    RetryableImage(
                        url = work.thumbUrl,
                        contentDescription = work.title,
                        contentScale = ContentScale.Crop,
                        placeholderColor = placeholderColor,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(UserPreviewThumbShape)
                            .background(colors.background),
                    )
                }
                repeat(3 - user.recentWorks.take(3).size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
