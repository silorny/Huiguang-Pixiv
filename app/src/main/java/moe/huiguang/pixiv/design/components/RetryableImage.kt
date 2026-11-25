package moe.huiguang.pixiv.design.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import coil3.size.Precision
import coil3.size.Size
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import moe.huiguang.pixiv.R

/**
 * Image with a visible failure state: a tapped area that re-requests the URL
 * instead of leaving a silent grey rectangle.
 *
 * Uses [rememberAsyncImagePainter] + [Image] (instead of `SubcomposeAsyncImage`)
 * to avoid `SubcomposeLayout` overhead inside scrolling grids and lists, and
 * tracks only error transitions via `onState` so `Empty -> Loading -> Success`
 * never triggers extra recompositions.
 */
@Composable
fun RetryableImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    darkPlaceholder: Boolean = false,
    constrainToBounds: Boolean = true,
    allowRgb565: Boolean = false,
    maxDecodeSizePx: Int = 0,
) {
    val context = LocalContext.current
    val colors = CouiPageDefaults.themeColors()
    var attempt by remember(url) { mutableIntStateOf(0) }
    var isError by remember(url, attempt) { mutableStateOf(false) }
    val sizeResolver = if (constrainToBounds && maxDecodeSizePx <= 0) rememberConstraintsSizeResolver() else null
    val request = remember(context, url, attempt, sizeResolver, allowRgb565, maxDecodeSizePx) {
        ImageRequest.Builder(context)
            .data(url)
            .allowRgb565(allowRgb565)
            .apply {
                when {
                    maxDecodeSizePx > 0 -> {
                        size(Size(maxDecodeSizePx, maxDecodeSizePx))
                        precision(Precision.INEXACT)
                    }
                    sizeResolver != null -> {
                        size(sizeResolver)
                        precision(Precision.INEXACT)
                    }
                }
            }
            .build()
    }
    val placeholder = if (darkPlaceholder) {
        Color(0xFF121212)
    } else {
        colors.background.copy(alpha = 0.5f)
    }

    val painter = rememberAsyncImagePainter(
        model = request,
        contentScale = contentScale,
        onState = { state ->
            val nextError = state is AsyncImagePainter.State.Error
            if (isError != nextError) isError = nextError
        },
    )
    Box(
        modifier = modifier
            .let { if (sizeResolver != null) it.then(sizeResolver) else it }
            .background(placeholder),
    ) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
        if (isError) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(placeholder)
                    .clickable {
                        isError = false
                        attempt++
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.image_retry),
                    kind = CouiTextKind.ItemSummary,
                    color = if (darkPlaceholder) {
                        Color(0xFFBBBBBB)
                    } else {
                        colors.summary
                    },
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}
