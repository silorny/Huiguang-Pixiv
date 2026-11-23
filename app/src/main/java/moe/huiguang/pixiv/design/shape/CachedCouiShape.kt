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
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Oplus G2 continuous-curvature `CouiShape` with process-wide caching of the
 * unit-corner curve samples so scrolling waterfalls and lists never recompute
 * `pow`/`sqrt` trig across frames or recycled cards.
 */
@Immutable
data class CachedCouiShape(
    val radius: Dp,
    val cornerWeight: Float = CouiCornerWeight,
) : Shape {
    init {
        require(radius.value.isFinite() && radius.value >= 0f)
        require(cornerWeight.isFinite() && cornerWeight >= 2f)
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = (radius.value * density.density).coerceIn(0f, size.minDimension.coerceAtLeast(0f) / 2f)
        if (r == 0f) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        val key = OutlineKey(size.width, size.height, r, cornerWeight)
        cachedOutlines[key]?.let { return it }

        val curveKey = CurveKey(ceil(r).toInt(), cornerWeight)
        val unitCoords = cachedUnitCurves[curveKey] ?: buildUnitCurve(r, cornerWeight).also {
            cachedUnitCurves.put(curveKey, it)
        }
        val segs = (unitCoords.size / 2) - 1
        val path = Path()
        path.moveTo(r, 0f)
        repeat(4) { corner ->
            val maxIdx = segs * 2
            for (index in 0..maxIdx) {
                val firstHalf = index <= segs
                val halfIndex = if (firstHalf) index else maxIdx - index
                val a = unitCoords[halfIndex * 2]
                val b = unitCoords[halfIndex * 2 + 1]
                val x = r * if (firstHalf) a else b
                val y = r * if (firstHalf) b else a
                when (corner) {
                    0 -> path.lineTo(size.width - r + x, r - y)
                    1 -> path.lineTo(size.width - r + y, size.height - r + x)
                    2 -> path.lineTo(r - x, size.height - r + y)
                    else -> path.lineTo(r - y, r - x)
                }
            }
        }
        path.close()
        return Outline.Generic(path).also { cachedOutlines.put(key, it) }
    }

    private data class CurveKey(val bucketRadiusPx: Int, val weight: Float)
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
        val cachedUnitCurves = LruCache<CurveKey, FloatArray>(24)
        val cachedOutlines = LruCache<OutlineKey, Outline>(128)
        val cachedPunchouts = LruCache<OutlineKey, Path>(128)

        fun buildUnitCurve(r: Float, cornerWeight: Float): FloatArray {
            val weight = cornerWeight.toDouble()
            val diagonal = 0.5.pow(1.0 / weight)
            val segments = ceil(sqrt(r * (weight - 1.0) * diagonal / (4.0 * 0.05))).toInt().coerceAtLeast(1)
            val coords = FloatArray((segments + 1) * 2)
            for (index in 0..segments) {
                val a = diagonal * index / segments
                val b = (1.0 - a.pow(weight)).pow(1.0 / weight)
                coords[index * 2] = a.toFloat()
                coords[index * 2 + 1] = b.toFloat()
            }
            return coords
        }
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
