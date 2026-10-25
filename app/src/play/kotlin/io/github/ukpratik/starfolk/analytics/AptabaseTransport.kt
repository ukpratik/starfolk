// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.analytics

import android.os.Build
import io.github.ukpratik.starfolk.BuildConfig
import io.github.ukpratik.starfolk.data.core.EventQueueEntity
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Aptabase ingestion over plain HTTPS (ADR-006): no SDK, no device ID. The session ID is random per process and never
 * stored. System props are limited to OS major version, app version and language. Verify the endpoint and payload
 * against Aptabase's docs when the key is created (it isn't exercised until then).
 */
class AptabaseTransport(private val appKey: String) : Transport {
    private val host = when {
        appKey.startsWith("A-EU-") -> "https://eu.aptabase.com"
        appKey.startsWith("A-US-") -> "https://us.aptabase.com"
        else -> "https://eu.aptabase.com"
    }

    override suspend fun send(batch: List<EventQueueEntity>): Boolean = withContext(Dispatchers.IO) {
        val body = buildJsonArray {
            for (e in batch) add(buildJsonObject {
                put("timestamp", Instant.ofEpochMilli(e.hourMillis).toString())
                put("sessionId", SESSION)
                put("eventName", e.name)
                put("systemProps", buildJsonObject {
                    put("isDebug", BuildConfig.DEBUG)
                    put("osName", "Android")
                    put("osVersion", Build.VERSION.RELEASE.substringBefore('.'))
                    put("locale", Locale.getDefault().language)
                    put("appVersion", BuildConfig.VERSION_NAME)
                    put("sdkVersion", "starfolk-http@1")
                })
                put("props", Json.parseToJsonElement(e.propsJson) as JsonObject)
            })
        }.toString()
        runCatching {
            val conn = URL("$host/api/v0/events").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000; conn.readTimeout = 10_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("App-Key", appKey)
            conn.outputStream.use { it.write(body.toByteArray()) }
            val ok = conn.responseCode in 200..299
            conn.disconnect()
            ok
        }.getOrDefault(false)
    }

    private companion object {
        val SESSION: String = UUID.randomUUID().toString()
    }
}
