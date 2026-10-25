// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ukpratik.starfolk.AppContainer
import io.github.ukpratik.starfolk.BuildConfig
import io.github.ukpratik.starfolk.MainActivity
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.feature.today.WhatsSent
import io.github.ukpratik.starfolk.notify.Reminders
import io.github.ukpratik.starfolk.ui.components.PaperButton
import io.github.ukpratik.starfolk.ui.components.SosNote
import io.github.ukpratik.starfolk.ui.components.StatusBarScrim
import io.github.ukpratik.starfolk.ui.paper.grain
import io.github.ukpratik.starfolk.ui.theme.StarfolkType
import io.github.ukpratik.starfolk.ui.theme.Tokens
import kotlinx.coroutines.launch

const val PRIVACY_URL = "https://ukpratik.github.io/starfolk-site/privacy/"

/** Settings (S18, US-021/022/024): Material 3 chrome on paper. */
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onSos: () -> Unit, onAbout: () -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf(false) }
    var showSent by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val json = container.userData.export()
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            exported = true
        }
    }
    val s = settings ?: return
    Box(Modifier.fillMaxSize().background(Tokens.Paper100).grain()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("← " + stringResource(R.string.back), style = StarfolkType.Body.copy(color = Tokens.Ink700), modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onBack).padding(vertical = 12.dp))
            Text(stringResource(R.string.settings), style = StarfolkType.Display.copy(color = Tokens.Ink900), modifier = Modifier.semantics { heading() })

            Section(stringResource(R.string.set_reminders))
            Toggle(stringResource(R.string.set_daily), s.reminderTime?.let { stringResource(R.string.set_daily_at, it) } ?: stringResource(R.string.set_off), s.reminderTime != null) { on ->
                val time = if (on) "08:00" else null
                scope.launch { container.settings.setReminderTime(time) }
                Reminders.scheduleDaily(context, time)
            }
            if (s.reminderTime != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in listOf("08:00", "12:30", "19:00", "21:00")) Chip(t, s.reminderTime == t) {
                    scope.launch { container.settings.setReminderTime(t) }; Reminders.scheduleDaily(context, t)
                }
            }
            if (!Reminders.canPost(context)) Text(stringResource(R.string.set_notif_denied), style = StarfolkType.Body.copy(color = Tokens.Ink700), modifier = Modifier.clickable(role = Role.Button) {
                context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
            }.border(1.dp, Tokens.Ink500).padding(12.dp))

            Section(stringResource(R.string.set_comfort))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.set_motion), style = StarfolkType.Body.copy(color = Tokens.Ink900), modifier = Modifier.weight(1f))
                for ((key, label) in listOf("system" to R.string.set_motion_system, "on" to R.string.set_motion_on)) Chip(stringResource(label), s.reducedMotion == key) {
                    scope.launch { container.settings.setReducedMotion(key) }
                }
            }
            Toggle(stringResource(R.string.set_haptics), null, s.haptics) { scope.launch { container.settings.setHaptics(it) } }

            Section(stringResource(R.string.set_privacy))
            if (container.analytics.available) {
                Toggle(stringResource(R.string.set_stats), stringResource(R.string.set_stats_sub), s.analyticsConsent == true) { scope.launch { container.analytics.setConsent(it) } }
                if (s.analyticsConsent == true) Toggle(stringResource(R.string.set_who5_share), stringResource(R.string.set_who5_share_sub), s.who5Share) { scope.launch { container.settings.setWho5Share(it) } }
                Text(stringResource(if (showSent) R.string.consent_hide else R.string.consent_see), style = StarfolkType.Hand.copy(color = Tokens.HandwritingInk), modifier = Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) { showSent = !showSent }.padding(top = 8.dp))
                if (showSent) WhatsSent()
            } else {
                Text(stringResource(if (BuildConfig.ANALYTICS_AVAILABLE) R.string.set_stats_unconfigured else R.string.set_sends_nothing), style = StarfolkType.Body.copy(color = Tokens.Ink700))
            }
            Text(stringResource(R.string.set_never_read), style = StarfolkType.Body.copy(color = Tokens.Ink900), modifier = Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.set_backup), style = StarfolkType.Body.copy(color = Tokens.Ink700))

            Section(stringResource(R.string.set_data))
            PaperButton(stringResource(R.string.set_export), { export.launch("starfolk-export.json") }, Modifier.fillMaxWidth())
            if (exported) Text(stringResource(R.string.set_exported), style = StarfolkType.Hand.copy(color = Tokens.Ink700))
            PaperButton(stringResource(R.string.set_delete), { deleting = true }, Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(2.dp, Tokens.Danger), textColor = Tokens.Danger)

            Section(stringResource(R.string.set_about_section))
            PaperButton(stringResource(R.string.about), onAbout, Modifier.fillMaxWidth())
            Text("Starfolk ${BuildConfig.VERSION_NAME} · ${if (BuildConfig.ANALYTICS_AVAILABLE) "Play" else "F-Droid"}", style = StarfolkType.Body.copy(color = Tokens.Ink500), modifier = Modifier.padding(top = 8.dp, bottom = 40.dp))
        }
        StatusBarScrim(Tokens.Paper100)
        SosNote(onSos, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 20.dp))
    }
    if (deleting) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.del_title), style = StarfolkType.H2.copy(color = Tokens.Ink900)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.del_body), style = StarfolkType.Body.copy(color = Tokens.Ink700))
                    OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text(stringResource(R.string.del_type)) })
                }
            },
            confirmButton = {
                TextButton(enabled = typed.trim() == "DELETE", onClick = {
                    deleting = false
                    scope.launch {
                        container.userData.wipe()
                        // Restart clean: the next launch is a first launch (US-024).
                        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        Runtime.getRuntime().exit(0)
                    }
                }) { Text(stringResource(R.string.del_confirm), color = Tokens.Danger) }
            },
            dismissButton = { TextButton({ deleting = false }) { Text(stringResource(R.string.cancel)) } },
            containerColor = Tokens.Paper100,
        )
    }
}

