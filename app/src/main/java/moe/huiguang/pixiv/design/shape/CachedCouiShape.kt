package moe.huiguang.pixiv.design.shape

import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.itos.coui.shape.CouiCornerWeight
import com.itos.coui.shape.CouiShape

/**
 * Caching wrapper around [CouiShape] (Oplus G2 continuous-curvature outline)
 * plus a corner punchout cache so scrolling waterfalls, lists, and [g2CornerMask]
 * surfaces reuse computed outlines and paths without recomputing geometry each frame.
 */
@Immutable
data class CachedCouiShape(
    val radius: Dp,
    val cornerWeight: Float = CouiCornerWeight,
) : Shape {
    private val delegate = CouiShape(radius = radius, cornerWeight = cornerWeight)

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (!size.width.isFinite() || !size.height.isFinite() || size.minDimension <= 0f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width.coerceAtLeast(0f), size.height.coerceAtLeast(0f)))
        }
        val r = (radius.value * density.density).coerceIn(0f, size.minDimension / 2f)
        if (r == 0f) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        val key = OutlineKey(size.width, size.height, r, cornerWeight)
        cachedOutlines[key]?.let { return it }
        return delegate.createOutline(size, layoutDirection, density).also { outline ->
            cachedOutlines.put(key, outline)
        }
    }

    private data class OutlineKey(val width: Float, val height: Float, val radiusPx: Float, val weight: Float)

    internal fun getOrBuildPunchout(size: Size, layoutDirection: LayoutDirection, density: Density): Path? {
        val r = (radius.value * density.density).coerceIn(0f, size.minDimension.coerceAtLeast(0f) / 2f)
        if (r == 0f) return null
        val key = OutlineKey(size.width, size.height, r, cornerWeight)
        cachedPunchouts[key]?.let { return it }
        val outline = createOutline(size, layoutDirection, density) as? Outline.Generic ?: return null
        val boundsPath = Path().apply {
            addRect(Rect(-0.5f, -0.5f, size.width + 0.5f, size.height + 0.5f))
        }
        return Path().apply {
            op(boundsPath, outline.path, PathOperation.Difference)
        }.also { cachedPunchouts.put(key, it) }
    }

    private companion object {
        val cachedOutlines = LruCache<OutlineKey, Outline>(256)
        val cachedPunchouts = LruCache<OutlineKey, Path>(256)
    }
}

/**
 * Draws the four outer corner wedges outside [shape] using [maskColor], giving
 * images and child content exact G2 continuous-curvature corners against a solid
 * card surface without triggering Skia's CPU `Threaded SW Clip Mask Render`.
 */
fun Modifier.g2CornerMask(
    shape: CachedCouiShape,
    maskColor: Color,
): Modifier = drawWithCache {
    val punchout = if (size.minDimension > 0f) {
        shape.getOrBuildPunchout(size, layoutDirection, this)
    } else {
        null
    }
    onDrawWithContent {
        drawContent()
        if (punchout != null) {
            drawPath(punchout, maskColor)
        }
    }
}
