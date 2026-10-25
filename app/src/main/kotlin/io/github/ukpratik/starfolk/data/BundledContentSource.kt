// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.data

import android.content.res.AssetManager
import io.github.ukpratik.starfolk.core.content.ContentBundle
import io.github.ukpratik.starfolk.core.content.ContentJson
import io.github.ukpratik.starfolk.core.content.ContentSource
import io.github.ukpratik.starfolk.core.content.HelplineCountry
import io.github.ukpratik.starfolk.core.content.Insight
import io.github.ukpratik.starfolk.core.content.Manifest
import io.github.ukpratik.starfolk.core.content.Quest
import io.github.ukpratik.starfolk.core.content.SosContent
import io.github.ukpratik.starfolk.core.content.Spark
import io.github.ukpratik.starfolk.core.content.Track
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

/**
 * Reads the packs that :tools:content wrote into assets/content (ADR-008). Every pack is checked against its sha256
 * in the manifest; a mismatch is a corrupt install, reported as [CorruptContentException].
 */
class BundledContentSource(private val assets: AssetManager) : ContentSource {

    class CorruptContentException(message: String) : IllegalStateException(message)

    private val mutex = Mutex()
    private var cached: Pair<Manifest, ContentBundle>? = null

    override suspend fun manifest(): Manifest = load().first
    override suspend fun bundle(): ContentBundle = load().second

    private suspend fun load(): Pair<Manifest, ContentBundle> = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) { read() }.also { cached = it }
    }

    private fun read(): Pair<Manifest, ContentBundle> {
        val manifest = ContentJson.decodeFromString(Manifest.serializer(), text("manifest.json"))
        val packs = manifest.packs.associate { entry ->
            val json = text(entry.file)
            if (sha256(json) != entry.sha256) throw CorruptContentException("pack ${entry.id} failed its checksum")
            entry.id to json
        }
        fun pack(id: String) = packs[id] ?: throw CorruptContentException("pack $id missing from the manifest")
        val bundle = ContentBundle(
            quests = ContentJson.decodeFromString(ListSerializer(Quest.serializer()), pack("quests")),
            tracks = packs.filterKeys { it.startsWith("shelter-") }.values.map { ContentJson.decodeFromString(Track.serializer(), it) },
            sparks = ContentJson.decodeFromString(ListSerializer(Spark.serializer()), pack("sparks")),
            insights = ContentJson.decodeFromString(ListSerializer(Insight.serializer()), pack("insights")),
            helplines = ContentJson.decodeFromString(ListSerializer(HelplineCountry.serializer()), pack("helplines")),
            sos = ContentJson.decodeFromString(SosContent.serializer(), pack("sos")),
        )
        return manifest to bundle
    }

    private fun text(file: String) = assets.open("content/$file").bufferedReader().use { it.readText() }

    private fun sha256(text: String) =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
