package moe.huiguang.pixiv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Scaffold
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiTextStyle
import com.itos.coui.topappbar.TopAppBar
import com.itos.coui.topappbar.TopAppBarSurface
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.design.components.CircleIconButton

/**
 * A secondary page shell: top bar with a back action and a padded body.
 * Used by settings / history / bookmarks until P7 fills them in, and by the
 * login page wrapper.
 */
@Composable
fun SecondaryScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { BasicText(text = title, style = LocalCouiTextStyle.current) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        content(padding)
    }
}

@Composable
fun PlaceholderScreen(
    title: String,
    hint: String,
    onBack: () -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    SecondaryScaffold(title = title, onBack = onBack) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.background),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = hint, kind = CouiTextKind.ItemSummary, color = colors.summary)
        }
    }
}

@Composable
fun FloatingSecondaryTopBar(
    title: String,
    backdrop: CouiBackdrop,
    onBack: () -> Unit,
    afterTitle: (@Composable RowScope.() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = CouiPageDefaults.themeColors()
    val dark = CouiTheme.isDark
    TopAppBarSurface(
        modifier = Modifier.fillMaxWidth(),
        backdrop = backdrop,
        backgroundAlpha = 1f,
        gradientColor = colors.background,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = CouiIcons.Filled.Back,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                backdrop = backdrop,
                dark = dark,
                blurRadius = 20f,
            )
            Spacer(Modifier.width(12.dp))
            BasicText(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (afterTitle == null) Modifier.weight(1f) else Modifier.widthIn(max = 160.dp),
                style = rememberCouiTextStyle(CouiTextKind.TopBar).copy(color = colors.title),
            )
            if (afterTitle != null) {
                Spacer(Modifier.width(12.dp))
                afterTitle()
                Spacer(Modifier.weight(1f))
            }
            trailing()
        }
    }
}

@Composable
fun comingSoon(): String = stringResource(R.string.coming_soon)
