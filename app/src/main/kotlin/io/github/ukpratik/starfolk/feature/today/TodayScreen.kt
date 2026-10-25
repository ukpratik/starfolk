// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ukpratik.starfolk.AppContainer
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.core.content.Spark
import io.github.ukpratik.starfolk.core.sky.Box as SkyBox
import io.github.ukpratik.starfolk.core.sky.SkyLayout
import io.github.ukpratik.starfolk.feature.sky.skyModel
import io.github.ukpratik.starfolk.ui.components.PaperButton
import io.github.ukpratik.starfolk.ui.components.SosNote
import io.github.ukpratik.starfolk.ui.components.Ticket
import io.github.ukpratik.starfolk.ui.paper.grain
import io.github.ukpratik.starfolk.ui.sky.SkyCanvas
import io.github.ukpratik.starfolk.ui.theme.StarfolkType
import io.github.ukpratik.starfolk.ui.theme.Tokens
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SKY_STRIP = 368.dp
private val DATE = DateTimeFormatter.ofPattern("EEE. d MMM", Locale.ENGLISH)
private val STAMP_DATE = DateTimeFormatter.ofPattern("d · MMM · yy", Locale.ENGLISH)

/** Today (S05, US-003…009): the sky strip, today's Spark and today's ticket, with the whole Done moment. */
@Composable
fun TodayScreen(container: AppContainer, skyDemo: Int, onSos: () -> Unit, onShelter: () -> Unit, onWho5: () -> Unit) {
    val vm: TodayViewModel = viewModel { TodayViewModel(container, skyDemo) }
    val state by vm.state.collectAsStateWithLifecycle()
    val moment by vm.moment.collectAsStateWithLifecycle()
    val offers by vm.offers.collectAsStateWithLifecycle()
    var choosingTime by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.undo)
    val doneLabel = stringResource(R.string.quest_done)
    LifecycleResumeEffect(Unit) { vm.refreshDay(); onPauseOrDispose { } }

    // US-006: 8-second undo after the stamp.
    val stamped = moment as? Moment.Stamped
    LaunchedEffect(stamped?.completionId) {
        if (stamped != null) {
            val result = snackbar.showSnackbar(doneLabel, actionLabel = undoLabel, duration = SnackbarDuration.Indefinite)
            if (result == SnackbarResult.ActionPerformed) vm.undo()
        }
    }
    LaunchedEffect(stamped?.completionId) {
        if (stamped != null) { kotlinx.coroutines.delay(UNDO_MS); snackbar.currentSnackbarData?.dismiss() }
    }

    Box(Modifier.fillMaxSize().background(Tokens.Paper100).grain()) {
        val s = state
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(SKY_STRIP)) {
                val w = maxWidth.value; val h = maxHeight.value
                if (s != null) {
                    val model = remember(w, h, s.sky, s.seed) {
                        skyModel(s.seed, s.sky, s.day, w, h, keepClear = listOf(SkyBox(0f, 0f, w, 150f)), strip = true)
                    }
                    val latest = s.sky.lastOrNull()?.id
                    SkyCanvas(
                        model, SkyLayout.describe(model, latest?.let { s.names[it] }), s.seed, Modifier.fillMaxSize(),
                        palette = s.palette, names = s.names, fresh = stamped?.completionId,
                    )
                    Column(Modifier.statusBarsPadding().padding(start = 22.dp, top = 12.dp).rotate(-3f)) {
                        Text(s.day.date.format(DATE).lowercase(), style = StarfolkType.HandL.copy(color = Tokens.OnNight))
                        Text("STARFOLK", style = StarfolkType.Tab.copy(color = Tokens.Foil), modifier = Modifier.padding(top = 4.dp).border(1.5.dp, Tokens.Foil).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                } else {
                    Box(Modifier.fillMaxSize().background(Tokens.Night700))
                }
                SosNote(onSos, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 20.dp))
            }
            if (s != null) {
                SparkLine(s.spark)
                offers.shelter?.let { ShelterTodayCard(it, onShelter, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) }
                if (offers.lowMood) LowMoodCard(onShelter, onSos, vm::snoozeLowMood, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
                offers.who5?.let { Who5Card(it, onTake = { vm.dismissWho5(); onWho5() }, onDismiss = vm::dismissWho5, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) }
                val done = s.done
                if (done == null) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        PaperButton(
                            if (s.swapsLeft > 0) pluralStringResource(R.plurals.swap, s.swapsLeft, s.swapsLeft) else stringResource(R.string.swaps_none),
                            onClick = vm::swap, enabled = s.swapsLeft > 0 && s.commit == null, modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                } else {
                    Text(stringResource(R.string.done_today), style = StarfolkType.Hand.copy(color = Tokens.Ink700), modifier = Modifier.padding(horizontal = 22.dp))
                }
                Spacer(Modifier.height(12.dp))
                Ticket(
                    quest = s.quest,
                    number = s.quest.id.takeLast(4).toIntOrNull() ?: 0,
                    committed = s.commit != null,
                    onCommit = { choosingTime = true },
                    onDone = { vm.done(s.quest) },
                    commitLabel = s.commit?.let { commitLabel(it.slot, it.remindAt) },
                    stampedOn = done?.let { s.day.date.format(STAMP_DATE).uppercase() },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Spacer(Modifier.height(120.dp))
        }

        if (stamped?.insight != null) {
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 150.dp)) {
                WhyCard(stamped.insight, onClose = vm::dismissMoment)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))
    }

    val reflect = moment as? Moment.Reflect
    if (reflect != null) {
        ReflectionSheet(reflect.quest, onSave = vm::reflect, onSkip = { vm.reflect(null, null, null) })
    }
    if (choosingTime && state != null) {
        WhenSheet(
            onPick = { choice -> choosingTime = false; vm.commit(state!!.quest.id, choice.slot, choice.remindAt) },
            onDismiss = { choosingTime = false },
        )
    }
}

private const val UNDO_MS = 8_000L

@Composable
private fun SparkLine(spark: Spark) {
    Text(
        buildAnnotatedString {
            val hl = spark.highlight
            val at = hl?.let { spark.text.indexOf(it) } ?: -1
            append("“")
            if (at >= 0 && hl != null) {
                append(spark.text.substring(0, at))
                withStyle(SpanStyle(background = Tokens.Highlighter, color = Tokens.Ink900)) { append(hl) }
                append(spark.text.substring(at + hl.length))
            } else append(spark.text)
            append("”")
        },
        style = StarfolkType.Body.copy(color = Tokens.Ink700),
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
    )
}
