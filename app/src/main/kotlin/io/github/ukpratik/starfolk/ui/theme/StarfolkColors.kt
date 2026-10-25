// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.ukpratik.starfolk.core.content.Realm

/** Design tokens (design-system §2, 04-design/final/assets/tokens.css). Keep the two in sync. */
@Immutable
object Tokens {
    val Night900 = Color(0xFF0F1730)
    val Night700 = Color(0xFF16213F)
    val Night500 = Color(0xFF24336A)
    val OnNight = Color(0xFFF3EBDD)

    val Paper50 = Color(0xFFFFFDF7)
    val Paper100 = Color(0xFFF3EBDD)
    val Paper200 = Color(0xFFE6DBC6)
    val Paper300 = Color(0xFFCDBFA6)
    val Ink900 = Color(0xFF1D1B22)
    val Ink700 = Color(0xFF4A4550)
    val Ink500 = Color(0xFF6B6570)
    val HandwritingInk = Color(0xFF1B3B8C)

    val FoilLight = Color(0xFFFFF1B0)
    val Foil = Color(0xFFF5C451)
    val FoilDark = Color(0xFFB9831C)

    val StampRed = Color(0xFFE2412F)
    val Danger = Color(0xFFB3261E)
    val Highlighter = Color(0xFFFFE36B)
    val TapeAmber = Color(0x73F29A1F)

    val Linen = Color(0xFFECE5D8)
    val LinenCard = Color(0xFFFBF8F2)
    val Pencil = Color(0xFF5A5560)
    val PencilSoft = Color(0xFF6B6372)
    val Sage = Color(0xFF5F7C68)
    val Lamp = Color(0xFFC98A3C)

    val ShelterTint = Color(0xFFE7D7F2)
}

/** Realm fill (chips, tabs), ink (text on paper, ≥ 4.5:1) and tint (highlights). Never colour alone: always a label. */
@Immutable
data class RealmColors(val fill: Color, val ink: Color, val tint: Color)

fun Realm.colors(): RealmColors = when (this) {
    Realm.CONNECT -> RealmColors(Color(0xFF3B8CFF), Color(0xFF1F5FCC), Color(0xFFBFD6FF))
    Realm.ADVENTURE -> RealmColors(Color(0xFF2FAE6B), Color(0xFF1E7A4A), Color(0xFFC7EBD3))
    Realm.PURPOSE -> RealmColors(Color(0xFFF29A1F), Color(0xFF9A5800), Color(0xFFFFD6A8))
    Realm.CARE -> RealmColors(Color(0xFFE7609E), Color(0xFFB0346E), Color(0xFFF8D3E4))
}
