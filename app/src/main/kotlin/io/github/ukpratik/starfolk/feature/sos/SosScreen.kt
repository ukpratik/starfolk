// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.sos

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ukpratik.starfolk.AppContainer
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.core.content.ContentBundle
import io.github.ukpratik.starfolk.ui.theme.StarfolkType
import io.github.ukpratik.starfolk.ui.theme.Tokens
import java.util.Locale

private enum class SosView { HUB, CALM, REACH, LINES }

/**
 * Need help now? (S15, US-014/015): plain words, no tabs, works offline. M-A wires the whole flow with the drafted
 * content; M-C adds the SIM-country resolver, manual country override and the reviewed copy.
 */
@Composable
fun SosScreen(container: AppContainer, onClose: () -> Unit) {
    val bundle by produceState<ContentBundle?>(null) { value = container.content.bundle() }
    var view by remember { mutableStateOf(SosView.HUB) }
    var step by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(Tokens.Linen).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            if (view == SosView.HUB) "× " + stringResource(R.string.close) else "← back",
            style = StarfolkType.Body.copy(color = Tokens.Pencil),
            modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { if (view == SosView.HUB) onClose() else view = SosView.HUB }.padding(vertical = 12.dp),
        )
        val content = bundle ?: return@Column
        when (view) {
            SosView.HUB -> {
                Text(stringResource(R.string.sos_title), style = StarfolkType.ShelterTitle.copy(color = Tokens.Pencil))
                Text(stringResource(R.string.sos_question), style = StarfolkType.Body.copy(color = Tokens.Pencil))
                Option(stringResource(R.string.sos_calm)) { step = 0; view = SosView.CALM }
                Option(stringResource(R.string.sos_reach)) { view = SosView.REACH }
                Option(stringResource(R.string.sos_lines)) { view = SosView.LINES }
                Text(stringResource(R.string.sos_danger), style = StarfolkType.Body.copy(color = Tokens.Pencil), modifier = Modifier.fillMaxWidth().border(1.dp, Tokens.PencilSoft).padding(14.dp))
            }
            SosView.CALM -> {
                val g = content.sos.grounding
                val s = g[step.coerceIn(0, g.lastIndex)]
                Text("CALM MY BODY · ${step + 1} OF ${g.size}", style = StarfolkType.Label.copy(color = Tokens.PencilSoft))
                Text(s.title, style = StarfolkType.ShelterTitle.copy(color = Tokens.Pencil))
                Text(s.body, style = StarfolkType.BodyL.copy(color = Tokens.Pencil))
                if (step < g.lastIndex) Option("Next") { step++ } else Option("Back to start") { view = SosView.HUB }
            }
            SosView.REACH -> {
                Text("Send one of these, or make it yours.", style = StarfolkType.ShelterTitle.copy(color = Tokens.Pencil))
                content.sos.templates.forEach { t ->
                    Option(t.text) {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t.text)
                        context.startActivity(Intent.createChooser(send, null))
                    }
                }
                Text("Starfolk doesn't see who you message or what you send.", style = StarfolkType.Body.copy(color = Tokens.PencilSoft))
            }
            SosView.LINES -> {
                val country = LocalConfiguration.current.locales[0].country.uppercase(Locale.ROOT)
                val entry = content.helplines.firstOrNull { it.country == country }
                Text("Free, confidential, in your country.", style = StarfolkType.ShelterTitle.copy(color = Tokens.Pencil))
                if (entry != null && entry.lines.isNotEmpty()) {
                    entry.lines.forEach { line ->
                        line.phone?.let { phone -> Option("${line.name} · call $phone (${line.hours})") { dial(context, phone) } }
                        line.sms?.let { sms -> Option("${line.name} · text $sms") { context.startActivity(Intent(Intent.ACTION_SENDTO, "smsto:${sms.replace(" ", "")}".toUri())) } }
                    }
                }
                val emergency = entry?.emergency
                Text(
                    if (emergency != null) "In immediate danger, call $emergency." else stringResource(R.string.sos_danger),
                    style = StarfolkType.Body.copy(color = Tokens.Pencil),
                )
                if (emergency != null) Option("Call $emergency") { dial(context, emergency) }
                Text("More helplines worldwide: findahelpline.com (needs internet).", style = StarfolkType.Body.copy(color = Tokens.PencilSoft))
            }
        }
    }
}

/** ACTION_DIAL only: opens the dialer with the number filled in, needs no permission (US-015). */
private fun dial(context: android.content.Context, number: String) =
    context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${number.replace(" ", "")}".toUri()))

@Composable
private fun Option(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).background(Tokens.LinenCard, RoundedCornerShape(4.dp))
            .border(1.5.dp, Tokens.Pencil, RoundedCornerShape(4.dp)).clickable(role = Role.Button, onClick = onClick).padding(16.dp),
    ) {
        Text(text, style = StarfolkType.H2.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = StarfolkType.Body.fontSize * 1.2f, color = Tokens.Pencil))
    }
}

/** The Shelter's plain "Need help now?" pill (design-system §5): no sticky-note playfulness in the quiet register. */
@Composable
fun ShelterSosPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.sos_note_a11y),
        style = StarfolkType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Tokens.Paper50),
        modifier = modifier.heightIn(min = 48.dp).background(Tokens.Ink900, RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
    )
}
