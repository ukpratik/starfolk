// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ukpratik.starfolk.AppContainer
import io.github.ukpratik.starfolk.core.content.ContentBundle
import io.github.ukpratik.starfolk.core.content.Insight
import io.github.ukpratik.starfolk.core.content.Quest
import io.github.ukpratik.starfolk.core.content.Realm
import io.github.ukpratik.starfolk.core.content.Spark
import io.github.ukpratik.starfolk.core.sky.CompletionLite
import io.github.ukpratik.starfolk.core.time.AppDay
import io.github.ukpratik.starfolk.data.QuestRepository
import io.github.ukpratik.starfolk.data.Who5Repository
import io.github.ukpratik.starfolk.core.safety.SafetyRules
import io.github.ukpratik.starfolk.core.shelter.ShelterProgress
import io.github.ukpratik.starfolk.data.core.CommitEntity
import io.github.ukpratik.starfolk.data.core.CompletionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodayUiState(
    val day: AppDay,
    val seed: Long,
    val palette: Int,
    val quest: Quest,
    val swapsLeft: Int,
    val commit: CommitEntity?,
    /** Today's completion of the current quest, if stamped. */
    val done: CompletionEntity?,
    val sky: List<CompletionLite>,
    val names: Map<Long, String>,
    val spark: Spark,
)

/** The reflection → stamp → why-card → undo moment after "Done" (US-006/007/009). */
/** Gentle, never-forced offers on Today (US-016/019, D-031). */
data class Offers(
    val shelter: ShelterCard? = null,
    val lowMood: Boolean = false,
    val who5: Who5Prompt? = null,
)
data class ShelterCard(val title: String, val day: Int, val doneToday: Boolean)
enum class Who5Prompt { BASELINE, RECHECK }

/** One-time asks after the first check-in, in order (US-010, US-020): never at onboarding. */
enum class Ask { NUDGE, CONSENT }

sealed interface Moment {
    data object None : Moment
    /** The reflection sheet is open for this completion. */
    data class Reflect(val completionId: Long, val quest: Quest) : Moment
    /** Stamped: the star pops in, the why card shows, undo is offered for 8 s. */
    data class Stamped(val completionId: Long, val insight: Insight?) : Moment
}

