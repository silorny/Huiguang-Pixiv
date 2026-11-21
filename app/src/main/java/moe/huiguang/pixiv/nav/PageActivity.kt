package moe.huiguang.pixiv.nav

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import moe.huiguang.pixiv.App
import moe.huiguang.pixiv.HuiguangActivity
import moe.huiguang.pixiv.ui.PageContent

/**
 * Hosts every secondary page, exactly like the COUI demo's
 * `DemoSecondaryActivity`: the page identity travels in an Intent extra, the
 * page itself is the Activity body, and `finish()` is the back action.
 *
 * The Activity stack is the back stack. Nothing here intercepts back, so the
 * ROM plays its predictive back animation when the gesture starts.
 */
class PageActivity : HuiguangActivity() {

    private var route: Route? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val decoded = RouteCodec.decode(intent.getStringExtra(EXTRA_ROUTE).orEmpty())
        if (decoded == null || !Route.isPage(decoded)) {
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        route = decoded
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun Page() {
        val current = route ?: return
        PageContent(
            container = (application as App).container,
            route = current,
            onOpen = { next -> open(this, next) },
            onBack = { finish() },
        )
    }

    companion object {

        private const val EXTRA_ROUTE = "moe.huiguang.pixiv.ROUTE"

        /** Starts [route] as a new Activity in the current task. */
        fun open(context: Context, route: Route) {
            require(Route.isPage(route)) { "tab routes belong to MainActivity: $route" }
            val intent = Intent(context, PageActivity::class.java)
                .putExtra(EXTRA_ROUTE, RouteCodec.encode(route))
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
