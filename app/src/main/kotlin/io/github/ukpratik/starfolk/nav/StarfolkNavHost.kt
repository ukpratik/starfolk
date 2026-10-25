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
import io.github.ukpratik.starfolk.feature.onboarding.OnboardingScreen
import io.github.ukpratik.starfolk.ui.theme.Tokens
import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ukpratik.starfolk.feature.shelter.ShelterScreen
import io.github.ukpratik.starfolk.feature.sky.SkyScreen
import io.github.ukpratik.starfolk.feature.sos.SosScreen
import io.github.ukpratik.starfolk.feature.today.TodayScreen

@Composable
fun StarfolkNavHost(container: AppContainer, skyDemo: Int) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val loaded = settings ?: run { Box(Modifier.fillMaxSize().background(Tokens.Paper100)); return }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val openSos = { nav.navigate(Routes.SOS) }
    // Light status-bar icons over night paper (Today, Sky); dark icons over day paper and linen.
    val view = LocalView.current
    val nightTop = route == Routes.TODAY || route == Routes.SKY
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !nightTop
        }
    }
    Box(Modifier.fillMaxSize()) {
        val start = remember { if (loaded.onboarded || skyDemo > 0) Routes.TODAY else Routes.ONBOARDING }
        fun goTab(route: String) = nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        NavHost(nav, startDestination = start, modifier = Modifier.fillMaxSize()) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    container,
                    onDone = { nav.navigate(Routes.TODAY) { popUpTo(Routes.ONBOARDING) { inclusive = true } } },
                    onShelter = { nav.navigate(Routes.TODAY) { popUpTo(Routes.ONBOARDING) { inclusive = true } }; nav.navigate(Routes.SHELTER) },
                    onSos = openSos,
                )
            }
            composable(Routes.TODAY) { TodayScreen(container, skyDemo, onSos = openSos) }
            composable(Routes.SKY) { SkyScreen(container, skyDemo, onSos = openSos, onOpenMemories = { goTab(Routes.MEMORIES) }) }
            composable(Routes.MEMORIES) { MemoriesScreen(container, onSos = openSos, onConnectAgain = { goTab(Routes.TODAY) }) }
            composable(Routes.SHELTER) { ShelterScreen(container, onSos = openSos) }
            composable(Routes.SOS) { SosScreen(container, onClose = { nav.popBackStack() }) }
        }
        val tab = Tab.entries.firstOrNull { it.route == route }
        if (tab != null) {
            NotebookTabs(tab, onSelect = { goTab(it.route) }, Modifier.align(Alignment.BottomCenter))
        }
    }
}