/** About (US-021): the disclaimer, privacy policy, content sources and licences, the expert reviewer credit. */
@Composable
fun AboutScreen(onBack: () -> Unit, onSos: () -> Unit) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(Tokens.Paper100).grain()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("← " + stringResource(R.string.back), style = StarfolkType.Body.copy(color = Tokens.Ink700), modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onBack).padding(vertical = 12.dp))
            Text(stringResource(R.string.about), style = StarfolkType.Display.copy(color = Tokens.Ink900), modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.disclaimer), style = StarfolkType.Body.copy(color = Tokens.Ink900), modifier = Modifier.border(1.5.dp, Tokens.Ink700).padding(14.dp))
            PaperButton(stringResource(R.string.about_privacy), { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_URL.toUri())) } }, Modifier.fillMaxWidth())
            Text(stringResource(R.string.about_content), style = StarfolkType.Body.copy(color = Tokens.Ink700))
            Text(stringResource(R.string.about_reviewer), style = StarfolkType.Body.copy(color = Tokens.Ink700))
            Text(stringResource(R.string.about_licences), style = StarfolkType.Body.copy(color = Tokens.Ink700))
            Text(io.github.ukpratik.starfolk.core.safety.Who5.ATTRIBUTION, style = StarfolkType.Body.copy(color = Tokens.Ink500))
        }
        StatusBarScrim(Tokens.Paper100)
        SosNote(onSos, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 20.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), style = StarfolkType.Label.copy(color = Tokens.Ink700), modifier = Modifier.padding(top = 22.dp, bottom = 4.dp).semantics { heading() })
}

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).background(Tokens.Paper50, RoundedCornerShape(6.dp)).clickable(role = Role.Switch) { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = StarfolkType.Body.copy(color = Tokens.Ink900))
            subtitle?.let { Text(it, style = StarfolkType.Body.copy(color = Tokens.Ink700, fontSize = StarfolkType.Label.fontSize * 1.2f)) }
        }
        Switch(checked, null, colors = SwitchDefaults.colors(checkedTrackColor = Tokens.Ink900))
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text, style = StarfolkType.Label.copy(color = if (selected) Tokens.Paper50 else Tokens.Ink900),
        modifier = Modifier.heightIn(min = 44.dp).background(if (selected) Tokens.Ink900 else Tokens.Paper50, RoundedCornerShape(22.dp))
            .border(1.5.dp, Tokens.Ink900, RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
    )
}
