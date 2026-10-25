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
import io.github.ukpratik.starfolk.data.core.CommitEntity
import io.github.ukpratik.starfolk.data.core.CompletionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    init {
        viewModelScope.launch { c.quests.recordOpen(); c.quests.ensureToday() }
    }

    /** Called on resume: a new app-day (04:00, travel, DST) deals a new ticket (US-003). */
    fun refreshDay() {
        val today = c.appDayClock.today()
        if (today != day.value) viewModelScope.launch { c.quests.ensureToday(); day.value = today }
    }

    fun swap() = viewModelScope.launch { c.quests.swap() }

    fun commit(questId: String, slot: String, remindAt: Long?) = viewModelScope.launch { c.quests.commit(questId, slot, remindAt) }

    fun done(quest: Quest) = viewModelScope.launch {
        val id = c.quests.complete(quest, source = "app")
        _moment.value = Moment.Reflect(id, quest)
    }

    fun reflect(mood: Int?, note: String?, name: String?) = viewModelScope.launch {
        val m = _moment.value as? Moment.Reflect ?: return@launch
        c.quests.reflect(m.completionId, mood, note, name)
        // US-009: every check-in gets a "why this works" card; quests without a linked insight use their own why.
        val insight = m.quest.insightId?.let { c.content.bundle().insights.firstOrNull { i -> i.id == it } }
            ?: Insight(id = "", title = WHY_FALLBACK_TITLE, body = m.quest.why, cite = m.quest.evidence.joinToString("; "))
        _moment.value = Moment.Stamped(m.completionId, insight)
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
