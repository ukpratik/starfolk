// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.platform

import android.app.Activity
import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.google.android.play.core.review.ReviewManagerFactory
import io.github.ukpratik.starfolk.BuildConfig
import io.github.ukpratik.starfolk.analytics.AptabaseTransport
import io.github.ukpratik.starfolk.analytics.Transport
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Play flavour (ADR-011): opt-in analytics, Install Referrer, In-App Review. */
object Flavour {
    /** null when no Aptabase key is configured: the build then behaves like "sends nothing". */
    fun transportFactory(): (() -> Transport)? =
        BuildConfig.APTABASE_KEY.takeIf { it.isNotBlank() }?.let { key -> { AptabaseTransport(key) } }

    /**
     * The utm_source of the install, read once (US-018). Null if unavailable: no Play Store, an old one, a timeout or any
     * error. Never throws, because it runs during onboarding.
     */
    suspend fun installSource(context: Context): String? =
        runCatching { withTimeoutOrNull(5_000) { readReferrer(context) } }.getOrNull()

    private suspend fun readReferrer(context: Context): String? = suspendCancellableCoroutine { cont ->
        val client = InstallReferrerClient.newBuilder(context).build()
        cont.invokeOnCancellation { runCatching { client.endConnection() } }
        client.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(code: Int) {
                val source = runCatching {
                    if (code == InstallReferrerClient.InstallReferrerResponse.OK) {
                        client.installReferrer.installReferrer.split('&').firstOrNull { it.startsWith("utm_source=") }?.substringAfter('=')
                    } else null
                }.getOrNull()
                runCatching { client.endConnection() }
                if (cont.isActive) cont.resume(source)
            }
            override fun onInstallReferrerServiceDisconnected() { if (cont.isActive) cont.resume(null) }
        })
    }

    /** In-App Review (US-025); Play decides whether it actually shows. */
    fun askForReview(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnSuccessListener { info -> manager.launchReviewFlow(activity, info) }
    }
}
