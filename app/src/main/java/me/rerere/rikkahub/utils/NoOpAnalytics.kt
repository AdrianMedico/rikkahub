package me.rerere.rikkahub.utils

import android.os.Bundle

/**
 * No-op analytics surface for builds that do not depend on Google
 * services. Mirrors the public surface of FirebaseAnalytics so that
 * existing call sites (e.g. `analytics.logEvent(name, params)` in
 * ChatVM) compile and run unchanged.
 *
 * Intentionally records nothing. If you need analytics in a custom
 * build, replace this class with a real implementation rather than
 * adding tracking here.
 */
class NoOpAnalytics {
    fun logEvent(event: String, params: Bundle? = null) {
        // no-op
    }

    fun setUserProperty(name: String, value: String?) {
        // no-op
    }

    fun setUserId(id: String?) {
        // no-op
    }

    fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        // no-op
    }
}
