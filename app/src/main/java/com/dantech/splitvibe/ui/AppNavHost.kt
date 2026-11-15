package com.dantech.splitvibe.ui

import android.net.Uri
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.designsystem.motion.LocalSvSharedTransition
import com.dantech.splitvibe.designsystem.motion.SvMotion
import com.dantech.splitvibe.designsystem.motion.SvSharedTransition
import com.dantech.splitvibe.ocr.BillImageLoader
import com.dantech.splitvibe.scan.DocumentScanner
import com.dantech.splitvibe.ui.history.HistoryScreen
import com.dantech.splitvibe.ui.history.HistoryViewModel
import com.dantech.splitvibe.ui.home.HomeScreen
import com.dantech.splitvibe.ui.settings.AccountActions
import com.dantech.splitvibe.ui.settings.SettingsScreen
import com.dantech.splitvibe.ui.settings.SettingsViewModel
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

@Serializable
data object HistoryKey : NavKey

/** The floating button on the home screen grows into the split screen; both carry this name. */
const val SPLIT_SURFACE = "split"

/**
 * The app's screens and how they lead to one another.
 *
 * @param sharedImages photos of a bill another app shared with Split Vibe, which a new split starts from.
 */
@Composable
fun AppNavHost(sharedImages: List<Uri> = emptyList()) {
    val backStack = rememberNavBackStack(HomeKey)
    // Scoped to the activity, so a split in progress survives a visit to Settings and rotation.
    val splitViewModel: SplitBillViewModel = koinViewModel()
    val settingsStore: SettingsStore = koinInject()
    val accountStore: AccountStore = koinInject()
    val imageLoader: BillImageLoader = koinInject()
    val scanner: DocumentScanner = koinInject()
    val billRepository: BillRepository = koinInject()
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    val account by accountStore.account.collectAsStateWithLifecycle()
    val bills by remember(billRepository) { billRepository.listings() }.collectAsStateWithLifecycle(emptyList())

    // Photos shared from another app start a split of their own.
    LaunchedEffect(sharedImages) {
        if (sharedImages.isEmpty()) return@LaunchedEffect
        splitViewModel.startNewSplit()
        splitViewModel.onPagesPicked(sharedImages)
        backStack.open(SplitKey)
    }
    val openBill: (String) -> Unit = { id -> splitViewModel.open(id) { backStack.open(SplitKey) } }

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
            // While a back swipe is held, the screen being left also shrinks a little, so it reads as
            // a card that is being pulled away from the screen behind it.
            predictivePopTransitionSpec = { fadeIn(fade) togetherWith (fadeOut(fade) + scaleOut(fade, targetScale = 0.85f)) },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                // A view model requested inside a screen lives as long as that screen stays open.
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<HomeKey> {
                    Screen {
                        HomeScreen(
                            bills = bills,
                            onSplitBill = {
                                splitViewModel.startNewSplit()
                                backStack.open(SplitKey)
                            },
                            onOpenBill = openBill,
                            onSeeAllBills = { backStack.open(HistoryKey) },
                            onOpenSettings = { backStack.open(SettingsKey) },
                        )
                    }
                }
                entry<SplitKey> {
                    Screen {
                        SplitScreen(
                            viewModel = splitViewModel,
                            aiStatus = account.ai,
                            newCameraTarget = imageLoader::newCameraTarget,
                            scanIntent = scanner::startIntent,
                            // Back to the list of bills when the bill was opened from there, else home.
                            onExit = { backStack.remove(SplitKey) },
                            onOpenSettings = { backStack.open(SettingsKey) },
                        )
                    }
                }
                entry<HistoryKey> {
                    Screen {
                        // Lives as long as the list is open, with what was searched for.
                        val historyViewModel: HistoryViewModel = koinViewModel()
                        val listings by historyViewModel.listings.collectAsStateWithLifecycle()
                        val query by historyViewModel.query.collectAsStateWithLifecycle()
                        HistoryScreen(
                            listings = listings,
                            query = query,
                            onQueryChanged = historyViewModel::onQueryChanged,
                            onOpen = openBill,
                            onDelete = historyViewModel::delete,
                            onBack = { backStack.remove(HistoryKey) },
                        )
                    }
                }
                entry<SettingsKey> {
                    Screen {
                        // Lives as long as Settings is open; it asks the proxy for the calls left when it starts.
                        val settingsViewModel: SettingsViewModel = koinViewModel()
                        val accountUi by settingsViewModel.ui.collectAsStateWithLifecycle()
                        // The activity, which Google's account picker is shown over.
                        val context = LocalContext.current
                        SettingsScreen(
                            settings = settings,
                            account = account,
                            accountUi = accountUi,
                            canSignIn = settingsViewModel.canSignIn,
                            actions = remember(settingsViewModel, context) {
                                AccountActions(
                                    onSignIn = { settingsViewModel.signIn(context) },
                                    onSignOut = { settingsViewModel.signOut(context) },
                                    onDeleteAccount = { settingsViewModel.deleteAccount(context) },
                                )
                            },
                            onSave = { localCurrency ->
                                settingsStore.save(localCurrency)
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
    val visibility = LocalNavAnimatedContentScope.current
    // Remembered, so what reads it is not composed again each time the screen is.
    val transition = remember(this, visibility) { SvSharedTransition(this, visibility) }
    CompositionLocalProvider(LocalSvSharedTransition provides transition, content = content)
}

/** Opens [key] unless it is already the screen on top, so a double tap does not open it twice. */
private fun NavBackStack<NavKey>.open(key: NavKey) {
    if (lastOrNull() != key) add(key)
}
