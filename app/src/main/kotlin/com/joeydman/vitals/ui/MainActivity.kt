package com.joeydman.vitals.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.joeydman.vitals.container
import com.joeydman.vitals.ui.screens.AppsScreen
import com.joeydman.vitals.ui.screens.BatteryScreen
import com.joeydman.vitals.ui.screens.OverviewScreen
import com.joeydman.vitals.ui.screens.SettingsScreen
import com.joeydman.vitals.ui.theme.VitalsTheme

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    OVERVIEW("overview", "Overview", Icons.Filled.Dashboard),
    BATTERY("battery", "Battery", Icons.Filled.BatteryChargingFull),
    APPS("apps", "Apps", Icons.Filled.Apps),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = container.repository
        setContent {
            VitalsTheme {
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            Tab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = backStack?.destination?.route == tab.route,
                                    onClick = {
                                        nav.navigate(tab.route) {
                                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = { Text(tab.label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    NavHost(nav, startDestination = Tab.OVERVIEW.route, modifier = Modifier.padding(padding)) {
                        composable(Tab.OVERVIEW.route) { OverviewScreen(repo) }
                        composable(Tab.BATTERY.route) { BatteryScreen(repo) }
                        composable(Tab.APPS.route) { AppsScreen(repo) }
                        composable(Tab.SETTINGS.route) { SettingsScreen(repo) }
                    }
                }
            }
        }
    }
}
