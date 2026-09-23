package com.catprint.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.catprint.ui.screens.AppViewModel
import com.catprint.ui.screens.EditorScreen
import com.catprint.ui.screens.PrinterScreen
import com.catprint.ui.screens.SettingsScreen

private data class Dest(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Адаптивная навигация: на узких экранах (телефон-портрет) —
 * нижняя панель, на широких (альбом/планшет) — боковая рейка.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun CatPrintNav(vm: AppViewModel, logLines: List<String>) {
    val nav = rememberNavController()
    val dests = listOf(
        Dest("editor", "Редактор", Icons.Filled.Description),
        Dest("printer", "Принтер", Icons.Filled.Print),
        Dest("settings", "Настройки", Icons.Filled.Settings)
    )
    val activity = LocalContext.current as ComponentActivity
    val sizeClass = calculateWindowSizeClass(activity = activity)
    val wide = sizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: "editor"

    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize()) {
        if (wide) {
            NavigationRail {
                dests.forEach { d ->
                    NavigationRailItem(
                        selected = current == d.route,
                        onClick = { nav.navigate(d.route) },
                        icon = { Icon(d.icon, d.label) },
                        label = { Text(d.label) }
                    )
                }
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            bottomBar = {
                if (!wide) {
                    NavigationBar {
                        dests.forEach { d ->
                            NavigationBarItem(
                                selected = current == d.route,
                                onClick = { nav.navigate(d.route) },
                                icon = { Icon(d.icon, d.label) },
                                label = { Text(d.label) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                NavHost(navController = nav, startDestination = "editor") {
                    composable("editor") { EditorScreen(vm) }
                    composable("printer") { PrinterScreen(vm, logLines) }
                    composable("settings") { SettingsScreen(vm) }
                }
            }
        }
    }
}
