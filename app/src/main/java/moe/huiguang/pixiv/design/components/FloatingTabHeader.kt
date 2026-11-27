package moe.huiguang.pixiv.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.topappbar.TopAppBarSurface

/**
 * Floating tab header shared across the primary tabs (首页 / 搜寻 / 我的).
 *
 * Uses the exact same typography, 64.dp row geometry, GPU scale transform
 * (`30.sp` -> `20.sp`), and spring curve as Home so switching bottom tabs
 * never shifts the top-left title position, size, or collapse animation.
 */
@Composable
fun FloatingTabHeader(
    title: String,
    backdrop: CouiBackdrop,
    collapsed: Boolean,
    statusTop: Dp,
    expandedSize: TextUnit = 30.sp,
    collapsedSize: TextUnit = 20.sp,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = CouiPageDefaults.themeColors()
    val collapseProgress by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 460f),
        label = "tabHeaderCollapse",
    )
    val baseTitleStyle = remember(colors.title, expandedSize) {
        TextStyle(
            color = colors.title,
            fontSize = expandedSize,
            fontWeight = FontWeight.SemiBold,
        )
    }
    val targetScale = (collapsedSize.value / expandedSize.value).coerceIn(0.4f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {},
    ) {
        val barAlpha = collapseProgress.coerceIn(0f, 1f)
        if (barAlpha > 0.001f) {
            TopAppBarSurface(
                modifier = Modifier.matchParentSize(),
                backdrop = backdrop,
                backgroundAlpha = barAlpha,
                gradientColor = colors.background,
            ) {
                Spacer(Modifier.fillMaxSize())
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = statusTop)
                .height(64.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = title,
                style = baseTitleStyle,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    val s = 1f + (targetScale - 1f) * collapseProgress.coerceIn(0f, 1f)
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
            )
            Spacer(Modifier.width(12.dp))
            actions()
        }
    }
}
