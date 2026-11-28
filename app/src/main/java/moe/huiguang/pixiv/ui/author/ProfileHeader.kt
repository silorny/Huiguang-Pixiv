package moe.huiguang.pixiv.ui.author

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.layout.CouiPageColors
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.UserProfile
import moe.huiguang.pixiv.design.components.GlowChip
import moe.huiguang.pixiv.design.components.GlowStatCapsule
import moe.huiguang.pixiv.design.components.GlowSurface
import moe.huiguang.pixiv.design.components.RetryableImage
import moe.huiguang.pixiv.design.components.StatValue
import moe.huiguang.pixiv.ui.common.copyPlainText

private val AuthorCoverShape = RoundedCornerShape(20.dp)
private val UidChipShape = RoundedCornerShape(6.dp)
private val BioCardShape = RoundedCornerShape(20.dp)

/**
 * Author profile header card: fused cover banner, overlapping avatar, UID badge,
 * follow toggle, expandable bio card with clickable links, platform chips, and stats capsule.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileHeader(
    userId: String,
    profile: UserProfile?,
    bookmarksCount: Int?,
    error: String?,
    isFollowed: Boolean,
    onToggleFollow: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenMyPixiv: () -> Unit,
    onOpenBookmarks: () -> Unit,
    backdrop: CouiBackdrop,
    dark: Boolean,
    titleProgress: Float,
    onNameRowPositioned: (Float) -> Unit,
    onRequestExternalLink: (AuthorLink) -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val nameShiftPx = with(density) { 10.dp.toPx() }
    val displayUid = profile?.id?.takeIf { it.isNotBlank() } ?: userId
    val coverUrl = profile?.backgroundUrl.orEmpty()
    val webpageFallback = stringResource(R.string.author_webpage_label)
    val commentText = profile?.comment?.trim().orEmpty()
    val profileLinks = remember(commentText, profile?.webpage, webpageFallback) {
        extractProfileLinks(commentText, profile?.webpage, webpageFallback)
    }
    val inlineLinks = remember(commentText, webpageFallback) {
        extractInlineLinkSpans(commentText, webpageFallback)
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (coverUrl.isNotBlank()) {
            // Fused cover + overlapping avatar + far-right UID strip.
            Box(Modifier.fillMaxWidth()) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 38.dp),
                ) {
                    val coverHeight = (maxWidth / 1.9f).coerceAtMost(220.dp)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(coverHeight)
                            .clip(AuthorCoverShape)
                            .background(colors.card, AuthorCoverShape),
                    ) {
                        RetryableImage(
                            url = coverUrl,
                            contentDescription = profile?.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, colors.background.copy(alpha = 0.28f)),
                                    ),
                                ),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .height(38.dp)
                        .padding(start = 94.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    UidCopyChip(
                        uid = displayUid,
                        summaryColor = colors.summary,
                        onCopy = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            copyPlainText(context, "uid", displayUid, resources.getString(R.string.author_uid_copied))
                        },
                    )
                }

                AuthorAvatar(
                    avatarUrl = profile?.avatarUrl.orEmpty(),
                    size = 72.dp,
                    ringWidth = 3.5.dp,
                    ringColor = colors.background,
                    placeholderColor = colors.card,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .onPlaced { coords ->
                        if (coords.isAttached) onNameRowPositioned(coords.positionInParent().y)
                    },
            ) {
                BasicText(
                    text = profile?.name ?: error ?: stringResource(R.string.search_loading),
                    style = TextStyle(
                        color = colors.title,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 28.sp,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                        .graphicsLayer {
                            val p = titleProgress.coerceIn(0f, 1f)
                            translationY = -nameShiftPx * p
                            alpha = (1f - p * 0.7f).coerceIn(0.3f, 1f)
                        },
                )
                Spacer(Modifier.width(12.dp))
                AuthorFollowChip(
                    isFollowed = isFollowed,
                    onClick = onToggleFollow,
                    backdrop = backdrop,
                    dark = dark,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UidCopyChip(
                    uid = displayUid,
                    summaryColor = colors.summary,
                    onCopy = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        copyPlainText(context, "uid", displayUid, resources.getString(R.string.author_uid_copied))
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .onPlaced { coords ->
                        if (coords.isAttached) onNameRowPositioned(coords.positionInParent().y)
                    },
            ) {
                AuthorAvatar(
                    avatarUrl = profile?.avatarUrl.orEmpty(),
                    size = 64.dp,
                    ringWidth = 0.dp,
                    ringColor = colors.background,
                    placeholderColor = colors.card,
                )
                Spacer(Modifier.width(14.dp))
                BasicText(
                    text = profile?.name ?: error ?: stringResource(R.string.search_loading),
                    style = TextStyle(
                        color = colors.title,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 28.sp,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            val p = titleProgress.coerceIn(0f, 1f)
                            translationY = -nameShiftPx * p
                            alpha = (1f - p * 0.7f).coerceIn(0.3f, 1f)
                        },
                )
                Spacer(Modifier.width(10.dp))
                AuthorFollowChip(
                    isFollowed = isFollowed,
                    onClick = onToggleFollow,
                    backdrop = backdrop,
                    dark = dark,
                )
            }
        }

        if (profile != null && commentText.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            AuthorBioCard(
                comment = commentText,
                inlineLinks = inlineLinks,
                colors = colors,
                backdrop = backdrop,
                dark = dark,
                onLinkClick = onRequestExternalLink,
            )
        }
        if (profile != null && profileLinks.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                profileLinks.forEach { link ->
                    key(link.url) {
                        AuthorLinkChip(
                            link = link,
                            backdrop = backdrop,
                            dark = dark,
                            accent = colors.accent,
                            onClick = { onRequestExternalLink(link) },
                        )
                    }
                }
            }
        }
        if (profile != null) {
            Spacer(Modifier.height(12.dp))
            GlowStatCapsule(
                stats = listOf(
                    StatValue("${profile.following}", stringResource(R.string.author_following), onClick = onOpenFollowing),
                    StatValue("${profile.mypixivCount}", stringResource(R.string.author_mypixiv), onClick = onOpenMyPixiv),
                    StatValue(bookmarksCount?.toString() ?: "—", stringResource(R.string.author_bookmarks), onClick = onOpenBookmarks),
                ),
                backdrop = backdrop,
                dark = dark,
            )
        }
    }
}

/**
 * Author follow button: blue CTA when unfollowed, luminous white capsule when followed
 * (using [GlowSurface] white pill in dark mode too so the state contrast stays unmistakable).
 */
