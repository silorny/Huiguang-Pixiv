package moe.huiguang.pixiv.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Floating tab header shared by 首页 / 搜寻 / 我的.
 *
 * Sits above the scrolling content: transparent at the top of the page, then
 * blurs in once the user scrolls. Uses a two-state animation (not a per-item
 * scroll offset) so the title never oscillates between sizes.
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
    val barAlpha by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = tween(200),
        label = "tabBar",
    )
    val titleSize by animateFloatAsState(
        targetValue = if (collapsed) collapsedSize.value else expandedSize.value,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "tabTitle",
    )
    TopAppBarSurface(
        modifier = Modifier.fillMaxWidth(),
        backdrop = backdrop,
        backgroundAlpha = barAlpha,
        gradientColor = colors.background,
    ) {
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
                style = TextStyle(
                    color = colors.title,
                    fontSize = titleSize.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(Modifier.width(12.dp))
            actions()
        }
    }
}
