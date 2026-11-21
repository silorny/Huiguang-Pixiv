package moe.huiguang.pixiv.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.theme.CouiTheme

/**
 * Chip-tab sized tag pill (32dp capsule, 14sp medium label).
 *
 * COUI's `ChipTab` is a backdrop-driven glass component; tags live inside the
 * scrolling content that feeds the page backdrop, and using the real chip there
 * makes the render graph cyclic (hwui stack overflow). This keeps the same
 * geometry and imitates the glass look with a soft shadow, a white-to-grey
 * vertical gradient and a light edge highlight, plus a press scale.
 */
@Composable
fun TagChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val colors = CouiPageDefaults.themeColors()
    val dark = CouiTheme.isDark
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "chipPress",
    )

    val top = when {
        selected -> colors.accent
        dark -> Color(0xFF2A2A2C)
        else -> Color.White
    }
    val bottom = when {
        selected -> colors.accent
        dark -> Color(0xFF1E1E20)
        else -> Color(0xFFF1F2F4)
    }

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 6.dp,
                shape = CouiCapsuleShape,
                clip = false,
                ambientColor = Color(0x14000000),
                spotColor = Color(0x26000000),
            )
            .clip(CouiCapsuleShape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(
                width = 0.5.dp,
                color = if (selected) {
                    Color.Transparent
                } else if (dark) {
                    Color(0x1FFFFFFF)
                } else {
                    Color(0xCCFFFFFF)
                },
                shape = CouiCapsuleShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .height(32.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = if (selected) Color.White else colors.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
