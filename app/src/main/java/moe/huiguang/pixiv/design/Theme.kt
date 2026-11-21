package moe.huiguang.pixiv.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.itos.coui.theme.CouiPresets
import com.itos.coui.theme.CouiTheme

/**
 * 绘光的主题入口。
 *
 * 单点决定深浅模式与品牌色：P1 跟随系统、强调色用 COUI 原生蓝；
 * P8 会把这里接到设置项（并加入品牌色候选）。除这里以外，界面不得再包
 * MaterialTheme 或自行构造色板。
 */
@Composable
fun HuiguangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) CouiPresets.Dark else CouiPresets.Light
    CouiTheme(
        darkTheme = darkTheme,
        colorScheme = scheme,
        content = content,
    )
}
