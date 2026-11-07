// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.content

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The content rules (LLD §3.2), shared by the :tools:content validator and the app's debug checks so they can't
 * disagree. Rules that need a finished content set (track gaps, reviewer sign-off, helpline freshness, the 12
 * countries) are warnings while drafting and errors in [Mode.RELEASE].
 */
object ContentRules {

    enum class Mode { DRAFT, RELEASE }
    enum class Severity { WARNING, ERROR }

    data class Issue(val severity: Severity, val itemId: String, val message: String) {
        override fun toString() = "${severity.name.lowercase()}: [$itemId] $message"
    }

    /** The 12 launch countries (D-011). */
    val LAUNCH_COUNTRIES = setOf("US", "CA", "GB", "IE", "AU", "NZ", "IN", "PH", "NG", "ZA", "SG", "KE")
    /**
     * Launch countries that ship with the emergency number and findahelpline.com only, because no crisis line could be
     * confirmed with its operator (KE, 2026-11-07: see helpline-verification.md). A warning, not a release error.
     */
    val EMERGENCY_ONLY_COUNTRIES = setOf("KE")
    /**
     * D-039: the user chose to ship 1.0.0 before the expert review of the Shelter and SOS texts (rule 6). Inside this
     * window a missing sign-off is a warning, not a release error. It expires on its own, so a later release can't
     * quietly ship unreviewed: get the review signed, or take a new decision and move the dates.
     */
    val REVIEW_WAIVER_FROM: LocalDate = LocalDate.parse("2026-11-07")
    val REVIEW_WAIVER_UNTIL: LocalDate = LocalDate.parse("2027-02-05")
    const val HELPLINE_MAX_AGE_DAYS = 90L
    const val MIN_QUESTS_PER_CELL = 6

    private val QUEST_ID = Regex("^q-(connect|adventure|purpose|care)-\\d{4}$")
    private val SHELTER_ID = Regex("^s-(breakup|work|health)-\\d{2}$")
    private val SPARK_ID = Regex("^sp-\\d{4}$")
    private val INSIGHT_ID = Regex("^i-[a-z0-9]+(-[a-z0-9]+)*$")
    private val SOS_ID = Regex("^sos-[a-z]+-\\d{2}$")
    private val PHONE = Regex("^\\+?[0-9][0-9 ]{1,18}[0-9]$")

