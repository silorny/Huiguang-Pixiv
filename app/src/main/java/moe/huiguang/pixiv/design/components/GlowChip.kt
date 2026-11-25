package moe.huiguang.pixiv.design.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.shape.CouiCapsuleShape

/**
 * 32dp capsule chip with the full 凝光 effect set.
 *
 * Restores `GlowSurface` (coloured FAB material, caustic shadow, contour edge,
 * touch-follow spotlight and press rebound, plus `whiteGlass` when unselected
 * over flat surfaces) and caches static chrome via [staticSurface] when seated
 * on flat backgrounds.
 */
@Composable
fun GlowChip(
    text: String,
    onClick: () -> Unit,
    backdrop: CouiBackdrop,
    dark: Boolean,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    whiteGlass: Boolean = false,
    blurRadius: Float = 0f,
    staticSurface: Boolean = true,
) {
    val colors = CouiPageDefaults.themeColors()
    val haptics = LocalHapticFeedback.current
    GlowSurface(
        backdrop = backdrop,
        dark = dark,
        modifier = modifier.height(32.dp),
        shape = CouiCapsuleShape,
        cornerWeight = 1f,
        preset = SpotlightPreset.ChipTab,
        blurRadius = blurRadius,
        whiteGlass = whiteGlass && !selected,
        colored = if (selected) colors.accent else null,
        cancelLightOnScroll = true,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        foregroundComposition = true,
        staticSurface = staticSurface,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = if (selected) Color.White else colors.title,
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
