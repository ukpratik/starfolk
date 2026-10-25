// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.analytics

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.ukpratik.starfolk.StarfolkApp
import java.util.concurrent.TimeUnit

/** Sends queued events when there's a network (ADR-006). Only scheduled after consent. */
class FlushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val analytics = (applicationContext as StarfolkApp).container.analytics
        return if (analytics.flush()) Result.success() else Result.retry()
    }

    companion object {
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<FlushWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(2, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("analytics-flush", ExistingWorkPolicy.KEEP, request)
        }
    }
}
