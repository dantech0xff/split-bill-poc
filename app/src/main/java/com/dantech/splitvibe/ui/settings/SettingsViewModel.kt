package com.dantech.splitvibe.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.data.account.AccountRepository
import com.dantech.splitvibe.data.account.GoogleSignInException
import com.dantech.splitvibe.data.proxy.ProxyException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the account part of Settings is doing, and what went wrong last, if anything. */
data class AccountUiState(val working: Boolean = false, val message: String? = null)

/** Signing in and out, deleting the account, and the calls left, for the Settings screen. */
class SettingsViewModel(private val repository: AccountRepository) : ViewModel() {

    private val state = MutableStateFlow(AccountUiState())
    val ui: StateFlow<AccountUiState> = state.asStateFlow()

    val canSignIn: Boolean get() = repository.canSignIn

    init {
        // The count shown is the proxy's, so it is asked for each time Settings opens.
        act(quiet = true) { repository.refresh() }
    }

    /** [activity] shows Google's account picker; it is used for this call only. */
    fun signIn(activity: Context) = act { repository.signIn(activity) }

    fun signOut(context: Context) = act { repository.signOut(context) }

    fun deleteAccount(context: Context) = act { repository.deleteAccount(context) }

    /** A quiet action shows no progress, and stays silent when the proxy just cannot be reached. */
    private fun act(quiet: Boolean = false, block: suspend () -> Unit) {
        if (state.value.working) return
        viewModelScope.launch {
            state.value = AccountUiState(working = !quiet)
            val message = try {
                block()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (quiet && e is IOException) null else messageOf(e)
            }
            state.value = AccountUiState(message = message)
        }
    }
}

/** What to tell the person when an account action did not work, or null when there is nothing to say. */
internal fun messageOf(error: Exception): String? = when (error) {
    is GoogleSignInException -> when (error.reason) {
        GoogleSignInException.Reason.CANCELLED -> null
        GoogleSignInException.Reason.NO_ACCOUNT -> "Add a Google account to this phone, then try again."
        GoogleSignInException.Reason.FAILED -> "Signing in with Google did not work. Please try again."
    }

    is ProxyException -> when (error.code) {
        "id_token" -> "Split Vibe's server did not accept this Google sign-in. Please try again."
        "sign_in_off" -> "Signing in is not available right now. Please try again later."
        AccountRepository.SESSION_REFUSED -> "Your sign-in has run out. Sign in again."
        else -> "Split Vibe's server had a problem. Try again in a moment."
    }

    is IOException -> "Could not reach Split Vibe's server. Check the internet connection and try again."

    else -> "Something went wrong: ${error.message ?: error.javaClass.simpleName}"
}
