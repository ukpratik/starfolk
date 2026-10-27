// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.ui.components.SosNote
import io.github.ukpratik.starfolk.ui.components.StatusBarScrim
import io.github.ukpratik.starfolk.ui.paper.grain
import io.github.ukpratik.starfolk.ui.theme.StarfolkType
import io.github.ukpratik.starfolk.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The privacy policy, bundled so it reads offline (US-021, BUG-005). The same text is published at [PRIVACY_URL]. */
@Composable
fun PrivacyScreen(onBack: () -> Unit, onSos: () -> Unit) {
    val context = LocalContext.current
    val lines by produceState(emptyList<String>()) {
        value = withContext(Dispatchers.IO) { context.assets.open("privacy.md").bufferedReader().use { it.readLines() } }
    }
    LegalPage(onBack, onSos) {
        for (line in lines) when {
            line.isBlank() -> Unit
            line.startsWith("# ") -> Text(line.drop(2), style = StarfolkType.Display.copy(color = Tokens.Ink900), modifier = Modifier.semantics { heading() })
            line.startsWith("## ") -> Text(line.drop(3), style = StarfolkType.H2.copy(color = Tokens.Ink900), modifier = Modifier.padding(top = 8.dp).semantics { heading() })
            line.startsWith("- ") -> Row {
                Text("•  ", style = StarfolkType.Body.copy(color = Tokens.Ink700))
                Text(line.drop(2), style = StarfolkType.Body.copy(color = Tokens.Ink900))
            }
            else -> Text(line, style = StarfolkType.Body.copy(color = Tokens.Ink900))
        }
        if (lines.isNotEmpty()) Text(
            stringResource(R.string.privacy_online), style = StarfolkType.Hand.copy(color = Tokens.HandwritingInk),
            modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_URL.toUri())) }
            }.padding(vertical = 12.dp),
        )
    }
}

private data class Library(val name: String, val version: String?, val licences: List<String>)
private data class Licences(val libraries: List<Library>, val texts: Map<String, Pair<String, String>>)

/** Open-source licences (US-021, BUG-005), from the list the licence gate generates at build time (ADR-013). */
@Composable
fun LicencesScreen(onBack: () -> Unit, onSos: () -> Unit) {
    val context = LocalContext.current
    val data by produceState<Licences?>(null) { value = withContext(Dispatchers.IO) { readLicences(context) } }
    var open by remember { mutableStateOf<Pair<String, String>?>(null) }
    LegalPage(onBack, onSos) {
        Text(stringResource(R.string.about_oss), style = StarfolkType.Display.copy(color = Tokens.Ink900), modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.about_licences), style = StarfolkType.Body.copy(color = Tokens.Ink700))
        val d = data ?: return@LegalPage
        for (lib in d.libraries) {
            val names = lib.licences.map { d.texts[it]?.first ?: it }
            Column(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).background(Tokens.Paper50).border(1.dp, Tokens.Paper300)
                    .clickable(role = Role.Button) { lib.licences.firstOrNull()?.let { d.texts[it] }?.let { open = it } }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(listOfNotNull(lib.name, lib.version).joinToString(" "), style = StarfolkType.Body.copy(color = Tokens.Ink900))
                Text(names.joinToString(", "), style = StarfolkType.Body.copy(color = Tokens.Ink700, fontSize = StarfolkType.Label.fontSize * 1.2f))
            }
        }
    }
    open?.let { (name, text) ->
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(name, style = StarfolkType.H2.copy(color = Tokens.Ink900)) },
            text = { Text(text, style = StarfolkType.Body.copy(color = Tokens.Ink900), modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton({ open = null }) { Text(stringResource(R.string.close)) } },
            containerColor = Tokens.Paper100,
        )
    }
}

private fun readLicences(context: Context): Licences {
    val root = context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
    val texts = root["licenses"]?.jsonObject.orEmpty().mapValues { (id, v) ->
        val o = v.jsonObject
        (o["name"]?.jsonPrimitive?.content ?: id) to (o["content"]?.jsonPrimitive?.content ?: o["url"]?.jsonPrimitive?.content.orEmpty())
    }
    val libraries = root["libraries"]?.jsonArray.orEmpty().map { e ->
        val o = e.jsonObject
        Library(
            name = o["name"]?.jsonPrimitive?.content ?: o["uniqueId"]!!.jsonPrimitive.content,
            version = o["artifactVersion"]?.jsonPrimitive?.content,
            licences = o["licenses"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
        )
    }.sortedBy { it.name.lowercase() }
    return Licences(libraries, texts)
}

@Composable
private fun LegalPage(onBack: () -> Unit, onSos: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Tokens.Paper100).grain()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("← " + stringResource(R.string.back), style = StarfolkType.Body.copy(color = Tokens.Ink700), modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onBack).padding(vertical = 12.dp))
            content()
        }
        StatusBarScrim(Tokens.Paper100)
        SosNote(onSos, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 20.dp))
    }
}
