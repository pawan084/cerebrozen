package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.auth.googleIdToken
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

private enum class AuthMode { Password, Otp }

/** Sign in / create account — email+password, passwordless email code (OTP), or
 * Google. Same backend flows as iOS and the web app; Google degrades gracefully
 * until a web client id is configured. */
@Composable
fun AuthScreen() {
    var mode by remember { mutableStateOf(AuthMode.Password) }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clientId = stringResource(R.string.google_web_client_id)

    fun run(block: suspend () -> Unit) {
        busy = true; error = null
        scope.launch {
            try { block() } catch (e: Exception) { error = e.message ?: "Something went wrong." } finally { busy = false }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("PRIVATE BY DESIGN", style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        Text(
            if (creating && mode == AuthMode.Password) "Create your space" else "Welcome back",
            style = MaterialTheme.typography.displaySmall, color = TextPrimary,
        )
        Text("Your quiet space for daily mental fitness — synced with iOS and the web.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)

        OutlinedButton(
            onClick = {
                run {
                    val result = googleIdToken(context, clientId)
                    if (result == null) {
                        error = "Google sign-in isn't set up yet — use email below."
                    } else {
                        Session.signInWithGoogle(result.first, result.second)
                    }
                }
            },
            enabled = !busy, modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue with Google") }

        Text("or", style = MaterialTheme.typography.bodyMedium, color = TextMuted,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))

        when (mode) {
            AuthMode.Password -> {
                if (creating) {
                    AppTextField(name, { name = it }, "Name", singleLine = true)
                }
                AppTextField(email, { email = it }, "Email", singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                AppTextField(password, { password = it }, "Password", singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                PrimaryButton(
                    text = if (busy) "One moment…" else if (creating) "Create my account" else "Sign in",
                    enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    run {
                        if (creating) Session.signUp(email.trim(), password, name.trim())
                        else Session.signIn(email.trim(), password)
                    }
                }

                TextButton(onClick = { creating = !creating; error = null }) {
                    Text(if (creating) "I already have an account" else "New here? Create your space", color = TextMuted)
                }
                if (!creating) {
                    // Reset completes via the emailed web link (same as iOS);
                    // the server always answers 200 — no account enumeration.
                    TextButton(
                        enabled = !busy && email.isNotBlank(),
                        onClick = {
                            run {
                                Session.forgotPassword(email.trim())
                                info = "Reset link sent — check your email."
                            }
                        },
                    ) {
                        Text("Forgot password?", color = TextMuted)
                    }
                }
                TextButton(onClick = { mode = AuthMode.Otp; error = null; info = null }) {
                    Text("Email me a code instead", color = Periwinkle)
                }
            }

            AuthMode.Otp -> {
                AppTextField(email, { email = it }, "Email", singleLine = true, enabled = !otpSent,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                if (otpSent) {
                    AppTextField(code, { if (it.length <= 6) code = it.filter(Char::isDigit) },
                        "6-digit code", singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                PrimaryButton(
                    text = if (busy) "One moment…" else if (otpSent) "Verify code" else "Email me a code",
                    enabled = !busy && (if (otpSent) code.length == 6 else email.isNotBlank()),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    run {
                        if (!otpSent) {
                            Session.otpRequest(email.trim()); otpSent = true
                            info = "Code sent to ${email.trim()} — check your email."
                        } else {
                            Session.otpVerify(email.trim(), code)
                        }
                    }
                }

                TextButton(onClick = { mode = AuthMode.Password; otpSent = false; code = ""; error = null; info = null }) {
                    Text("Use a password instead", color = TextMuted)
                }
            }
        }

        info?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Periwinkle) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
