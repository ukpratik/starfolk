// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ukpratik.starfolk.core.content.Realm
import io.github.ukpratik.starfolk.core.quest.Energy
import io.github.ukpratik.starfolk.core.time.AppDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** How the user first arrived (US-002): metrics are reported per entry path (BA review). */
enum class EntryPath { QUEST, SHELTER }

data class Settings(
    val onboarded: Boolean,
    /** Seeds the sky's layout and the daily pick. Set once, at onboarding or the Shelter fast path. */
    val skySeed: Long,
    val palette: Int,
    val preferredRealm: Realm,
    val energy: Energy,
    val entryPath: EntryPath?,
    val installAppDay: AppDay?,
)

/** DataStore at files/datastore/settings.preferences_pb: backed up by the include-list rules (LLD §2.3, D-032). */
class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            onboarded = p[ONBOARDED] ?: false,
            skySeed = p[SKY_SEED] ?: 0L,
            palette = p[PALETTE] ?: 0,
            preferredRealm = p[REALM]?.let { runCatching { Realm.valueOf(it) }.getOrNull() } ?: Realm.CONNECT,
            energy = p[ENERGY]?.let { runCatching { Energy.valueOf(it) }.getOrNull() } ?: Energy.OK,
            entryPath = p[ENTRY]?.let { runCatching { EntryPath.valueOf(it) }.getOrNull() },
            installAppDay = p[INSTALL_DAY]?.let(::AppDay),
        )
    }

    suspend fun current(): Settings = settings.first()

    /** The 3-tap onboarding (US-001): pick your sky, your realm, today's energy. */
    suspend fun completeOnboarding(palette: Int, realm: Realm, energy: Energy, seed: Long, today: AppDay) = store.edit { p ->
        p[PALETTE] = palette; p[REALM] = realm.name; p[ENERGY] = energy.name
        p.putIfAbsent(SKY_SEED, seed); p.putIfAbsent(ENTRY, EntryPath.QUEST.name); p.putIfAbsent(INSTALL_DAY, today.epochDay)
        p[ONBOARDED] = true
    }

    /** "Going through something right now?" (US-002): skip onboarding, create a default sky silently. */
    suspend fun startFromShelter(seed: Long, today: AppDay) = store.edit { p ->
        p.putIfAbsent(SKY_SEED, seed); p.putIfAbsent(ENTRY, EntryPath.SHELTER.name); p.putIfAbsent(INSTALL_DAY, today.epochDay)
        p[ONBOARDED] = true
    }

    suspend fun setEnergy(energy: Energy) = store.edit { it[ENERGY] = energy.name }

    private fun <T> androidx.datastore.preferences.core.MutablePreferences.putIfAbsent(key: Preferences.Key<T>, value: T) {
        if (this[key] == null) this[key] = value
    }

    private companion object {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val SKY_SEED = longPreferencesKey("sky_seed")
        val PALETTE = intPreferencesKey("sky_palette")
        val REALM = stringPreferencesKey("preferred_realm")
        val ENERGY = stringPreferencesKey("energy_last")
        val ENTRY = stringPreferencesKey("entry_path")
        val INSTALL_DAY = longPreferencesKey("install_app_day")
    }
}
