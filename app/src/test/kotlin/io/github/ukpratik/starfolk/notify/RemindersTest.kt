// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.notify

import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.starfolk.StarfolkApp
import io.github.ukpratik.starfolk.core.content.TrackId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** ADR-012 / NFR-011 / US-006: neutral lock-screen text, Shelter-safe Spark, idempotent Done from the notification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemindersTest {
    private val app = ApplicationProvider.getApplicationContext<StarfolkApp>()
    private val c get() = app.container
    private val nm get() = app.getSystemService(NotificationManager::class.java)

    @org.junit.Before fun setUp() {
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun broadcast(intent: Intent) {
        app.sendBroadcast(intent)
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test fun `the daily Spark is private on the lock screen and neutral during a Shelter track`() = runBlocking {
        c.shelter.start(TrackId.BREAKUP)
        val result = TestListenableWorkerBuilder<DailySparkWorker>(app).build().doWork()
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        val n = shadowOf(nm).allNotifications.single()
        assertThat(n.visibility).isEqualTo(NotificationCompat.VISIBILITY_PRIVATE)
        assertThat(n.publicVersion.extras.getCharSequence("android.text").toString()).isEqualTo("A quest is waiting")
        assertThat(n.extras.getCharSequence("android.text").toString()).isEqualTo("A small step is waiting for you.")
    }

    @Test fun `Done from the notification is idempotent, and a stale day is ignored`() = runBlocking {
        val pick = c.quests.ensureToday()
        val today = c.appDayClock.today().epochDay
        fun done(day: Long) = Intent(app, DoneReceiver::class.java).setAction(DoneReceiver.ACTION_DONE)
            .putExtra(DoneReceiver.EXTRA_QUEST, pick.questId).putExtra(DoneReceiver.EXTRA_DAY, day)
        broadcast(done(today - 1))
        Thread.sleep(500)
        assertThat(c.quests.completions().first()).isEmpty()
        broadcast(done(today))
        broadcast(done(today))
        Thread.sleep(800)
        val all = c.quests.completions().first()
        assertThat(all).hasSize(1)
        assertThat(all.single().source).isEqualTo("notification")
    }

    @Test fun `a commit reminder from another app-day never posts`() = runBlocking {
        val worker = TestListenableWorkerBuilder<CommitReminderWorker>(app)
            .setInputData(workDataOf(DoneReceiver.EXTRA_QUEST to "q-connect-0001", DoneReceiver.EXTRA_DAY to 1L)).build()
        worker.doWork()
        assertThat(shadowOf(nm).allNotifications).isEmpty()
    }
}
