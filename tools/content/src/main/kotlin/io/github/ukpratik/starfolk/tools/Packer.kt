// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.tools

import io.github.ukpratik.starfolk.core.content.ContentBundle
import io.github.ukpratik.starfolk.core.content.ContentJson
import io.github.ukpratik.starfolk.core.content.HelplineCountry
import io.github.ukpratik.starfolk.core.content.Insight
import io.github.ukpratik.starfolk.core.content.Manifest
import io.github.ukpratik.starfolk.core.content.PackEntry
import io.github.ukpratik.starfolk.core.content.Quest
import io.github.ukpratik.starfolk.core.content.SosContent
import io.github.ukpratik.starfolk.core.content.Spark
import io.github.ukpratik.starfolk.core.content.Track
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.builtins.ListSerializer

/** Writes the app's asset packs + manifest.json (LLD §3.1). The output is a pure function of the input. */
object Packer {
    const val SCHEMA_VERSION = 1

    fun pack(bundle: ContentBundle, meta: ContentMeta, out: File): Manifest {
        out.deleteRecursively()
        out.mkdirs()
        val packs = mutableListOf<PackEntry>()
        fun write(id: String, json: String, count: Int) {
            val file = "$id.json"
            out.resolve(file).writeText(json)
            packs += PackEntry(id, meta.packVersions[id] ?: 1, file, sha256(json), count)
        }
        write("quests", ContentJson.encodeToString(ListSerializer(Quest.serializer()), bundle.quests.sortedBy { it.id }), bundle.quests.size)
        for (track in bundle.tracks) {
            val id = "shelter-${track.track.name.lowercase()}"
            write(id, ContentJson.encodeToString(Track.serializer(), track.copy(days = track.days.sortedBy { it.day })), track.days.size)
        }
        write("sparks", ContentJson.encodeToString(ListSerializer(Spark.serializer()), bundle.sparks.sortedBy { it.id }), bundle.sparks.size)
        write("insights", ContentJson.encodeToString(ListSerializer(Insight.serializer()), bundle.insights.sortedBy { it.id }), bundle.insights.size)
        write("helplines", ContentJson.encodeToString(ListSerializer(HelplineCountry.serializer()), bundle.helplines.sortedBy { it.country }), bundle.helplines.size)
        write("sos", ContentJson.encodeToString(SosContent.serializer(), bundle.sos), bundle.sos.templates.size + bundle.sos.grounding.size)
        val manifest = Manifest(SCHEMA_VERSION, meta.contentVersion, meta.date, packs)
        out.resolve("manifest.json").writeText(ContentJson.encodeToString(Manifest.serializer(), manifest))
        return manifest
    }

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
