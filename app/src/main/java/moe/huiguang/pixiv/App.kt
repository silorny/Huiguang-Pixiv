package moe.huiguang.pixiv

import android.app.Application
import coil3.SingletonImageLoader

/** Application entry point: builds the one and only [AppContainer]. */
class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Coil must fetch through the API client so every image request carries
        // the `Referer: https://www.pixiv.net/` header that i.pximg.net demands.
        SingletonImageLoader.setSafe { container.imageLoader }
    }
}
