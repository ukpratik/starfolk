// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.content

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.starfolk.core.content.ContentRules.Mode
import io.github.ukpratik.starfolk.core.content.ContentRules.Severity
import java.time.LocalDate
import org.junit.Test

class ContentRulesTest {
    private val today = LocalDate.parse("2026-10-25")
    private val banned = listOf("cure your", "treats depression", "you should have", "diagnos*")

    private fun quest(id: String = "q-connect-0001", title: String = "Text one friend: “thought of you.”", level: Level = Level.SPARK, minutes: Int = 2, realm: Realm = Realm.CONNECT, stamp: Int? = null, insightId: String? = null, allow: List<String> = emptyList(), reviewedBy: String? = null) =
        Quest(id, realm, level, minutes, title, "Small reach-outs keep friendships alive.", insightId = insightId, evidence = listOf("Hall 2018"), stamp = stamp, allowPhrases = allow, reviewedBy = reviewedBy)

    private fun day(n: Int, reviewed: Boolean = true) = ShelterDay(
        id = "s-breakup-%02d".format(n), day = n, title = "Day $n", insight = "A short insight.",
        step = ShelterStep("One small step", "Detail."), evidence = listOf("Pennebaker"),
        reviewedBy = if (reviewed) "Dr Test" else null, reviewedOn = if (reviewed) "2026-10-01" else null,
    )

    private fun helpline(c: String, verified: String? = "2026-10-20") =
        HelplineCountry(c, listOf(HelplineLine("Line", phone = "116 123", hours = "24/7")), "112", verified, "https://example.org")

    private val sixSparks = (1..6).map { quest("q-connect-%04d".format(it)) }

    private fun bundle(
        quests: List<Quest> = sixSparks,
        days: List<ShelterDay> = listOf(day(1), day(2)),
        helplines: List<HelplineCountry> = ContentRules.LAUNCH_COUNTRIES.map { helpline(it) },
    ) = ContentBundle(
        quests = quests,
        tracks = listOf(Track(TrackId.BREAKUP, "Breakup", "21 days", 2, days)),
        sparks = listOf(Spark("sp-0001", "The people you miss are probably missing you too.", "probably missing you too.")),
        insights = listOf(Insight("i-liking-gap", "They liked you more than you think.", "People underestimate how much others liked them.", "Boothby 2018")),
        helplines = helplines,
        sos = SosContent(listOf(SosTemplate("sos-reach-01", "Can you call me?")), listOf(GroundingStep("sos-ground-01", "5 things", "Name 5 things you can see.")), "Dr Test", "2026-10-01"),
    )

    private fun lockOf(b: ContentBundle) = buildSet {
        b.quests.forEach { add(it.id) }; b.tracks.flatMap { it.days }.forEach { add(it.id) }
        b.sparks.forEach { add(it.id) }; b.insights.forEach { add(it.id) }
        b.sos.templates.forEach { add(it.id) }; b.sos.grounding.forEach { add(it.id) }
    }

    private fun errors(b: ContentBundle, mode: Mode = Mode.DRAFT, lock: Set<String> = lockOf(b)) =
        ContentRules.validate(b, lock, banned, mode, today).filter { it.severity == Severity.ERROR }.map { it.toString() }

    @Test fun `a clean bundle has no errors`() {
        assertThat(errors(bundle())).isEmpty()
        assertThat(errors(bundle(), Mode.RELEASE)).isEmpty()
    }

    @Test fun `ids must match the pattern, be unique and be in ids lock`() {
        val b = bundle(quests = sixSparks.drop(1) + listOf(quest("q-connect-1"), quest("q-connect-0002"), quest("q-connect-0002")))
        val e = errors(b)
        assertThat(e.any { "q-connect-1" in it && "doesn't match" in it }).isTrue()
        assertThat(e.any { "q-connect-0002" in it && "duplicate" in it }).isTrue()
        assertThat(errors(bundle(), lock = emptySet()).any { "ids.lock" in it }).isTrue()
    }

    @Test fun `references, levels and adventure stamps are checked`() {
        assertThat(errors(bundle(quests = sixSparks.drop(1) + listOf(quest(insightId = "i-nope")))).single()).contains("insightId")
        assertThat(errors(bundle(quests = sixSparks.drop(1) + listOf(quest(minutes = 5)))).single()).contains("Spark takes at most 2")
        assertThat(errors(bundle(quests = sixSparks + listOf(quest("q-adventure-0001", realm = Realm.ADVENTURE, level = Level.FLAME, minutes = 30))))).isNotEmpty()
        assertThat(errors(bundle(quests = sixSparks + listOf(quest("q-adventure-0001", realm = Realm.ADVENTURE, level = Level.FLAME, minutes = 30, stamp = 3))))).isEmpty()
    }

    @Test fun `banned phrases match whole phrases only, and a prefix entry matches word forms`() {
        assertThat(errors(bundle(quests = sixSparks.drop(1) + listOf(quest(title = "This will cure your heartbreak."))))).isNotEmpty()
        assertThat(errors(bundle(quests = sixSparks.drop(1) + listOf(quest(title = "Get a self-diagnosis quiz."))))).isNotEmpty()
        assertThat(errors(bundle(quests = sixSparks.drop(1) + listOf(quest(title = "A secure yourself moment."))))).isEmpty()
    }

    @Test fun `an allowPhrases override needs a reviewer`() {
        val title = "You should have a bath tonight."
        assertThat(errors(bundle(quests = sixSparks.drop(1) + listOf(quest(title = title, allow = listOf("you should have")))))).isNotEmpty()
        assertThat(errors(bundle(quests = sixSparks.drop(1) + listOf(quest(title = title, allow = listOf("you should have"), reviewedBy = "Dr Test"))))).isEmpty()
    }

    @Test fun `draft tolerates gaps and missing reviews, release does not`() {
        val b = bundle(days = listOf(day(1, reviewed = false)))
        assertThat(errors(b)).isEmpty()
        val release = errors(b, Mode.RELEASE)
        assertThat(release.any { "missing days [2]" in it }).isTrue()
        assertThat(release.any { "reviewed_by" in it }).isTrue()
    }

    @Test fun `helplines must be plausible, complete and fresh for a release`() {
        val stale = ContentRules.LAUNCH_COUNTRIES.map { if (it == "GB") helpline(it, "2026-01-01") else helpline(it) }
        assertThat(errors(bundle(helplines = stale), Mode.RELEASE).single()).contains("more than 90 days")
        val missing = ContentRules.LAUNCH_COUNTRIES.filter { it != "KE" }.map { helpline(it) }
        assertThat(errors(bundle(helplines = missing), Mode.RELEASE).single()).contains("launch country missing")
        assertThat(errors(bundle(helplines = missing))).isEmpty()
        val bad = listOf(HelplineCountry("GB", listOf(HelplineLine("X", phone = "call us", hours = "24/7")), "999", "2026-10-20", "s"))
        assertThat(errors(bundle(helplines = bad)).any { "isn't plausible" in it }).isTrue()
    }

    @Test fun `fewer than six Spark quests is an error`() {
        assertThat(errors(bundle(quests = sixSparks.take(5))).single()).contains("Spark quests")
    }

    @Test fun `the strict JSON rejects unknown fields`() {
        val json = """{"id":"sp-0001","text":"Hi","colour":"red"}"""
        val result = runCatching { ContentJson.decodeFromString(Spark.serializer(), json) }
        assertThat(result.isFailure).isTrue()
    }
}