private const val WHY_FALLBACK_TITLE = "Small things, done, add up."

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val c: AppContainer, private val skyDemo: Int) : ViewModel() {
    private val day = MutableStateFlow(c.appDayClock.today())
    private val bundle = flow { emit(c.content.bundle()) }

    private val _moment = MutableStateFlow<Moment>(Moment.None)
    val moment: StateFlow<Moment> = _moment.asStateFlow()

    val state: StateFlow<TodayUiState?> = day.flatMapLatest { d ->
        c.quests.pickFlow(d).filterNotNull().flatMapLatest { pick ->
            combine(
                c.quests.commitFlow(d, pick.questId),
                c.quests.completions(),
                c.quests.names(),
                c.settings.settings,
                bundle,
            ) { commit, completions, names, settings, b ->
                build(d, pick.questId, pick.swapsUsed, commit, completions, names, settings.skySeed, settings.palette, b)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _ask = MutableStateFlow<Ask?>(null)
    val ask: StateFlow<Ask?> = _ask.asStateFlow()

    private val _review = MutableStateFlow(false)
    /** True once: the UI asks Play for a review (US-025), then calls [reviewShown]. */
    val review: StateFlow<Boolean> = _review.asStateFlow()

    private val _offers = MutableStateFlow(Offers())
    val offers: StateFlow<Offers> = _offers.asStateFlow()

    init {
        viewModelScope.launch {
            c.quests.recordOpen(); c.quests.ensureToday(); refreshOffers(afterCheckin = false)
            c.analytics.checkMilestones()
        }
    }

    /** Re-evaluates the Shelter card, the low-mood offer and the WHO-5 prompts (deterministic rules, :core). */
    private suspend fun refreshOffers(afterCheckin: Boolean) {
        val today = c.appDayClock.today()
        val s = c.settings.current()
        val active = c.shelter.active()
        val tracks = c.content.bundle().tracks
        val card = active?.let { a ->
            ShelterCard(tracks.first { it.track == a.track }.title, ShelterProgress.dayToShow(a, today), ShelterProgress.doneToday(a, today))
        }
        val who5 = c.who5.all.first()
        val baseline = who5.firstOrNull { it.kind == Who5Repository.BASELINE }
        val latestPct = who5.lastOrNull()?.percent
        val lowMood = SafetyRules.lowMoodOffer(c.quests.recentMoods(), latestPct, active != null, s.lowMoodSnoozedUntil, s.lowMoodLastShown, today)
        if (lowMood) { c.settings.lowMoodShown(today); c.analytics.track(io.github.ukpratik.starfolk.core.metrics.Event.ShelterOfferShown) }
        val appDayIndex = s.installAppDay?.let { today.daysSince(it) } ?: 0
        val prompt = when {
            SafetyRules.who5RecheckDue(baseline?.let { AppDay(it.appDay) }, who5.any { it.kind == Who5Repository.RECHECK }, today) -> Who5Prompt.RECHECK
            afterCheckin && SafetyRules.who5BaselineOffer(baseline != null, c.appDayClock.localHour(), appDayIndex, s.who5OfferedOn == today, s.who5Dismissals) ->
                Who5Prompt.BASELINE.also { c.settings.who5Offered(today) }
            else -> _offers.value.who5.takeIf { it == Who5Prompt.BASELINE && s.who5OfferedOn == today }
        }
        // Keep the low-mood card up for the rest of the day once it has been shown.
        _offers.value = Offers(card, lowMood || (_offers.value.lowMood && s.lowMoodLastShown == today && active == null), prompt)
    }

    fun snoozeLowMood() = viewModelScope.launch {
        c.settings.snoozeLowMood(c.appDayClock.today() + SafetyRules.SNOOZE_DAYS)
        _offers.update { it.copy(lowMood = false) }
    }

    fun dismissWho5() = viewModelScope.launch {
        if (_offers.value.who5 == Who5Prompt.BASELINE) c.settings.who5Dismissed()
        _offers.update { it.copy(who5 = null) }
    }

    /** Called on resume: a new app-day (04:00, travel, DST) deals a new ticket (US-003). */
    fun refreshDay() {
        val today = c.appDayClock.today()
        viewModelScope.launch {
            if (today != day.value) { c.quests.ensureToday(); day.value = today }
            refreshOffers(afterCheckin = false)
        }
    }

    fun swap() = viewModelScope.launch { c.quests.swap() }

    /** Returns true when the UI should request notification permission for this reminder (US-005). */
    fun commit(questId: String, slot: String, remindAt: Long?, schedule: (Long) -> Unit) = viewModelScope.launch {
        c.quests.commit(questId, slot, remindAt)
        if (remindAt != null) schedule(remindAt)
    }

    fun done(quest: Quest) = viewModelScope.launch {
        val id = c.quests.complete(quest, source = "app")
        c.analytics.track(io.github.ukpratik.starfolk.core.metrics.Event.QuestCompleted(quest.realm.name, quest.level.name, "app"))
        _moment.value = Moment.Reflect(id, quest)
    }

    fun nudgeAnswered(time: String?) = viewModelScope.launch {
        c.settings.notificationsAsked()
        c.settings.setReminderTime(time)
        _ask.value = if (c.analytics.available && c.settings.current().analyticsConsent == null) Ask.CONSENT else null
    }

    fun consentAnswered(yes: Boolean) = viewModelScope.launch { c.analytics.setConsent(yes); _ask.value = null }

    fun reviewShown() = viewModelScope.launch { c.settings.reviewPrompted(); _review.value = false }

    fun shared(quest: Quest) = viewModelScope.launch { c.analytics.track(io.github.ukpratik.starfolk.core.metrics.Event.QuestShared(quest.realm.name)) }

    fun lowMoodAccepted() = viewModelScope.launch { c.analytics.track(io.github.ukpratik.starfolk.core.metrics.Event.ShelterOfferAccepted) }

    fun reflect(mood: Int?, note: String?, name: String?) = viewModelScope.launch {
        val m = _moment.value as? Moment.Reflect ?: return@launch
        c.quests.reflect(m.completionId, mood, note, name)
        // US-009: every check-in gets a "why this works" card; quests without a linked insight use their own why.
        val insight = m.quest.insightId?.let { c.content.bundle().insights.firstOrNull { i -> i.id == it } }
            ?: Insight(id = "", title = WHY_FALLBACK_TITLE, body = m.quest.why, cite = m.quest.evidence.joinToString("; "))
        _moment.value = Moment.Stamped(m.completionId, insight)
        refreshOffers(afterCheckin = true)
        val st = c.settings.current()
        _ask.value = when {
            !st.notificationsAsked -> Ask.NUDGE
            c.analytics.available && st.analyticsConsent == null -> Ask.CONSENT
            else -> null
        }
        // US-025: once, after the 5th quest, on a good day; never in the Shelter.
        val count = c.quests.completions().first().size
        if (count >= 5 && (mood ?: 0) >= 4 && !st.reviewPrompted && c.shelter.active() == null) _review.value = true
    }

    fun undo() = viewModelScope.launch {
        val id = when (val m = _moment.value) { is Moment.Stamped -> m.completionId; is Moment.Reflect -> m.completionId; Moment.None -> return@launch }
        c.quests.undo(id)
        _moment.value = Moment.None
    }

    fun dismissMoment() = _moment.update { Moment.None }

    private fun build(
        d: AppDay, questId: String, swapsUsed: Int, commit: CommitEntity?, completions: List<CompletionEntity>,
        names: List<io.github.ukpratik.starfolk.data.personal.StarNameEntity>, seed: Long, palette: Int, b: ContentBundle,
    ): TodayUiState {
        val quest = b.quests.firstOrNull { it.id == questId } ?: b.quests.first()
        val real = completions.map { CompletionLite(it.id, Realm.valueOf(it.realm), AppDay(it.appDay), it.stamp) }
        // Sky spike (D-012): demo completions only when the benchmark asks for them.
        val sky = if (skyDemo > 0) io.github.ukpratik.starfolk.feature.sky.demoCompletions(skyDemo, d) else real
        return TodayUiState(
            day = d, seed = seed, palette = palette, quest = quest,
            swapsLeft = (QuestRepository.MAX_SWAPS - swapsUsed).coerceAtLeast(0),
            commit = commit, done = completions.firstOrNull { it.appDay == d.epochDay && it.questId == questId },
            sky = sky, names = names.associate { it.completionId to it.name },
            spark = b.sparks[Math.floorMod(d.epochDay, b.sparks.size.toLong()).toInt()],
        )
    }
}
