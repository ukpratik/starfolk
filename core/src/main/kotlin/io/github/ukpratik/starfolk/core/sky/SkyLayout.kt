// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.sky

import io.github.ukpratik.starfolk.core.content.Realm
import io.github.ukpratik.starfolk.core.time.AppDay
import io.github.ukpratik.starfolk.core.time.SplitMix64
import java.time.format.TextStyle
import java.util.Locale

/** What the sky needs to know about one completed quest. Names and notes stay in private.db, never here. */
data class CompletionLite(val id: Long, val realm: Realm, val appDay: AppDay, val stampCategory: Int? = null)

/** A rectangle in dp, top-left origin. */
data class Box(val x: Float, val y: Float, val w: Float, val h: Float) {
    fun contains(px: Float, py: Float, margin: Float = 0f) =
        px >= x - margin && px <= x + w + margin && py >= y - margin && py <= y + h + margin
}

data class SkyStar(val completionId: Long, val x: Float, val y: Float, val rotation: Float, val size: Float)
data class SkyCluster(val x: Float, val y: Float, val count: Int, val label: String)
data class SkyStamp(val completionId: Long, val x: Float, val y: Float, val rotation: Float, val category: Int)
data class SkyDust(val x: Float, val y: Float, val radius: Float)

data class SkyModel(
    val stars: List<SkyStar>,
    val clusters: List<SkyCluster>,
    val stamps: List<SkyStamp>,
    /** Seeded stickers so day 0 never looks empty (US-007). Empty once the first real star exists. */
    val starters: List<SkyStar>,
    val dust: List<SkyDust>,
    /** 0 = no flame yet, 1–3 = Purpose stages (design-system §6). */
    val flameStage: Int,
    /** 0–1: Care quests in the last 3 app-days warm the paper. */
    val warmth: Float,
    val totalStars: Int,
    val totalStamps: Int,
) {
    val drawOps: Int get() = stars.size + clusters.size + stamps.size + starters.size + dust.size + if (flameStage > 0) 1 else 0
}

/**
 * The sky is a pure function of (seed, completions, today, canvas) (D-012, ADR-007): the same inputs always give the
 * same collage, a backup restore rebuilds it from completions, and it never decays.
 */
object SkyLayout {
    const val MAX_STARS = 40
    const val MAX_CLUSTER_SIZE = 12
    const val MAX_CLUSTERS = 20
    private const val MIN_CLUSTER_SIZE = 3
    const val MAX_STAMPS = 12
    const val DUST = 60
    const val MIN_DISTANCE_DP = 28f
    const val MAX_DRAW_OPS = 200
    private const val ATTEMPTS = 30
    private const val STAMP_SLOT_DP = 72f
    private const val STAMP_ROW_DP = 70f

    fun compute(
        seed: Long,
        completions: List<CompletionLite>,
        today: AppDay,
        width: Float,
        height: Float,
        /** Areas to keep clear (the date line, the SOS note). */
        keepClear: List<Box> = emptyList(),
        /** The Today strip shows only the newest few stickers and no constellations; the Sky tab shows everything. */
        maxStars: Int = MAX_STARS,
        maxStamps: Int = MAX_STAMPS,
        showClusters: Boolean = true,
    ): SkyModel {
        val ordered = completions.sortedBy { it.id }
        val connect = ordered.filter { it.realm == Realm.CONNECT }
        val adventure = ordered.filter { it.realm == Realm.ADVENTURE }
        val purposeCount = ordered.count { it.realm == Realm.PURPOSE }
        val careRecent = ordered.count { it.realm == Realm.CARE && today.daysSince(it.appDay) in 0..2 }

        // Stamps sit in rows on the horizon band; stars live above the top stamp row.
        val horizonY = height * 0.80f
        val stampCount = minOf(adventure.size, maxStamps)
        val columns = (width / STAMP_SLOT_DP).toInt().coerceAtLeast(1)
        val stampRows = (stampCount + columns - 1) / columns
        val starBottom = minOf(height * 0.72f, horizonY - stampRows * STAMP_ROW_DP + 10f).coerceAtLeast(height * 0.3f)
        val starArea = Box(16f, 16f, width - 32f, starBottom - 16f)
        val placed = mutableListOf<Pair<Float, Float>>()

        // Keep-clear areas always win; spacing is best effort. A crowded sky overlaps stickers, never the SOS note.
        fun place(key: Long, area: Box): Pair<Float, Float> {
            val rng = SplitMix64(seed xor (key * -0x61c8864680b583ebL))
            var fallback: Pair<Float, Float>? = null
            repeat(ATTEMPTS) {
                val candidate = (area.x + rng.nextFloat() * area.w) to (area.y + rng.nextFloat() * area.h)
                val (cx, cy) = candidate
                if (keepClear.any { it.contains(cx, cy, margin = 12f) }) return@repeat
                if (fallback == null) fallback = candidate
                if (placed.none { (px, py) -> dist2(px, py, cx, cy) < MIN_DISTANCE_DP * MIN_DISTANCE_DP }) return candidate.also { placed += it }
            }
            val chosen = fallback ?: (area.x + area.w / 2 to area.y + area.h - 1f)
            return chosen.also { placed += it }
        }
        fun rotation(key: Long) = SplitMix64(seed xor key xor 0x5EEDL).nextFloat() * 30f - 15f

        // Oldest stars beyond MAX_STARS merge into constellations, by month (then by year if there are too many).
        // An overflow too small for a constellation (1–2 stars) stays individual: at most maxStars + 2 stars.
        val keep = if (showClusters && connect.size - maxStars in 1 until MIN_CLUSTER_SIZE) connect.size else maxStars
        val individual = connect.takeLast(keep)
        val overflow = if (showClusters) connect.dropLast(keep) else emptyList()
        // Individual stars are placed first, oldest to newest, so adding a star never moves the ones already there.
        val stars = individual.map { c ->
            val (x, y) = place(c.id, starArea)
            val size = 0.8f + SplitMix64(seed xor c.id xor 0xA11CEL).nextFloat() * 0.6f
            SkyStar(c.id, x, y, rotation(c.id), size)
        }
        val clusters = clusterGroups(overflow).map { group ->
            val (x, y) = place(-(group.first().id + 1_000_000L), starArea)
            SkyCluster(x, y, group.size, label(group))
        }
        val starters = if (connect.isEmpty()) {
            (1..3).map { i ->
                val key = -i.toLong()
                val (x, y) = place(key, starArea)
                SkyStar(key, x, y, rotation(key), 0.9f)
            }
        } else emptyList()

        // Stamps sit in rows along the horizon, one 72 dp slot each, newest at the front.
        val visibleStamps = adventure.takeLast(maxStamps)
        val slot = width / columns
        val stamps = visibleStamps.mapIndexed { i, c ->
            val rng = SplitMix64(seed xor c.id xor 0x57A3BL)
            val x = slot * (i % columns + 0.5f) + (rng.nextFloat() - 0.5f) * slot * 0.2f
            val y = horizonY - (i / columns) * STAMP_ROW_DP + (rng.nextFloat() - 0.5f) * 10f
            SkyStamp(c.id, x, y, rotation(c.id), c.stampCategory ?: Math.floorMod(c.id, 8L).toInt())
        }

        val dustRng = SplitMix64(seed xor 0xD057L)
        val dust = List(DUST) { SkyDust(dustRng.nextFloat() * width, dustRng.nextFloat() * height * 0.85f, 0.6f + dustRng.nextFloat() * 0.8f) }

        return SkyModel(
            stars = stars,
            clusters = clusters,
            stamps = stamps,
            starters = starters,
            dust = dust,
            flameStage = flameStage(purposeCount),
            warmth = (careRecent / 3f).coerceIn(0f, 1f),
            totalStars = connect.size,
            totalStamps = adventure.size,
        ).also { check(it.drawOps <= MAX_DRAW_OPS) { "sky draw ops ${it.drawOps} > $MAX_DRAW_OPS" } }
    }

