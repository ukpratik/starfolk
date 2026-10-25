// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.shelter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ukpratik.starfolk.AppContainer
import io.github.ukpratik.starfolk.core.content.ShelterDay
import io.github.ukpratik.starfolk.core.content.Track
import io.github.ukpratik.starfolk.core.content.TrackId
import io.github.ukpratik.starfolk.core.shelter.ShelterProgress
import io.github.ukpratik.starfolk.core.shelter.SlipChoice
import io.github.ukpratik.starfolk.core.shelter.TrackState
import io.github.ukpratik.starfolk.core.time.AppDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShelterUiState(
    val today: AppDay,
    val tracks: List<Track>,
    val states: List<TrackState>,
    val introSeen: Boolean,
) {
    val active: TrackState? get() = ShelterProgress.active(states)
    val activeTrack: Track? get() = active?.let { a -> tracks.firstOrNull { it.track == a.track } }
    val dayNumber: Int? get() = active?.let { ShelterProgress.dayToShow(it, today) }
    val day: ShelterDay? get() = dayNumber?.let { n -> activeTrack?.days?.firstOrNull { it.day == n } }
    val doneToday: Boolean get() = active?.let { ShelterProgress.doneToday(it, today) } ?: false
    fun stateOf(track: TrackId) = states.firstOrNull { it.track == track }
}

class ShelterViewModel(private val c: AppContainer) : ViewModel() {
    private val today = MutableStateFlow(c.appDayClock.today())

    private val _graduated = MutableStateFlow<Pair<Track, TrackState>?>(null)
    /** Set when the last day of a track was just done (US-017). */
    val graduated: StateFlow<Pair<Track, TrackState>?> = _graduated.asStateFlow()

    val state = combine(today, flow { emit(c.content.bundle().tracks) }, c.shelter.states, c.settings.settings) { d, tracks, states, s ->
        ShelterUiState(d, tracks.sortedBy { it.track.ordinal }, states, s.shelterIntroSeen)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshDay() { today.value = c.appDayClock.today() }
    fun introSeen() = viewModelScope.launch { c.settings.shelterIntroSeen() }
    fun start(track: TrackId) = viewModelScope.launch { c.shelter.start(track); c.analytics.track(io.github.ukpratik.starfolk.core.metrics.Event.ShelterTrackStarted(track.name)) }

    fun markDone(track: Track) = viewModelScope.launch {
        val day = c.shelter.active()?.currentDay
        val graduated = c.shelter.markDone(track.track, track.length)
        if (day != null) c.analytics.track(io.github.ukpratik.starfolk.core.metrics.Event.ShelterDayDone(track.track.name, day))
        if (graduated) {
            val finished = c.shelter.states.first().first { it.track == track.track }
            _graduated.value = track to finished
        }
    }

    fun slip(track: TrackId, choice: SlipChoice, reachedOut: Boolean = false) = viewModelScope.launch { c.shelter.slip(track, choice, reachedOut); if (!reachedOut) c.analytics.track(io.github.ukpratik.starfolk.core.metrics.Event.ShelterSlip(track.name)) }
    fun setNoContact(track: TrackId, on: Boolean) = viewModelScope.launch { c.shelter.setNoContact(track, on) }
    fun journal(track: TrackId, day: Int) = c.shelter.journal(track, day)
    fun saveJournal(track: TrackId, day: Int, body: String) = viewModelScope.launch { c.shelter.saveJournal(track, day, body) }
    fun dismissGraduation() { _graduated.value = null }
}
