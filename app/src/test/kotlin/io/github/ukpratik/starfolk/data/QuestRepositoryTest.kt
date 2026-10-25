// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.data

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.starfolk.core.content.ContentBundle
import io.github.ukpratik.starfolk.core.content.ContentSource
import io.github.ukpratik.starfolk.core.content.Manifest
import io.github.ukpratik.starfolk.core.content.Realm
import io.github.ukpratik.starfolk.core.quest.Energy
import io.github.ukpratik.starfolk.core.time.AppDayClock
import io.github.ukpratik.starfolk.core.time.Clock
import io.github.ukpratik.starfolk.data.core.CoreDatabase
import io.github.ukpratik.starfolk.data.personal.PrivateDatabase
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class QuestRepositoryTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val zone = ZoneId.of("Europe/London")
    private var now: Instant = LocalDateTime.parse("2026-10-25T09:00").atZone(zone).toInstant()
    private val clock = object : Clock { override fun now() = now; override fun zone() = zone }

    private lateinit var core: CoreDatabase
    private lateinit var personal: PrivateDatabase
    private lateinit var bundle: ContentBundle
    private lateinit var repo: QuestRepository
    private lateinit var settings: SettingsRepository
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Before fun setUp() = runBlocking {
        core = Room.inMemoryDatabaseBuilder(app, CoreDatabase::class.java).allowMainThreadQueries().build()
        personal = Room.inMemoryDatabaseBuilder(app, PrivateDatabase::class.java).allowMainThreadQueries().build()
        bundle = BundledContentSource(app.assets).bundle()
        val content = object : ContentSource {
            override suspend fun manifest(): Manifest = error("unused")
            override suspend fun bundle() = this@QuestRepositoryTest.bundle
        }
        val file = File(app.filesDir, "test-${System.nanoTime()}.preferences_pb")
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        settings.completeOnboarding(palette = 2, realm = Realm.CONNECT, energy = Energy.OK, seed = 99L, today = AppDayClock(clock).today())
        repo = QuestRepository(core.dao(), personal.dao(), content, settings, AppDayClock(clock)) { now.toEpochMilli() }
    }

    @After fun tearDown() { scope.cancel(); core.close(); personal.close() }

    private fun quest(id: String) = bundle.quests.first { it.id == id }

    @Test fun `the daily pick is created once and survives the rest of the app-day`() = runTest {
        val first = repo.ensureToday()
        now = now.plus(Duration.ofHours(18)) // 03:00 next calendar day is still the same app-day
        assertThat(repo.ensureToday()).isEqualTo(first)
        now = now.plus(Duration.ofHours(1)) // 04:00: a new app-day
        assertThat(repo.ensureToday().appDay).isEqualTo(first.appDay + 1)
    }

    @Test fun `at most two swaps, never the same ticket twice`() = runTest {
        val shown = mutableListOf(repo.ensureToday().questId)
        assertThat(repo.swap()).isTrue(); shown += repo.ensureToday().questId
        assertThat(repo.swap()).isTrue(); shown += repo.ensureToday().questId
        assertThat(repo.swap()).isFalse()
        assertThat(shown.toSet()).hasSize(3)
        assertThat(repo.ensureToday().swapsUsed).isEqualTo(2)
    }

    @Test fun `Done is idempotent per quest and app-day`() = runTest {
        val q = quest(repo.ensureToday().questId)
        val a = repo.complete(q, "app")
        val b = repo.complete(q, "notification")
        assertThat(b).isEqualTo(a)
        assertThat(repo.completions().first()).hasSize(1)
    }

    @Test fun `reflection lives in private db and undo removes everything`() = runTest {
        val q = quest(repo.ensureToday().questId)
        val id = repo.complete(q, "app")
        repo.reflect(id, mood = 4, note = "  She sent six voice notes  ", name = "Maya")
        assertThat(personal.dao().checkins().first().single().note).isEqualTo("She sent six voice notes")
        assertThat(personal.dao().names().first().single().name).isEqualTo("Maya")
        assertThat(core.query("SELECT * FROM completion", null).columnNames.toList()).containsNoneOf("mood", "note", "name")
        repo.undo(id)
        assertThat(repo.completions().first()).isEmpty()
        assertThat(personal.dao().checkins().first()).isEmpty()
        assertThat(personal.dao().names().first()).isEmpty()
    }

    @Test fun `a low mood makes the next day's ticket a Spark`() = runTest {
        val q = quest(repo.ensureToday().questId)
        repo.reflect(repo.complete(q, "app"), mood = 1, note = null, name = null)
        repeat(10) {
            now = now.plus(Duration.ofDays(1))
            assertThat(quest(repo.ensureToday().questId).level.name).isEqualTo("SPARK")
        }
    }
}
