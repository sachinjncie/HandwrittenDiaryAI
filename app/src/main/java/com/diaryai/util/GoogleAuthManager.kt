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
    val error: String? = null
)

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) {
    companion object {
        // Drive AppDataFolder scope — gives access only to app's private Drive folder
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val RC_SIGN_IN = 9001
    }

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
        .build()

    private val _authState = MutableStateFlow(GoogleAuthState())
    val authState: StateFlow<GoogleAuthState> = _authState.asStateFlow()

    val signInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, gso)
    }

    init {
        // Restore sign-in state from last session
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

    /** Call this from your ActivityResultLauncher callback */
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
            val msg = when (e.statusCode) {
                12501 -> "Sign-in cancelled"
                12502 -> "Sign-in in progress"
                10   -> "Developer configuration error — check SHA-1 in Firebase console"
                else -> "Sign-in failed (code ${e.statusCode})"
            }
            _authState.value = GoogleAuthState(isSignedIn = false, error = msg)
        }
    }

    fun signOut() {
        signInClient.signOut().addOnCompleteListener {
            _authState.value = GoogleAuthState()
            settingsManager.googleAccountEmail = ""
        }
    }

    fun getSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    val isSignedIn: Boolean
        get() = GoogleSignIn.getLastSignedInAccount(context) != null
}
