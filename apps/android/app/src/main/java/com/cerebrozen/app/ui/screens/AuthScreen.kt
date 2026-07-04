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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/** Sign in / create account — the same email flow as iOS and the web app. */
@Composable
fun AuthScreen() {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("PRIVATE BY DESIGN", style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        Text(
            if (creating) "Create your space" else "Welcome back",
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
        )
        Text(
            "Your quiet space for daily mental fitness — synced with iOS and the web.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )

        if (creating) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        if (creating) Session.signUp(email.trim(), password, name.trim())
                        else Session.signIn(email.trim(), password)
                    } catch (e: Exception) {
                        error = e.message ?: "Something went wrong."
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "One moment…" else if (creating) "Create my account" else "Sign in")
        }

        TextButton(onClick = { creating = !creating; error = null }) {
            Text(
                if (creating) "I already have an account" else "New here? Create your space",
                color = TextMuted,
            )
        }
    }
}
