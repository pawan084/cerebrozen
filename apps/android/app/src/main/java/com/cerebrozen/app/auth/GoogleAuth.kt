package com.cerebrozen.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Launches Google sign-in via Credential Manager and returns (idToken, name),
 * or null if it's unconfigured / cancelled / unavailable — so the caller can
 * degrade gracefully (mirrors the iOS "inert until GIDClientID is set" state).
 */
suspend fun googleIdToken(context: Context, webClientId: String): Pair<String, String>? {
    if (webClientId.isBlank()) return null
    return try {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(context).getCredential(context, request)
        val cred = result.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val gid = GoogleIdTokenCredential.createFrom(cred.data)
            gid.idToken to (gid.displayName ?: "")
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}
