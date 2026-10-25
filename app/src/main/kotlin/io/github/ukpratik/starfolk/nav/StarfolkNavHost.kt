// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.nav

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.ukpratik.starfolk.AppContainer
import io.github.ukpratik.starfolk.feature.memories.MemoriesScreen
import io.github.ukpratik.starfolk.feature.shelter.ShelterScreen
import io.github.ukpratik.starfolk.feature.sky.SkyScreen
import io.github.ukpratik.starfolk.feature.sos.SosScreen
import io.github.ukpratik.starfolk.feature.today.TodayScreen

@Composable
fun StarfolkNavHost(container: AppContainer, skyDemo: Int) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val openSos = { nav.navigate(Routes.SOS) }
    // Light status-bar icons over night paper (Today, Sky); dark icons over day paper and linen.
    val view = LocalView.current
    val nightTop = route == Routes.TODAY || route == Routes.SKY || route == null
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !nightTop
        }
    }
    Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = Routes.TODAY, modifier = Modifier.fillMaxSize()) {
            composable(Routes.TODAY) { TodayScreen(container, skyDemo, onSos = openSos) }
            composable(Routes.SKY) { SkyScreen(container, skyDemo, onSos = openSos) }
            composable(Routes.MEMORIES) { MemoriesScreen(onSos = openSos) }
            composable(Routes.SHELTER) { ShelterScreen(container, onSos = openSos) }
            composable(Routes.SOS) { SosScreen(container, onClose = { nav.popBackStack() }) }
        }
        val tab = Tab.entries.firstOrNull { it.route == route }
        if (tab != null) {
            NotebookTabs(tab, onSelect = { selected ->
                nav.navigate(selected.route) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }, Modifier.align(Alignment.BottomCenter))
        }
    }
}
