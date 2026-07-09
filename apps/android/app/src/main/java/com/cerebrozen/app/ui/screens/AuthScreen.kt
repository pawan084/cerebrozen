package com.cerebro.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerebro.app.BuildConfig
import com.cerebro.app.auth.googleIdToken
import com.cerebro.app.net.Session
import com.cerebro.app.ui.theme.Ink
import com.cerebro.app.ui.theme.Night
import com.cerebro.app.ui.theme.NightMid
import com.cerebro.app.ui.theme.Periwinkle
import com.cerebro.app.ui.theme.TextMuted
import com.cerebro.app.ui.theme.TextPrimary
import com.cerebro.app.ui.theme.TextSoft
import kotlinx.coroutines.launch
import com.cerebro.app.ui.theme.CereBroTheme // apne theme ka naam use karo


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
    var passwordVisible by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID

    fun run(block: suspend () -> Unit) {
        busy = true; error = null
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                busy = false
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF4A3B8D), NightMid, Night))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 46.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {

            Box(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(vertical = 15.dp)) {
                    Box(
                        modifier = Modifier
//                            .align(Alignment.CenterStart)
                            .size(40.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.07f))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                            .clickable {
                                if (creating) {
                                    creating = false; error = null; info = null
                                } else {
                                    (context as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = TextSoft)
                    }
                    Column(modifier = Modifier.padding(horizontal = 10.dp)) {
                        Text(
                            "PRIVATE BY DESIGN",
                            style = MaterialTheme.typography.labelSmall,
                            color = Periwinkle,
                            letterSpacing = 3.sp,
                        )
                        Text(
                            if (creating && mode == AuthMode.Password) "Create your space" else "Sign in",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontSize = 34.sp,
                                lineHeight = 38.sp
                            ),
                            color = TextPrimary,
//                            modifier = Modifier.align(Alignment.Center),
                        )

                    }
                }
            }
            AuthInfoCard(
                if (creating) {
                    "Create your quiet space for daily mental fitness, journal and check-ins."
                } else {
                    "Sign in to keep your plan, journal and check-ins in sync across devices."
                },
            )
            AuthWhiteButton("Continue with Google", enabled = !busy, leading = "G") {
                run {
                    if (clientId.isBlank()) {
                        error =
                            "Google sign-in isn't set up yet. Add GOOGLE_WEB_CLIENT_ID and rebuild."
                    } else {
                        val result = googleIdToken(context, clientId)
                        if (result != null) Session.signInWithGoogle(result.first, result.second)
                    }
                }
            }
            AuthDivider(if (mode == AuthMode.Password) "OR USE EMAIL" else "EMAIL CODE")

            when (mode) {
                AuthMode.Password -> {
                    if (creating) AppTextField(name, { name = it }, "Name", singleLine = true)
                    AppTextField(
                        email,
                        { email = it },
                        "Email",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        placeholderText = "you@email.com",
                    )
                    AppTextField(
                        password,
                        { password = it },
                        "Password",
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = null,
                                    tint = Periwinkle,
                                )
                            }
                        },
                        placeholderText = "●●●●●●●",
                    )
                    AuthWhiteButton(
                        if (busy) "One moment..." else if (creating) "Create my account" else "Continue with email",
                        enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                        leading = "@",
                    ) {
                        run {
                            if (creating) Session.signUp(email.trim(), password, name.trim())
                            else Session.signIn(email.trim(), password)
                        }
                    }

                    if (!creating) {
                        TextButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentWidth(Alignment.CenterHorizontally),
                            enabled = !busy && email.isNotBlank(),
                            onClick = {
                                run {
                                    Session.forgotPassword(email.trim())
                                    info = "Reset link sent - check your email."
                                }
                            },
                        ) {
                            Text(
                                "Forgot password?", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextSoft,
                            )
                        }
                    }
                    TextButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentWidth(Alignment.CenterHorizontally),
                        onClick = { creating = !creating; error = null }) {
                        Text(
                            if (creating) "I already have an account" else "New here? Create your space",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextSoft,
                        )
                    }

                }

                AuthMode.Otp -> {
                    AppTextField(
                        email,
                        { email = it },
                        "Email",
                        singleLine = true,
                        enabled = !otpSent,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                    if (otpSent) {
                        AppTextField(
                            code,
                            { if (it.length <= 6) code = it.filter(Char::isDigit) },
                            "6-digit code",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    AuthWhiteButton(
                        if (busy) "One moment..." else if (otpSent) "Verify code" else "Email me a code",
                        enabled = !busy && (if (otpSent) code.length == 6 else email.isNotBlank()),
                        leading = "@",
                    ) {
                        run {
                            if (!otpSent) {
                                Session.otpRequest(email.trim())
                                otpSent = true
                                info = "Code sent to ${email.trim()} - check your email."
                            } else {
                                Session.otpVerify(email.trim(), code)
                            }
                        }
                    }
                    TextButton(onClick = {
                        mode = AuthMode.Password; otpSent = false; code = ""; error = null; info =
                        null
                    }) {
                        Text("Use a password instead", color = TextSoft)
                    }
                }
            }

            info?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Periwinkle) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    return

}

@Composable
private fun AuthInfoCard(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
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
            Text("○", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
            color = TextSoft,
        )
    }
}

@Composable
private fun AuthWhiteButton(
    label: String,
    enabled: Boolean = true,
    leading: String? = null,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.32f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (it == "G") Color(0xFF4285F4) else Ink,
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Ink,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AuthDivider(label: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.16f))
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 1.6.sp,
            textAlign = TextAlign.Center,
        )
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.16f))
        )
    }
}


@Preview(
    showBackground = true,
    showSystemUi = true,
    device = "id:pixel_7"
)
@Composable
fun AuthScreenPreview() {
    CereBroTheme {
        AuthScreen()
    }
}