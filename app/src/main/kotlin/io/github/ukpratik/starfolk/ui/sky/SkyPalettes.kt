// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.ui.sky

import androidx.compose.ui.graphics.Color

/** The six night papers offered at onboarding ("pick your sky", US-001): light, mid and deep stops. */
data class SkyPalette(val name: String, val light: Color, val mid: Color, val deep: Color)

val SkyPalettes = listOf(
    SkyPalette("Indigo", Color(0xFF24336A), Color(0xFF16213F), Color(0xFF0F1730)),
    SkyPalette("Deep sea", Color(0xFF1F4A5C), Color(0xFF143341), Color(0xFF0B1F2A)),
    SkyPalette("Plum", Color(0xFF4A2D63), Color(0xFF301D44), Color(0xFF1C1029)),
    SkyPalette("Forest", Color(0xFF26493B), Color(0xFF173126), Color(0xFF0D1E17)),
    SkyPalette("Ink", Color(0xFF2E2C3A), Color(0xFF1D1B26), Color(0xFF111017)),
    SkyPalette("Dusk", Color(0xFF5A3150), Color(0xFF3A2140), Color(0xFF1F1530)),
)
