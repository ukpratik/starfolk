// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.ukpratik.starfolk.MainActivity
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.StarfolkApp
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Reminders (US-005/010, ADR-012, NFR-011): one-time WorkManager jobs, re-enqueued after each fire (no exact alarms).
 * At most two a day: the daily Spark and one commit reminder. VISIBILITY_PRIVATE with a neutral public version; the text
 * never mentions the Shelter, a breakup or a health condition.
 */
object Reminders {
    const val CHANNEL_DAILY = "daily"
    const val CHANNEL_COMMIT = "commit"
    private const val WORK_DAILY = "daily-spark"
    private const val WORK_COMMIT = "commit-reminder"
    const val ID_DAILY = 1
    const val ID_COMMIT = 2

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL_DAILY, context.getString(R.string.channel_daily), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_COMMIT, context.getString(R.string.channel_commit), NotificationManager.IMPORTANCE_HIGH))
    }

    fun canPost(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Daily Spark at [time] ("HH:mm"), or cancel when null. */
    fun scheduleDaily(context: Context, time: String?) {
        val wm = WorkManager.getInstance(context)
        if (time == null) { wm.cancelUniqueWork(WORK_DAILY); return }
        val (h, m) = time.split(':').map { it.toInt() }
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        var next = now.with(LocalTime.of(h, m))
        if (!next.isAfter(now)) next = next.plusDays(1)
        wm.enqueueUniqueWork(
            WORK_DAILY, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DailySparkWorker>().setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS).build(),
        )
    }

    /** One commit reminder per app-day, replaced by a newer commit or a snooze. */
    fun scheduleCommit(context: Context, questId: String, appDay: Long, atMillis: Long) {
        val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0)
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_COMMIT, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CommitReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(DoneReceiver.EXTRA_QUEST to questId, DoneReceiver.EXTRA_DAY to appDay)).build(),
        )
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWork()
        NotificationManagerCompat.from(context).cancelAll()
    }

    internal fun base(context: Context, channel: String, title: String, text: String): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val public = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(context.getString(R.string.app_name)).setContentText(context.getString(R.string.notif_public)).build()
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setContentIntent(open).setAutoCancel(true)
    }

    @android.annotation.SuppressLint("MissingPermission") // checked by canPost()
    internal fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (canPost(context)) NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}

/** The daily Spark (US-010). In a Shelter track it says only "A small step is waiting for you." */
class DailySparkWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as StarfolkApp).container
        val settings = c.settings.current()
        val text = if (c.shelter.active() != null) applicationContext.getString(R.string.notif_shelter) else {
            val sparks = c.content.bundle().sparks
            sparks[Math.floorMod(c.appDayClock.today().epochDay, sparks.size.toLong()).toInt()].text
        }
        Reminders.post(applicationContext, Reminders.ID_DAILY, Reminders.base(applicationContext, Reminders.CHANNEL_DAILY, applicationContext.getString(R.string.app_name), text))
        Reminders.scheduleDaily(applicationContext, settings.reminderTime) // re-enqueue tomorrow's
        return Result.success()
    }
}

/** "Your quest: …" at the committed time, with Done and Snooze 1 h (US-005/006). */
class CommitReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as StarfolkApp).container
        val questId = inputData.getString(DoneReceiver.EXTRA_QUEST) ?: return Result.success()
        val day = inputData.getLong(DoneReceiver.EXTRA_DAY, -1)
        if (day != c.appDayClock.today().epochDay) return Result.success() // a stale reminder from another app-day
        val quest = c.content.bundle().quests.firstOrNull { it.id == questId } ?: return Result.success()
        val builder = Reminders.base(applicationContext, Reminders.CHANNEL_COMMIT, applicationContext.getString(R.string.notif_commit_title), quest.title)
            .addAction(0, applicationContext.getString(R.string.notif_done), DoneReceiver.intent(applicationContext, DoneReceiver.ACTION_DONE, questId, day))
            .addAction(0, applicationContext.getString(R.string.notif_snooze), DoneReceiver.intent(applicationContext, DoneReceiver.ACTION_SNOOZE, questId, day))
        Reminders.post(applicationContext, Reminders.ID_COMMIT, builder)
        return Result.success()
    }
}
