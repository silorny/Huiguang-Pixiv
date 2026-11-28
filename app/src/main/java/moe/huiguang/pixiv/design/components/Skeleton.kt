package moe.huiguang.pixiv.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.theme.CouiTheme
import moe.huiguang.pixiv.design.AdaptiveTokens
import moe.huiguang.pixiv.design.rememberAdaptiveGridColumns
import moe.huiguang.pixiv.design.rememberIsLandscapeSplit
import moe.huiguang.pixiv.design.shape.CachedCouiShape

private val SkeletonCardOuterShape = CachedCouiShape(18.dp, 3f)
private val SkeletonCardInnerShape = CachedCouiShape(14.dp, 3f)
private val SkeletonDetailCoverShape = CachedCouiShape(20.dp, 3f)
private val SkeletonThumbShape = CachedCouiShape(12.dp, 3f)

private val SkeletonColumnHeightPatterns = listOf(
    listOf(210.dp, 168.dp, 192.dp),
    listOf(164.dp, 224.dp, 178.dp),
    listOf(196.dp, 156.dp, 212.dp),
    listOf(172.dp, 206.dp, 168.dp),
    listOf(220.dp, 174.dp, 186.dp),
)

/**
 * Lightweight grayscale waterfall skeleton using COUI `outlineVariant` per the
 * design contract (Section 5: 3–6 placeholder cards).
 */
@Composable
fun WaterfallSkeleton(
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 24.dp,
    columns: Int = rememberAdaptiveGridColumns(),
) {
    val colors = CouiPageDefaults.themeColors()
    val placeholder = CouiTheme.colorScheme.outlineVariant
    val columnCount = columns.coerceAtLeast(1)

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = topPadding,
                    bottom = bottomPadding,
                ),
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(columnCount) { index ->
            SkeletonColumn(
                heights = SkeletonColumnHeightPatterns[index % SkeletonColumnHeightPatterns.size],
                cardColor = colors.card,
                placeholderColor = placeholder,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SkeletonColumn(
    heights: List<Dp>,
    cardColor: androidx.compose.ui.graphics.Color,
    placeholderColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        heights.forEach { imageHeight ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(cardColor, SkeletonCardOuterShape)
                    .padding(6.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(imageHeight)
                        .background(placeholderColor, SkeletonCardInnerShape),
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.82f)
                        .height(14.dp)
                        .padding(horizontal = 4.dp)
                        .background(placeholderColor, CircleShape),
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.48f)
                        .height(11.dp)
                        .padding(horizontal = 4.dp)
                        .background(placeholderColor, CircleShape),
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** Detail page skeleton placeholder for cover, title, author and stats capsule. */
@Composable
fun DetailSkeleton(
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = CouiPageDefaults.themeColors()
    val placeholder = CouiTheme.colorScheme.outlineVariant
    val isExpanded = rememberIsLandscapeSplit()

    if (isExpanded) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = topPadding),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(440.dp)
                    .background(placeholder, SkeletonDetailCoverShape),
            )
            Column(Modifier.weight(1f)) {
                DetailMetaSkeleton(cardColor = colors.card, placeholder = placeholder)
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = topPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = AdaptiveTokens.ReadableMaxWidth)
                    .fillMaxWidth(),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(placeholder, SkeletonDetailCoverShape),
                )
                Spacer(Modifier.height(16.dp))
                DetailMetaSkeleton(cardColor = colors.card, placeholder = placeholder)
            }
        }
    }
}

@Composable
private fun DetailMetaSkeleton(
    cardColor: androidx.compose.ui.graphics.Color,
    placeholder: androidx.compose.ui.graphics.Color,
) {
    Box(
        Modifier
            .fillMaxWidth(0.68f)
            .height(20.dp)
            .background(placeholder, CircleShape),
    )
    Spacer(Modifier.height(8.dp))
    Box(
        Modifier
            .fillMaxWidth(0.32f)
            .height(13.dp)
            .background(placeholder, CircleShape),
    )
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .background(placeholder, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .width(112.dp)
                    .height(14.dp)
                    .background(placeholder, CircleShape),
            )
            Box(
                Modifier
                    .width(72.dp)
                    .height(11.dp)
                    .background(placeholder, CircleShape),
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(cardColor, CouiCapsuleShape),
    )
}

/**
 * Lightweight grayscale list skeleton matching [WorkRow] cards for bookmarks /
 * history / profile lists.
 */
@Composable
fun WorkRowListSkeleton(
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 24.dp,
    count: Int = 5,
) {
    val colors = CouiPageDefaults.themeColors()
    val placeholder = CouiTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding, bottom = bottomPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = AdaptiveTokens.ReadableMaxWidth)
                .fillMaxWidth(),
        ) {
            repeat(count) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .background(colors.card, SkeletonCardOuterShape)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .background(placeholder, SkeletonThumbShape),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.76f)
                            .height(14.dp)
                            .background(placeholder, CircleShape),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.46f)
                            .height(11.dp)
                            .background(placeholder, CircleShape),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.32f)
                            .height(10.dp)
                            .background(placeholder, CircleShape),
                    )
                }
            }
            }
        }
    }
}
