package com.example.vehiclerecognition.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.vehiclerecognition.ui.camera.CameraScreen
import com.example.vehiclerecognition.ui.camera.CameraViewModel
import com.example.vehiclerecognition.ui.settings.SettingsScreen
import com.example.vehiclerecognition.ui.settings.SettingsViewModel
import com.example.vehiclerecognition.ui.watchlist.WatchlistScreen
import com.example.vehiclerecognition.ui.watchlist.WatchlistViewModel
import kotlinx.coroutines.flow.collectLatest

// FR 1.16: Basic Navigation
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Camera : Screen("camera", "Camera", Icons.Filled.CameraAlt)
    object Watchlist : Screen("watchlist", "Watchlist", Icons.Filled.List)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

val bottomNavItems = listOf(
    Screen.Camera,
    Screen.Watchlist,
    Screen.Settings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            paddingValues = innerPadding,
            snackbarHostState = snackbarHostState
        )
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    paddingValues: PaddingValues,
    snackbarHostState: SnackbarHostState
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Camera.route,
        modifier = Modifier.padding(paddingValues)
    ) {
        composable(Screen.Camera.route) {
            val cameraViewModel: CameraViewModel = hiltViewModel()
            CameraScreen(viewModel = cameraViewModel)
        }
        composable(Screen.Watchlist.route) {
            val watchlistViewModel: WatchlistViewModel = hiltViewModel()
            LaunchedEffect(Unit) {
                watchlistViewModel.errorEvent.collectLatest {
                    snackbarHostState.showSnackbar(
                        message = it,
                        duration = SnackbarDuration.Short
                    )
                }
            }
            // Refresh template information when navigating to watchlist
            // This ensures the FAB shows up immediately after adding templates in settings
            LaunchedEffect(navController.currentBackStackEntry) {
                watchlistViewModel.refreshTemplateInformation()
            }
            WatchlistScreen(viewModel = watchlistViewModel)
        }
        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(viewModel = settingsViewModel)
        }
    }
} 