package com.dantech.splitvibe.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.groups.GroupRepository
import com.dantech.splitvibe.data.groups.Invitation
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvHero
import com.dantech.splitvibe.designsystem.components.SvHeroKind
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPersonChoiceRow
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvSectionHeader
import com.dantech.splitvibe.designsystem.components.SvTextField
import com.dantech.splitvibe.designsystem.components.SvTopBar
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.resolve
import com.dantech.splitvibe.ui.common.ProxyErrors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where joining a group stands. */
data class JoinState(
    val invitation: Invitation? = null,
    val working: Boolean = false,
    val message: UiText? = null,
    /** The user asked for a kept place and waits for the owner. */
    val waiting: Boolean = false,
    /** The user is in the group, which opens next. */
    val joinedGroupId: String? = null,
)

/** Opens an invite link: shows the group, then joins it as a new member or asks for a kept place. */
class JoinGroupViewModel(
    private val token: String,
    private val repository: GroupRepository,
    private val account: StateFlow<Account>,
    private val settings: StateFlow<AppSettings>,
    private val saveName: (String) -> Unit,
) : ViewModel() {

    private val mutableState = MutableStateFlow(JoinState())
    val state: StateFlow<JoinState> = mutableState.asStateFlow()

    /** Invites need a sign-in; one opened before it loads as soon as someone signs in. */
    val signedIn: StateFlow<Boolean> = account.map { it.session != null }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        account.value.session != null,
    )
    val myName: String get() = settings.value.myName

    init {
        signedIn.filter { it }.onEach { load() }.launchIn(viewModelScope)
    }

    fun load() {
        if (!signedIn.value) return
        run {
            val invitation = repository.invitation(token)
            mutableState.update {
                it.copy(invitation = invitation, joinedGroupId = invitation.group.id.takeIf { invitation.alreadyMember })
            }
        }
    }

    fun join(name: String) = run {
        saveName(name)
        val group = repository.join(token, name)
        mutableState.update { it.copy(joinedGroupId = group.group.id) }
    }

    /** Asks to be the kept place [memberId]; the group's owner decides. */
    fun claim(memberId: String, name: String) = run {
        saveName(name)
        repository.claim(token, memberId, name)
        mutableState.update { it.copy(waiting = true) }
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.update { it.copy(working = true, message = null) }
            try {
                block()
                mutableState.update { it.copy(working = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(working = false, message = ProxyErrors.messageOf(e)) }
            }
        }
    }
}

/**
 * An invite link: the group it is for, the name the user goes by in it, and the places kept for
 * someone without the app, one of which the user may ask to be. The way in stands at the bottom of
 * the screen, above the keyboard while the name is typed.
 */
@Composable
fun JoinGroupScreen(
    state: JoinState,
    signedIn: Boolean,
    myName: String,
    onJoin: (String) -> Unit,
    onClaim: (memberId: String, name: String) -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(myName) }
    var claiming by rememberSaveable { mutableStateOf<String?>(null) }
    val invitation = state.invitation.takeIf { signedIn }
    // A place someone else took meanwhile is no longer offered once the invite loads again.
    val chosen = invitation?.placeholders?.firstOrNull { it.id == claiming }
    Scaffold(
        // The name is typed in here, so the screen and its button end above the keyboard.
        modifier = Modifier.imePadding(),
        topBar = {
            SvTopBar(
                title = stringResource(R.string.groups_join_title),
                backLabel = stringResource(R.string.common_back),
                onBack = onBack,
            )
        },
        bottomBar = {
            if (invitation != null && !state.waiting) {
                // Where the floating bar puts its main action on other screens.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = SvSpacing.screen, end = SvSpacing.screen, top = SvSpacing.sm, bottom = SvSpacing.xl),
                ) {
                    SvPrimaryButton(
                        text = if (chosen ==
                            null
                        ) {
                            stringResource(R.string.groups_join)
                        } else {
                            stringResource(R.string.groups_ask_to_be, chosen.name)
                        },
                        onClick = { if (chosen == null) onJoin(name) else onClaim(chosen.id, name) },
                        enabled = name.isNotBlank() && !state.working,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = SvSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.lg),
        ) {
            val margins = Modifier.padding(horizontal = SvSpacing.screen)
            if (!signedIn) {
                SvMessageCard(
                    text = stringResource(R.string.groups_join_sign_in),
                    isError = false,
                    action = stringResource(R.string.common_sign_in_in_settings),
                    onAction = onOpenSettings,
                    modifier = margins,
                )
                return@Column
            }
            state.message?.let {
                SvMessageCard(
                    text = it.resolve(),
                    isError = true,
                    action = stringResource(R.string.common_try_again),
                    onAction = onRetry,
                    modifier = margins,
                )
            }
            if (invitation == null) {
                if (state.working) SvLoadingIndicator(modifier = margins)
                return@Column
            }
            // A screen reader reads the block as one element.
            SvHero(modifier = margins.semantics(mergeDescendants = true) {}, kind = SvHeroKind.Accent) {
                Text(invitation.group.name, style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(R.string.groups_balances_in, invitation.group.currency.code),
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                )
            }
            if (state.waiting) {
                SvMessageCard(text = stringResource(R.string.groups_join_asked), isError = false, modifier = margins)
                return@Column
            }
            SvTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.groups_your_name),
                modifier = margins.fillMaxWidth(),
                maxLength = NAME_LENGTH,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            if (invitation.placeholders.isNotEmpty()) {
                Column {
                    SvSectionHeader(
                        stringResource(R.string.groups_join_placeholders),
                        hint = stringResource(R.string.groups_join_placeholders_hint),
                    )
                    // One choice out of the places kept, or none, for a screen reader too.
                    SvCard(
                        modifier = margins
                            .fillMaxWidth()
                            .selectableGroup(),
                    ) {
                        invitation.placeholders.forEach { placeholder ->
                            SvPersonChoiceRow(
                                name = placeholder.name,
                                selected = claiming == placeholder.id,
                                onSelect = { claiming = if (claiming == placeholder.id) null else placeholder.id },
                            )
                        }
                    }
                }
            }
        }
    }
}
