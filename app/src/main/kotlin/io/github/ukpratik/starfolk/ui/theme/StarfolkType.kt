// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.ukpratik.starfolk.R

// Bundled OFL fonts (design-system §3), never downloaded at runtime (US-020).
val Bricolage = FontFamily(Font(R.font.bricolage_500, FontWeight.Medium), Font(R.font.bricolage_800, FontWeight.ExtraBold))
val CourierPrime = FontFamily(Font(R.font.courier_prime_400, FontWeight.Normal), Font(R.font.courier_prime_700, FontWeight.Bold))
val Caveat = FontFamily(Font(R.font.caveat_500, FontWeight.Medium), Font(R.font.caveat_700, FontWeight.Bold))

object StarfolkType {
    val Display = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 42.sp, letterSpacing = (-0.03).em)
    val H1 = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.02).em)
    val Quest = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 27.sp, letterSpacing = (-0.02).em)
    val H2 = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 23.sp, lineHeight = 25.sp, letterSpacing = (-0.015).em)
    val ShelterTitle = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 35.sp)
    val Body = TextStyle(fontFamily = CourierPrime, fontSize = 15.sp, lineHeight = 22.sp)
    val BodyL = TextStyle(fontFamily = CourierPrime, fontSize = 17.sp, lineHeight = 28.sp)
    val Label = TextStyle(fontFamily = CourierPrime, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.12.em)
    val Button = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    val Tab = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 0.14.em)
    val Hand = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    val HandL = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 28.sp)
}
