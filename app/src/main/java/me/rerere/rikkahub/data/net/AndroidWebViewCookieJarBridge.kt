package me.rerere.rikkahub.data.net

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * CookieJar implementation that reads/writes cookies through
 * [android.webkit.CookieManager] so that authentication cookies set
 * by a system WebView (for example, a Cloudflare Access OTP login
 * flow) are sent on subsequent OkHttp requests to the same domain,
 * and OkHttp-set cookies are visible to any WebView that loads the
 * same URL.
 *
 * Android does not expose a public [java.net.CookieStore] for the
 * WebView cookie jar, so we use the
 * [android.webkit.CookieManager.getCookie]/[setCookie] string API
 * instead.
 */
class AndroidWebViewCookieJarBridge(
    private val cookieManager: CookieManager,
) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Cookies are flushed to the system jar by the WebView, but
        // OkHttp-set cookies also need to be persisted for other
        // OkHttp requests in the same session.
        for (cookie in cookies) {
            val headerValue = cookie.toSetCookieHeaderValue(url)
            cookieManager.setCookie(url.toString(), headerValue)
        }
        cookieManager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val headerValue = cookieManager.getCookie(url.toString()) ?: return emptyList()
        if (headerValue.isBlank()) return emptyList()
        return parseSetCookieHeaders(url, headerValue)
    }

    private fun Cookie.toSetCookieHeaderValue(url: HttpUrl): String {
        val sb = StringBuilder()
        sb.append(name).append("=").append(value)
        sb.append("; Domain=").append(url.host)
        sb.append("; Path=").append(path.ifBlank { "/" })
        if (url.isHttps) sb.append("; Secure")
        if (httpOnly) sb.append("; HttpOnly")
        if (persistent && expiresAt > 0L) {
            val maxAge = ((expiresAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            sb.append("; Max-Age=").append(maxAge)
        }
        return sb.toString()
    }

    private fun parseSetCookieHeaders(url: HttpUrl, headerValue: String): List<Cookie> {
        val results = ArrayList<Cookie>()
        // The cookie header value may contain multiple cookies
        // separated by "; " (note: the Set-Cookie header itself is
        // also semicolon-separated, but the cookie manager joins
        // them with "; " in the form returned by getCookie).
        for (raw in headerValue.split("; ")) {
            if (raw.isBlank()) continue
            // Try the simple "name=value" form first.
            val eq = raw.indexOf('=')
            if (eq <= 0) continue
            val name = raw.substring(0, eq).trim()
            val value = raw.substring(eq + 1).trim()
            val builder = Cookie.Builder()
                .name(name)
                .value(value)
                .domain(url.host)
                .path("/")
            val parsed = try {
                builder.build()
            } catch (_: IllegalArgumentException) {
                null
            }
            if (parsed != null) results.add(parsed)
        }
        return results
    }
}
