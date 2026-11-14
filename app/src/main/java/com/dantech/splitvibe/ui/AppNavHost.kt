package com.dantech.splitvibe.ui

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.designsystem.motion.LocalSvSharedTransition
import com.dantech.splitvibe.designsystem.motion.SvMotion
import com.dantech.splitvibe.designsystem.motion.SvSharedTransition
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

/** The floating button on the home screen grows into the split screen; both carry this name. */
const val SPLIT_SURFACE = "split"

@Composable
fun AppNavHost() {
    val backStack = rememberNavBackStack(HomeKey)
    // Scoped to the activity, so a split in progress survives a visit to Settings and rotation.
    val splitViewModel: SplitBillViewModel = koinViewModel()
    val settingsStore: SettingsStore = koinInject()
    val imageLoader: BillImageLoader = koinInject()
    val settings by settingsStore.settings.collectAsStateWithLifecycle()

    // One timing for the fade between screens and for a surface that grows into the next screen,
    // so both stay together when a back swipe scrubs through them.
    val fade = SvMotion.screenChange<Float>()

    // The screens share one layer in which a surface of one screen can grow into the next screen.
    SharedTransitionLayout {
        NavDisplay(
            backStack = backStack,
            sharedTransitionScope = this,
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            popTransitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            predictivePopTransitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                // A view model requested inside a screen lives as long as that screen stays open.
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<HomeKey> {
                    Screen {
                        HomeScreen(
                            contacts = splitViewModel.contacts,
                            onSplitBill = {
                                splitViewModel.startNewSplit()
                                backStack.open(SplitKey)
                            },
                            onOpenSettings = { backStack.open(SettingsKey) },
                        )
                    }
                }
                entry<SplitKey> {
                    Screen {
                        SplitScreen(
                            viewModel = splitViewModel,
                            hasApiKey = settings.hasApiKey,
                            newCameraTarget = imageLoader::newCameraTarget,
                            onExit = { backStack.removeAll { it != HomeKey } },
                            onOpenSettings = { backStack.open(SettingsKey) },
                        )
                    }
                }
                entry<SettingsKey> {
                    Screen {
                        SettingsScreen(
                            settings = settings,
                            onSave = { apiKey, model, localCurrency ->
                                settingsStore.save(apiKey, model, localCurrency)
                                backStack.remove(SettingsKey)
                            },
                            onBack = { backStack.remove(SettingsKey) },
                        )
                    }
                }
            },
        )
    }
}

/** Gives the screen what it needs to let one of its surfaces grow into the next screen. */
@Composable
private fun SharedTransitionScope.Screen(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSvSharedTransition provides SvSharedTransition(this, LocalNavAnimatedContentScope.current),
        content = content,
    )
}

/** Opens [key] unless it is already the screen on top, so a double tap does not open it twice. */
private fun NavBackStack<NavKey>.open(key: NavKey) {
    if (lastOrNull() != key) add(key)
}
