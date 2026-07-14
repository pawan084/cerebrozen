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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerebrozen.app.BuildConfig
import com.cerebrozen.app.R
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

/** Sign in — email + password only. Accounts are invitation-based (the
 * platform has no self-signup/OTP/Google endpoints); the inherited paths are
 * hidden rather than left as dead buttons. Debug builds prefill the
 * walkthrough account. */
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
    // Debug convenience: the walkthrough account, so a dev build signs in
    // with one tap. Never compiled into release (BuildConfig.DEBUG).
    var email by rememberSaveable { mutableStateOf(if (BuildConfig.DEBUG) "worker@acme-test.example" else "") }
    var password by remember { mutableStateOf(if (BuildConfig.DEBUG) "walkthrough123" else "") }
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
    // Copy used inside non-composable closures — resolved once per composition.
    val genericError = stringResource(R.string.auth_error_generic)
    val googleNotSetup = stringResource(R.string.auth_google_not_setup)
    val resetSent = stringResource(R.string.auth_reset_sent)
    val codeSentTemplate = stringResource(R.string.auth_otp_code_sent)

    fun run(block: suspend () -> Unit) {
        busy = true; error = null
        scope.launch {
            try { block() } catch (e: Exception) { error = e.message ?: genericError } finally { busy = false }
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
                    Icon(Icons.Outlined.ArrowBackIosNew, stringResource(R.string.common_back), tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Column {
                Text(stringResource(R.string.auth_eyebrow), style = MaterialTheme.typography.labelSmall,
                    color = AuthEyebrow, letterSpacing = 1.8.sp)
                Text(
                    if (creating) stringResource(R.string.auth_create_title) else stringResource(R.string.auth_signin_title),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp),
                    color = Color.White,
                )
            }
        }
        AuthInfoCard(
            if (creating) {
                stringResource(R.string.auth_info_create)
            } else {
                stringResource(R.string.auth_info_signin)
            },
            modifier = Modifier.appear(2),
        )

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
                    AuthFieldLabel(stringResource(R.string.auth_name_label)) {
                        AppTextField(name, { name = it }, "", placeholderText = stringResource(R.string.auth_name_placeholder), singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }))
                    }
                }
                AuthFieldLabel(stringResource(R.string.auth_email_label)) {
                    AppTextField(email, { email = it }, "", placeholderText = stringResource(R.string.auth_email_placeholder), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }))
                }
                AuthFieldLabel(stringResource(R.string.auth_password_label)) {
                    AppTextField(password, { password = it }, "", placeholderText = "••••••••", singleLine = true,
                        visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitPw() }),
                        trailingIcon = {
                            IconButton(onClick = { showPw = !showPw }) {
                                Icon(
                                    if (showPw) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showPw) stringResource(R.string.auth_hide_password)
                                    else stringResource(R.string.auth_show_password),
                                    tint = Periwinkle,
                                )
                            }
                        })
                }
                AuthWhiteButton(
                    text = if (busy) stringResource(R.string.common_one_moment)
                    else if (creating) stringResource(R.string.auth_create_cta)
                    else stringResource(R.string.auth_email_cta),
                    enabled = pwReady,
                    modifier = Modifier.fillMaxWidth(),
                ) { submitPw() }
                if (creating) {
                    TextButton(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        onClick = { creating = false; error = null; info = null },
                    ) {
                        Text(stringResource(R.string.auth_have_account), style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                } else {
                    // Password reset and self-signup are hidden until the
                    // platform grows those endpoints (accounts are
                    // invitation-based; docs/TODO.md).
                    Text(
                        "Your account comes from your organization's invitation. Ask your admin if you need one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 18.dp),
                    )
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
                            info = codeSentTemplate.format(email.trim())
                        } else {
                            Session.otpVerify(email.trim(), code)
                        }
                    }
                }
                AppTextField(email, { email = it }, stringResource(R.string.auth_email_label), singleLine = true, enabled = !otpSent,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitOtp() }))
                if (otpSent) {
                    AppTextField(code, { if (it.length <= 6) code = it.filter(Char::isDigit) },
                        stringResource(R.string.auth_otp_code_label), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitOtp() }))
                }
                PrimaryButton(
                    text = if (busy) stringResource(R.string.common_one_moment)
                    else if (otpSent) stringResource(R.string.auth_verify_cta)
                    else stringResource(R.string.auth_email_code_cta),
                    enabled = otpReady,
                    modifier = Modifier.fillMaxWidth(),
                ) { submitOtp() }

                TextButton(onClick = { mode = AuthMode.Password; otpSent = false; code = ""; error = null; info = null }) {
                    Text(stringResource(R.string.auth_use_password), color = TextMuted)
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
