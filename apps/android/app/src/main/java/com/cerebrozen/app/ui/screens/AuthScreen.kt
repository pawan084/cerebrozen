package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerebrozen.app.BuildConfig
import com.cerebrozen.app.auth.googleIdToken
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.Danger
import com.cerebrozen.app.ui.theme.Ink
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.NightMid
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.PeriwinkleDeep
import com.cerebrozen.app.ui.theme.PeriwinkleSoft
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextMuted2
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.AuthEyebrow
import com.cerebrozen.app.ui.theme.AuthFieldLabel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AuthMode { Password, Otp }

/**
 * Sign up, then run the post-signup personalization writes shielded from
 * cancellation.
 *
 * The race: [Session.signUp] flips [Session.signedIn] (Compose-observable),
 * which immediately swaps the root composition from Onboarding to the
 * signed-in NavHost — disposing this screen and cancelling its
 * rememberCoroutineScope() at the next suspension point. Without a shield,
 * everything after signup dies before it reaches the network (observed
 * on-device: POST /auth/signup → 201, then zero of the follow-up consent/
 * profile/check-in calls). NonCancellable lets [personalize] run to
 * completion; the writes stay best-effort (each is runCatching-wrapped by
 * the caller), so a partial failure still never blocks entering the app.
 */
internal suspend fun signUpThenPersonalize(
    signUp: suspend () -> Unit,
    personalize: suspend () -> Unit,
) {
    signUp()
    withContext(NonCancellable) { personalize() }
}

/** Sign in / create account — email+password, passwordless email code (OTP), or
 * Google. Same backend flows as iOS and the web app; Google degrades gracefully
 * until a web client id is configured. */
