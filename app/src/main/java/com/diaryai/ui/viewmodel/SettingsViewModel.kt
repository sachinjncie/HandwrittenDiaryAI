package com.diaryai.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diaryai.service.GemmaModelManager
import com.diaryai.service.ModelDownloadStatus
import com.diaryai.util.GoogleAuthManager
import com.diaryai.util.GoogleAuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val gemmaModelManager: GemmaModelManager
) : ViewModel() {

    val googleAuthState: StateFlow<GoogleAuthState> = googleAuthManager.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GoogleAuthState())

    val modelDownloadStatus: StateFlow<ModelDownloadStatus> = gemmaModelManager.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelDownloadStatus())

    // ── Google Sign-In ───────────────────────────────────────────────────

    fun getGoogleSignInIntent(withDriveScope: Boolean = false): Intent =
        googleAuthManager.getSignInIntent(withDriveScope)

    fun handleGoogleSignInResult(data: Intent?) {
        googleAuthManager.handleSignInResult(data)
    }

    fun googleSignOut() {
        googleAuthManager.signOut()
    }

    // ── Gemma Model ──────────────────────────────────────────────────────

    /**
     * Start downloading the Gemma model.
     *
     * @param url      Direct HuggingFace resolve URL for the .task file
     * @param hfToken  HuggingFace read token (required — Gemma is a gated model)
     * @param wifiOnly If true, download only on Wi-Fi
     */
    fun downloadGemmaModel(
        url: String = GemmaModelManager.MODEL_URL_CPU_INT4,
        hfToken: String = "",
        wifiOnly: Boolean = false
    ) {
        gemmaModelManager.startDownload(url, hfToken, wifiOnly)
    }

    fun cancelModelDownload() {
        gemmaModelManager.cancelDownload()
    }

    fun pollModelDownload() {
        gemmaModelManager.pollDownloadProgress()
    }

    fun deleteGemmaModel() {
        gemmaModelManager.deleteModel()
    }
}
