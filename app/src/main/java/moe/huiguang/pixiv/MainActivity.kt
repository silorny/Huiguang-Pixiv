package moe.huiguang.pixiv

import android.content.Intent
import androidx.compose.runtime.Composable
import moe.huiguang.pixiv.ui.AppRoot

/**
 * The tab shell. Everything pushed on top of a tab is a real [PageActivity],
 * so this Activity never owns a back stack.
 */
class MainActivity : HuiguangActivity() {

    override fun shouldFinishEarlyOnCreate(): Boolean =
        !isTaskRoot &&
            intent?.hasCategory(Intent.CATEGORY_LAUNCHER) == true &&
            intent?.action == Intent.ACTION_MAIN

    @Composable
    override fun Page() {
        AppRoot((application as App).container)
    }
}
