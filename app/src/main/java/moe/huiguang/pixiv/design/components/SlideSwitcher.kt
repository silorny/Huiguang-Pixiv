package moe.huiguang.pixiv.design.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Horizontal switch animation for a segmented control.
 *
 * Forward switches slide in from the right and push the old content left;
 * switching back mirrors it. `index` is the selected segment.
 */
@Composable
fun SlideSwitcher(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    AnimatedContent(
        targetState = index,
        modifier = modifier,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            val duration = 280
            (
                slideInHorizontally(tween(duration)) { it / 3 * dir } +
                    fadeIn(tween(duration / 2))
                ) togetherWith (
                slideOutHorizontally(tween(duration)) { -it / 3 * dir } +
                    fadeOut(tween(duration / 2))
                )
        },
        label = "segmentContent",
    ) { selected ->
        content(selected)
    }
}
