package moe.huiguang.pixiv.ui.login

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog
import com.itos.coui.dialog.LoadingDialog
import com.itos.coui.layout.Scaffold
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiTextStyle
import com.itos.coui.topappbar.TopAppBar
import com.itos.coui.topappbar.TopAppBarButton
import kotlinx.coroutines.launch
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.store.SettingsStore
import moe.huiguang.pixiv.ui.common.BackButton

/**
 * Sign-in through Pixiv's own page.
 *
 * The app never sees the password: once the page has a session, the
 * `PHPSESSID` cookie is read from `CookieManager` and verified with
 * `/ajax/user/self` before it is persisted. An anonymous visitor also gets a
 * `PHPSESSID`, so the cookie alone proves nothing — that verification step is
 * the actual login test.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    api: PixivApi,
    settingsStore: SettingsStore,
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
) {
    val dark = CouiTheme.isDark
    val context = LocalContext.current
    val resources = LocalResources.current
    val windowBackdrop = rememberCouiWindowBackdrop()
    val scope = rememberCoroutineScope()
    var verifying by remember { mutableStateOf(false) }
    var loginSucceeded by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    val noCookie = stringResource(R.string.login_no_cookie)
    val noSession = stringResource(R.string.login_no_session)
    val verifyingText = stringResource(R.string.login_verifying)

    fun clearWebViewSession() {
        runCatching {
            webView?.apply {
                clearCache(true)
                clearHistory()
                clearFormData()
            }
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
            WebStorage.getInstance().deleteAllData()
        }
    }

    fun submit() {
        val cookie = CookieManager.getInstance().getCookie(SITE)
        if (cookie.isNullOrBlank() || !cookie.contains("PHPSESSID")) {
            problem = noCookie
            return
        }
        verifying = true
        scope.launch {
            val userId = runCatching { api.currentUserId(cookie) }.getOrNull()
            verifying = false
            if (userId.isNullOrBlank()) {
                problem = noSession
            } else {
                loginSucceeded = true
                settingsStore.saveSession(cookie, userId)
                // Keep the verified cookie only in Keystore-encrypted app storage,
                // clearing any plaintext/SQLite copy held inside WebView.
                clearWebViewSession()
                onLoggedIn()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            clearWebViewSession()
            webView?.destroy()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { BasicText(text = stringResource(R.string.login_title), style = LocalCouiTextStyle.current) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    TopAppBarButton(onClick = { submit() }, text = true) {
                        Text(
                            text = stringResource(R.string.login_done),
                            kind = CouiTextKind.ItemTitle,
                        )
                    }
                },
            )
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    this.settings.javaScriptEnabled = true
                    this.settings.domStorageEnabled = true
                    this.settings.allowContentAccess = false
                    this.settings.allowFileAccess = false
                    this.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    this.settings.safeBrowsingEnabled = true
                    this.settings.useWideViewPort = true
                    this.settings.loadWithOverviewMode = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val allowed = isAllowedLoginUrl(request.url)
                            if (!allowed && request.isForMainFrame) {
                                Toast.makeText(
                                    context,
                                    resources.getString(R.string.login_blocked_url),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            return !allowed
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            webView = view
                        }
                    }
                    loadUrl(LOGIN_URL)
                }.also { webView = it }
            },
        )
    }

    val dialogMaterial = if (windowBackdrop != null) CouiDialogDefaults.material(dark) else null
    LoadingDialog(
        visible = verifying,
        message = verifyingText,
        material = dialogMaterial,
        backdrop = windowBackdrop,
    )

    Dialog(
        visible = problem != null,
        onDismissRequest = { problem = null },
        title = stringResource(R.string.login_failed_title),
        message = problem.orEmpty(),
        material = dialogMaterial,
        backdrop = windowBackdrop,
        confirmAction = CouiDialogAction(
            text = stringResource(R.string.action_ok),
            onClick = { problem = null },
        ),
    )
}

private const val SITE = "https://www.pixiv.net"

private const val LOGIN_URL =
    "https://accounts.pixiv.net/login?lang=zh&source=pc&view_type=page&ref=wwwtop_accounts_index"

internal fun isAllowedLoginUrl(uri: Uri?): Boolean {
    if (uri == null || uri.scheme?.lowercase() != "https") return false
    val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
    return host == "pixiv.net" || host.endsWith(".pixiv.net") ||
        host == "pixiv.org" || host.endsWith(".pixiv.org") ||
        host == "accounts.google.com" ||
        host == "gstatic.com" || host.endsWith(".gstatic.com") ||
        host == "recaptcha.net" || host.endsWith(".recaptcha.net") ||
        host == "challenges.cloudflare.com" ||
        host == "appleid.apple.com" ||
        host == "api.twitter.com" || host == "twitter.com" || host == "x.com"
}
