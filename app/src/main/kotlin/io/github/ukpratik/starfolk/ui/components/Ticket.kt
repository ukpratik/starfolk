// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.core.content.Quest
import io.github.ukpratik.starfolk.ui.theme.StarfolkType
import io.github.ukpratik.starfolk.ui.theme.Tokens
import io.github.ukpratik.starfolk.ui.theme.colors
import kotlinx.coroutines.launch

private const val HOLD_MS = 1200

/**
 * Today's quest as a tearable ticket (design-system §5). Hold "Hold to tear" for 1.2 s to commit (US-005), with a
 * TalkBack "Commit" action as the gesture-free path. [onCommit] fires once the stub tears.
 */
@Composable
fun Ticket(quest: Quest, number: Int, committed: Boolean, onCommit: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val realm = quest.realm.colors()
    val stubTilt by animateFloatAsState(if (committed) -8f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "stub")
    Row(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(Tokens.Paper50),
    ) {
        Box(
            Modifier
                .width(62.dp)
                .fillMaxHeight()
                .graphicsLayer { rotationZ = stubTilt; translationX = if (committed) -14f * density else 0f }
                .drawBehind {
                    val dash = 6.dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        drawLine(Tokens.Paper300, androidx.compose.ui.geometry.Offset(size.width - 1, y), androidx.compose.ui.geometry.Offset(size.width - 1, y + dash), 2.dp.toPx())
                        y += dash * 2
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            VerticalText("ADMIT ONE · ${quest.realm.name}", StarfolkType.Tab.copy(color = realm.ink, letterSpacing = StarfolkType.Tab.letterSpacing * 1.5f))
        }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(quest.level.name, style = StarfolkType.Label.copy(color = Tokens.Paper50), modifier = Modifier.background(realm.fill).padding(horizontal = 6.dp, vertical = 2.dp))
                Text("  ${quest.minutes} MIN · Nº %04d".format(number), style = StarfolkType.Label.copy(color = Tokens.Ink700))
            }
            Text(
                buildAnnotatedString { withStyle(SpanStyle()) { append(quest.title) } },
                style = StarfolkType.Quest.copy(color = Tokens.Ink900),
                modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
            )
            Text(quest.why, style = StarfolkType.Body.copy(color = Tokens.Ink700))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HoldToTear(committed, onCommit, Modifier.weight(1.5f))
                PaperButton(stringResource(R.string.stamp_it), onDone, Modifier.weight(1.1f), border = BorderStroke(2.dp, Tokens.StampRed), textColor = Tokens.StampRed)
            }
        }
    }
}

@Composable
private fun HoldToTear(committed: Boolean, onCommit: () -> Unit, modifier: Modifier) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var done by remember { mutableStateOf(committed) }
    val label = stringResource(R.string.hold_to_tear)
    fun commit() { if (!done) { done = true; haptics.performHapticFeedback(HapticFeedbackType.LongPress); onCommit() } }
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Tokens.Ink900)
            .semantics {
                role = Role.Button
                customActions = listOf(CustomAccessibilityAction("Commit") { commit(); true })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (done) return@awaitEachGesture
                    val job = scope.launch {
                        progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                        commit()
                    }
                    waitForUpOrCancellation()
                    if (!done) { job.cancel(); scope.launch { progress.animateTo(0f, tween(200)) } }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = if (done) 1f else progress.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }.background(io.github.ukpratik.starfolk.core.content.Realm.CONNECT.colors().fill))
        Text(if (done) "Torn ✓" else label, style = StarfolkType.Button.copy(color = Tokens.Paper50))
    }
}

@Composable
fun PaperButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    border: BorderStroke = BorderStroke(2.dp, Tokens.Ink900),
    textColor: androidx.compose.ui.graphics.Color = if (filled) Tokens.Paper50 else Tokens.Ink900,
) {
    Box(
        modifier
            .height(52.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) Tokens.Ink900 else Tokens.Paper50)
            .border(border, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = StarfolkType.Button.copy(color = textColor), maxLines = 1, softWrap = false)
    }
}

/** Text rotated to read bottom-to-top, laid out in its rotated bounds (the ticket stub). */
@Composable
fun VerticalText(text: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    Text(
        text,
        style = style,
        maxLines = 1,
        modifier = modifier
            .layout { measurable, _ ->
                val p = measurable.measure(Constraints())
                layout(p.height, p.width) { p.place(-(p.width - p.height) / 2, (p.width - p.height) / 2) }
            }
            .graphicsLayer { rotationZ = -90f },
    )
}
