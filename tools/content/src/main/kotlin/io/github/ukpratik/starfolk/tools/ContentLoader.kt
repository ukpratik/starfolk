// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.tools

import io.github.ukpratik.starfolk.core.content.ContentBundle
import io.github.ukpratik.starfolk.core.content.ContentJson
import io.github.ukpratik.starfolk.core.content.HelplineCountry
import io.github.ukpratik.starfolk.core.content.Insight
import io.github.ukpratik.starfolk.core.content.Quest
import io.github.ukpratik.starfolk.core.content.SosContent
import io.github.ukpratik.starfolk.core.content.Spark
import io.github.ukpratik.starfolk.core.content.Track
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** content/v1/meta.json: versions live in the repo, so packing is reproducible (no build timestamps, ADR-011). */
@Serializable
data class ContentMeta(val contentVersion: String, val date: String, val packVersions: Map<String, Int>)

/** Reads the authoring layout (LLD §1): the quests and shelter folders, sparks, insights, helplines, sos. */
class ContentLoader(private val dir: File) {

    class LoadError(message: String) : Exception(message)

    fun meta(): ContentMeta = read("meta.json") { ContentJson.decodeFromString(ContentMeta.serializer(), it) }

    fun load(): ContentBundle = ContentBundle(
        quests = files("quests").flatMap { f -> read(f) { ContentJson.decodeFromString(ListSerializer(Quest.serializer()), it) } },
        tracks = files("shelter").map { f -> read(f) { ContentJson.decodeFromString(Track.serializer(), it) } }.sortedBy { it.track },
        sparks = read("sparks.json") { ContentJson.decodeFromString(ListSerializer(Spark.serializer()), it) },
        insights = read("insights.json") { ContentJson.decodeFromString(ListSerializer(Insight.serializer()), it) },
        helplines = read("helplines.json") { ContentJson.decodeFromString(ListSerializer(HelplineCountry.serializer()), it) },
        sos = read("sos.json") { ContentJson.decodeFromString(SosContent.serializer(), it) },
    )

    fun idsLock(): List<String> = dir.resolve("../ids.lock").takeIf { it.exists() }?.readLines()
        ?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") } ?: emptyList()

    fun bannedPhrases(): List<String> = dir.resolve("banned-phrases.txt").readLines()

    private fun files(sub: String): List<String> =
        dir.resolve(sub).listFiles { f -> f.extension == "json" }?.map { "$sub/${it.name}" }?.sorted()
            ?: throw LoadError("missing directory $sub/")

    private fun <T> read(path: String, parse: (String) -> T): T {
        val file = dir.resolve(path)
        if (!file.exists()) throw LoadError("missing $path")
        return try {
            parse(file.readText())
        } catch (e: Exception) {
            throw LoadError("$path: ${e.message?.lineSequence()?.first()}")
        }
    }
}
