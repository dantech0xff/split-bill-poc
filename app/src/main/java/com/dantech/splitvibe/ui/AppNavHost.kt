package com.dantech.splitvibe.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.ocr.BillImageLoader
import com.dantech.splitvibe.ui.home.HomeScreen
import com.dantech.splitvibe.ui.settings.SettingsScreen
import com.dantech.splitvibe.ui.split.SplitBillViewModel
import com.dantech.splitvibe.ui.split.SplitScreen
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

// The screens. They are serializable so the open screens come back after the system ends the process.
@Serializable
data object HomeKey : NavKey

@Serializable
data object SplitKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Composable
fun AppNavHost() {
    val backStack = rememberNavBackStack(HomeKey)
    // Scoped to the activity, so a split in progress survives a visit to Settings and rotation.
    val splitViewModel: SplitBillViewModel = koinViewModel()
    val settingsStore: SettingsStore = koinInject()
    val imageLoader: BillImageLoader = koinInject()
    val settings by settingsStore.settings.collectAsStateWithLifecycle()

    NavDisplay(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            // A view model requested inside a screen lives as long as that screen stays open.
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<HomeKey> {
                HomeScreen(
                    contacts = splitViewModel.contacts,
                    onSplitBill = {
                        splitViewModel.startNewSplit()
                        backStack.open(SplitKey)
                    },
                    onOpenSettings = { backStack.open(SettingsKey) },
                )
            }
            entry<SplitKey> {
                SplitScreen(
                    viewModel = splitViewModel,
                    hasApiKey = settings.hasApiKey,
                    newCameraTarget = imageLoader::newCameraTarget,
                    onExit = { backStack.removeAll { it != HomeKey } },
                    onOpenSettings = { backStack.open(SettingsKey) },
                )
            }
            entry<SettingsKey> {
                SettingsScreen(
                    settings = settings,
                    onSave = { apiKey, model, localCurrency ->
                        settingsStore.save(apiKey, model, localCurrency)
                        backStack.remove(SettingsKey)
                    },
                    onBack = { backStack.remove(SettingsKey) },
                )
            }
        },
    )
}

/** Opens [key] unless it is already the screen on top, so a double tap does not open it twice. */
private fun NavBackStack<NavKey>.open(key: NavKey) {
    if (lastOrNull() != key) add(key)
}
