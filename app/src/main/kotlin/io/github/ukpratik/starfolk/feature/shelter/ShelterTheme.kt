// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.shelter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ukpratik.starfolk.ui.theme.StarfolkType
import io.github.ukpratik.starfolk.ui.theme.Tokens

/** The quiet register (D-010, design-system §2): linen and pencil; dark linen when the system is dark (D-026). */
@Immutable
data class Linen(val bg: Color, val card: Color, val rule: Color, val pencil: Color, val soft: Color, val sage: Color, val lamp: Color, val dark: Boolean)

@Composable
fun linen(): Linen = if (isSystemInDarkTheme()) {
    Linen(Color(0xFF211E26), Color(0xFF2A2630), Color(0xFF37323E), Color(0xFFE4DCCD), Color(0xFFB9B0A2), Color(0xFF9DBBA6), Color(0xFFE0A65A), dark = true)
} else {
    Linen(Tokens.Linen, Tokens.LinenCard, Color(0xFFE4E8EE), Tokens.Pencil, Tokens.PencilSoft, Tokens.Sage, Tokens.Lamp, dark = false)
}

/** A pencil-drawn lantern with a still glow (no flicker in M-C; motion is reserved, design-system §7). */
@Composable
fun Lantern(l: Linen, modifier: Modifier = Modifier) {
    Canvas(modifier.size(120.dp).semantics { contentDescription = "" }) {
        val c = center; val u = size.minDimension / 150f
        drawCircle(Brush.radialGradient(listOf(l.lamp.copy(alpha = 0.28f), Color.Transparent), center = Offset(c.x, c.y + 12 * u), radius = 58 * u))
        val s = Stroke(1.6f * u, cap = StrokeCap.Round)
        drawPath(Path().apply { moveTo(c.x - 13 * u, c.y - 45 * u); quadraticTo(c.x, c.y - 60 * u, c.x + 13 * u, c.y - 45 * u) }, l.pencil, style = s)
        drawLine(l.pencil, Offset(c.x - 17 * u, c.y - 35 * u), Offset(c.x + 17 * u, c.y - 35 * u), s.width)
        drawPath(Path().apply { moveTo(c.x - 15 * u, c.y - 35 * u); lineTo(c.x - 21 * u, c.y + 37 * u); lineTo(c.x + 21 * u, c.y + 37 * u); lineTo(c.x + 15 * u, c.y - 35 * u) }, l.pencil, style = s)
        drawLine(l.pencil, Offset(c.x - 21 * u, c.y + 37 * u), Offset(c.x + 21 * u, c.y + 37 * u), s.width)
        drawPath(Path().apply { moveTo(c.x, c.y + 9 * u); cubicTo(c.x - 7 * u, c.y, c.x - 2 * u, c.y - 7 * u, c.x, c.y - 14 * u); cubicTo(c.x + 3 * u, c.y - 6 * u, c.x + 9 * u, c.y, c.x, c.y + 9 * u); close() }, l.lamp)
    }
}

/** Days walked as pencil tally marks (5-gates). Never a streak, never a gap (US-012). */
@Composable
fun Tally(days: Int, l: Linen, modifier: Modifier = Modifier) {
    val shown = days.coerceAtMost(30)
    Canvas(modifier.width((shown * 9 + (shown / 5) * 6 + 4).coerceAtLeast(10).dp).height(28.dp)) {
        var x = 4.dp.toPx(); val top = 3.dp.toPx(); val bottom = 25.dp.toPx()
        for (i in 1..shown) {
            if (i % 5 == 0) {
                drawLine(l.pencil, Offset(x - 38.dp.toPx(), bottom - 4.dp.toPx()), Offset(x + 2.dp.toPx(), top + 4.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                x += 15.dp.toPx()
            } else {
                drawLine(l.pencil, Offset(x, top + (i % 2).dp.toPx()), Offset(x + (i % 3 - 1).dp.toPx(), bottom), 2.dp.toPx(), StrokeCap.Round)
                x += 9.dp.toPx()
            }
        }
    }
}

/** Quiet button: pencil outline, typewriter text, no stamp or spring (D-010). */
@Composable
fun QuietButton(text: String, l: Linen, modifier: Modifier = Modifier, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 52.dp).background(if (filled) l.pencil else Color.Transparent, RoundedCornerShape(6.dp))
            .border(1.5.dp, l.pencil, RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = StarfolkType.Body.copy(color = if (filled) l.bg else l.pencil, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = StarfolkType.BodyL.fontSize * 0.95f))
    }
}

/** An index card under a paper clip: today's one small step. */
@Composable
fun ClipCard(l: Linen, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).background(l.card).border(1.dp, l.rule).padding(20.dp)) { content() }
        Canvas(Modifier.align(Alignment.TopEnd).padding(end = 28.dp).size(16.dp, 42.dp)) {
            drawRoundRect(Color(0xFF9BA3AC), cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f), style = Stroke(2.5.dp.toPx()))
        }
    }
}
