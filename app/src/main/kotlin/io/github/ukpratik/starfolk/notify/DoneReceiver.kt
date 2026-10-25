// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import io.github.ukpratik.starfolk.StarfolkApp
import io.github.ukpratik.starfolk.core.metrics.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Done / Snooze from the commit notification (US-006, ADR-012): goAsync + an idempotent completion, no trampoline
 * activity. Actions from a previous app-day are ignored.
 */
class DoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val questId = intent.getStringExtra(EXTRA_QUEST) ?: return
        val day = intent.getLongExtra(EXTRA_DAY, -1)
        // Null only outside a real broadcast (e.g. a direct call); the work still runs.
        val pending: PendingResult? = goAsync()
        scope.launch {
            try {
                val c = (context.applicationContext as StarfolkApp).container
                NotificationManagerCompat.from(context).cancel(Reminders.ID_COMMIT)
                if (day != c.appDayClock.today().epochDay) return@launch
                val quest = c.content.bundle().quests.firstOrNull { it.id == questId } ?: return@launch
                when (intent.action) {
                    ACTION_DONE -> {
                        c.quests.complete(quest, source = "notification")
                        c.analytics.track(Event.QuestCompleted(quest.realm.name, quest.level.name, "notification"))
                    }
                    ACTION_SNOOZE -> Reminders.scheduleCommit(context, questId, day, System.currentTimeMillis() + 60 * 60 * 1000L)
                }
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "io.github.ukpratik.starfolk.DONE"
        const val ACTION_SNOOZE = "io.github.ukpratik.starfolk.SNOOZE"
        const val EXTRA_QUEST = "quest"
        const val EXTRA_DAY = "app_day"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun intent(context: Context, action: String, questId: String, day: Long): PendingIntent = PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, DoneReceiver::class.java).setAction(action).putExtra(EXTRA_QUEST, questId).putExtra(EXTRA_DAY, day),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
