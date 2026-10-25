// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.memories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.ui.components.SosNote
import io.github.ukpratik.starfolk.ui.paper.grain
import io.github.ukpratik.starfolk.ui.theme.StarfolkType
import io.github.ukpratik.starfolk.ui.theme.Tokens

/** Memories (S09). M-A shows the empty state; the list arrives with check-ins in M-B. */
@Composable
fun MemoriesScreen(onSos: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Tokens.Paper100).grain()) {
        Column(Modifier.align(Alignment.Center).padding(horizontal = 32.dp)) {
            Text(stringResource(R.string.memories_empty_title), style = StarfolkType.H1.copy(color = Tokens.Ink900))
            Text(stringResource(R.string.memories_empty_body), style = StarfolkType.Body.copy(color = Tokens.Ink700), modifier = Modifier.padding(top = 12.dp))
        }
        SosNote(onSos, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 20.dp))
    }
}
