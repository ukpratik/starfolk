// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ukpratik.starfolk.ui.theme.StarfolkType
import io.github.ukpratik.starfolk.ui.theme.Tokens

/** The five check-in moods (US-006). Text labels are always shown: never emoji or colour alone (NFR-008). */
val MoodLabels = listOf("rough", "meh", "ok", "good", "alive!")

/** Hand-drawn mood faces; the selected one is circled in red marker (design-system §5). */
@Composable
fun MoodPicker(selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (mood in 1..5) {
            val isSelected = selected == mood
            Column(
                Modifier.selectable(isSelected, role = Role.RadioButton) { onSelect(mood) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.size(width = 56.dp, height = 50.dp)) { face(mood, isSelected) }
                Text(MoodLabels[mood - 1], style = StarfolkType.Hand.copy(color = Tokens.Ink700))
            }
        }
    }
}

/** A small read-only face for Memories. */
@Composable
fun MoodFaceIcon(mood: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.size(28.dp)) { face(mood, selected = false, scale = 0.6f) }
}

private fun DrawScope.face(mood: Int, selected: Boolean, scale: Float = 1f) {
    val s = 2.dp.toPx() * scale
    val c = center
    val r = 17.dp.toPx() * scale
    val ink = Tokens.Ink900
    drawCircle(ink, r, c, style = Stroke(s))
    val eyeY = c.y - r * 0.25f
    val ex = r * 0.38f
    when (mood) {
        1 -> { line(c.x - ex - r * 0.12f, eyeY, c.x - ex + r * 0.12f, eyeY, s); line(c.x + ex - r * 0.12f, eyeY, c.x + ex + r * 0.12f, eyeY, s) }
        5 -> { chevron(c.x - ex, eyeY, r * 0.14f, s); chevron(c.x + ex, eyeY, r * 0.14f, s) }
        else -> { drawCircle(ink, s * 0.9f, Offset(c.x - ex, eyeY)); drawCircle(ink, s * 0.9f, Offset(c.x + ex, eyeY)) }
    }
    val mouthY = c.y + r * 0.35f
    val w = r * 0.45f
    val curve = when (mood) { 1 -> -0.35f; 2 -> 0f; 3 -> 0.2f; 4 -> 0.45f; else -> 0.6f } * r
    val mouth = Path().apply { moveTo(c.x - w, mouthY); quadraticTo(c.x, mouthY + curve, c.x + w, mouthY) }
    drawPath(mouth, ink, style = Stroke(s, cap = StrokeCap.Round))
    if (selected) rotate(-8f) {
        drawOval(Tokens.StampRed, topLeft = Offset(c.x - r * 1.45f, c.y - r * 1.3f), size = Size(r * 2.9f, r * 2.6f), style = Stroke(2.5.dp.toPx()))
    }
}

private fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float) =
    drawLine(Tokens.Ink900, Offset(x1, y1), Offset(x2, y2), w, StrokeCap.Round)

private fun DrawScope.chevron(x: Float, y: Float, h: Float, w: Float) {
    line(x - h, y + h * 0.5f, x, y - h * 0.5f, w); line(x, y - h * 0.5f, x + h, y + h * 0.5f, w)
}

