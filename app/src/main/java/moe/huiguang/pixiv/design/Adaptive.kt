package moe.huiguang.pixiv.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

enum class WindowWidthTier {
    Compact,
    Medium,
    Expanded,
}

@Immutable
data class AdaptiveLayoutMetrics(
    val windowSize: DpSize,
    val widthTier: WindowWidthTier,
    val isLandscapeSplit: Boolean,
)

object AdaptiveTokens {
    val ReadableMaxWidth: Dp = 680.dp
    val SearchBarMaxWidth: Dp = 640.dp
    val SwitcherMaxWidth: Dp = 480.dp
    val SheetMaxWidth: Dp = 560.dp
    val TagHeaderMaxWidth: Dp = 720.dp
    val AuthorPaneWidth: Dp = 360.dp
    val DefaultMinCellWidth: Dp = 220.dp
}

@Composable
fun rememberWindowSizeDp(): DpSize {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return with(density) {
        DpSize(containerSize.width.toDp(), containerSize.height.toDp())
    }
}

@Composable
fun rememberWindowWidthTier(): WindowWidthTier {
    val width = rememberWindowSizeDp().width
    return when {
        width >= 840.dp -> WindowWidthTier.Expanded
        width >= 600.dp -> WindowWidthTier.Medium
        else -> WindowWidthTier.Compact
    }
}

@Composable
fun rememberAdaptiveLayoutMetrics(): AdaptiveLayoutMetrics {
    val size = rememberWindowSizeDp()
    val tier = when {
        size.width >= 840.dp -> WindowWidthTier.Expanded
        size.width >= 600.dp -> WindowWidthTier.Medium
        else -> WindowWidthTier.Compact
    }
    return AdaptiveLayoutMetrics(
        windowSize = size,
        widthTier = tier,
        isLandscapeSplit = size.width >= 600.dp && size.width > size.height,
    )
}

@Composable
fun rememberIsLandscapeSplit(): Boolean = rememberAdaptiveLayoutMetrics().isLandscapeSplit

fun computeAdaptiveGridColumns(
    availableWidth: Dp,
    minCellWidth: Dp = AdaptiveTokens.DefaultMinCellWidth,
    horizontalPadding: Dp = 32.dp,
    spacing: Dp = 12.dp,
    minColumns: Int = 2,
    maxColumns: Int = 5,
): Int {
    val contentWidth = (availableWidth - horizontalPadding).coerceAtLeast(minCellWidth)
    val rawColumns = ((contentWidth + spacing) / (minCellWidth + spacing)).toInt()
    return rawColumns.coerceIn(minColumns, maxColumns)
}

@Composable
fun rememberAdaptiveGridColumns(
    availableWidth: Dp = rememberWindowSizeDp().width,
    minCellWidth: Dp = AdaptiveTokens.DefaultMinCellWidth,
    horizontalPadding: Dp = 32.dp,
    spacing: Dp = 12.dp,
    minColumns: Int = 2,
    maxColumns: Int = 5,
): Int = computeAdaptiveGridColumns(
    availableWidth = availableWidth,
    minCellWidth = minCellWidth,
    horizontalPadding = horizontalPadding,
    spacing = spacing,
    minColumns = minColumns,
    maxColumns = maxColumns,
)

fun Modifier.readableWidth(maxWidth: Dp = AdaptiveTokens.ReadableMaxWidth): Modifier =
    this.widthIn(max = maxWidth)

@Composable
fun CenteredReadableBox(
    modifier: Modifier = Modifier,
    maxWidth: Dp = AdaptiveTokens.ReadableMaxWidth,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}
