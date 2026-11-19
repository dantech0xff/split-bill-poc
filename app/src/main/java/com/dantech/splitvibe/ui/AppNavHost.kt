package com.dantech.splitvibe.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.dantech.splitvibe.BuildConfig
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.designsystem.motion.LocalSvSharedTransition
import com.dantech.splitvibe.designsystem.motion.SvMotion
import com.dantech.splitvibe.designsystem.motion.SvSharedTransition
import com.dantech.splitvibe.notifications.Push
import com.dantech.splitvibe.ocr.BillImageLoader
import com.dantech.splitvibe.scan.DocumentScanner
import com.dantech.splitvibe.ui.claim.ClaimActions
import com.dantech.splitvibe.ui.claim.ClaimPayment
import com.dantech.splitvibe.ui.claim.ClaimScreen
import com.dantech.splitvibe.ui.claim.ClaimViewModel
import com.dantech.splitvibe.ui.common.LegalLinks
import com.dantech.splitvibe.ui.common.ShareEffect
import com.dantech.splitvibe.ui.groups.GroupActions
import com.dantech.splitvibe.ui.groups.GroupScreen
import com.dantech.splitvibe.ui.groups.GroupViewModel
import com.dantech.splitvibe.ui.groups.GroupsScreen
import com.dantech.splitvibe.ui.groups.GroupsViewModel
import com.dantech.splitvibe.ui.groups.JoinGroupScreen
import com.dantech.splitvibe.ui.groups.JoinGroupViewModel
import com.dantech.splitvibe.ui.history.HistoryScreen
import com.dantech.splitvibe.ui.history.HistoryViewModel
import com.dantech.splitvibe.ui.home.HomeScreen
import com.dantech.splitvibe.ui.insights.InsightsActions
import com.dantech.splitvibe.ui.insights.InsightsScreen
import com.dantech.splitvibe.ui.insights.InsightsViewModel
import com.dantech.splitvibe.ui.payments.PaymentMethodsScreen
import com.dantech.splitvibe.ui.payments.PaymentMethodsViewModel
import com.dantech.splitvibe.ui.paywall.ProActions
import com.dantech.splitvibe.ui.paywall.ProScreen
import com.dantech.splitvibe.ui.paywall.ProViewModel
import com.dantech.splitvibe.ui.recurring.RecurringActions
import com.dantech.splitvibe.ui.recurring.RecurringScreen
import com.dantech.splitvibe.ui.recurring.RecurringViewModel
import com.dantech.splitvibe.ui.requests.RequestActions
import com.dantech.splitvibe.ui.requests.RequestLinkScreen
import com.dantech.splitvibe.ui.requests.RequestLinkViewModel
import com.dantech.splitvibe.ui.requests.RequestsScreen
import com.dantech.splitvibe.ui.requests.RequestsViewModel
import com.dantech.splitvibe.ui.settings.AccountActions
import com.dantech.splitvibe.ui.settings.SettingsScreen
import com.dantech.splitvibe.ui.settings.SettingsViewModel
import com.dantech.splitvibe.ui.split.SplitBillViewModel
import com.dantech.splitvibe.ui.split.SplitScreen
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

// The screens. They are serializable so the open screens come back after the system ends the process.
@Serializable
data object HomeKey : NavKey

@Serializable
data object SplitKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object HistoryKey : NavKey

@Serializable
data object PaymentMethodsKey : NavKey

@Serializable
data object GroupsKey : NavKey

@Serializable
data class GroupKey(val id: String) : NavKey

/** An invite link that was opened: `/g/{token}` on the proxy's address. */
@Serializable
data class JoinGroupKey(val token: String) : NavKey

@Serializable
data object RequestsKey : NavKey

/** The link of the bill open on the split screen, where everyone at the table picks what they had. */
@Serializable
data object ClaimKey : NavKey

/** A payment request link that was opened: `/r/{token}` on the proxy's address. */
@Serializable
data class RequestLinkKey(val token: String) : NavKey

