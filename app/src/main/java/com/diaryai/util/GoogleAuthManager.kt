package com.diaryai.util

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class GoogleAuthState(
    val isSignedIn: Boolean = false,
    val accountEmail: String? = null,
    val accountName: String? = null,
    val error: String? = null,
    val needsSetup: Boolean = false   // true when SHA-1 not registered
)

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) {
    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }

    // Sign-in options: request email only first, Drive scope added after setup
    private val gsoEmailOnly = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .build()

    private val gsoDrive = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
        .build()

    private val _authState = MutableStateFlow(GoogleAuthState())
    val authState: StateFlow<GoogleAuthState> = _authState.asStateFlow()

    val signInClientEmailOnly: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, gsoEmailOnly)
    }

    val signInClientDrive: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, gsoDrive)
    }

    init {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (lastAccount != null) {
            _authState.value = GoogleAuthState(
                isSignedIn = true,
                accountEmail = lastAccount.email,
                accountName = lastAccount.displayName
            )
            settingsManager.googleAccountEmail = lastAccount.email ?: ""
        }
    }

    /**
     * Returns the intent to launch for sign-in.
     * Uses email-only first (no Drive scope) to avoid SHA-1 requirement.
     * Drive scope is requested separately after the user sets up OAuth properly.
     */
    fun getSignInIntent(withDriveScope: Boolean = false): Intent {
        return if (withDriveScope) {
            signInClientDrive.signInIntent
        } else {
            signInClientEmailOnly.signInIntent
        }
    }

    fun handleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
            _authState.value = GoogleAuthState(
                isSignedIn = true,
                accountEmail = account.email,
                accountName = account.displayName
            )
            settingsManager.googleAccountEmail = account.email ?: ""
        } catch (e: ApiException) {
            val (msg, needsSetup) = when (e.statusCode) {
                12501 -> Pair("Sign-in cancelled", false)
                12502 -> Pair("Sign-in already in progress", false)
                10    -> Pair(
                    "One-time setup needed: register this app's SHA-1 fingerprint in Google Cloud Console. " +
                    "See the Drive Setup guide below.",
                    true
                )
                else  -> Pair("Sign-in error (code ${e.statusCode}). Try again.", false)
            }
            _authState.value = GoogleAuthState(
                isSignedIn = false,
                error = msg,
                needsSetup = needsSetup
            )
        }
    }

    fun signOut() {
        signInClientEmailOnly.signOut().addOnCompleteListener {
            _authState.value = GoogleAuthState()
            settingsManager.googleAccountEmail = ""
        }
    }

    fun getSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    val isSignedIn: Boolean
        get() = GoogleSignIn.getLastSignedInAccount(context) != null
}
