package moe.huiguang.pixiv.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.layout.CouiPageColors
import com.itos.coui.shape.CouiShape
import com.itos.coui.theme.CouiTheme
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.design.components.GlowChip

/**
 * Rounded card container matching Settings groups, hosting [ExpandableText]
 * with a blue COUI [GlowChip] expand/collapse action.
 */
@Composable
fun IntroCard(
    text: String,
    colors: CouiPageColors,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 4,
) {
    val shape = CouiShape(20.dp, 3f)
    val cardBackdrop = rememberCouiBackdrop()
    val dark = CouiTheme.isDark
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 360f),
            ),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .couiBackdrop(cardBackdrop)
                .background(colors.card, shape),
        )
        ExpandableText(
            text = text,
            colors = colors,
            collapsedLines = collapsedLines,
            backdrop = cardBackdrop,
            dark = dark,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

/**
 * Four-line caption with an expand/collapse blue [GlowChip] affordance,
 * shared by the artwork, novel, author, and pixivision detail pages.
 */
@Composable
fun ExpandableText(
    text: String,
    colors: CouiPageColors,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 4,
    backdrop: CouiBackdrop? = null,
    dark: Boolean = CouiTheme.isDark,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    var canExpand by remember(text) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        BasicText(
            text = text,
            style = TextStyle(
                color = colors.title.copy(alpha = 0.85f),
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Start,
            ),
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            onTextLayout = { result ->
                if (!expanded && result.hasVisualOverflow) {
                    canExpand = true
                }
            },
        )
        if (canExpand || text.length > 80) {
            Spacer(Modifier.height(10.dp))
            val chipBackdrop = backdrop ?: rememberCouiBackdrop()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                GlowChip(
                    text = stringResource(
                        if (expanded) R.string.detail_collapse else R.string.detail_expand,
                    ),
                    selected = true,
                    onClick = { expanded = !expanded },
                    backdrop = chipBackdrop,
                    dark = dark,
                )
            }
        }
    }
}
