package com.dantech.splitvibe.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.account.AccountRepository
import com.dantech.splitvibe.data.account.Deletion
import com.dantech.splitvibe.data.account.DeletionOutcome
import com.dantech.splitvibe.data.account.GoogleSignInException
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.llm.waitHint
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.sentences
import com.dantech.splitvibe.text.text
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the account part of Settings is doing, and how the last action ended, if there is anything to say. */
data class AccountUiState(
    val working: Boolean = false,
    val message: UiText? = null,
    val isError: Boolean = false,
    /**
     * The action the person started on this visit to Settings that runs or that ended with [message],
     * which shows by the button that started it; null for what Settings found out by itself, such as a
     * count it could not get, or a deletion started on an earlier visit.
     */
    val action: AccountAction? = null,
)

/** What the person can do with their account in Settings. */
enum class AccountAction { SIGN_IN, SIGN_OUT, DELETE }

/** Signing in and out, deleting the account, and the calls left, for the Settings screen. */
class SettingsViewModel(private val repository: AccountRepository) : ViewModel() {

    private val state = MutableStateFlow(AccountUiState())
    val ui: StateFlow<AccountUiState> = state.asStateFlow()

    val canSignIn: Boolean get() = repository.canSignIn

    /** Whether the deletion that runs was started here, rather than on an earlier visit to Settings. */
    private var deletingHere = false

    init {
        // A deletion goes on after Settings is left, so Settings follows it whenever it shows, and
        // says how one that ended while it was closed came out.
        viewModelScope.launch {
            repository.deletion.collect { deletion ->
                val action = AccountAction.DELETE.takeIf { deletingHere }
                when (deletion) {
                    null -> Unit
                    Deletion.Running -> state.value = AccountUiState(working = true, action = action)
                    is Deletion.Ended -> said(textOf(deletion.outcome), isError = deletion.outcome !in DONE, action = action)
                    is Deletion.Refused -> said(messageOf(deletion.error), isError = true, action = action)
                }
            }
        }
        // One the app was ended in the middle of, which its start could not finish, such as without a connection.
        repository.finishInterruptedDeletion()
    }

    /**
     * The count shown is the proxy's, so it is asked for each time Settings opens, quietly: no
     * progress, and nothing to say when the proxy just cannot be reached. An action stops it, because
     * its count may be the install's and it must not end the action's progress.
     */
    private val refreshing: Job = viewModelScope.launch {
        try {
            repository.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // Offline: the card simply shows no count.
        } catch (e: Exception) {
            state.value = AccountUiState(message = messageOf(e), isError = true)
        }
    }

    /** [activity] shows Google's account picker; it is used for this call only. */
    fun signIn(activity: Context) = act(AccountAction.SIGN_IN) { repository.signIn(activity) }

    fun signOut(context: Context) = act(AccountAction.SIGN_OUT) { repository.signOut(context) }

    /**
     * Deletes the account, and [withPhoneData] also what only this phone keeps, once the proxy has
     * deleted the account. It goes on when Settings is left, and Settings says how it ended.
     */
    fun deleteAccount(withPhoneData: Boolean) {
        if (state.value.working) return
        refreshing.cancel()
        deletingHere = true
        state.value = AccountUiState(working = true, action = AccountAction.DELETE)
        repository.deleteAccount(withPhoneData)
    }

    /** Runs one account action at a time. */
    private fun act(action: AccountAction, block: suspend () -> Unit) {
        if (state.value.working) return
        refreshing.cancel()
        state.value = AccountUiState(working = true, action = action)
        viewModelScope.launch {
            state.value = try {
                block()
                AccountUiState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AccountUiState(message = messageOf(e), isError = true, action = action)
            }
        }
    }

    /** Shows [message] about how the last deletion ended, which then counts as said. */
    private fun said(message: UiText?, isError: Boolean, action: AccountAction?) {
        state.value = AccountUiState(message = message, isError = isError, action = action)
        deletingHere = false
        repository.deletionShown()
    }

    private companion object {
        /** The outcomes of a deletion that did all the person asked for. */
        val DONE = setOf(DeletionOutcome.DELETED, DeletionOutcome.DELETED_WITH_PHONE_DATA)
    }
}

/** What Settings says about a deletion that ended as [outcome]. */
internal fun textOf(outcome: DeletionOutcome): UiText = when (outcome) {
    DeletionOutcome.DELETED -> text(R.string.settings_account_deleted)
    DeletionOutcome.DELETED_WITH_PHONE_DATA -> text(R.string.settings_account_and_phone_data_deleted)
    DeletionOutcome.ACCOUNT_DATA_NOT_DELETED -> text(R.string.settings_account_data_not_deleted)
    DeletionOutcome.PHONE_DATA_NOT_DELETED -> text(R.string.settings_phone_data_not_deleted)
    DeletionOutcome.NOT_CONFIRMED -> text(R.string.settings_deletion_not_confirmed)
}

/**
 * What to tell the person when an account action did not work, or null when there is nothing to say.
 * An action the proxy limited, such as deleting the account more often than it lets an account in an
 * hour, changed nothing. An exception's own message, written for developers, is never shown; the
 * Pro screen's sign-in says the same as Settings.
 */
internal fun messageOf(error: Exception): UiText? = when (error) {
    is GoogleSignInException -> when (error.reason) {
        GoogleSignInException.Reason.CANCELLED -> null
        GoogleSignInException.Reason.NO_ACCOUNT -> text(R.string.sign_in_no_account)
        GoogleSignInException.Reason.UNAVAILABLE -> text(R.string.sign_in_unavailable)
        GoogleSignInException.Reason.FAILED -> text(R.string.sign_in_failed)
    }

    is ProxyException -> when (error.code) {
        "id_token" -> text(R.string.sign_in_id_token)
        "sign_in_off" -> text(R.string.sign_in_off)
        ProxyApi.SESSION_REFUSED -> text(R.string.sign_in_run_out)
        "rate_limited" -> sentences(text(R.string.account_rate_limited), waitHint(error.retryAfterSeconds))
        else -> text(R.string.server_problem)
    }

    is IOException -> text(R.string.server_unreachable)

    else -> text(R.string.common_something_went_wrong)
}
