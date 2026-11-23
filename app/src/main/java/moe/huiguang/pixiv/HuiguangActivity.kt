package moe.huiguang.pixiv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.itos.coui.menu.ProvideCouiMenuBackdrop
import moe.huiguang.pixiv.data.store.ThemeMode
import moe.huiguang.pixiv.design.HuiguangTheme

/**
 * Shared host for every Activity in the app: edge-to-edge window plus the COUI
 * theme.
 *
 * The theme follows [ThemeMode] from the shared settings store, so switching
 * light/dark updates every Activity without recreating them. Deliberately
 * contains no window-animation override and no back interception, which is what
 * lets the ROM own the task transitions and the predictive back animation.
 */
abstract class HuiguangActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        enableHighRefreshRate()
        val settings = (application as App).container.settings
        setContent {
            val state by settings.state.collectAsState()
            val dark = when (state.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            HuiguangTheme(
                darkTheme = dark,
                contourGlow = state.contourGlow,
            ) {
                ProvideCouiMenuBackdrop(isolateContent = false) { windowSource ->
                    Box(Modifier.fillMaxSize().then(windowSource)) {
                        Page()
                    }
                }
            }
        }
    }

    @Composable
    protected abstract fun Page()

    /** Requests the highest refresh-rate display mode matching the current resolution (e.g. 165Hz). */
    @Suppress("DEPRECATION")
    private fun enableHighRefreshRate() {
        runCatching {
            val activeDisplay = display ?: windowManager.defaultDisplay ?: return
            val current = activeDisplay.mode
            val best = activeDisplay.supportedModes
                .filter {
                    current == null ||
                        (it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight)
                }
                .maxByOrNull { it.refreshRate }
                ?: return
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = best.modeId
                preferredRefreshRate = best.refreshRate
            }
        }
    }
}
