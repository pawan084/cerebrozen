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
    // Debug convenience: the platform's DEV-SEEDED member, so a dev build signs in with one
    // tap. It must be an account that actually exists — this prefilled
    // `worker@acme-test.example` / `walkthrough123` until 2026-07-17, which the platform
    // has never seeded, so tapping Continue on a fresh device just... did nothing visible.
    // No error worth reading, no account: the one-tap convenience cost more time than
    // typing would have. Found on the owner's phone (see docs/ANDROID_QA.md).
    //
    // Keep these in step with services/platform's seeder (PERSONAS in e2e/tests/helpers.ts
    // is the same three accounts) — a prefill that names a non-existent account is worse
    // than an empty field, because an empty field tells you to go and find a credential.
    //
    // The `BuildConfig.DEBUG` gate must stay a plain compile-time constant so R8 folds the
    // branch away and these literals never reach a release APK. Do NOT make it configurable
    // (an env var, a gradle property): apps/admin shipped exactly that mistake — the
    // condition became runtime-unknowable, the minifier kept the branch, and the demo
    // credentials went out in the public bundle. `test_release_apk_has_no_dev_credentials`
    // in this module's QA notes is the check; assertions live in the release build itself.
    // Prefill the dev-seeded member ONLY on the sign-IN path. A signup form must start
    // empty: demo@ already exists (→ 409) and demo12345 is under the 10-char signup minimum,
    // so prefilling it made "Create my account" fail out of the box.
    var email by rememberSaveable { mutableStateOf(if (BuildConfig.DEBUG && !initialCreating) "demo@cerebrozen.in" else "") }
    var password by remember { mutableStateOf(if (BuildConfig.DEBUG && !initialCreating) "demo12345" else "") }
    var code by rememberSaveable { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showPw by remember { mutableStateOf(false) }
    // The email is already registered — offer a one-tap switch to sign-in instead of a 409.
    var existingAccount by remember { mutableStateOf(false) }
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
    val emailError = stringResource(R.string.auth_error_email)
    val passwordShortError = stringResource(R.string.auth_error_password_short)
    val signinCredsError = stringResource(R.string.auth_error_signin_creds)
    val networkError = stringResource(R.string.auth_error_network)

    fun run(block: suspend () -> Unit) {
        busy = true; error = null; existingAccount = false
        scope.launch {
            try {
                block()
            } catch (e: Session.ApiException) {
                // Differentiate without enabling account enumeration: a 409 on signup means
                // "you already have one" (→ offer sign-in); a 401 on sign-in is the SAME
                // "email or password is wrong" whether or not the email exists.
                when {
                    creating && e.code == 409 -> existingAccount = true
                    !creating && e.code == 401 -> error = signinCredsError
                    else -> error = e.message ?: genericError
                }
            } catch (e: Exception) {
                // Not an HTTP status — a dropped connection, DNS, timeout. Say so, so the
                // person retries the network rather than doubting their password.
                error = networkError
            } finally {
                busy = false
            }
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
                    if (creating) {
                        // Fail fast client-side so a bad address / short password never
                        // round-trips to a 400. Mirrors the platform's signup rules.
                        val e = email.trim()
                        val domain = e.substringAfterLast("@", "")
                        if ("@" !in e || e.startsWith("@") || "." !in domain || domain.startsWith(".")) {
                            error = emailError; return
                        }
                        if (password.length < 10) { error = passwordShortError; return }
                    }
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
                // The platform lowercases the address on signup — show the person exactly
                // what they'll type at sign-in, so a stray capital never surprises them later.
                if (creating && "@" in email.trim() && !email.trim().endsWith("@")) {
                    Text(
                        stringResource(R.string.auth_email_preview, email.trim().lowercase()),
                        style = MaterialTheme.typography.bodySmall, color = TextMuted,
                        modifier = Modifier.padding(start = 2.dp),
                    )
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
                // Password-strength meter (signup only): the platform's floor is 10 chars, so
                // a short one shows red with the length hint; a longer / mixed one earns fair
                // then strong. Guidance, never a gate — the button already enforces the rule.
                if (creating && password.isNotEmpty()) {
                    val strength = when {
                        password.length < 10 -> 0
                        password.length < 14 && password.all { it.isLetterOrDigit() } -> 1
                        else -> 2
                    }
                    val barColor = listOf(Danger, Color(0xFFE0B341), Color(0xFF6BCB77))[strength]
                    val label = stringResource(
                        listOf(R.string.auth_pw_weak, R.string.auth_pw_fair, R.string.auth_pw_strong)[strength],
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { i ->
                            Box(
                                Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                                    .background(if (i <= strength) barColor else Color.White.copy(alpha = 0.12f)),
                            )
                        }
                    }
                    Text(label, style = MaterialTheme.typography.bodySmall, color = barColor,
                        modifier = Modifier.padding(start = 2.dp))
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
        // Already registered: don't dead-end on a 409 — offer the one tap that helps. The
        // typed email (and password) carry over, so it's a genuine one-tap switch to sign-in.
        if (existingAccount) {
            AuthInfoCard(stringResource(R.string.auth_account_exists))
            AuthWhiteButton(
                text = stringResource(R.string.auth_signin_instead),
                modifier = Modifier.fillMaxWidth(),
            ) { creating = false; existingAccount = false; error = null; info = null }
        }
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