/** What the finished bills add up to, and their export. */
@Serializable
data object InsightsKey : NavKey

@Serializable
data object RecurringKey : NavKey

/** Pro: its plans, buying it, and managing it once bought. */
@Serializable
data object ProKey : NavKey

/** The floating button on the home screen grows into the split screen; both carry this name. */
const val SPLIT_SURFACE = "split"

/**
 * The app's screens and how they lead to one another.
 *
 * @param sharedImages photos of a bill another app shared with Split Vibe, which a new split starts from.
 * @param link an invite or request link, or a tapped notice, that opened the app.
 */
@Composable
fun AppNavHost(sharedImages: List<Uri> = emptyList(), link: AppLink? = null) {
    val backStack = rememberNavBackStack(HomeKey)
    // Scoped to the activity, so a split in progress survives a visit to Settings and rotation.
    val splitViewModel: SplitBillViewModel = koinViewModel()
    // Scoped to the activity too, so a bill made to recur from the list of bills is stored whatever is opened next.
    val recurringViewModel: RecurringViewModel = koinViewModel()
    val askForNotices = rememberNoticeAsk()
    val settingsStore: SettingsStore = koinInject()
    val accountStore: AccountStore = koinInject()
    val imageLoader: BillImageLoader = koinInject()
    val scanner: DocumentScanner = koinInject()
    val billRepository: BillRepository = koinInject()
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    val account by accountStore.account.collectAsStateWithLifecycle()
    val bills by remember(billRepository) { billRepository.listings() }.collectAsStateWithLifecycle(emptyList())
    // The privacy policy and the terms of use on the proxy's address; none in a build without one.
    val legalLinks = remember { LegalLinks.of(BuildConfig.AI_PROXY_URL) }

    // Photos shared from another app start a split of their own.
    LaunchedEffect(sharedImages) {
        if (sharedImages.isEmpty()) return@LaunchedEffect
        splitViewModel.startNewSplit()
        splitViewModel.onPagesPicked(sharedImages)
        backStack.open(SplitKey)
    }
    val openBill: (String) -> Unit = { id -> splitViewModel.open(id) { backStack.open(SplitKey) } }

    LaunchedEffect(link) {
        when (link) {
            is AppLink.JoinGroup -> backStack.open(JoinGroupKey(link.token))
            is AppLink.OpenRequest -> backStack.open(RequestLinkKey(link.token))
            is AppLink.Group -> backStack.open(GroupKey(link.id))
            AppLink.Requests -> backStack.open(RequestsKey)
            is AppLink.OpenBill -> openBill(link.id)
            null -> Unit
        }
    }
    // Asked once someone is signed in, as that is when notices can come; saying no changes nothing else.
    NoticePermission(signedIn = account.session != null)

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
                            onOpenGroups = { backStack.open(GroupsKey) },
                            onOpenRequests = { backStack.open(RequestsKey) },
                            onOpenInsights = { backStack.open(InsightsKey) },
                            onOpenRecurring = { backStack.open(RecurringKey) },
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
                            onOpenPro = { backStack.open(ProKey) },
                            onOpenPaymentMethods = { backStack.open(PaymentMethodsKey) },
                            onPickTogether = { backStack.open(ClaimKey) },
                        )
                    }
                }
                entry<ClaimKey> {
                    Screen {
                        // Works on the bill the split screen has open, which stays the one copy that is written.
                        val claimViewModel: ClaimViewModel = koinViewModel { parametersOf(splitViewModel) }
                        val claimState by claimViewModel.state.collectAsStateWithLifecycle()
                        val saved by claimViewModel.bill.collectAsStateWithLifecycle()
                        val methods by claimViewModel.methods.collectAsStateWithLifecycle()
                        val method by claimViewModel.method.collectAsStateWithLifecycle()
                        val methodChanged by claimViewModel.methodChanged.collectAsStateWithLifecycle()
                        LaunchedEffect(claimState.closed) { if (claimState.closed) backStack.remove(ClaimKey) }
                        // The link is followed live only while the screen shows.
                        LifecycleStartEffect(claimViewModel) {
                            claimViewModel.resume()
                            onStopOrDispose { claimViewModel.pause() }
                        }
                        ClaimScreen(
                            state = claimState,
                            saved = saved,
                            payment = ClaimPayment(methods, method, methodChanged),
                            actions = ClaimActions(
                                onMatch = claimViewModel::match,
                                onRemove = claimViewModel::remove,
                                onLock = claimViewModel::lock,
                                onSend = claimViewModel::send,
                                onReopen = claimViewModel::reopen,
                                onRevoke = claimViewModel::revoke,
                                onRetry = claimViewModel::start,
                                onMakeNewLink = claimViewModel::makeNewLink,
                                onChooseMethod = claimViewModel::chooseMethod,
                                onAddMethod = { backStack.open(PaymentMethodsKey) },
                                onOpenSettings = { backStack.open(SettingsKey) },
                                onDismissMessage = claimViewModel::dismissMessage,
                                onBack = { backStack.remove(ClaimKey) },
                            ),
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
                            onRepeat = { id, name, schedule, firstDraft ->
                                recurringViewModel.repeat(id, name, schedule, firstDraft)
                                // Each draft comes with a notice, which needs leave on Android 13 and later.
                                askForNotices()
                                backStack.open(RecurringKey)
                            },
                        )
                    }
                }
                entry<InsightsKey> {
                    Screen {
                        val insightsViewModel: InsightsViewModel = koinViewModel()
                        val insights by insightsViewModel.state.collectAsStateWithLifecycle()
                        val export by insightsViewModel.export.collectAsStateWithLifecycle()
                        InsightsScreen(
                            state = insights,
                            export = export,
                            actions = remember(insightsViewModel) {
                                InsightsActions(
                                    onChoosePeriod = insightsViewModel::choose,
                                    onShare = insightsViewModel::share,
                                    onSaveToFolder = insightsViewModel::saveToFolder,
                                    onSaveTo = insightsViewModel::saveTo,
                                    onShared = insightsViewModel::shared,
                                    onPickerMissing = insightsViewModel::pickerMissing,
                                    onDismissMessage = insightsViewModel::dismissMessage,
                                    onBack = { backStack.remove(InsightsKey) },
                                )
                            },
                        )
                    }
                }
                entry<RecurringKey> {
                    Screen {
                        val rows by recurringViewModel.rows.collectAsStateWithLifecycle()
                        RecurringScreen(
                            rows = rows,
                            actions = remember(recurringViewModel) {
                                RecurringActions(
                                    onPause = recurringViewModel::pause,
                                    onResume = recurringViewModel::resume,
                                    onDelete = recurringViewModel::delete,
                                    onBack = { backStack.remove(RecurringKey) },
                                )
                            },
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
                                    onDeleteAccount = settingsViewModel::deleteAccount,
                                    onOpenPro = { backStack.open(ProKey) },
                                )
                            },
                            onSave = { localCurrency, myName ->
                                settingsStore.save(localCurrency)
                                settingsStore.saveName(myName)
                                backStack.remove(SettingsKey)
                            },
                            onOpenPaymentMethods = { backStack.open(PaymentMethodsKey) },
                            onBack = { backStack.remove(SettingsKey) },
                            legalLinks = legalLinks,
                        )
                    }
                }
                entry<ProKey> {
                    Screen {
                        // Lives as long as the screen is open; it asks for the plans and the person's Pro when it starts.
                        val proViewModel: ProViewModel = koinViewModel()
                        val proState by proViewModel.state.collectAsStateWithLifecycle()
                        // The activity, which Google's account picker and Google Play's purchase are shown over.
                        val activity = LocalActivity.current
                        ProScreen(
                            state = proState,
                            actions = remember(proViewModel, activity) {
                                ProActions(
                                    onSignIn = { activity?.let(proViewModel::signIn) },
                                    onSelect = proViewModel::select,
                                    onBuy = { activity?.let(proViewModel::buy) },
                                    onSwitch = { plan -> activity?.let { proViewModel.switchTo(it, plan) } },
                                    onRestore = proViewModel::restore,
                                    onRetry = proViewModel::load,
                                    onDismissMessage = proViewModel::dismissMessage,
                                    onBack = { backStack.remove(ProKey) },
                                )
                            },
                            legalLinks = legalLinks,
                        )
                    }
                }
                entry<GroupsKey> {
                    Screen {
                        val groupsViewModel: GroupsViewModel = koinViewModel()
                        val groups by groupsViewModel.groups.collectAsStateWithLifecycle()
                        val signedIn by groupsViewModel.signedIn.collectAsStateWithLifecycle()
                        val status by groupsViewModel.status.collectAsStateWithLifecycle()
                        val created by groupsViewModel.created.collectAsStateWithLifecycle()
                        LaunchedEffect(created) {
                            created?.let { id ->
                                groupsViewModel.openedCreated()
                                backStack.open(GroupKey(id))
                            }
                        }
                        GroupsScreen(
                            groups = groups,
                            signedIn = signedIn,
                            status = status,
                            myName = groupsViewModel.myName,
                            localCurrency = groupsViewModel.localCurrency,
                            onCreate = groupsViewModel::create,
                            onOpen = { id -> backStack.open(GroupKey(id)) },
                            onOpenSettings = { backStack.open(SettingsKey) },
                            onDismissMessage = groupsViewModel::dismissMessage,
                            onBack = { backStack.remove(GroupsKey) },
                        )
                    }
                }
                entry<GroupKey> { key ->
                    Screen {
                        val groupViewModel: GroupViewModel = koinViewModel(key = "group-${key.id}") { parametersOf(key.id) }
                        val group by groupViewModel.group.collectAsStateWithLifecycle()
                        val status by groupViewModel.status.collectAsStateWithLifecycle()
                        val share by groupViewModel.share.collectAsStateWithLifecycle()
                        val left by groupViewModel.left.collectAsStateWithLifecycle()
                        val openRequests by groupViewModel.openRequests.collectAsStateWithLifecycle()
                        LaunchedEffect(left) { if (left) backStack.remove(key) }
                        ShareEffect(share = share, onShared = groupViewModel::shared)
                        GroupScreen(
                            state = group,
                            status = status,
                            openRequests = openRequests,
                            actions = remember(groupViewModel) {
                                GroupActions(
                                    onInvite = groupViewModel::invite,
                                    onRevokeInvites = groupViewModel::revokeInvites,
                                    onRename = groupViewModel::rename,
                                    onAddPlaceholder = groupViewModel::addPlaceholder,
                                    onApprove = groupViewModel::approve,
                                    onRemoveMember = groupViewModel::removeMember,
                                    onReject = groupViewModel::reject,
                                    onDeleteBill = groupViewModel::deleteBill,
                                    onMarkReceived = groupViewModel::markReceived,
                                    onAskFor = groupViewModel::askFor,
                                    onLeave = groupViewModel::leave,
                                    onRefresh = groupViewModel::refresh,
                                    onDismissMessage = groupViewModel::dismissMessage,
                                    onBack = { backStack.remove(key) },
                                )
                            },
                        )
                    }
                }
                entry<JoinGroupKey> { key ->
                    Screen {
                        val joinViewModel: JoinGroupViewModel = koinViewModel(key = "join-${key.token}") { parametersOf(key.token) }
                        val state by joinViewModel.state.collectAsStateWithLifecycle()
                        val joinSignedIn by joinViewModel.signedIn.collectAsStateWithLifecycle()
                        // Once in, the invite gives way to the group itself.
                        LaunchedEffect(state.joinedGroupId) {
                            state.joinedGroupId?.let { id ->
                                backStack.remove(key)
                                backStack.open(GroupKey(id))
                            }
                        }
                        JoinGroupScreen(
                            state = state,
                            signedIn = joinSignedIn,
                            myName = joinViewModel.myName,
                            onJoin = joinViewModel::join,
                            onClaim = joinViewModel::claim,
                            onOpenSettings = { backStack.open(SettingsKey) },
                            onRetry = joinViewModel::load,
                            onBack = { backStack.remove(key) },
                        )
                    }
                }
                entry<RequestsKey> {
                    Screen {
                        val requestsViewModel: RequestsViewModel = koinViewModel()
                        val requests by requestsViewModel.requests.collectAsStateWithLifecycle()
                        val signedIn by requestsViewModel.signedIn.collectAsStateWithLifecycle()
                        val status by requestsViewModel.status.collectAsStateWithLifecycle()
                        val share by requestsViewModel.share.collectAsStateWithLifecycle()
                        val creditorNames by requestsViewModel.creditorNames.collectAsStateWithLifecycle()
                        ShareEffect(share = share, onShared = requestsViewModel::shared)
                        RequestsScreen(
                            requests = requests,
                            signedIn = signedIn,
                            status = status,
                            creditorNames = creditorNames,
                            actions = remember(requestsViewModel) {
                                RequestActions(
                                    onReportPaid = requestsViewModel::reportPaid,
                                    onConfirmPaid = requestsViewModel::confirmPaid,
                                    onRemind = requestsViewModel::remind,
                                    onSetReminders = requestsViewModel::setReminders,
                                    onDelete = requestsViewModel::delete,
                                    onRefresh = requestsViewModel::refresh,
                                    onOpenSettings = { backStack.open(SettingsKey) },
                                    onDismissMessage = requestsViewModel::dismissMessage,
                                    onBack = { backStack.remove(RequestsKey) },
                                )
                            },
                        )
                    }
                }
                entry<RequestLinkKey> { key ->
                    Screen {
                        val linkViewModel: RequestLinkViewModel = koinViewModel(key = "request-${key.token}") { parametersOf(key.token) }
                        val state by linkViewModel.state.collectAsStateWithLifecycle()
                        val linkSignedIn by linkViewModel.signedIn.collectAsStateWithLifecycle()
                        RequestLinkScreen(
                            state = state,
                            signedIn = linkSignedIn,
                            onAccept = linkViewModel::accept,
                            onReportPaid = linkViewModel::reportPaid,
                            onOpenSettings = { backStack.open(SettingsKey) },
                            onRetry = linkViewModel::load,
                            onBack = { backStack.remove(key) },
                        )
                    }
                }
                entry<PaymentMethodsKey> {
                    Screen {
                        val paymentMethodsViewModel: PaymentMethodsViewModel = koinViewModel()
                        val methods by paymentMethodsViewModel.methods.collectAsStateWithLifecycle()
                        PaymentMethodsScreen(
                            methods = methods,
                            onSave = paymentMethodsViewModel::save,
                            onMakeMain = paymentMethodsViewModel::makeMain,
                            onRemove = paymentMethodsViewModel::remove,
                            onBack = { backStack.remove(PaymentMethodsKey) },
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

/**
 * Asks once for leave to show notices, on Android 13 and later, when push is set up in this build
 * and someone is signed in. Whatever the answer, every flow works the same.
 */
@Composable
private fun NoticePermission(signedIn: Boolean) {
    if (!Push.isConfigured || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(signedIn) {
        if (!signedIn) return@LaunchedEffect
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val preferences = context.getSharedPreferences("notices", Context.MODE_PRIVATE)
        if (granted || preferences.getBoolean(ASKED, false)) return@LaunchedEffect
        preferences.edit { putBoolean(ASKED, true) }
        ask.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * What asks for leave to show notices on Android 13 and later, when it was not given yet, such as
 * when the user makes a bill recur. Whatever the answer, every flow works the same.
 */
@Composable
private fun rememberNoticeAsk(): () -> Unit {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context, ask) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

private const val ASKED = "asked"
