// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.ui.sky

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import kotlin.math.cos
import kotlin.math.sin

/**
 * The sky's stickers, rasterised once (ADR-007, D-026): foil star, 8 adventure stamp categories, 3 flame stages.
 * Shadows and perforations are baked in, so the sky draws only bitmaps. Placeholder art until the D-025 set lands.
 */
class StickerAtlas private constructor(private val px: Float) {
    companion object {
        private val cache = HashMap<Float, StickerAtlas>()

        /** One atlas per density for the whole process, so switching tabs never re-rasterises stickers (D-012 spike). */
        @Synchronized
        fun forDensity(px: Float): StickerAtlas = cache.getOrPut(px) { StickerAtlas(px) }

        private const val FLAME = 99
        // outdoors, water, music, food, travel, art, sport, night, flame
        private val PANEL = intArrayOf(0xFFDCE7F5.toInt(), 0xFFCDE6EE.toInt(), 0xFFE7D7F2.toInt(), 0xFFFFE2C2.toInt(), 0xFFE2EBD3.toInt(), 0xFFF8D3E4.toInt(), 0xFFC7EBD3.toInt(), 0xFF24336A.toInt(), 0xFFFFF1D6.toInt())
    }

    val star: ImageBitmap by lazy { star(px * 40) }
    val stamps: List<ImageBitmap> by lazy { (0 until 8).map { stamp(it, (px * 58).toInt(), (px * 66).toInt()) } }
    val flames: List<ImageBitmap> by lazy { (1..3).map { stamp(FLAME, (px * 52).toInt(), (px * 60).toInt(), flameStage = it) } }

    private fun star(size: Float): ImageBitmap {
        val s = size.toInt()
        val bmp = createBitmap(s, s)
        val c = Canvas(bmp)
        val path = starPath(s / 2f, s / 2f, s * 0.46f, s * 0.2f)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x59000000 }
        c.withTranslation(px * 1f, px * 2f) { drawPath(path, shadow) }
        val foil = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, s.toFloat(), s.toFloat(), intArrayOf(0xFFFFF1B0.toInt(), 0xFFF5C451.toInt(), 0xFFB9831C.toInt()), null, Shader.TileMode.CLAMP)
        }
        c.drawPath(path, foil)
        return bmp.asImageBitmap()
    }

    private fun stamp(category: Int, w: Int, h: Int, flameStage: Int = 0): ImageBitmap {
        val bmp = createBitmap(w + (px * 3).toInt(), h + (px * 4).toInt())
        val c = Canvas(bmp)
        val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF3EBDD.toInt() }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x59000000 }
        val edge = perforated(w.toFloat(), h.toFloat(), px * 3f)
        c.withTranslation(px * 2f, px * 3f) { drawPath(edge, shadow) }
        c.drawPath(edge, paper)
        val inset = px * 6f
        val panel = RectF(inset, inset, w - inset, h - inset - px * 8f)
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PANEL[if (category == FLAME) 8 else category] }
        c.drawRect(panel, panelPaint)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1D1B22.toInt(); style = Paint.Style.STROKE; strokeWidth = px * 1.6f; strokeCap = Paint.Cap.ROUND }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1D1B22.toInt() }
        glyph(c, category, panel, ink, fill, flameStage)
        return bmp.asImageBitmap()
    }

    private fun glyph(c: Canvas, category: Int, r: RectF, ink: Paint, fill: Paint, flameStage: Int) {
        val cx = r.centerX(); val cy = r.centerY(); val u = r.width() / 10f
        when (category) {
            0 -> c.drawPath(Path().apply { moveTo(cx - 4 * u, cy + 3 * u); lineTo(cx - u, cy - 3 * u); lineTo(cx + u, cy); lineTo(cx + 2 * u, cy - u); lineTo(cx + 4 * u, cy + 3 * u); close() }, fill)
            1 -> for (i in 0..2) c.drawPath(Path().apply { val y = cy - 2 * u + i * 2 * u; moveTo(cx - 4 * u, y); cubicTo(cx - 2 * u, y - u, cx, y + u, cx + 4 * u, y) }, ink)
            2 -> { c.drawCircle(cx - u, cy + 2 * u, 1.3f * u, fill); c.drawLine(cx + 0.3f * u, cy + 2 * u, cx + 0.3f * u, cy - 3 * u, ink); c.drawLine(cx + 0.3f * u, cy - 3 * u, cx + 2.5f * u, cy - 2 * u, ink) }
            3 -> { c.drawArc(RectF(cx - 3.5f * u, cy - 2 * u, cx + 3.5f * u, cy + 3 * u), 0f, 180f, true, fill); c.drawLine(cx + u, cy - 3 * u, cx + 3 * u, cy - u, ink) }
            4 -> { c.drawLine(cx - 3 * u, cy + 3 * u, cx - u, cy - 3 * u, ink); c.drawLine(cx + 3 * u, cy + 3 * u, cx + u, cy - 3 * u, ink); c.drawLine(cx, cy + 2 * u, cx, cy + 0.5f * u, ink); c.drawLine(cx, cy - 0.5f * u, cx, cy - 1.5f * u, ink) }
            5 -> c.drawPath(Path().apply { moveTo(cx - 3 * u, cy + 2 * u); cubicTo(cx - u, cy - 4 * u, cx + u, cy + 4 * u, cx + 3 * u, cy - 2 * u) }, ink.apply { strokeWidth *= 2 })
            6 -> { c.drawCircle(cx, cy, 3 * u, ink); c.drawLine(cx - 3 * u, cy, cx + 3 * u, cy, ink); c.drawLine(cx, cy - 3 * u, cx, cy + 3 * u, ink) }
            7 -> { c.drawCircle(cx, cy, 3 * u, fill); c.drawCircle(cx + 1.5f * u, cy - u, 2.6f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PANEL[7] }) }
            FLAME -> {
                val s = 0.6f + flameStage * 0.18f
                val flame = Path().apply { moveTo(cx, cy - 4 * u * s); cubicTo(cx + 3 * u * s, cy - u * s, cx + 3 * u * s, cy + 3 * u * s, cx, cy + 3.5f * u * s); cubicTo(cx - 3 * u * s, cy + 3 * u * s, cx - 3 * u * s, cy - u * s, cx, cy - 4 * u * s); close() }
                c.drawPath(flame, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF29A1F.toInt() })
            }
        }
    }

    private fun perforated(w: Float, h: Float, r: Float): Path {
        val path = Path().apply { addRect(0f, 0f, w, h, Path.Direction.CW) }
        val holes = Path()
        var x = r * 2; while (x < w - r) { holes.addCircle(x, 0f, r, Path.Direction.CW); holes.addCircle(x, h, r, Path.Direction.CW); x += r * 3 }
        var y = r * 2; while (y < h - r) { holes.addCircle(0f, y, r, Path.Direction.CW); holes.addCircle(w, y, r, Path.Direction.CW); y += r * 3 }
        path.op(holes, Path.Op.DIFFERENCE)
        return path
    }

    private fun starPath(cx: Float, cy: Float, outer: Float, inner: Float) = Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val a = Math.toRadians(-90.0 + i * 36.0)
            val x = cx + (r * cos(a)).toFloat(); val y = cy + (r * sin(a)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
