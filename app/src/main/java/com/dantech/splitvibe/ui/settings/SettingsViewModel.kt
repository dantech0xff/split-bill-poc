package com.dantech.splitvibe.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.data.account.AccountRepository
import com.dantech.splitvibe.data.account.GoogleSignInException
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the account part of Settings is doing, and how the last action ended, if there is anything to say. */
data class AccountUiState(val working: Boolean = false, val message: String? = null, val isError: Boolean = false)

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

    fun deleteAccount(context: Context) = act(done = "Your account was deleted.") { repository.deleteAccount(context) }

    /** Runs one account action at a time. [done] is said once it has worked. */
    private fun act(done: String? = null, block: suspend () -> Unit) {
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

/** What to tell the person when an account action did not work, or null when there is nothing to say. */
internal fun messageOf(error: Exception): String? = when (error) {
    is GoogleSignInException -> when (error.reason) {
        GoogleSignInException.Reason.CANCELLED -> null
        GoogleSignInException.Reason.NO_ACCOUNT -> "Add a Google account to this phone, then try again."
        GoogleSignInException.Reason.UNAVAILABLE -> "Signing in with Google is not available on this phone."
        GoogleSignInException.Reason.FAILED -> "Signing in with Google did not work. Please try again."
    }

    is ProxyException -> when (error.code) {
        "id_token" -> "Split Vibe's server did not accept this Google sign-in. Please try again."
        "sign_in_off" -> "Signing in is not available right now. Please try again later."
        ProxyApi.SESSION_REFUSED -> "Your sign-in has run out. Sign in again."
        else -> "Split Vibe's server had a problem. Try again in a moment."
    }

    is IOException -> "Could not reach Split Vibe's server. Check the internet connection and try again."

    else -> "Something went wrong: ${error.message ?: error.javaClass.simpleName}"
}
