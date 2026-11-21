package moe.huiguang.pixiv.ui.common

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import com.itos.coui.icons.CouiIcons
import com.itos.coui.theme.LocalCouiContentColor
import com.itos.coui.topappbar.TopAppBarButton
import moe.huiguang.pixiv.R

/** The one back affordance used by every secondary page. */
@Composable
fun BackButton(onClick: () -> Unit) {
    val icon = CouiIcons.Filled.Back
    val tint = LocalCouiContentColor.current
    TopAppBarButton(onClick = onClick) {
        Image(
            imageVector = icon,
            contentDescription = stringResource(R.string.action_back),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}
