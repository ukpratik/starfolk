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
    /** US-016: "Not now" on the low-mood card snoozes it until this app-day. */
    val lowMoodSnoozedUntil: AppDay? = null,
    val lowMoodLastShown: AppDay? = null,
    val who5Dismissals: Int = 0,
    val who5OfferedOn: AppDay? = null,
    /** US-015: manual helpline country (ISO alpha-2); null = automatic. */
    val helplineCountry: String? = null,
    /** US-021: the first-entry Shelter disclaimer was shown. */
    val shelterIntroSeen: Boolean = false,
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
            lowMoodSnoozedUntil = p[LOWMOOD_SNOOZE]?.let(::AppDay),
            lowMoodLastShown = p[LOWMOOD_SHOWN]?.let(::AppDay),
            who5Dismissals = p[WHO5_DISMISSALS] ?: 0,
            who5OfferedOn = p[WHO5_OFFERED]?.let(::AppDay),
            helplineCountry = p[HELPLINE_COUNTRY],
            shelterIntroSeen = p[SHELTER_INTRO] ?: false,
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
    suspend fun lowMoodShown(today: AppDay) = store.edit { it[LOWMOOD_SHOWN] = today.epochDay }
    suspend fun snoozeLowMood(until: AppDay) = store.edit { it[LOWMOOD_SNOOZE] = until.epochDay }
    suspend fun who5Offered(today: AppDay) = store.edit { it[WHO5_OFFERED] = today.epochDay }
    suspend fun who5Dismissed() = store.edit { it[WHO5_DISMISSALS] = (it[WHO5_DISMISSALS] ?: 0) + 1 }
    suspend fun setHelplineCountry(country: String?) = store.edit { if (country == null) it.remove(HELPLINE_COUNTRY) else it[HELPLINE_COUNTRY] = country }
    suspend fun shelterIntroSeen() = store.edit { it[SHELTER_INTRO] = true }

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
        val LOWMOOD_SNOOZE = longPreferencesKey("lowmood_offer_snoozed_until")
        val LOWMOOD_SHOWN = longPreferencesKey("lowmood_offer_last_shown")
        val WHO5_DISMISSALS = intPreferencesKey("who5_offer_dismissals")
        val WHO5_OFFERED = longPreferencesKey("who5_offered_on")
        val HELPLINE_COUNTRY = stringPreferencesKey("helpline_country")
        val SHELTER_INTRO = booleanPreferencesKey("shelter_intro_seen")
    }
}
