// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ukpratik.starfolk.AppContainer
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.core.content.Realm
import io.github.ukpratik.starfolk.core.quest.Energy
import io.github.ukpratik.starfolk.core.sky.SkyLayout
import io.github.ukpratik.starfolk.ui.components.PaperButton
import io.github.ukpratik.starfolk.ui.components.SosNote
import io.github.ukpratik.starfolk.ui.paper.grain
import io.github.ukpratik.starfolk.ui.sky.SkyCanvas
import io.github.ukpratik.starfolk.ui.sky.SkyPalettes
import io.github.ukpratik.starfolk.ui.theme.StarfolkType
import io.github.ukpratik.starfolk.ui.theme.Tokens
import io.github.ukpratik.starfolk.ui.theme.colors
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Welcome + 3-tap onboarding (S01–S04, US-001/002): pick your sky, what you want more of, today's energy. No account,
 * no name, no notification permission here. The sky seed is created on the first tap and never changes.
 */
@Composable
fun OnboardingScreen(container: AppContainer, onDone: () -> Unit, onShelter: () -> Unit, onSos: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var palette by rememberSaveable { mutableIntStateOf(0) }
    var realm by rememberSaveable { mutableStateOf<Realm?>(null) }
    val seed = rememberSaveable { Random.nextLong() }
    val scope = rememberCoroutineScope()
    val today = remember { container.appDayClock.today() }
    BackHandler(enabled = step > 0) { step-- }

    Box(Modifier.fillMaxSize().background(Tokens.Paper100).grain()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(84.dp))
            when (step) {
                0 -> {
                    Text(stringResource(R.string.welcome_title), style = StarfolkType.Display.copy(color = Tokens.Ink900), modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.welcome_body), style = StarfolkType.BodyL.copy(color = Tokens.Ink700), modifier = Modifier.padding(top = 16.dp))
                    Spacer(Modifier.height(32.dp))
                    PaperButton(stringResource(R.string.welcome_begin), onClick = { step = 1 }, modifier = Modifier.fillMaxWidth(), filled = true)
                    Text(
                        stringResource(R.string.welcome_shelter),
                        style = StarfolkType.Hand.copy(color = Tokens.Ink700),
                        modifier = Modifier.padding(top = 20.dp).heightIn(min = 48.dp).clickable(role = Role.Button) {
                            scope.launch { container.settings.startFromShelter(seed, today); container.firstOpen(); withContext(Dispatchers.Main) { onShelter() } }
                        },
                    )
                    Text(stringResource(R.string.welcome_privacy), style = StarfolkType.Body.copy(color = Tokens.Ink500), modifier = Modifier.padding(top = 24.dp))
                }
                1 -> {
                    StepHeader(1, stringResource(R.string.onb_sky_title))
                    BoxWithConstraints(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(6.dp))) {
                        val model = remember(maxWidth, maxHeight) { SkyLayout.compute(seed, emptyList(), today, maxWidth.value, maxHeight.value) }
                        SkyCanvas(model, stringResource(R.string.onb_sky_preview), seed, Modifier.fillMaxSize(), tornBottom = false, palette = palette)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkyPalettes.forEachIndexed { i, p ->
                            Box(
                                Modifier.weight(1f).aspectRatio(0.8f).clip(RoundedCornerShape(4.dp)).background(p.mid)
                                    .then(if (i == palette) Modifier.border(3.dp, Tokens.Ink900, RoundedCornerShape(4.dp)) else Modifier)
                                    .selectable(i == palette, role = Role.RadioButton) { palette = i }
                                    .semantics { contentDescription = p.name },
                            )
                        }
                    }
                    Text(SkyPalettes[palette].name, style = StarfolkType.Hand.copy(color = Tokens.Ink700), modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(24.dp))
                    PaperButton(stringResource(R.string.onb_next), onClick = { step = 2 }, modifier = Modifier.fillMaxWidth(), filled = true)
                }
                2 -> {
                    StepHeader(2, stringResource(R.string.onb_realm_title))
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (r in Realm.entries) {
                            val c = r.colors()
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (realm == r) c.tint else Tokens.Paper50)
                                    .border(2.dp, if (realm == r) Tokens.Ink900 else Tokens.Paper300, RoundedCornerShape(6.dp))
                                    .selectable(realm == r, role = Role.RadioButton) { realm = r; step = 3 }.padding(16.dp),
                            ) {
                                Text(realmName(r), style = StarfolkType.H2.copy(color = Tokens.Ink900))
                                Text(realmBlurb(r), style = StarfolkType.Body.copy(color = Tokens.Ink700))
                            }
                        }
                    }
                }
                else -> {
                    StepHeader(3, stringResource(R.string.onb_energy_title))
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for ((e, label) in listOf(Energy.LOW to R.string.energy_low, Energy.OK to R.string.energy_ok, Energy.HIGH to R.string.energy_high)) {
                            PaperButton(stringResource(label), modifier = Modifier.fillMaxWidth(), onClick = {
                                scope.launch {
                                    container.settings.completeOnboarding(palette, realm ?: Realm.CONNECT, e, seed, today)
                                    container.firstOpen()
                                    container.analytics.track(io.github.ukpratik.starfolk.core.metrics.Event.OnboardingCompleted((realm ?: Realm.CONNECT).name, e.name))
                                    // Navigation must run on the main thread; DataStore resumes us wherever it finished.
                                    withContext(Dispatchers.Main) { onDone() }
                                }
                            })
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
        SosNote(onSos, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 20.dp))
    }
}

@Composable
private fun StepHeader(n: Int, title: String) {
    Text("$n / 3", style = StarfolkType.Label.copy(color = Tokens.Ink700))
    Text(title, style = StarfolkType.H1.copy(color = Tokens.Ink900), modifier = Modifier.padding(top = 6.dp, bottom = 18.dp).rotate(-0.5f).semantics { heading() })
}

@Composable
private fun realmName(r: Realm) = stringResource(when (r) { Realm.CONNECT -> R.string.realm_connect; Realm.ADVENTURE -> R.string.realm_adventure; Realm.PURPOSE -> R.string.realm_purpose; Realm.CARE -> R.string.realm_care })

@Composable
private fun realmBlurb(r: Realm) = stringResource(when (r) { Realm.CONNECT -> R.string.realm_connect_blurb; Realm.ADVENTURE -> R.string.realm_adventure_blurb; Realm.PURPOSE -> R.string.realm_purpose_blurb; Realm.CARE -> R.string.realm_care_blurb })
