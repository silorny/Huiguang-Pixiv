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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.theme.CouiTheme

/**
 * Lightweight grayscale waterfall skeleton using COUI `outlineVariant` per the
 * design contract (Section 5: 3–6 placeholder cards).
 */
@Composable
fun WaterfallSkeleton(
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 24.dp,
) {
    val colors = CouiPageDefaults.themeColors()
    val placeholder = CouiTheme.colorScheme.outlineVariant
    val leftHeights = listOf(210.dp, 168.dp, 192.dp)
    val rightHeights = listOf(164.dp, 224.dp, 178.dp)

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
        SkeletonColumn(
            heights = leftHeights,
            cardColor = colors.card,
            placeholderColor = placeholder,
            modifier = Modifier.weight(1f),
        )
        SkeletonColumn(
            heights = rightHeights,
            cardColor = colors.card,
            placeholderColor = placeholder,
            modifier = Modifier.weight(1f),
        )
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
                    .clip(CouiShape(20.dp, 3f))
                    .background(cardColor)
                    .padding(6.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(imageHeight)
                        .clip(CouiShape(14.dp, 3f))
                        .background(placeholderColor),
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.82f)
                        .height(14.dp)
                        .padding(horizontal = 4.dp)
                        .clip(CouiCapsuleShape)
                        .background(placeholderColor),
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.48f)
                        .height(11.dp)
                        .padding(horizontal = 4.dp)
                        .clip(CouiCapsuleShape)
                        .background(placeholderColor),
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = topPadding),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(CouiShape(20.dp, 3f))
                .background(placeholder),
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth(0.68f)
                .height(20.dp)
                .clip(CouiCapsuleShape)
                .background(placeholder),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth(0.32f)
                .height(13.dp)
                .clip(CouiCapsuleShape)
                .background(placeholder),
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(placeholder),
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .width(112.dp)
                        .height(14.dp)
                        .clip(CouiCapsuleShape)
                        .background(placeholder),
                )
                Box(
                    Modifier
                        .width(72.dp)
                        .height(11.dp)
                        .clip(CouiCapsuleShape)
                        .background(placeholder),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(CouiCapsuleShape)
                .background(colors.card),
        )
    }
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding, bottom = bottomPadding),
    ) {
        repeat(count) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(CouiShape(18.dp, 3f))
                    .background(colors.card)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CouiShape(12.dp, 3f))
                        .background(placeholder),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.76f)
                            .height(14.dp)
                            .clip(CouiCapsuleShape)
                            .background(placeholder),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.46f)
                            .height(11.dp)
                            .clip(CouiCapsuleShape)
                            .background(placeholder),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.32f)
                            .height(10.dp)
                            .clip(CouiCapsuleShape)
                            .background(placeholder),
                    )
                }
            }
        }
    }
}
