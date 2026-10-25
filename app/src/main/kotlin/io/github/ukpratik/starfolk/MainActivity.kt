// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.ukpratik.starfolk.nav.StarfolkNavHost
import io.github.ukpratik.starfolk.ui.theme.StarfolkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Light status-bar icons over the night-paper sky; dark navigation icons over day paper.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as StarfolkApp).container
        // Sky spike (D-012, NFR-002): `--ei sky_demo N` renders N demo completions for the frame-timing benchmark.
        val skyDemo = intent.getIntExtra(EXTRA_SKY_DEMO, 0).coerceIn(0, 500)
        setContent { StarfolkTheme { StarfolkNavHost(container, skyDemo) } }
    }

    companion object {
        const val EXTRA_SKY_DEMO = "sky_demo"
    }
}
