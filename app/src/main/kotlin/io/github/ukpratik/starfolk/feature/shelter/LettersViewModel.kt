// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.shelter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ukpratik.starfolk.AppContainer
import io.github.ukpratik.starfolk.core.content.TrackId
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Unsent letters (US-013). A letter saves itself while it's written and whenever the editor is left (BUG-002), so
 * Back, Home or a swipe from recents never loses it. Saves run one at a time; the row id survives process death.
 */
class LettersViewModel(private val c: AppContainer, private val track: TrackId, private val handle: SavedStateHandle) : ViewModel() {
    val letters = c.shelter.letters(track)
    val writing = handle.getStateFlow(WRITING, false)

    private val saves = Mutex()
    private var autosave: Job? = null
    private var letterId: Long?
        get() = handle[LETTER_ID]
        set(value) { handle[LETTER_ID] = value }

    /** Opens the editor on a kept letter, or on a new one when [id] is null. */
    fun open(id: Long?) {
        autosave?.cancel()
        viewModelScope.launch { saves.withLock { letterId = id } }
        handle[WRITING] = true
    }

    fun edited(body: String) {
        autosave?.cancel()
        autosave = viewModelScope.launch { delay(AUTOSAVE_MS); save(body) }
    }

    /** Saves now (app going to the background). */
    fun flush(body: String) {
        autosave?.cancel()
        viewModelScope.launch { save(body) }
    }

    /** "Keep it", Back or "← back": save and return to the list. */
    fun close(body: String) {
        flush(body)
        handle[WRITING] = false
    }

    fun release(id: Long) = viewModelScope.launch { c.shelter.releaseLetter(id) }

    // NonCancellable: a save that has started always records its row id, so the next save updates instead of duplicating.
    private suspend fun save(body: String) = withContext(NonCancellable) {
        saves.withLock {
            val text = body.trim()
            if (text.isNotEmpty()) letterId = c.shelter.saveLetter(track, letterId, text)
        }
    }

    private companion object {
        const val AUTOSAVE_MS = 600L
        const val WRITING = "writing"
        const val LETTER_ID = "letterId"
    }
}
