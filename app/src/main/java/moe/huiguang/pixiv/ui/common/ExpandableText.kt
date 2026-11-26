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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
import moe.huiguang.pixiv.data.model.CaptionTarget
import moe.huiguang.pixiv.data.model.parseCaptionContent
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
    rawHtml: String? = null,
    onInternalLink: ((CaptionTarget) -> Unit)? = null,
    onExternalLink: ((String) -> Unit)? = null,
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
            rawHtml = rawHtml,
            onInternalLink = onInternalLink,
            onExternalLink = onExternalLink,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

/**
 * Collapsible text block with a blue COUI [GlowChip] expand/collapse affordance,
 * shared by detail cards and the update changelog card.
 */
@Composable
fun ExpandableText(
    text: String,
    colors: CouiPageColors,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 4,
    backdrop: CouiBackdrop? = null,
    dark: Boolean = CouiTheme.isDark,
    expandLabel: String = stringResource(R.string.detail_expand),
    collapseLabel: String = stringResource(R.string.detail_collapse),
    rawHtml: String? = null,
    onInternalLink: ((CaptionTarget) -> Unit)? = null,
    onExternalLink: ((String) -> Unit)? = null,
) {
    val linksEnabled = rawHtml != null || onInternalLink != null || onExternalLink != null
    val parsedCaption = remember(text, rawHtml, linksEnabled) {
        if (linksEnabled) parseCaptionContent(rawHtml ?: text) else null
    }
    val displayText = parsedCaption?.text?.ifBlank { text } ?: text
    var expanded by remember(displayText) { mutableStateOf(false) }
    var canExpand by remember(displayText) { mutableStateOf(false) }
    val annotated = remember(displayText, parsedCaption, colors.accent, onInternalLink, onExternalLink) {
        if (parsedCaption == null || parsedCaption.links.isEmpty()) {
            AnnotatedString(displayText)
        } else {
            buildCaptionAnnotatedString(
                text = parsedCaption.text,
                links = parsedCaption.links,
                linkColor = colors.accent,
                onInternalLink = onInternalLink,
                onExternalLink = onExternalLink,
            )
        }
    }
    Column(modifier.fillMaxWidth()) {
        BasicText(
            text = annotated,
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
                if (!expanded) {
                    canExpand = result.hasVisualOverflow
                }
            },
        )
        if (canExpand) {
            Spacer(Modifier.height(10.dp))
            val chipBackdrop = backdrop ?: rememberCouiBackdrop()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                GlowChip(
                    text = if (expanded) collapseLabel else expandLabel,
                    selected = true,
                    onClick = { expanded = !expanded },
                    backdrop = chipBackdrop,
                    dark = dark,
                )
            }
        }
    }
}

private fun buildCaptionAnnotatedString(
    text: String,
    links: List<moe.huiguang.pixiv.data.model.CaptionLink>,
    linkColor: Color,
    onInternalLink: ((CaptionTarget) -> Unit)?,
    onExternalLink: ((String) -> Unit)?,
): AnnotatedString = buildAnnotatedString {
    append(text)
    val style = TextLinkStyles(
        style = SpanStyle(
            color = linkColor,
            textDecoration = TextDecoration.Underline,
        ),
    )
    for (link in links) {
        if (link.start < 0 || link.end > text.length || link.start >= link.end) continue
        addLink(
            clickable = LinkAnnotation.Clickable(
                tag = link.displayUrl,
                styles = style,
                linkInteractionListener = {
                    when (val target = link.target) {
                        is CaptionTarget.External -> onExternalLink?.invoke(target.url)
                        else -> onInternalLink?.invoke(target)
                    }
                },
            ),
            start = link.start,
            end = link.end,
        )
    }
}
