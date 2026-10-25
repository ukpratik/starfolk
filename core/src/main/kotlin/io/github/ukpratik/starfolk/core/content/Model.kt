// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Content schema v1 (LLD §3.1). Field names are the JSON names; unknown fields are rejected (ContentJson).

@Serializable
enum class Realm {
    @SerialName("connect") CONNECT,
    @SerialName("adventure") ADVENTURE,
    @SerialName("purpose") PURPOSE,
    @SerialName("care") CARE,
}

/** Courage level: Spark ≤ 2 min, Flame ≤ 30 min, Wildfire half a day or bolder. */
@Serializable
enum class Level {
    @SerialName("spark") SPARK,
    @SerialName("flame") FLAME,
    @SerialName("wildfire") WILDFIRE,
}

@Serializable
enum class TrackId {
    @SerialName("breakup") BREAKUP,
    @SerialName("work") WORK,
    @SerialName("health") HEALTH,
}

@Serializable
data class QuestSafety(
    /** "ok" or "daylight": daylight quests are never suggested as a night commit. */
    val night: String = "ok",
    /** Rough cost in USD; 0 = free. */
    val cost: Int = 0,
)

@Serializable
data class Quest(
    val id: String,
    val realm: Realm,
    val level: Level,
    val minutes: Int,
    val title: String,
    val why: String,
    val insightId: String? = null,
    /** Invite-style share copy ("Who's in?") for quests you can do with friends (US-018). */
    val groupable: Boolean = false,
    /** The check-in offers "Who was it?", which names a star (US-008). */
    val namesStar: Boolean = false,
    val evidence: List<String>,
    val tags: List<String> = emptyList(),
    /** Adventure stamp category 0–7 (design-system §6); ignored for other realms. */
    val stamp: Int? = null,
    val safety: QuestSafety = QuestSafety(),
    val allowPhrases: List<String> = emptyList(),
    @SerialName("reviewed_by") val reviewedBy: String? = null,
    @SerialName("reviewed_on") val reviewedOn: String? = null,
)

@Serializable
data class ShelterStep(val title: String, val detail: String)

@Serializable
data class ShelterDay(
    val id: String,
    val day: Int,
    val title: String,
    val insight: String,
    val step: ShelterStep,
    val journalPrompt: String? = null,
    val evidence: List<String>,
    val allowPhrases: List<String> = emptyList(),
    @SerialName("reviewed_by") val reviewedBy: String? = null,
    @SerialName("reviewed_on") val reviewedOn: String? = null,
)

@Serializable
data class Track(
    val track: TrackId,
    val title: String,
    val summary: String,
    val length: Int,
    val days: List<ShelterDay>,
)

@Serializable
data class Spark(
    val id: String,
    val text: String,
    /** A substring of [text] that the UI highlights with the marker. */
    val highlight: String? = null,
)

@Serializable
data class Insight(
    val id: String,
    val title: String,
    val body: String,
    val cite: String,
)

@Serializable
data class HelplineLine(
    val name: String,
    val phone: String? = null,
    val sms: String? = null,
    val hours: String,
)

@Serializable
data class HelplineCountry(
    /** ISO 3166-1 alpha-2, upper case. */
    val country: String,
    val lines: List<HelplineLine>,
    val emergency: String,
    /** ISO date the numbers were last checked against the source; null = not yet verified (release fails). */
    @SerialName("verified_on") val verifiedOn: String? = null,
    val source: String,
)

@Serializable
data class SosTemplate(val id: String, val text: String)

@Serializable
data class GroundingStep(val id: String, val title: String, val body: String)

@Serializable
data class SosContent(
    val templates: List<SosTemplate>,
    val grounding: List<GroundingStep>,
    @SerialName("reviewed_by") val reviewedBy: String? = null,
    @SerialName("reviewed_on") val reviewedOn: String? = null,
)

/** Everything the app reads. Built by the packer from content/v1 and read back by BundledContentSource. */
data class ContentBundle(
    val quests: List<Quest>,
    val tracks: List<Track>,
    val sparks: List<Spark>,
    val insights: List<Insight>,
    val helplines: List<HelplineCountry>,
    val sos: SosContent,
)

@Serializable
data class PackEntry(
    val id: String,
    val version: Int,
    val file: String,
    val sha256: String,
    val count: Int,
)

@Serializable
data class Manifest(
    val schemaVersion: Int,
    val contentVersion: String,
    val generatedAt: String,
    val packs: List<PackEntry>,
)
