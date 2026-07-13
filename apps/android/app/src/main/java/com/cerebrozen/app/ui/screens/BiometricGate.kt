package com.cerebrozen.app.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Confirm the user's device credential (PIN/password/pattern or biometrics) before
 * a sensitive journal-lock change — in *either* direction, since unlocking is the
 * sensitive one. Falls through to success when no secure lock is set up (so the
 * control never becomes unusable), mirroring the iOS Face ID gate.
 *
 * Single shared implementation used by both the Settings privacy screen and the
 * in-Journal "Private mode" toggle, so the gate can't drift between call sites.
 */
internal fun requestScreenLock(activity: FragmentActivity?, onResult: (Boolean) -> Unit) {
    if (activity == null) { onResult(true); return }
    val auths = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    if (BiometricManager.from(activity).canAuthenticate(auths) != BiometricManager.BIOMETRIC_SUCCESS) {
        onResult(true)
        return
    }
    BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
        },
    ).authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm screen lock")
            .setSubtitle("Use your phone PIN, password, pattern, or biometrics")
            .setAllowedAuthenticators(auths)
            .build(),
    )
}
