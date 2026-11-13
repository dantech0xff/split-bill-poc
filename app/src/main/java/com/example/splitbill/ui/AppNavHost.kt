package com.example.splitbill.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.splitbill.AppContainer
import com.example.splitbill.ui.home.HomeScreen
import com.example.splitbill.ui.settings.SettingsScreen
import com.example.splitbill.ui.split.SplitBillViewModel
import com.example.splitbill.ui.split.SplitScreen

private object Routes {
    const val HOME = "home"
    const val SPLIT = "split"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    // Scoped to the activity, so a split in progress survives a visit to Settings and rotation.
    val splitViewModel: SplitBillViewModel = viewModel(factory = SplitBillViewModel.Factory)
    val settings by container.settings.settings.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                contacts = splitViewModel.contacts,
                onSplitBill = {
                    splitViewModel.startNewSplit()
                    navController.navigate(Routes.SPLIT) { launchSingleTop = true }
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
            )
        }
        composable(Routes.SPLIT) {
            SplitScreen(
                viewModel = splitViewModel,
                hasApiKey = settings.hasApiKey,
                newCameraTarget = container.imageLoader::newCameraTarget,
                onExit = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                onSave = { apiKey, model ->
                    container.settings.save(apiKey, model)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
