// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.analytics

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.starfolk.core.content.Realm
import io.github.ukpratik.starfolk.core.metrics.Event
import io.github.ukpratik.starfolk.core.quest.Energy
import io.github.ukpratik.starfolk.core.time.AppDay
import io.github.ukpratik.starfolk.core.time.AppDayClock
import io.github.ukpratik.starfolk.core.time.Clock
import io.github.ukpratik.starfolk.data.SettingsRepository
import io.github.ukpratik.starfolk.data.core.AppOpenEntity
import io.github.ukpratik.starfolk.data.core.CoreDatabase
import io.github.ukpratik.starfolk.data.core.EventQueueEntity
import java.io.File
import java.time.Instant
import java.time.ZoneId
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

/** US-020 / NFR-006 / D-008: nothing leaves without consent; the transport isn't even built without it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AnalyticsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var now = Instant.parse("2026-10-25T09:41:27Z")
    private val clock = object : Clock { override fun now() = now; override fun zone() = ZoneId.of("UTC") }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var db: CoreDatabase
    private lateinit var settings: SettingsRepository
    private val sent = mutableListOf<EventQueueEntity>()
    private var transportsBuilt = 0
    private var flushesScheduled = 0
    private lateinit var analytics: Analytics

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(app, CoreDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(app.filesDir, "a-${System.nanoTime()}.preferences_pb") })
        analytics = Analytics(db.analytics(), settings, AppDayClock(clock), { now.toEpochMilli() }, {
            transportsBuilt++
            Transport { batch -> sent += batch; true }
        }) { flushesScheduled++ }
    }

    @After fun tearDown() { scope.cancel(); db.close() }

    @Test fun `before consent only pre-consent events are queued, and nothing is sent`() = runTest {
        analytics.track(Event.FirstOpen("QUEST"))
        analytics.track(Event.QuestCompleted("CONNECT", "SPARK", "app"))
        assertThat(db.analytics().batch(10).map { it.name }).containsExactly("first_open")
        assertThat(transportsBuilt).isEqualTo(0)
        assertThat(flushesScheduled).isEqualTo(0)
    }

    @Test fun `No deletes the queue and stays silent forever`() = runTest {
        analytics.track(Event.FirstOpen("QUEST"))
        analytics.setConsent(false)
        analytics.track(Event.QuestCompleted("CONNECT", "SPARK", "app"))
        assertThat(db.analytics().size()).isEqualTo(0)
        assertThat(analytics.flush()).isTrue()
        assertThat(transportsBuilt).isEqualTo(0)
    }

    @Test fun `Yes sends the queue, with timestamps rounded to the hour and no free text`() = runTest {
        analytics.track(Event.FirstOpen("SHELTER"))
        analytics.setConsent(true)
        analytics.track(Event.QuestCompleted("CONNECT", "SPARK", "notification"))
        analytics.flush()
        assertThat(sent.map { it.name }).containsExactly("first_open", "quest_completed").inOrder()
        assertThat(sent.all { it.hourMillis % 3_600_000L == 0L }).isTrue()
        assertThat(sent.last().propsJson).isEqualTo("""{"realm":"connect","level":"spark","source":"notification"}""")
        assertThat(db.analytics().size()).isEqualTo(0)
    }

    @Test fun `milestones are computed on the device and sent once`() = runTest {
        val install = AppDay.of(java.time.LocalDate.parse("2026-10-01"))
        settings.completeOnboarding(0, Realm.CONNECT, Energy.OK, 1L, install)
        analytics.setConsent(true)
        db.dao().recordOpen(AppOpenEntity(install.epochDay + 1))
        analytics.checkMilestones()
        analytics.checkMilestones()
        analytics.flush()
        val names = sent.map { it.name + it.propsJson }
        assertThat(names.count { it.startsWith("activation") }).isEqualTo(1)
        assertThat(names).contains("""retained{"day":"1","retained":"true"}""")
        assertThat(names).contains("""retained{"day":"7","retained":"false"}""")
    }
}
