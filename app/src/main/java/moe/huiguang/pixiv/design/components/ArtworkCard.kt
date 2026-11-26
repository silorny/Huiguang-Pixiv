package moe.huiguang.pixiv.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.SearchCategory
import moe.huiguang.pixiv.data.model.SearchItem
import moe.huiguang.pixiv.design.shape.CachedCouiShape

private val CardOuterShape = CachedCouiShape(20.dp, 3f)
private val CardInnerShape = CachedCouiShape(14.dp, 3f)
private val BadgeShape = CachedCouiShape(8.dp, 3f)
private val BadgeTextStyle = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)

private val WATERFALL_THUMB_RULES = listOf(
    "/c/480x960/" to "/c/240x480/",
    "/c/540x540_70/" to "/c/360x360_70/",
    "/c/600x600/img-master/" to "/c/360x360_70/img-master/",
    "/c/600x600/custom-thumb/" to "/c/360x360_70/custom-thumb/",
)

/** Rewrites large Pixiv thumbnail CDN presets to lighter equivalents for fast waterfall scrolling. */
internal fun downgradeWaterfallThumbUrl(url: String): String {
    if (url.isEmpty()) return url
    for ((from, to) in WATERFALL_THUMB_RULES) {
        if (url.contains(from)) return url.replace(from, to)
    }
    return url
}

/**
 * The one media card used by home, search results and user pages.
 *
 * Illustrations and manga show their aspect-matched cover plus multi-page
 * (`P·N`) and R-18 badges; novels use a vertical cover ratio, word-count badge
 * and a brief excerpt so all three types sit naturally in the same waterfall.
 */
@Composable
fun ArtworkCard(
    item: SearchItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showAuthor: Boolean = true,
    revealR18: Boolean = true,
    lowQualityThumb: Boolean = false,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val isNovel = item.category == SearchCategory.NOVEL
    val isManga = item.category == SearchCategory.MANGA
    // Novels do not report image pixel dimensions; Pixiv covers are ~427x600.
    val ratio = item.aspectRatio?.takeIf { it > 0.1f } ?: if (isNovel) 1.38f else 0.75f
    val maskR18 = item.isAdult && !revealR18
    val rawThumb = item.thumbUrl.ifBlank { item.fullUrl }
    val imageUrl = if (lowQualityThumb) downgradeWaterfallThumbUrl(rawThumb) else rawThumb

    Column(
        modifier = modifier
            .clip(CardOuterShape)
            .background(colors.card)
            .couiButtonFeedback(dark, interaction, list = true)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio((1f / ratio).coerceIn(0.45f, 1.8f))
                .clip(CardInnerShape)
                .background(colors.background),
        ) {
            RetryableImage(
                url = imageUrl,
                contentDescription = listOf(item.title, item.userName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                contentScale = ContentScale.Crop,
                allowRgb565 = lowQualityThumb,
                maxDecodeSizePx = if (lowQualityThumb) 360 else 0,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (maskR18) Modifier.blur(20.dp) else Modifier),
            )
            if (maskR18) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x55000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    CornerBadge(text = stringResource(R.string.card_r18_masked), adult = true)
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    if (item.isAdult) {
                        CornerBadge(text = "R-18", adult = true)
                    } else {
                        Spacer(Modifier)
                    }
                    when {
                        isNovel -> {
                            val badge = if (item.textCount > 0) {
                                stringResource(R.string.card_novel_words, formatCharCount(item.textCount))
                            } else {
                                stringResource(R.string.filter_novel)
                            }
                            CornerBadge(text = badge)
                        }

                        item.pageCount > 1 -> {
                            val prefix = if (isManga) "${stringResource(R.string.filter_manga)} · " else ""
                            CornerBadge(text = "${prefix}P·${item.pageCount}")
                        }

                        isManga -> CornerBadge(text = stringResource(R.string.filter_manga))
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title.ifBlank { stringResource(R.string.untitled_work) },
            kind = CouiTextKind.ItemTitle,
            color = colors.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (isNovel && !item.seriesTitle.isNullOrBlank()) {
            Text(
                text = item.seriesTitle.orEmpty(),
                kind = CouiTextKind.ItemSummary,
                color = colors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (isNovel && item.description.isNotBlank()) {
            Text(
                text = item.description,
                kind = CouiTextKind.ItemSummary,
                color = colors.summary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        if (showAuthor && item.userName.isNotBlank()) {
            Text(
                text = item.userName,
                kind = CouiTextKind.ItemSummary,
                color = colors.summary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        } else if (!isNovel || item.description.isBlank()) {
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun formatCharCount(count: Int): String {
    val useMyriad = booleanResource(R.bool.use_myriad_unit)
    return when {
        useMyriad && count >= 10_000 -> stringResource(R.string.count_unit_myriad, count / 10_000f)
        count >= 1_000 -> stringResource(R.string.count_unit_thousand, count / 1_000f)
        else -> count.toString()
    }
}

@Composable
private fun CornerBadge(
    text: String,
    modifier: Modifier = Modifier,
    adult: Boolean = false,
) {
    val bg = if (adult) Color(0xD9C83A3A) else Color(0xB8000000)
    val stroke = if (adult) Color(0x40FFFFFF) else Color(0x2EFFFFFF)
    Box(
        modifier
            .background(bg, BadgeShape)
            .border(0.5.dp, stroke, BadgeShape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        BasicText(
            text = text,
            style = BadgeTextStyle,
        )
    }
}
