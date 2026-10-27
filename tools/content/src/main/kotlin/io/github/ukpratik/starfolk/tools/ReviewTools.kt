// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.tools

import io.github.ukpratik.starfolk.core.content.ContentBundle
import io.github.ukpratik.starfolk.core.content.HelplineCountry
import io.github.ukpratik.starfolk.core.content.SosContent
import io.github.ukpratik.starfolk.core.content.Track
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Release helpers for the two human gates (US-026, D-015, D-011):
 * - [reviewPack]: every Shelter day and SOS text as one Markdown document for the psychologist.
 * - [sign]: stamps reviewed_by / reviewed_on on reviewed items (the validator's release mode requires them).
 * - [verifyHelpline]: stamps verified_on after a person checked a country's numbers against its source.
 */
object ReviewTools {
    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  "; explicitNulls = false; encodeDefaults = false }

    /** Ties a review to exact text: any change to a Shelter day or SOS item changes it. */
    fun fingerprint(b: ContentBundle): String {
        val text = b.tracks.sortedBy { it.track.ordinal }.toString() + b.sos.toString()
        return java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
    }

    fun reviewPack(b: ContentBundle): String = buildString {
        appendLine("# Starfolk: expert review pack (Shelter + SOS)")
        appendLine()
        appendLine("Starfolk is a free self-help app (not therapy). Please review every item below for: clinical accuracy, safety (no harm, no method depiction, appropriate escalation to professional help), no shame or blame, no diagnosis or treatment claims, and tone. Mark each item ✅ OK, ✏️ change (with a suggested edit), or ❌ remove.")
        appendLine()
        appendLine("Content fingerprint: `${fingerprint(b)}` (sha256 of the Shelter tracks and SOS content). A sign-off covers exactly this text; regenerate the pack after any edit.")
        appendLine()
        appendLine("| | Reviewer | Credential | Date | Signature |\n|---|---|---|---|---|\n| Sign-off | | | | |")
        for (t in b.tracks.sortedBy { it.track.ordinal }) {
            appendLine(); appendLine("## ${t.title} (${t.length} days)"); appendLine(); appendLine("_${t.summary}_")
            for (d in t.days.sortedBy { it.day }) {
                appendLine(); appendLine("### ${d.id} · Day ${d.day}: ${d.title}")
                appendLine(); appendLine(d.insight)
                appendLine(); appendLine("**Step:** ${d.step.title} — ${d.step.detail}")
                d.journalPrompt?.let { appendLine(); appendLine("**Journal prompt:** $it") }
                appendLine(); appendLine("_Evidence: ${d.evidence.joinToString("; ")}_")
                appendLine(); appendLine("- [ ] ✅ OK  - [ ] ✏️ Change: ______  - [ ] ❌ Remove")
            }
        }
        appendLine(); appendLine("## SOS: reach-someone templates")
        for (t in b.sos.templates) { appendLine(); appendLine("**${t.id}:** ${t.text}"); appendLine(); appendLine("- [ ] ✅  - [ ] ✏️ ______  - [ ] ❌") }
        appendLine(); appendLine("## SOS: grounding (5-4-3-2-1)")
        for (g in b.sos.grounding) { appendLine(); appendLine("**${g.id}: ${g.title}** ${g.body}"); appendLine(); appendLine("- [ ] ✅  - [ ] ✏️ ______  - [ ] ❌") }
    }

    /** Signs [ids] (or every Shelter day and the SOS block with "all") in the authoring files under [dir]. */
    fun sign(dir: File, ids: Set<String>, by: String, on: LocalDate): Int {
        require(by.isNotBlank()) { "reviewer name and credential are required" }
        var count = 0
        dir.resolve("shelter").listFiles { f -> f.extension == "json" }!!.forEach { f ->
            val track = Json.decodeFromString(Track.serializer(), f.readText())
            val signed = track.copy(days = track.days.map { d ->
                if ("all" in ids || d.id in ids) { count++; d.copy(reviewedBy = by, reviewedOn = on.toString()) } else d
            })
            f.writeText(pretty.encodeToString(Track.serializer(), signed) + "\n")
        }
        if ("all" in ids || "sos" in ids) {
            val f = dir.resolve("sos.json")
            val sos = Json.decodeFromString(SosContent.serializer(), f.readText())
            f.writeText(pretty.encodeToString(SosContent.serializer(), sos.copy(reviewedBy = by, reviewedOn = on.toString())) + "\n")
            count++
        }
        return count
    }

    fun verifyHelpline(dir: File, country: String, on: LocalDate): Boolean {
        val f = dir.resolve("helplines.json")
        val list = Json.decodeFromString(ListSerializer(HelplineCountry.serializer()), f.readText())
        if (list.none { it.country == country }) return false
        val updated = list.map { if (it.country == country) it.copy(verifiedOn = on.toString()) else it }
        f.writeText(pretty.encodeToString(ListSerializer(HelplineCountry.serializer()), updated) + "\n")
        return true
    }

}
