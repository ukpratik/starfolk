// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.ui.paper

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import io.github.ukpratik.starfolk.core.time.SplitMix64

/**
 * Paper grain: one 128 px tile of faint dark specks, generated once and drawn with a repeating ImageShader (D-026: no
 * blend modes, no RenderEffect, cheap on low-end phones).
 */
private fun grainTile(): ImageBitmap {
    val size = 128
    val rng = SplitMix64(0x6A11L)
    val pixels = IntArray(size * size) {
        val alpha = if (rng.nextInt(5) == 0) 6 + rng.nextInt(16) else 0
        alpha shl 24 or 0x1D1B22
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** One grain brush for the whole process: the tile is generated once, not per screen. */
private val GrainBrush by lazy { ShaderBrush(ImageShader(grainTile(), TileMode.Repeated, TileMode.Repeated)) }

fun Modifier.grain(): Modifier {
    val brush = GrainBrush
    return drawWithContent {
        drawContent()
        drawRect(brush)
    }
}
