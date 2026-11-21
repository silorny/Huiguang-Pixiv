package moe.huiguang.pixiv.design.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import moe.huiguang.pixiv.R

/**
 * Image with a visible failure state: a tapped area that re-requests the URL
 * instead of leaving a silent grey rectangle.
 *
 * Uses [rememberAsyncImagePainter] + [Image] (instead of `SubcomposeAsyncImage`)
 * to avoid `SubcomposeLayout` overhead inside scrolling grids and lists.
 */
@Composable
fun RetryableImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    darkPlaceholder: Boolean = false,
) {
    val colors = CouiPageDefaults.themeColors()
    var attempt by remember(url) { mutableIntStateOf(0) }
    val placeholder = if (darkPlaceholder) {
        Color(0xFF121212)
    } else {
        colors.background.copy(alpha = 0.5f)
    }

    key(attempt) {
        val painter = rememberAsyncImagePainter(model = url)
        val state by painter.state.collectAsState()
        Box(modifier = modifier.background(placeholder)) {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
            if (state is AsyncImagePainter.State.Error) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(placeholder)
                        .clickable { attempt++ },
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
}
