// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.shelter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ukpratik.starfolk.AppContainer
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.core.content.ContentBundle
import io.github.ukpratik.starfolk.feature.sos.ShelterSosPill
import io.github.ukpratik.starfolk.ui.theme.LocalRegister
import io.github.ukpratik.starfolk.ui.theme.Register
import io.github.ukpratik.starfolk.ui.theme.StarfolkType
import io.github.ukpratik.starfolk.ui.theme.Tokens

/** The Shelter (S11) in the quiet register. M-A lists the tracks; day progression arrives in M-C (US-011/012). */
@Composable
fun ShelterScreen(container: AppContainer, onSos: () -> Unit) {
    val bundle by produceState<ContentBundle?>(null) { value = container.content.bundle() }
    CompositionLocalProvider(LocalRegister provides Register.SHELTER) {
        Box(Modifier.fillMaxSize().background(Tokens.Linen)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(start = 26.dp, end = 26.dp, top = 72.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(stringResource(R.string.shelter_title), style = StarfolkType.ShelterTitle.copy(color = Tokens.Pencil))
                Text(stringResource(R.string.shelter_body), style = StarfolkType.BodyL.copy(color = Tokens.Pencil))
                bundle?.tracks?.forEach { track ->
                    Column(
                        Modifier.fillMaxWidth().background(Tokens.LinenCard, RoundedCornerShape(4.dp))
                            .border(1.dp, Tokens.PencilSoft.copy(alpha = 0.4f), RoundedCornerShape(4.dp)).padding(18.dp),
                    ) {
                        Text("${track.title.uppercase()} · ${track.length} DAYS", style = StarfolkType.Label.copy(color = Tokens.Sage))
                        Text(track.summary, style = StarfolkType.Body.copy(color = Tokens.Pencil), modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            ShelterSosPill(onSos, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 20.dp))
        }
    }
}
