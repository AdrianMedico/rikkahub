package me.rerere.rikkahub.ui.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import me.rerere.rikkahub.ui.theme.RikkahubTheme

/**
 * Generic WebView login activity for authentication flows that run
 * outside the app's own token system. The first use case is Cloudflare
 * Access OTP before the Cloudflare Tunnel: the WebView performs the
 * email-OTP flow, Cloudflare sets a `cf_clearance` cookie scoped to
 * the configured domain, and the activity finishes with RESULT_OK
 * once the cookie is present and the URL is no longer a Cloudflare
 * access challenge page.
 *
 * The system [CookieManager] is used for cookie storage. OkHttp is
 * wired to the same manager via [okhttp3.JavaNetCookieJar] in
 * [me.rerere.rikkahub.di.dataSourceModule], so any cookie set by this
 * WebView is sent on subsequent OkHttp requests to the same domain.
 *
 * The caller passes the login URL via [EXTRA_LOGIN_URL] and receives
 * [Intent] result data with [EXTRA_LOGIN_HOST] when the login is
 * complete (or [RESULT_CANCELED] if the user dismisses the activity).
 */
class CloudflareLoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loginUrl = intent.getStringExtra(EXTRA_LOGIN_URL)
        if (loginUrl.isNullOrBlank()) {
            finishWithCanceled("missing $EXTRA_LOGIN_URL")
            return
        }
        val targetHost = android.net.Uri.parse(loginUrl).host ?: ""
        setContent {
            RikkahubTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    CloudflareLoginScreen(
                        loginUrl = loginUrl,
                        targetHost = targetHost,
                        onSuccess = { finishWithSuccess() },
                        onCancel = { finishWithCanceled("user_cancelled") },
                    )
                }
            }
        }
    }

    private fun finishWithSuccess() {
        val data = Intent().apply {
            putExtra(EXTRA_LOGIN_HOST, android.net.Uri.parse(intent.getStringExtra(EXTRA_LOGIN_URL)).host)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    private fun finishWithCanceled(reason: String) {
        val data = Intent().apply { putExtra(EXTRA_LOGIN_CANCEL_REASON, reason) }
        setResult(RESULT_CANCELED, data)
        finish()
    }

    @Composable
    private fun CloudflareLoginScreen(
        loginUrl: String,
        targetHost: String,
        onSuccess: () -> Unit,
        onCancel: () -> Unit,
    ) {
        var progress by remember { mutableStateOf(0) }
        var pageTitle by remember { mutableStateOf("") }
        var statusText by remember { mutableStateOf<String?>(null) }
        Column(modifier = Modifier.fillMaxSize()) {
            // Status row
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Login",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Authenticate via $targetHost to grant RikkaHub access.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                pageTitle.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                statusText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        buildWebView(
                            loginUrl = loginUrl,
                            targetHost = targetHost,
                            onProgress = { progress = it },
                            onTitle = { pageTitle = it },
                            onStatus = { statusText = it },
                            onSuccess = onSuccess,
                            onCancel = onCancel,
                        )
                    },
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(
        loginUrl: String,
        targetHost: String,
        onProgress: (Int) -> Unit,
        onTitle: (String) -> Unit,
        onStatus: (String?) -> Unit,
        onSuccess: () -> Unit,
        onCancel: () -> Unit,
    ): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString = "RikkaHub-Android/${me.rerere.rikkahub.BuildConfig.VERSION_NAME}"
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgress(newProgress)
            }
        }
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                url?.let { onTitle(it) }
                onProgress(10)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                onProgress(100)
                url?.let { evaluateCompletion(it, targetHost, onStatus, onSuccess) }
            }
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // Keep navigation inside the WebView so cookies are
                // set on the system CookieManager for the right domain.
                return false
            }
        }
        setOnLongClickListener { v ->
            // Allow the user to cancel the login by long-pressing the WebView.
            v.performLongClick()
            true
        }
        // Persist cookies across app launches. Required for the
        // cf_clearance cookie to survive process death.
        CookieManager.getInstance().setAcceptCookie(true)
        loadUrl(loginUrl)
    }

    private fun WebView.evaluateCompletion(
        url: String,
        targetHost: String,
        onStatus: (String?) -> Unit,
        onSuccess: () -> Unit,
    ) {
        val current = android.net.Uri.parse(url)
        val host = current.host.orEmpty()
        val isCloudflareChallenge = host.endsWith(".cloudflareaccess.com") ||
            host == "cloudflareaccess.com" ||
            host.endsWith(".cloudflare.com") && current.path?.contains("login") == true
        val cookieManager = CookieManager.getInstance()
        val hasCfClearance = cookieManager.getCookie(url)?.contains("cf_clearance=") == true ||
            cookieManager.getCookie("https://$targetHost")?.contains("cf_clearance=") == true
        when {
            isCloudflareChallenge -> onStatus("Waiting for Cloudflare Access...")
            hasCfClearance -> {
                onStatus("Authenticated. Continuing...")
                onSuccess()
            }
            else -> {
                // Not a Cloudflare challenge and no clearance cookie
                // means the user reached a page that does not require
                // auth, or auth completed silently. Treat as success
                // when the host matches the target.
                if (host == targetHost) {
                    onStatus("Login flow completed.")
                    onSuccess()
                } else {
                    onStatus("Redirected to $host")
                }
            }
        }
    }

    companion object {
        const val EXTRA_LOGIN_URL = "login_url"
        const val EXTRA_LOGIN_HOST = "login_host"
        const val EXTRA_LOGIN_CANCEL_REASON = "cancel_reason"
    }
}
