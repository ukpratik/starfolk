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

/** Wiring for the whole app (ADR-004). Properties are lazy so cold start only pays for what the first screen uses. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clock: Clock = SystemClock
    val appDayClock by lazy { AppDayClock(clock) }
    val content: ContentSource by lazy { BundledContentSource(appContext.assets) }

    private val coreDb by lazy { CoreDatabase.open(appContext) }
    private val privateDb by lazy { PrivateDatabase.open(appContext) }
    val settings by lazy { SettingsRepository(PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile("settings") }) }
    private val nowMillis = { clock.now().toEpochMilli() }
    val shelter by lazy { ShelterRepository(coreDb.shelter(), privateDb.shelter(), appDayClock, nowMillis) }
    val who5 by lazy { Who5Repository(privateDb.shelter(), appDayClock, nowMillis) }
    val quests by lazy { QuestRepository(coreDb.dao(), privateDb.dao(), content, settings, appDayClock, shelterActive = { shelter.active() != null }, nowMillis = nowMillis) }
}
