// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.starfolk.core.content.TrackId
import io.github.ukpratik.starfolk.core.shelter.ShelterProgress
import io.github.ukpratik.starfolk.core.shelter.SlipChoice
import io.github.ukpratik.starfolk.core.shelter.TrackStatus
import io.github.ukpratik.starfolk.core.time.AppDayClock
import io.github.ukpratik.starfolk.core.time.Clock
import io.github.ukpratik.starfolk.data.core.CoreDatabase
import io.github.ukpratik.starfolk.data.personal.PrivateDatabase
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ShelterRepositoryTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val zone = ZoneId.of("America/New_York")
    private var now: Instant = LocalDateTime.parse("2026-10-25T22:00").atZone(zone).toInstant()
    private val clock = object : Clock { override fun now() = now; override fun zone() = zone }
    private lateinit var core: CoreDatabase
    private lateinit var personal: PrivateDatabase
    private lateinit var repo: ShelterRepository

    @Before fun setUp() {
        core = Room.inMemoryDatabaseBuilder(app, CoreDatabase::class.java).allowMainThreadQueries().build()
        personal = Room.inMemoryDatabaseBuilder(app, PrivateDatabase::class.java).allowMainThreadQueries().build()
        repo = ShelterRepository(core.shelter(), personal.shelter(), AppDayClock(clock)) { now.toEpochMilli() }
    }

    @After fun tearDown() { core.close(); personal.close() }
    private fun nextDay() { now = now.plus(Duration.ofDays(1)) }

    @Test fun `one step per app-day, and a step at 1 am still counts for yesterday`() = runTest {
        repo.start(TrackId.BREAKUP)
        assertThat(repo.markDone(TrackId.BREAKUP, 21)).isFalse()
        assertThat(repo.active()!!.daysWalked).isEqualTo(1)
        now = now.plus(Duration.ofHours(3)) // 01:00: same app-day
        repo.markDone(TrackId.BREAKUP, 21)
        assertThat(repo.active()!!.daysWalked).isEqualTo(1)
        now = now.plus(Duration.ofHours(4)) // 05:00: the next app-day
        repo.markDone(TrackId.BREAKUP, 21)
        assertThat(repo.active()!!.currentDay).isEqualTo(3)
    }

    @Test fun `slip keeps days walked, reaching out restarts only the counter`() = runTest {
        repo.start(TrackId.BREAKUP)
        repeat(4) { repo.markDone(TrackId.BREAKUP, 21); nextDay() }
        repo.setNoContact(TrackId.BREAKUP, true)
        nextDay(); nextDay()
        assertThat(ShelterProgress.noContactDays(repo.active()!!, AppDayClock(clock).today())).isEqualTo(2)
        repo.slip(TrackId.BREAKUP, SlipChoice.KEEP, reachedOut = true)
        val s = repo.active()!!
        assertThat(ShelterProgress.noContactDays(s, AppDayClock(clock).today())).isEqualTo(0)
        assertThat(s.daysWalked).isEqualTo(4)
        repo.slip(TrackId.BREAKUP, SlipChoice.RESTART, reachedOut = false)
        assertThat(repo.active()!!.currentDay).isEqualTo(1)
        assertThat(repo.active()!!.daysWalked).isEqualTo(4)
    }

    @Test fun `letters and journal live in private db, and release deletes for good`() = runTest {
        repo.start(TrackId.BREAKUP)
        repo.saveLetter(TrackId.BREAKUP, null, "Dear you, I miss the Sunday walks.")
        repo.saveJournal(TrackId.BREAKUP, 1, "Heavy day.")
        val letter = repo.letters(TrackId.BREAKUP).first().single()
        assertThat(repo.journal(TrackId.BREAKUP, 1).first()!!.body).isEqualTo("Heavy day.")
        repo.releaseLetter(letter.id)
        assertThat(repo.letters(TrackId.BREAKUP).first()).isEmpty()
        val coreTables = core.query("SELECT name FROM sqlite_master WHERE type='table'", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        assertThat(coreTables).containsNoneOf("letter", "journal", "who5")
    }

    @Test fun `switching tracks pauses the old one, and the last day graduates`() = runTest {
        repo.start(TrackId.BREAKUP); repo.markDone(TrackId.BREAKUP, 21); nextDay()
        repo.start(TrackId.WORK)
        val states = repo.states.first()
        assertThat(states.first { it.track == TrackId.BREAKUP }.status).isEqualTo(TrackStatus.PAUSED)
        var graduated = false
        repeat(14) { graduated = repo.markDone(TrackId.WORK, 14); nextDay() }
        assertThat(graduated).isTrue()
        assertThat(repo.active()).isNull()
    }
}
