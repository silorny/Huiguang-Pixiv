package moe.huiguang.pixiv.design.components

import android.graphics.BlendMode
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiFabFeedback
import com.itos.coui.effect.couiFloatingSurface
import com.itos.coui.effect.couiToolbarFeedback
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.floatingactionbutton.CouiFabDefaults
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.couiTouchMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.motion.rememberCouiTouchMotion
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults

/**
 * Floating "凝光" surface used by the custom chrome this app owns: the search
 * pill, the mode chips, the detail top-bar buttons and the viewer chrome.
 *
 * It assembles the same four effects the COUI components use:
 *  - the ordinary shadow + caustic from `CouiTopAppBarButtonDefaults`,
 *  - the floating material sampled from an opaque sibling backdrop,
 *  - the contour edge and the touch-follow spotlight,
 *  - the COUI touch / press motion with its spring rebound.
 *
 * [backdrop] must be an opaque **sibling** source (never a parent of this
 * node), otherwise the render graph becomes cyclic. Screens build one from the
 * flat page colour; because the material only needs a tone, it also keeps the
 * buttons uniform instead of taking on the cover image's colours.
 */
@Composable
fun GlowSurface(
    backdrop: CouiBackdrop,
    dark: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = CouiCapsuleShape,
    cornerWeight: Float = 1f,
    preset: SpotlightPreset = SpotlightPreset.OpaqueSmall2,
    search: Boolean = false,
    /** Pixel radius for the sampled backdrop; 0 keeps the preset (top-bar) blur. */
    blurRadius: Float = 0f,
    /** Light theme only: white frosted glass instead of the grey toolbar tint. */
    whiteGlass: Boolean = false,
    /** Non-null paints a coloured FAB material (e.g. the selected mode chip). */
    colored: Color? = null,
    interactive: Boolean = true,
    cancelLightOnScroll: Boolean = false,
    onClick: (() -> Unit)? = null,
    foregroundComposition: Boolean = true,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val contour = LocalCouiContourGlowEnabled.current
    val themeTint = CouiTheme.materialTintFor(dark)
    val renderer = remember { CouiFloatingRenderer.standalone() }
    val contentLayer = rememberGraphicsLayer()
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    // Wide surfaces are capsules, square ones are circles; this also drives the
    // material's stroke type the same way TopAppBarButton decides it.
    val capsule = bounds.width != bounds.height
    val material = remember(dark, capsule, search, colored, themeTint, density, blurRadius, whiteGlass) {
        when {
            whiteGlass && !dark -> {
                // Match the white glass of the standard COUI top bar: an almost
                // opaque white frost over the blurred backdrop, not a grey tint.
                CouiTopAppBarButtonDefaults.material(false, capsule, search = search).copy(
                    firstColor = Color(0xCCFFFFFF),
                    firstBlendMode = BlendMode.SRC_OVER,
                    secondColor = Color(0x33FFFFFF),
                    secondBlendMode = BlendMode.OVERLAY,
                    blurRadius = if (blurRadius > 0f) blurRadius else 20f,
                    edgeAlpha = 0.7f,
                    innerGlowAlpha = 0.12f,
                    index = 2,
                    extension = with(density) { 4.dp.roundToPx() },
                    themeTint = Color.Transparent,
                )
            }

            colored != null -> {
                val base = CouiFabDefaults.material(
                    colored,
                    dark,
                    extension = with(density) { 4.dp.roundToPx() },
                )
                if (blurRadius > 0f) base.copy(blurRadius = blurRadius) else base
            }

            else -> {
                CouiTopAppBarButtonDefaults.material(dark, capsule, search = search).copy(
                    index = 2,
                    extension = with(density) { 4.dp.roundToPx() },
                    themeTint = themeTint,
                    blurRadius = if (blurRadius > 0f) blurRadius else 0f,
                )
            }
        }
    }
    val shadow = remember(dark, capsule, search, colored, density, contour) {
        if (colored != null) {
            CouiFabDefaults.shadow(colored, density.density, contour)
        } else {
            CouiTopAppBarButtonDefaults.shadow(
                dark = dark,
                capsule = capsule,
                density = density.density,
                contour = contour,
                search = search,
            )
        }
    }
    val motion = rememberCouiTouchMotion(interactive)
    val press = rememberCouiPressMotion()
    val interaction = remember { MutableInteractionSource() }
    val (lightInput, drawLight) = rememberCouiSpotlight(
        interactive = interactive,
        preset = preset,
        dark = dark,
        cancelOnConsumedMove = cancelLightOnScroll,
    )
    val feedback = if (colored != null) {
        Modifier.couiFabFeedback(dark, interaction)
    } else {
        Modifier.couiToolbarFeedback(dark, interaction, interactive)
    }

    BoxWithConstraints(
        modifier
            .onSizeChanged { bounds = it }
            .couiFloatingSurface(
                backdrop = backdrop,
                renderer = renderer,
                material = material,
                shadow = shadow,
                shape = shape,
                contentLayer = contentLayer,
                contourGlowEnabled = contour,
                foregroundComposition = foregroundComposition,
            ) {
                CouiFloatingSurface(
                    bounds = Rect(
                        Offset(motion.translationX.value, motion.translationY.value),
                        Size(bounds.width.toFloat(), bounds.height.toFloat()),
                    ),
                    scaleX = motion.scaleX.value + press.scale.value - 1f,
                    scaleY = motion.scaleY.value + press.scale.value - 1f,
                    cornerWeight = cornerWeight,
                )
            },
    ) {
        // A plain Box measures children with the minimum constraints zeroed, so
        // a 22dp icon inside a 40dp circle would be recorded at the top-left.
        // Fill whichever axis the caller fixed, and let the other wrap.
        val fillWidth = constraints.hasFixedWidth
        val fillHeight = constraints.hasFixedHeight
        Box(
            Modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
                .graphicsLayer {
                    translationX = motion.translationX.value
                    translationY = motion.translationY.value
                    scaleX = motion.scaleX.value + press.scale.value - 1f
                    scaleY = motion.scaleY.value + press.scale.value - 1f
                }
                .couiTouchMotion(motion, interactive)
                .couiPressMotion(press, interactive)
                .then(lightInput)
                .drawWithContent { contentLayer.record { this@drawWithContent.drawContent() } }
                .clip(shape)
                .drawBehind { drawLight?.invoke(this) }
                .then(feedback)
                .then(
                    if (onClick != null && interactive) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}

/**
 * Floating chrome follows the surface behind it, not the app theme: the viewer
 * sits on a black canvas even in light mode, so its chrome must use the dark
 * material.
 */
fun isDarkBackdrop(color: Color): Boolean = color.luminance() < 0.5f
