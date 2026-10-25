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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.ukpratik.starfolk.AppContainer
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.core.content.ContentBundle
import io.github.ukpratik.starfolk.core.content.Level
import io.github.ukpratik.starfolk.core.sky.Box as SkyBox
import io.github.ukpratik.starfolk.core.sky.SkyLayout
import io.github.ukpratik.starfolk.feature.sky.DEMO_SEED
import io.github.ukpratik.starfolk.feature.sky.demoCompletions
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

/**
 * Today (S05). M-A shell: the sky strip, today's Spark and one ticket. The persisted daily pick, swaps, check-in and
 * reminders arrive in M-B (QuestPicker, Room); until then the ticket rotates through Spark quests by app-day.
 */
@Composable
fun TodayScreen(container: AppContainer, skyDemo: Int, onSos: () -> Unit) {
    val bundle by produceState<ContentBundle?>(null) { value = container.content.bundle() }
    val today = remember { container.appDayClock.today() }
    var committed by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Tokens.Paper100).grain().verticalScroll(rememberScrollState()),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(SKY_STRIP)) {
            val w = maxWidth.value; val h = maxHeight.value
            val model = remember(w, h, skyDemo) {
                skyModel(DEMO_SEED, demoCompletions(skyDemo, today), today, w, h, keepClear = listOf(SkyBox(0f, 0f, w, 150f)), strip = true)
            }
            SkyCanvas(model, SkyLayout.describe(model, null), DEMO_SEED, Modifier.fillMaxSize())
            Column(Modifier.statusBarsPadding().padding(start = 22.dp, top = 12.dp).rotate(-3f)) {
                Text(today.date.format(DateTimeFormatter.ofPattern("EEE. d MMM", Locale.ENGLISH)).lowercase(), style = StarfolkType.HandL.copy(color = Tokens.OnNight))
                Text("STARFOLK", style = StarfolkType.Tab.copy(color = Tokens.Foil), modifier = Modifier.padding(top = 4.dp).border(1.5.dp, Tokens.Foil).padding(horizontal = 8.dp, vertical = 3.dp))
            }
            SosNote(onSos, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 20.dp))
        }
        val content = bundle
        if (content != null) {
            val spark = content.sparks[(today.epochDay % content.sparks.size).toInt()]
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
            val sparks = content.quests.filter { it.level == Level.SPARK }
            val quest = sparks[(today.epochDay % sparks.size).toInt()]
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                PaperButton(pluralStringResource(R.plurals.swap, 2, 2), onClick = {}, modifier = Modifier.align(Alignment.CenterEnd))
            }
            Spacer(Modifier.height(12.dp))
            Ticket(quest, number = quest.id.takeLast(4).toInt(), committed = committed, onCommit = { committed = true }, onDone = {}, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Spacer(Modifier.height(110.dp))
    }
}
