// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.ui.theme.StarfolkType
import io.github.ukpratik.starfolk.ui.theme.Tokens

/** Paper notebook tabs along the bottom edge (D-024): the selected tab is taller and joined to the page. */
@Composable
fun NotebookTabs(current: Tab, onSelect: (Tab) -> Unit, backdrop: Color, modifier: Modifier = Modifier) {
    // The page's own paper sits behind the tabs, so scrolled content never shows through them.
    Row(
        modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to backdrop.copy(alpha = 0f), 0.18f to backdrop, 1f to backdrop))
            .navigationBarsPadding().padding(top = 14.dp).padding(start = 12.dp, end = 12.dp, bottom = 10.dp).height(56.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (tab in Tab.entries) {
            val selected = tab == current
            val shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(if (selected) 54.dp else 48.dp)
                    .clip(shape)
                    .background(tab.paper)
                    .then(if (selected) Modifier.border(2.dp, Tokens.Ink900, shape) else Modifier)
                    .semantics { role = Role.Tab; this.selected = selected }
                    .clickable { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(tab.label), style = StarfolkType.Tab.copy(color = Tokens.Ink900), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

enum class Tab(val route: String, val label: Int, val paper: Color) {
    TODAY(Routes.TODAY, R.string.tab_today, Tokens.Paper50),
    SKY(Routes.SKY, R.string.tab_sky, Color(0xFFBFD6FF)),
    MEMORIES(Routes.MEMORIES, R.string.tab_memories, Color(0xFFFFD6A8)),
    SHELTER(Routes.SHELTER, R.string.tab_shelter, Tokens.ShelterTint),
}

object Routes {
    const val ONBOARDING = "onboarding"
    const val TODAY = "today"
    const val SKY = "sky"
    const val MEMORIES = "memories"
    const val SHELTER = "shelter"
    const val LETTERS = "shelter/letters"
    const val WHO5 = "who5"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val PRIVACY = "about/privacy"
    const val LICENCES = "about/licences"
    /** The SOS flow is a full-screen modal with no tabs (S15). */
    const val SOS = "sos"
}
