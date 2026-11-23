package moe.huiguang.pixiv.design.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.theme.CouiPresets
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults

/**
 * Circular floating button for top bars that sit over media.
 *
 * Uses the same floating material / shadow / contour / spotlight / press
 * motion as a COUI toolbar button, but samples the page's flat sibling
 * backdrop instead of the cover image. That keeps the circle one uniform tone
 * over any artwork while keeping the full 凝光 effect set.
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    backdrop: CouiBackdrop,
    dark: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    blurRadius: Float = 20f,
    staticSurface: Boolean = false,
) {
    val iconTint = if (dark) Color.White else CouiPresets.Light.onSurface
    GlowSurface(
        backdrop = backdrop,
        dark = dark,
        modifier = modifier.size(size),
        shape = CouiTopAppBarButtonDefaults.circleShape,
        cornerWeight = 2f,
        preset = SpotlightPreset.OpaqueSmall2,
        blurRadius = blurRadius,
        whiteGlass = true,
        onClick = onClick,
        foregroundComposition = true,
        staticSurface = staticSurface,
    ) {
        Image(
            imageVector = icon,
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(iconTint),
            modifier = Modifier.size(22.dp),
        )
    }
}