@Composable
private fun AuthorFollowChip(
    isFollowed: Boolean,
    onClick: () -> Unit,
    backdrop: CouiBackdrop,
    dark: Boolean,
) {
    val label = stringResource(if (isFollowed) R.string.author_followed else R.string.author_following)
    if (!isFollowed || !dark) {
        GlowChip(
            text = label,
            selected = !isFollowed,
            whiteGlass = isFollowed,
            onClick = onClick,
            backdrop = backdrop,
            dark = dark,
        )
    } else {
        val haptics = LocalHapticFeedback.current
        GlowSurface(
            backdrop = backdrop,
            dark = true,
            modifier = Modifier.height(32.dp),
            shape = CouiCapsuleShape,
            cornerWeight = 1f,
            preset = SpotlightPreset.ChipTab,
            colored = Color(0xEAEFF3F6),
            cancelLightOnScroll = true,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            foregroundComposition = true,
            staticSurface = true,
        ) {
            BasicText(
                text = label,
                style = TextStyle(
                    color = Color(0xFF14171A),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
    }
}

@Composable
private fun AuthorAvatar(
    avatarUrl: String,
    size: Dp,
    ringWidth: Dp,
    ringColor: Color,
    placeholderColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (ringWidth > 0.dp) ringColor else placeholderColor)
            .then(if (ringWidth > 0.dp) Modifier.padding(ringWidth) else Modifier),
    ) {
        if (avatarUrl.isNotBlank()) {
            RetryableImage(
                url = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(placeholderColor),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(placeholderColor),
            )
        }
    }
}

@Composable
private fun UidCopyChip(
    uid: String,
    summaryColor: Color,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "UID $uid",
        kind = CouiTextKind.ItemSummary,
        color = summaryColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(UidChipShape)
            .clickable(onClick = onCopy)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun AuthorBioCard(
    comment: String,
    inlineLinks: List<AuthorLink>,
    colors: CouiPageColors,
    backdrop: CouiBackdrop,
    dark: Boolean,
    onLinkClick: (AuthorLink) -> Unit,
) {
    var expanded by remember(comment) { mutableStateOf(false) }
    var canExpand by remember(comment) { mutableStateOf(false) }

    val annotatedComment = remember(comment, inlineLinks, colors.accent) {
        buildLinkedBioAnnotatedString(
            text = comment,
            inlineLinks = inlineLinks,
            linkColor = colors.accent,
            onLinkClick = onLinkClick,
        )
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(BioCardShape)
            .background(colors.card, BioCardShape)
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 360f),
            ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            BasicText(
                text = annotatedComment,
                style = TextStyle(
                    color = colors.title.copy(alpha = 0.86f),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Start,
                ),
                maxLines = if (expanded) Int.MAX_VALUE else 3,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlowChip(
                        text = stringResource(if (expanded) R.string.detail_collapse else R.string.detail_expand),
                        selected = true,
                        onClick = { expanded = !expanded },
                        backdrop = backdrop,
                        dark = dark,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthorLinkChip(
    link: AuthorLink,
    backdrop: CouiBackdrop,
    dark: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val chipColor = platformChipColor(link.platform, dark = dark, fallbackAccent = accent)
    GlowSurface(
        backdrop = backdrop,
        dark = dark,
        modifier = Modifier.height(34.dp),
        shape = CouiCapsuleShape,
        cornerWeight = 1f,
        preset = SpotlightPreset.ChipTab,
        colored = chipColor,
        cancelLightOnScroll = true,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        foregroundComposition = true,
        staticSurface = true,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            ExternalLinkGlyph(Color.White)
            BasicText(
                text = link.label,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
    }
}

@Composable
private fun ExternalLinkGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(14.dp)) {
        val strokeWidth = 1.6.dp.toPx()
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.45f, h * 0.22f)
            lineTo(w * 0.18f, h * 0.22f)
            lineTo(w * 0.18f, h * 0.84f)
            lineTo(w * 0.78f, h * 0.84f)
            lineTo(w * 0.78f, h * 0.55f)
        }
        drawPath(path, color = tint, style = stroke)
        drawLine(
            color = tint,
            start = Offset(w * 0.48f, h * 0.52f),
            end = Offset(w * 0.84f, h * 0.16f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        val arrowHead = Path().apply {
            moveTo(w * 0.58f, h * 0.16f)
            lineTo(w * 0.84f, h * 0.16f)
            lineTo(w * 0.84f, h * 0.42f)
        }
        drawPath(arrowHead, color = tint, style = stroke)
    }
}