    fun validate(
        bundle: ContentBundle,
        idsLock: Set<String>,
        bannedPhrases: List<String>,
        mode: Mode,
        buildDate: LocalDate,
    ): List<Issue> {
        val issues = mutableListOf<Issue>()
        fun error(id: String, msg: String) = issues.add(Issue(Severity.ERROR, id, msg))
        fun releaseError(id: String, msg: String) =
            issues.add(Issue(if (mode == Mode.RELEASE) Severity.ERROR else Severity.WARNING, id, msg))
        val reviewWaived = !buildDate.isBefore(REVIEW_WAIVER_FROM) && !buildDate.isAfter(REVIEW_WAIVER_UNTIL)
        fun reviewMissing(id: String) =
            if (reviewWaived) issues.add(Issue(Severity.WARNING, id, "needs reviewed_by and reviewed_on (waived until $REVIEW_WAIVER_UNTIL, D-039)"))
            else releaseError(id, "needs reviewed_by and reviewed_on")

        // Rule 2: IDs: pattern, unique across packs, recorded in ids.lock.
        val allIds = buildList {
            bundle.quests.forEach { add(it.id to QUEST_ID) }
            bundle.tracks.flatMap { it.days }.forEach { add(it.id to SHELTER_ID) }
            bundle.sparks.forEach { add(it.id to SPARK_ID) }
            bundle.insights.forEach { add(it.id to INSIGHT_ID) }
            bundle.sos.templates.forEach { add(it.id to SOS_ID) }
            bundle.sos.grounding.forEach { add(it.id to SOS_ID) }
        }
        allIds.groupBy { it.first }.filter { it.value.size > 1 }.keys.forEach { error(it, "duplicate id") }
        for ((id, pattern) in allIds) {
            if (!pattern.matches(id)) error(id, "id doesn't match ${pattern.pattern}")
            if (id !in idsLock) error(id, "id missing from content/ids.lock (append it; ids are never reused)")
        }

        // Rules 3–4: quests: references, evidence, lengths, realm-specific fields.
        val insightIds = bundle.insights.map { it.id }.toSet()
        for (q in bundle.quests) {
            if (q.insightId != null && q.insightId !in insightIds) error(q.id, "insightId '${q.insightId}' not found")
            if (q.evidence.isEmpty()) error(q.id, "needs at least one evidence entry")
            if (q.title.length > 120) error(q.id, "title is ${q.title.length} chars (max 120)")
            if (q.why.length > 140) error(q.id, "why is ${q.why.length} chars (max 140)")
            if (q.minutes <= 0) error(q.id, "minutes must be positive")
            if (q.level == Level.SPARK && q.minutes > 2) error(q.id, "a Spark takes at most 2 minutes")
            if (q.level == Level.FLAME && q.minutes > 30) error(q.id, "a Flame takes at most 30 minutes")
            if (q.realm == Realm.ADVENTURE && (q.stamp == null || q.stamp !in 0..7)) {
                error(q.id, "adventure quests need a stamp category 0–7")
            }
        }
        for (s in bundle.sparks) if (s.text.length > 140) error(s.id, "spark is ${s.text.length} chars (max 140)")
        for (s in bundle.sparks) {
            if (s.highlight != null && s.highlight !in s.text) error(s.id, "highlight isn't part of the text")
        }
        for (i in bundle.insights) if (words(i.body) > 60) error(i.id, "insight body is ${words(i.body)} words (max 60)")

        // Rule 5: every track has days 1..length with no gaps.
        for (track in bundle.tracks) {
            val days = track.days.map { it.day }
            days.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { error("track:${track.track}", "day $it twice") }
            val missing = (1..track.length).filterNot { it in days }
            if (missing.isNotEmpty()) releaseError("track:${track.track}", "missing days $missing")
            days.filter { it !in 1..track.length }.forEach { error("track:${track.track}", "day $it outside 1..${track.length}") }
            for (d in track.days) {
                if (d.evidence.isEmpty()) error(d.id, "needs at least one evidence entry")
                if (words(d.insight) > 120) error(d.id, "insight is ${words(d.insight)} words (max 120)")
                // Rule 6: Shelter content needs expert sign-off (D-015, US-026).
                if (d.reviewedBy.isNullOrBlank() || d.reviewedOn.isNullOrBlank()) reviewMissing(d.id)
            }
        }
        if (bundle.sos.reviewedBy.isNullOrBlank() || bundle.sos.reviewedOn.isNullOrBlank()) {
            reviewMissing("sos")
        }

        // Rule 7: banned phrases, whole-phrase and case-insensitive; an override needs a reviewer.
        val matchers = bannedPhrases.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { it to phraseRegex(it) }
        fun scan(id: String, texts: List<String?>, allow: List<String>, reviewedBy: String?) {
            val text = texts.filterNotNull().joinToString("\n")
            for ((phrase, regex) in matchers) {
                val hit = regex.find(text) ?: continue
                val allowed = allow.any { it.equals(hit.value, ignoreCase = true) } && !reviewedBy.isNullOrBlank()
                if (!allowed) error(id, "banned phrase \"$phrase\" in \"${hit.value}\"")
            }
        }
        bundle.quests.forEach { scan(it.id, listOf(it.title, it.why), it.allowPhrases, it.reviewedBy) }
        bundle.tracks.flatMap { it.days }.forEach {
            scan(it.id, listOf(it.title, it.insight, it.step.title, it.step.detail, it.journalPrompt), it.allowPhrases, it.reviewedBy)
        }
        bundle.tracks.forEach { scan("track:${it.track.name.lowercase()}", listOf(it.title, it.summary), emptyList(), null) }
        bundle.sparks.forEach { scan(it.id, listOf(it.text), emptyList(), null) }
        bundle.insights.forEach { scan(it.id, listOf(it.title, it.body), emptyList(), null) }
        bundle.sos.templates.forEach { scan(it.id, listOf(it.text), emptyList(), null) }
        bundle.sos.grounding.forEach { scan(it.id, listOf(it.title, it.body), emptyList(), null) }

        // Rule 8: helplines.
        val countries = bundle.helplines.map { it.country }
        countries.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { error("helpline:$it", "country listed twice") }
        (LAUNCH_COUNTRIES - countries.toSet()).sorted().forEach { releaseError("helpline:$it", "launch country missing") }
        for (h in bundle.helplines) {
            val id = "helpline:${h.country}"
            if (!Regex("^[A-Z]{2}$").matches(h.country)) error(id, "country must be ISO alpha-2 upper case")
            if (!PHONE.matches(h.emergency)) error(id, "emergency number '${h.emergency}' isn't plausible")
            for (line in h.lines) {
                if (line.phone == null && line.sms == null) error(id, "${line.name}: needs a phone or sms number")
                line.phone?.let { if (!PHONE.matches(it)) error(id, "${line.name}: phone '$it' isn't plausible") }
                line.sms?.let { if (!PHONE.matches(it)) error(id, "${line.name}: sms '$it' isn't plausible") }
            }
            if (h.lines.isEmpty()) {
                if (h.country in EMERGENCY_ONLY_COUNTRIES) {
                    issues.add(Issue(Severity.WARNING, id, "no helpline lines: emergency number and findahelpline.com only (by decision)"))
                } else {
                    releaseError(id, "no helpline lines (users get the emergency fallback only)")
                }
            }
            val verified = h.verifiedOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            when {
                h.verifiedOn != null && verified == null -> error(id, "verified_on '${h.verifiedOn}' isn't an ISO date")
                verified == null -> releaseError(id, "not verified yet")
                ChronoUnit.DAYS.between(verified, buildDate) > HELPLINE_MAX_AGE_DAYS ->
                    releaseError(id, "verified $verified, more than $HELPLINE_MAX_AGE_DAYS days before $buildDate")
            }
        }

        // D-031: low mood and Low energy only ever get Spark quests, so enough of them must exist.
        val sparkQuests = bundle.quests.count { it.level == Level.SPARK }
        if (sparkQuests < MIN_QUESTS_PER_CELL) error("coverage", "only $sparkQuests Spark quests (need ≥ $MIN_QUESTS_PER_CELL for low-energy days)")

        // Rule 9: coverage, so the 30-day no-repeat window doesn't run dry.
        for (realm in Realm.entries) for (level in Level.entries) {
            val n = bundle.quests.count { it.realm == realm && it.level == level }
            if (n < MIN_QUESTS_PER_CELL) {
                issues.add(Issue(Severity.WARNING, "coverage", "${realm.name.lowercase()}/${level.name.lowercase()} has $n quests (want ≥ $MIN_QUESTS_PER_CELL)"))
            }
        }
        return issues
    }

    /** "cure your" matches the whole phrase; a trailing `*` makes it a prefix ("diagnos*" matches "diagnosis"). */
    internal fun phraseRegex(phrase: String): Regex {
        val prefix = phrase.endsWith("*")
        val core = Regex.escape(phrase.removeSuffix("*")).replace(" ", "\\E\\s+\\Q")
        return Regex("(?<![\\p{L}])$core${if (prefix) "[\\p{L}]*" else "(?![\\p{L}])"}", RegexOption.IGNORE_CASE)
    }

    private fun words(text: String) = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
}
