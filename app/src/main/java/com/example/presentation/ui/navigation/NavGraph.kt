package com.example.presentation.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.AppDatabase
import com.example.data.repository.ProjectRepository
import com.example.presentation.ui.screens.editor.EditorScreen
import com.example.presentation.ui.screens.home.HomeScreen
import com.example.presentation.ui.screens.settings.SettingsScreen
import com.example.presentation.viewmodel.EditorViewModel
import com.example.presentation.viewmodel.ExportViewModel
import com.example.presentation.viewmodel.HomeViewModel
import com.example.presentation.viewmodel.SettingsViewModel

object NavDestinations {
    const val HOME = "home"
    const val EDITOR = "editor/{projectId}"
    const val SETTINGS = "settings"

    fun editorRoute(projectId: String) = "editor/$projectId"
}

@Composable
fun ShortCutNavGraph(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext

    val database = remember { AppDatabase.getInstance(context) }
    val repository = remember { ProjectRepository(database) }

    NavHost(
        navController = navController,
        startDestination = NavDestinations.HOME,
        modifier = modifier
    ) {
        // Home Screen
        composable(NavDestinations.HOME) {
            val homeViewModel = remember { HomeViewModel(repository) }
            HomeScreen(
                homeViewModel = homeViewModel,
                onOpenProject = { projectId ->
                    navController.navigate(NavDestinations.editorRoute(projectId))
                },
                onOpenSettings = {
                    navController.navigate(NavDestinations.SETTINGS)
                }
            )
        }

        // Editor Screen
        composable(
            route = NavDestinations.EDITOR,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            val editorViewModel = remember(projectId) {
                EditorViewModel(
                    projectId = projectId,
                    repository = repository,
                    context = context
                )
            }
            val exportViewModel = remember(projectId) {
                ExportViewModel()
            }

            EditorScreen(
                editorViewModel = editorViewModel,
                exportViewModel = exportViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Settings Screen
        composable(NavDestinations.SETTINGS) {
            val settingsViewModel = remember { SettingsViewModel(context) }
            SettingsScreen(
                settingsViewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
