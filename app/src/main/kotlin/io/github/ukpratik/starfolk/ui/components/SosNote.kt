// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.ui.theme.StarfolkType
import io.github.ukpratik.starfolk.ui.theme.Tokens

/** The yellow "Need help now?" sticky note: on every Day screen, top right, one tap to SOS (US-014). */
@Composable
fun SosNote(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.sos_note_a11y)
    Text(
        text = stringResource(R.string.sos_note),
        style = StarfolkType.Body.copy(fontFamily = io.github.ukpratik.starfolk.ui.theme.CourierPrime, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = StarfolkType.Body.fontSize * 0.95f),
        color = Tokens.Ink900,
        modifier = modifier
            .rotate(3f)
            .width(124.dp)
            .sizeIn(minHeight = 48.dp)
            .shadow(2.dp, RectangleShape)
            .background(Tokens.Highlighter)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(12.dp),
    )
}
