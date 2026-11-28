package moe.huiguang.pixiv.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.segmentbutton.CouiSegmentedControlDefaults
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.shape.CouiCapsuleShape

/**
 * Computes an adaptive [SegmentedControl] container width from measured [labels] widths so
 * fractional display densities (`2.975x`, etc.) never lose 1–2px to integer rounding inside
 * `CouiSegmentMotion` and truncate labels with an ellipsis.
 */
@Composable
fun rememberAdaptiveSegmentedControlWidth(labels: List<String>): Dp {
    val textStyle = CouiSegmentedControlDefaults.textStyle()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, textStyle, density, measurer) {
        val maxLabelWidthPx = labels.maxOfOrNull { label ->
            measurer.measure(label, textStyle, maxLines = 1).size.width
        } ?: 0
        val labelDp = with(density) { maxLabelWidthPx.toDp() }
        // Match COUI defaults (min 52.dp segment, 12.dp * 2 labelPadding, 4.dp * 2 outer padding)
        // plus 6.dp per segment so CouiSegmentMotion leaves the `last == -1` equal-slot path with
        // positive headroom on every segment at non-integer densities.
        val segmentWidth = max(52.dp, labelDp + 24.dp) + 6.dp
        segmentWidth * labels.size + 8.dp
    }
}

/**
 * Floating top-bar [SegmentedControl] pairing live-backdrop frosted glass,
 * COUI contour glow + spotlight, and a subtle capsule elevation shadow.
 */
@Composable
fun FloatingSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: CouiBackdrop,
    dark: Boolean,
    modifier: Modifier = Modifier,
    maxWidth: Dp = Dp.Unspecified,
) {
    val shadowColor = Color.Black.copy(alpha = if (dark) 0.42f else 0.14f)
    val adaptiveWidth = rememberAdaptiveSegmentedControlWidth(labels)
    val widthModifier = if (maxWidth != Dp.Unspecified) {
        Modifier.width(adaptiveWidth.coerceAtMost(maxWidth)).widthIn(max = maxWidth)
    } else {
        Modifier.width(adaptiveWidth)
    }
    Box(
        modifier = modifier.shadow(
            elevation = if (dark) 8.dp else 6.dp,
            shape = CouiCapsuleShape,
            clip = false,
            ambientColor = shadowColor,
            spotColor = shadowColor,
        ),
    ) {
        SegmentedControl(
            modifier = widthModifier,
            labels = labels,
            selectedIndex = selectedIndex,
            onSelected = onSelected,
            backdrop = backdrop,
            dark = dark,
        )
    }
}
