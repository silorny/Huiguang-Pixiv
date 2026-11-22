package moe.huiguang.pixiv.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.theme.CouiPresets
import com.itos.coui.theme.CouiTheme

/**
 * 绘光的主题入口。
 *
 * 单点决定深浅模式与凝光轮廓光开关；强调色固定使用 COUI 原生蓝。
 * 除这里以外，界面不得再包 MaterialTheme 或自行构造色板。
 */
@Composable
fun HuiguangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    contourGlow: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) CouiPresets.Dark else CouiPresets.Light
    CompositionLocalProvider(LocalCouiContourGlowEnabled provides contourGlow) {
        CouiTheme(
            darkTheme = darkTheme,
            colorScheme = scheme,
            content = content,
        )
    }
}
