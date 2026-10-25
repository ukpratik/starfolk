// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.metrics

import io.github.ukpratik.starfolk.core.time.AppDay

/**
 * The complete list of anonymous events (PRD §10, US-020, D-008). Props are typed enums or small counts, so free text
 * (moods, notes, names, letters, WHO-5 answers, country) can't be constructed. This list is shown verbatim in Settings
 * ("See exactly what's sent") and in the privacy policy.
 */
sealed class Event(val name: String, val props: Map<String, String> = emptyMap()) {
    data class FirstOpen(val entryPath: String) : Event("first_open", mapOf("entry_path" to entryPath.lowercase()))
    data class OnboardingCompleted(val realm: String, val energy: String) : Event("onboarding_completed", mapOf("realm" to realm.lowercase(), "energy" to energy.lowercase()))
    data class QuestCompleted(val realm: String, val level: String, val source: String) :
        Event("quest_completed", mapOf("realm" to realm.lowercase(), "level" to level.lowercase(), "source" to source))
    data class QuestShared(val realm: String) : Event("quest_shared", mapOf("realm" to realm.lowercase()))
    data class ShelterTrackStarted(val track: String) : Event("shelter_track_started", mapOf("track" to track.lowercase()))
    data class ShelterDayDone(val track: String, val day: Int) : Event("shelter_day_done", mapOf("track" to track.lowercase(), "day" to day.toString()))
    data class ShelterSlip(val track: String) : Event("shelter_slip", mapOf("track" to track.lowercase()))
    data class SosOpened(val option: String) : Event("sos_opened", mapOf("option" to option))
    data object ShelterOfferShown : Event("shelter_offer_shown")
    data object ShelterOfferAccepted : Event("shelter_offer_accepted")
    data class Activation(val questsByDay6: Int) : Event("activation", mapOf("quests_by_day6" to if (questsByDay6 >= 3) "3+" else questsByDay6.toString()))
    data class Retained(val day: Int, val retained: Boolean) : Event("retained", mapOf("day" to day.toString(), "retained" to retained.toString()))
    data class Who5Delta(val bucket: String) : Event("who5_delta", mapOf("bucket" to bucket))
    data class InstallReferrer(val source: String) : Event("install_referrer", mapOf("source" to source.take(32).filter { it.isLetterOrDigit() || it == '_' }))

    /** Only these may be queued before consent; they're sent on "Yes" and deleted on "No" (US-020). */
    val preConsent: Boolean get() = this is FirstOpen || this is OnboardingCompleted || this is InstallReferrer

    companion object {
        /** Human-readable list for "See exactly what's sent". */
        val DESCRIPTIONS = listOf(
            "first_open" to "the app was opened for the first time, and whether you started from quests or the Shelter",
            "onboarding_completed" to "which realm you picked and your energy that day",
            "quest_completed" to "a quest's realm and courage level, and whether you stamped it in the app or from a notification",
            "quest_shared" to "a quest's realm when you share it",
            "shelter_track_started / day_done / slip" to "which track, and the day number",
            "sos_opened" to "which help option was opened (calm, reach, lines), never your country",
            "activation / retained" to "how many quests you did in your first week, and whether you came back on day 1, 7 and 30",
            "who5_delta" to "only if you turn it on: how your wellbeing score changed, as a range",
            "install_referrer" to "whether you installed from a shared quest",
        )
    }
}

/**
 * Activation and classic retention, computed on the device so no persistent ID is needed (D-018, US-020).
 * [opens] are the app-days the app was opened; [completions] the app-days quests were completed.
 */
object Milestones {
    val RETENTION_DAYS = listOf(1, 7, 30)

    fun due(install: AppDay, today: AppDay, opens: Set<AppDay>, completions: List<AppDay>, alreadySent: Set<String>): List<Event> {
        val index = today.daysSince(install)
        val out = mutableListOf<Event>()
        if (index >= 7 && "activation" !in alreadySent) {
            out += Event.Activation(completions.count { it.daysSince(install) in 0..6 })
        }
        for (d in RETENTION_DAYS) {
            val key = "retained_$d"
            if (key in alreadySent || index < d) continue
            out += Event.Retained(d, retained = (install + d) in opens)
        }
        return out
    }

    fun key(e: Event): String? = when (e) {
        is Event.Activation -> "activation"
        is Event.Retained -> "retained_${e.day}"
        else -> null
    }
}
