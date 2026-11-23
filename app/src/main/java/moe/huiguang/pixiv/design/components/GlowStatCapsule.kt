package moe.huiguang.pixiv.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text

/** One value/label pair shown inside a [GlowStatCapsule]. */
data class StatValue(
    val value: String,
    val label: String,
    val onClick: (() -> Unit)? = null,
)

/**
 * Data capsule rendered as a real COUI floating surface.
 *
 * Unlike a flat fill, this adds the 凝光 set — white frosted material, caustic
 * glow, contour edge and a soft projection — with a divider-free row. The
 * material samples an opaque sibling backdrop (the page colour), so it stays
 * level and avoids a render-graph cycle with the scrolling content.
 */
@Composable
fun GlowStatCapsule(
    stats: List<StatValue>,
    backdrop: CouiBackdrop,
    dark: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 58.dp,
) {
    if (stats.isEmpty()) return
    val colors = CouiPageDefaults.themeColors()
    GlowSurface(
        backdrop = backdrop,
        dark = dark,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = CouiCapsuleShape,
        cornerWeight = 1f,
        interactive = false,
        foregroundComposition = true,
        whiteGlass = true,
        staticSurface = true,
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stats.forEach { stat ->
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CouiCapsuleShape)
                        .then(if (stat.onClick != null) Modifier.clickable(onClick = stat.onClick) else Modifier)
                        .padding(horizontal = 6.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(
                        text = stat.value,
                        style = TextStyle(
                            color = colors.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stat.label,
                        kind = CouiTextKind.ItemSummary,
                        color = colors.summary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