@Composable
fun AuthScreen(
    onBack: (() -> Unit)? = null,
    initialCreating: Boolean = false,
    onAccountCreated: suspend () -> Unit = {},
) {
    var mode by remember { mutableStateOf(AuthMode.Password) }
    var creating by remember(initialCreating) { mutableStateOf(initialCreating) }
    // Identifiers survive rotation so they aren't re-typed. Password is deliberately
    // NOT saved — persisting a secret into instance state is an anti-pattern.
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showPw by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    // Web (server) OAuth client id, resolved at build time (blank = Google inert).
    val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID

    fun run(block: suspend () -> Unit) {
        busy = true; error = null
        scope.launch {
            try { block() } catch (e: Exception) { error = e.message ?: "Something went wrong." } finally { busy = false }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PeriwinkleDeep.copy(alpha = 0.55f), NightMid, Night))),
    ) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 30.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (onBack != null) {
                Box(
                    Modifier.size(45.dp).clip(RoundedCornerShape(23.dp))
                        .background(Color.White.copy(alpha = 0.10f)).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.ArrowBackIosNew, "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Column {
                Text("PRIVATE BY DESIGN", style = MaterialTheme.typography.labelSmall,
                    color = AuthEyebrow, letterSpacing = 1.8.sp)
                Text(
                    if (creating) "Create your space" else "Sign in",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp),
                    color = Color.White,
                )
            }
        }
        AuthInfoCard(
            if (creating) {
                "Create your quiet space for daily mental fitness, journal and check-ins."
            } else {
                "Sign in to keep your plan, journal and check-ins in sync across devices."
            },
            modifier = Modifier.appear(2),
        )

        AuthWhiteButton(
            text = "G   Continue with Google",
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().appear(3),
        ) {
            run {
                val result = googleIdToken(context, clientId)
                if (result == null) {
                    error = "Google sign-in isn't set up yet — use email below."
                } else {
                    Session.signInWithGoogle(result.first, result.second)
                }
            }
        }

        AuthDivider(if (mode == AuthMode.Password) "OR USE EMAIL" else "EMAIL CODE")

        when (mode) {
            AuthMode.Password -> {
                val pwReady = !busy && email.isNotBlank() && password.isNotBlank()
                fun submitPw() {
                    if (!pwReady) return
                    focus.clearFocus()
                    run {
                        if (creating) {
                            signUpThenPersonalize(
                                signUp = { Session.signUp(email.trim(), password, name.trim()) },
                                personalize = onAccountCreated,
                            )
                        } else {
                            Session.signIn(email.trim(), password)
                        }
                    }
                }
                if (creating) {
                    AuthFieldLabel("Name") {
                        AppTextField(name, { name = it }, "", placeholderText = "Your name", singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }))
                    }
                }
                AuthFieldLabel("Email") {
                    AppTextField(email, { email = it }, "", placeholderText = "you@email.com", singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }))
                }
                AuthFieldLabel("Password") {
                    AppTextField(password, { password = it }, "", placeholderText = "••••••••", singleLine = true,
                        visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitPw() }),
                        trailingIcon = {
                            IconButton(onClick = { showPw = !showPw }) {
                                Icon(
                                    if (showPw) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showPw) "Hide password" else "Show password",
                                    tint = Periwinkle,
                                )
                            }
                        })
                }
                AuthWhiteButton(
                    text = if (busy) "One moment…" else if (creating) "@   Create my account" else "@   Continue with email",
                    enabled = pwReady,
                    modifier = Modifier.fillMaxWidth(),
                ) { submitPw() }
                if (creating) {
                    TextButton(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        onClick = { creating = false; error = null; info = null },
                    ) {
                        Text("I already have an account", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                } else {
                    // Reset completes via the emailed web link (same as iOS);
                    // the server always answers 200 — no account enumeration.
                    TextButton(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        enabled = !busy && email.isNotBlank(),
                        onClick = {
                            run {
                                Session.forgotPassword(email.trim())
                                info = "Reset link sent — check your email."
                            }
                        },
                    ) {
                        Text("Forgot password?", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                    TextButton(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp),
                        onClick = { creating = true; error = null; info = null },
                    ) {
                        Text("New here? Create your space", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                }
            }

            AuthMode.Otp -> {
                val otpReady = !busy && (if (otpSent) code.length == 6 else email.isNotBlank())
                fun submitOtp() {
                    if (!otpReady) return
                    focus.clearFocus()
                    run {
                        if (!otpSent) {
                            Session.otpRequest(email.trim()); otpSent = true
                            info = "Code sent to ${email.trim()} — check your email."
                        } else {
                            Session.otpVerify(email.trim(), code)
                        }
                    }
                }
                AppTextField(email, { email = it }, "Email", singleLine = true, enabled = !otpSent,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitOtp() }))
                if (otpSent) {
                    AppTextField(code, { if (it.length <= 6) code = it.filter(Char::isDigit) },
                        "6-digit code", singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitOtp() }))
                }
                PrimaryButton(
                    text = if (busy) "One moment…" else if (otpSent) "Verify code" else "Email me a code",
                    enabled = otpReady,
                    modifier = Modifier.fillMaxWidth(),
                ) { submitOtp() }

                TextButton(onClick = { mode = AuthMode.Password; otpSent = false; code = ""; error = null; info = null }) {
                    Text("Use a password instead", color = TextMuted)
                }
            }
        }

        info?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Periwinkle) }
        error?.let { Text(it, color = Danger) }
    }
    }
}

@Composable
private fun AuthFieldLabel(label: String, field: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = AuthFieldLabel)
        field()
    }
}

/** Contextual "why sign in" card — a glass pane with a lavender lock well and a
 * line of reassurance, mirroring the shared card treatment. */
@Composable
private fun AuthInfoCard(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Periwinkle.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = PeriwinkleSoft,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSoft,
        )
    }
}

/** White pill CTA reserved for the Google sign-in — a neutral, high-contrast
 * counterpoint to the lavender [PrimaryButton]. */
@Composable
private fun AuthWhiteButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .pressScale(pressed, down = 0.97f)
            .clip(RoundedCornerShape(26.dp))
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.32f))
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 28.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Ink else TextMuted2,
        )
    }
}

/** Labelled hairline divider — two faint rules flanking a small caption, used to
 * separate the social CTA from the email form. */
@Composable
private fun AuthDivider(label: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(LineStroke))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
            letterSpacing = 1.6.sp,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.weight(1f).height(1.dp).background(LineStroke))
    }
}
