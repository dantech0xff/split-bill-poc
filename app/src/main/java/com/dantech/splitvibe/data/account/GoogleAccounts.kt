package com.dantech.splitvibe.data.account

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** The Google account a person picked, with the ID token Google made out to this app for it. */
data class GoogleAccount(val idToken: String, val email: String)

class GoogleSignInException(val reason: Reason, cause: Throwable? = null) : Exception("Google sign-in ended: $reason", cause) {
    enum class Reason {
        /** The person closed Google's account picker. */
        CANCELLED,

        /** The phone has no Google account to offer. */
        NO_ACCOUNT,

        /** The phone cannot sign in with Google at all, for example because it has no Google Play services. */
        UNAVAILABLE,
        FAILED,
    }
}

/** Which Google account the person is, as the phone's account picker tells it. */
interface GoogleAccounts {
    /** Shows Google's account picker over [activity]. */
    suspend fun pick(activity: Context): GoogleAccount

    /** Tells the phone the person signed out, so the next sign-in offers every account again. */
    suspend fun forget(context: Context)
}

/** Sign in with Google through the system's Credential Manager, for the app's web client ID at Google. */
class CredentialManagerGoogleAccounts(private val webClientId: String) : GoogleAccounts {

    override suspend fun pick(activity: Context): GoogleAccount {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId = webClientId).build())
            .build()
        val response = try {
            CredentialManager.create(activity).getCredential(context = activity, request = request)
        } catch (e: GetCredentialCancellationException) {
            throw GoogleSignInException(GoogleSignInException.Reason.CANCELLED, e)
        } catch (e: NoCredentialException) {
            throw GoogleSignInException(GoogleSignInException.Reason.NO_ACCOUNT, e)
        } catch (e: GetCredentialProviderConfigurationException) {
            throw GoogleSignInException(GoogleSignInException.Reason.UNAVAILABLE, e)
        } catch (e: GetCredentialUnsupportedException) {
            throw GoogleSignInException(GoogleSignInException.Reason.UNAVAILABLE, e)
        } catch (e: GetCredentialException) {
            throw GoogleSignInException(GoogleSignInException.Reason.FAILED, e)
        }
        val credential = response.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw GoogleSignInException(GoogleSignInException.Reason.FAILED)
        }
        val google = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (e: GoogleIdTokenParsingException) {
            throw GoogleSignInException(GoogleSignInException.Reason.FAILED, e)
        }
        return GoogleAccount(idToken = google.idToken, email = google.id)
    }

    override suspend fun forget(context: Context) {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }
}
