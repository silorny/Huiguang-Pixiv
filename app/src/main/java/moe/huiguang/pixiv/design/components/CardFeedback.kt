package moe.huiguang.pixiv.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Lightweight [IndicationNodeFactory] for scrolling waterfall/list cards.
 *
 * Avoids per-card `collectIsPressedAsState` / `animateFloatAsState` /
 * `ViewTreeObserver` allocations during fast flings and paints the press
 * overlay via cached `drawOutline` directly inside `DrawModifierNode` (zero
 * `Outline.Generic` CPU clip masks and zero recompositions).
 */
@Composable
fun rememberCouiCardIndication(
    shape: Shape,
    dark: Boolean,
): IndicationNodeFactory = remember(shape, dark) {
    CouiCardIndicationFactory(shape, dark)
}

private data class CouiCardIndicationFactory(
    private val shape: Shape,
    private val dark: Boolean,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        CouiCardIndicationNode(interactionSource, shape, dark)
}

private class CouiCardIndicationNode(
    private val interactionSource: InteractionSource,
    private val shape: Shape,
    private val dark: Boolean,
) : androidx.compose.ui.Modifier.Node(), DrawModifierNode {

    private val pressAlpha = Animatable(0f)
    private var animJob: Job? = null
    private var cachedSize: androidx.compose.ui.geometry.Size = androidx.compose.ui.geometry.Size.Unspecified
    private var cachedDirection: LayoutDirection? = null
    private var cachedOutline: Outline? = null

    override fun onAttach() {
        coroutineScope.launch {
            var pressCount = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        pressCount++
                        animateTo(1f, durationMs = 60)
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        pressCount = (pressCount - 1).coerceAtLeast(0)
                        if (pressCount == 0) {
                            animateTo(0f, durationMs = 180)
                        }
                    }
                }
            }
        }
    }

    private fun animateTo(target: Float, durationMs: Int) {
        animJob?.cancel()
        animJob = coroutineScope.launch {
            pressAlpha.animateTo(target, tween(durationMs))
        }
    }

    override fun ContentDrawScope.draw() {
        val a = pressAlpha.value
        if (a > 0.001f) {
            val outline = if (cachedSize == size && cachedDirection == layoutDirection && cachedOutline != null) {
                cachedOutline!!
            } else {
                shape.createOutline(size, layoutDirection, this).also {
                    cachedSize = size
                    cachedDirection = layoutDirection
                    cachedOutline = it
                }
            }
            val baseColor = if (dark) Color(0x33FFFFFF) else Color(0x1F000000)
            drawOutline(outline, baseColor.copy(alpha = baseColor.alpha * a))
        }
        drawContent()
    }
}