    fun flameStage(purposeCount: Int) = when {
        purposeCount == 0 -> 0
        purposeCount < 5 -> 1
        purposeCount < 15 -> 2
        else -> 3
    }

    /** The TalkBack text for the sky (US-007, NFR-008). [latestName] comes from private.db in the app layer. */
    fun describe(model: SkyModel, latestName: String?): String = buildString {
        append("Your sky: ")
        append(plural(model.totalStars, "star"))
        append(", ").append(plural(model.totalStamps, "stamp"))
        if (model.flameStage > 0) append(", flame stage ${model.flameStage}")
        append('.')
        if (latestName != null) append(" Last added: a star for $latestName.")
    }

    private fun clusterGroups(overflow: List<CompletionLite>): List<List<CompletionLite>> {
        if (overflow.isEmpty()) return emptyList()
        // Months with fewer than 3 stars join the next month, so there's never a one-star constellation.
        val months = mutableListOf<MutableList<CompletionLite>>()
        for (month in overflow.groupBy { it.appDay.date.withDayOfMonth(1) }.values) {
            val last = months.lastOrNull()
            if (last != null && last.size < MIN_CLUSTER_SIZE) last += month else months += month.toMutableList()
        }
        if (months.size > 1 && months.last().size < MIN_CLUSTER_SIZE) months[months.size - 2] += months.removeAt(months.size - 1)
        val byMonth = months.flatMap { balancedChunks(it, MAX_CLUSTER_SIZE) }
        if (byMonth.size <= MAX_CLUSTERS) return byMonth
        val byYear = overflow.groupBy { it.appDay.date.year }.values.toList()
        return byYear.takeLast(MAX_CLUSTERS)
    }

    /** Splits into the fewest chunks of at most [max], evenly sized (12 + 1 becomes 7 + 6, never 12 + 1). */
    private fun <T> balancedChunks(items: List<T>, max: Int): List<List<T>> {
        val n = (items.size + max - 1) / max
        val base = items.size / n; val extra = items.size % n
        var start = 0
        return List(n) { i -> val size = base + if (i < extra) 1 else 0; items.subList(start, start + size).also { start += size } }
    }

    private fun label(group: List<CompletionLite>): String {
        val first = group.first().appDay.date
        val last = group.last().appDay.date
        val friends = plural(group.size, "friend")
        return if (first.year == last.year && first.month == last.month) {
            "$friends · ${first.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${first.year}"
        } else if (first.year == last.year) {
            "$friends · ${first.year}"
        } else {
            "$friends · ${first.year}–${last.year}"
        }
    }

    private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
    private fun dist2(ax: Float, ay: Float, bx: Float, by: Float): Float { val dx = ax - bx; val dy = ay - by; return dx * dx + dy * dy }
}
