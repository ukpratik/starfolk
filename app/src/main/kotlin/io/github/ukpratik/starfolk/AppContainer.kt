// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.ukpratik.starfolk.core.content.ContentSource
import io.github.ukpratik.starfolk.core.time.AppDayClock
import io.github.ukpratik.starfolk.core.time.Clock
import io.github.ukpratik.starfolk.data.BundledContentSource
import io.github.ukpratik.starfolk.data.QuestRepository
import io.github.ukpratik.starfolk.data.SettingsRepository
import io.github.ukpratik.starfolk.data.ShelterRepository
import io.github.ukpratik.starfolk.data.Who5Repository
import io.github.ukpratik.starfolk.data.core.CoreDatabase
import io.github.ukpratik.starfolk.data.personal.PrivateDatabase
import io.github.ukpratik.starfolk.platform.SystemClock
import io.github.ukpratik.starfolk.platform.Flavour
import io.github.ukpratik.starfolk.analytics.Analytics
import io.github.ukpratik.starfolk.analytics.FlushWorker
import io.github.ukpratik.starfolk.data.UserData

/** Wiring for the whole app (ADR-004). Properties are lazy so cold start only pays for what the first screen uses. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clock: Clock = SystemClock
    fun appContextForSettings(): Context = appContext
    val appDayClock by lazy { AppDayClock(clock) }
    val content: ContentSource by lazy { BundledContentSource(appContext.assets) }

    private val coreDb by lazy { CoreDatabase.open(appContext) }
    private val privateDb by lazy { PrivateDatabase.open(appContext) }
    val settings by lazy { SettingsRepository(PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile("settings") }) }
    private val nowMillis = { clock.now().toEpochMilli() }
    val analytics by lazy {
        Analytics(coreDb.analytics(), settings, appDayClock, nowMillis, Flavour.transportFactory()) { FlushWorker.schedule(appContext) }
    }
    /** first_open (with entry path) and the install referrer, each once (US-018/020). */
    suspend fun firstOpen() {
        val s = settings.current()
        if (!s.firstOpenTracked) {
            settings.firstOpenTracked()
            analytics.track(io.github.ukpratik.starfolk.core.metrics.Event.FirstOpen((s.entryPath ?: io.github.ukpratik.starfolk.data.EntryPath.QUEST).name))
        }
        if (!s.referrerChecked) {
            settings.referrerChecked()
            Flavour.installSource(appContext)?.let { analytics.track(io.github.ukpratik.starfolk.core.metrics.Event.InstallReferrer(it)) }
        }
    }

    val userData by lazy { UserData(appContext, { coreDb }, { privateDb }, settings) }
    val shelter by lazy { ShelterRepository(coreDb.shelter(), privateDb.shelter(), appDayClock, nowMillis) }
    val who5 by lazy {
        Who5Repository(privateDb.shelter(), appDayClock, nowMillis, shareDelta = { settings.current().who5Share }, onDelta = { analytics.track(io.github.ukpratik.starfolk.core.metrics.Event.Who5Delta(it)) })
    }
    val quests by lazy { QuestRepository(coreDb.dao(), privateDb.dao(), content, settings, appDayClock, shelterActive = { shelter.active() != null }, nowMillis = nowMillis) }
}
