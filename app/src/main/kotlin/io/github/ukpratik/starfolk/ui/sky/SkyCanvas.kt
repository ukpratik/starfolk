// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.ui.sky

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.ukpratik.starfolk.core.sky.SkyModel
import io.github.ukpratik.starfolk.core.time.SplitMix64
import io.github.ukpratik.starfolk.ui.theme.StarfolkType
import io.github.ukpratik.starfolk.ui.theme.Tokens
import kotlin.math.roundToInt

/**
 * The collage sky (US-007, ADR-007): one Canvas, static between interactions, drawing pre-rasterised stickers at the
 * positions [SkyModel] computed in :core. Positions are in dp; the model must be computed for this canvas's dp size.
 */
@Composable
fun SkyCanvas(
    model: SkyModel,
    description: String,
    seed: Long,
    modifier: Modifier = Modifier,
    tornBottom: Boolean = true,
    palette: Int = 0,
    /** Star names by completion id, from private.db; drawn in handwriting next to the star (US-008). */
    names: Map<Long, String> = emptyMap(),
    /** A just-earned star or stamp pops in (US-007): scale 0 → 1 with a spring. */
    fresh: Long? = null,
) {
    val reduced = io.github.ukpratik.starfolk.ui.theme.LocalReducedMotion.current
    val pop = remember(fresh) { Animatable(if (fresh == null || reduced) 1f else 0f) }
    LaunchedEffect(fresh) { if (fresh != null && !reduced) pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 180f)) }
    val density = LocalDensity.current
    val atlas = remember(density.density) { StickerAtlas.forDensity(density.density) }
    val measurer = rememberTextMeasurer()
    val labelStyle = StarfolkType.Hand.copy(color = Tokens.OnNight, fontSize = StarfolkType.Hand.fontSize * 0.8f)
    // Cluster labels are measured once per model, not on every frame.
    val nameStyle = StarfolkType.Hand.copy(color = Tokens.OnNight)
    val nameLayouts = remember(density.density) { HashMap<String, androidx.compose.ui.text.TextLayoutResult>() }
    val labels = remember(model, density.density) {
        model.clusters.map { measurer.measure(it.label, labelStyle, maxLines = 1, softWrap = false) }
    }
    Canvas(modifier.semantics { contentDescription = description }) {
        val d = density.density
        paper(seed, tornBottom, SkyPalettes[palette.coerceIn(0, SkyPalettes.lastIndex)])
        // Care warms the paper (US-007): a lamp-coloured glow from the horizon, up to 35% after Care on 3 recent days.
        if (model.warmth > 0f) drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Tokens.Lamp.copy(alpha = 0.35f * model.warmth)), startY = size.height * 0.35f, endY = size.height),
        )
        for (dust in model.dust) drawCircle(Tokens.OnNight.copy(alpha = 0.55f), dust.radius * d, Offset(dust.x * d, dust.y * d))
        for (s in model.starters + model.stars) {
            val scale = if (s.completionId == fresh) pop.value else 1f
            sticker(atlas.star, s.x * d, s.y * d, s.rotation, 34f * d * s.size * scale)
            names[s.completionId]?.let { name ->
                val label = nameLayouts.getOrPut(name) { measurer.measure(name, nameStyle, maxLines = 1, softWrap = false) }
                val lx = (s.x * d + 20f * d).coerceAtMost(size.width - label.size.width - 6f * d)
                drawText(label, topLeft = Offset(lx, s.y * d - label.size.height / 2f), alpha = scale.coerceIn(0f, 1f))
            }
        }
        for ((ci, c) in model.clusters.withIndex()) {
            for (i in 0 until minOf(c.count, 5)) {
                val o = SplitMix64(c.x.toLong() * 31 + i).let { Offset((it.nextFloat() - 0.5f) * 36f * d, (it.nextFloat() - 0.5f) * 24f * d) }
                sticker(atlas.star, c.x * d + o.x, c.y * d + o.y, 0f, 18f * d)
            }
            val label = labels[ci]
            val lx = (c.x * d + 22f * d).coerceAtMost(size.width - label.size.width - 8f * d).coerceAtLeast(8f * d)
            drawText(label, topLeft = Offset(lx, c.y * d - 10f * d))
        }
        for (st in model.stamps) sticker(atlas.stamps[st.category], st.x * d, st.y * d, st.rotation, 58f * d * if (st.completionId == fresh) pop.value else 1f)
        if (model.flameStage > 0) sticker(atlas.flames[model.flameStage - 1], size.width - 70f * d, size.height * 0.8f, -4f, 52f * d)
    }
}

/** Night paper with a torn bottom edge, drawn as a filled path (D-026: no clipping). Warmth tints it for Care. */
private fun DrawScope.paper(seed: Long, tornBottom: Boolean, palette: SkyPalette) {
    val brush = Brush.radialGradient(
        listOf(palette.light, palette.mid, palette.deep),
        center = Offset(size.width * 0.7f, size.height * 0.1f),
        radius = size.maxDimension * 0.9f,
    )
    if (!tornBottom) { drawRect(brush); return }
    val rng = SplitMix64(seed xor 0x7EA2L)
    val path = Path().apply {
        moveTo(0f, 0f); lineTo(size.width, 0f)
        var x = size.width
        lineTo(x, size.height * 0.95f)
        while (x > 0f) {
            x -= size.width * (0.04f + rng.nextFloat() * 0.03f)
            lineTo(x.coerceAtLeast(0f), size.height * (0.92f + rng.nextFloat() * 0.06f))
        }
        close()
    }
    drawPath(path, brush)
}

private fun DrawScope.sticker(image: androidx.compose.ui.graphics.ImageBitmap, cx: Float, cy: Float, rotation: Float, width: Float) {
    val h = width * image.height / image.width
    rotate(rotation, Offset(cx, cy)) {
        drawImage(
            image,
            dstOffset = IntOffset((cx - width / 2).roundToInt(), (cy - h / 2).roundToInt()),
            dstSize = IntSize(width.roundToInt(), h.roundToInt()),
        )
    }
}
