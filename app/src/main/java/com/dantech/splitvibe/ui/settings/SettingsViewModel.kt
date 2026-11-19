package com.dantech.splitvibe.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.account.AccountRepository
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
data class AccountUiState(val working: Boolean = false, val message: UiText? = null, val isError: Boolean = false)

/** Signing in and out, deleting the account, and the calls left, for the Settings screen. */
class SettingsViewModel(private val repository: AccountRepository) : ViewModel() {

    private val state = MutableStateFlow(AccountUiState())
    val ui: StateFlow<AccountUiState> = state.asStateFlow()

    val canSignIn: Boolean get() = repository.canSignIn

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
    fun signIn(activity: Context) = act { repository.signIn(activity) }

    fun signOut(context: Context) = act { repository.signOut(context) }

    fun deleteAccount(context: Context) = act(done = text(R.string.settings_account_deleted)) { repository.deleteAccount(context) }

    /** Runs one account action at a time. [done] is said once it has worked. */
    private fun act(done: UiText? = null, block: suspend () -> Unit) {
        if (state.value.working) return
        refreshing.cancel()
        state.value = AccountUiState(working = true)
        viewModelScope.launch {
            state.value = try {
                block()
                AccountUiState(message = done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AccountUiState(message = messageOf(e), isError = true)
            }
        }
    }
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
