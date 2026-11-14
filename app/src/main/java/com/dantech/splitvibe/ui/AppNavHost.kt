package com.dantech.splitvibe.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.ocr.BillImageLoader
import com.dantech.splitvibe.ui.home.HomeScreen
import com.dantech.splitvibe.ui.settings.SettingsScreen
import com.dantech.splitvibe.ui.split.SplitBillViewModel
import com.dantech.splitvibe.ui.split.SplitScreen
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private object Routes {
    const val HOME = "home"
    const val SPLIT = "split"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    // Scoped to the activity, so a split in progress survives a visit to Settings and rotation.
    val splitViewModel: SplitBillViewModel = koinViewModel()
    val settingsStore: SettingsStore = koinInject()
    val imageLoader: BillImageLoader = koinInject()
    val settings by settingsStore.settings.collectAsStateWithLifecycle()

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
                newCameraTarget = imageLoader::newCameraTarget,
                onExit = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                onSave = { apiKey, model, localCurrency ->
                    settingsStore.save(apiKey, model, localCurrency)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
