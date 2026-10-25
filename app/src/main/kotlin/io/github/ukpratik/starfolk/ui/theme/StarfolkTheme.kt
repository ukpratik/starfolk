// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Day = bold, kinetic, celebrates. Shelter = soft, slow, never celebrates (design-brief §2). */
enum class Register { DAY, SHELTER }

val LocalRegister = staticCompositionLocalOf { Register.DAY }

/** US-022 / NFR-008: animations become instant crossfades when true. */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** US-022: haptics can be switched off. */
val LocalHaptics = staticCompositionLocalOf { true }

private val ChromeColors = lightColorScheme(
    primary = Tokens.Ink900,
    onPrimary = Tokens.Paper50,
    secondary = Tokens.Ink700,
    surface = Tokens.Paper100,
    onSurface = Tokens.Ink900,
    background = Tokens.Paper100,
    onBackground = Tokens.Ink900,
    error = Tokens.Danger,
)

/** Material 3 is themed for the chrome only (settings, sheets, dialogs); the world is custom (design-system §5). */
@Composable
fun StarfolkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChromeColors,
        typography = Typography(bodyLarge = StarfolkType.Body, bodyMedium = StarfolkType.Body, labelLarge = StarfolkType.Button),
        content = content,
    )
}
