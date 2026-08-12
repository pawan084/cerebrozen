package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.Danger
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Ok
import kotlinx.coroutines.launch

/** How to reach the trusted contact. Wire values of the cross-stack contract
 * (`TrustedContactIn.method` — email | sms | phone); never translate them. */
private val METHODS = listOf(
    PickOption("email", R.string.trusted_method_email),
    PickOption("sms", R.string.trusted_method_sms),
    PickOption("phone", R.string.trusted_method_phone),
)

/** Where the value goes: an address for email, a number otherwise. */
internal fun trustedKeyboard(method: String): KeyboardType =
    if (method == "email") KeyboardType.Email else KeyboardType.Phone

/** A contact is savable once there is something to send to. Name is optional —
 * the escalation email addresses the person generically if it is blank, and
 * demanding a name before someone can name a lifeline is friction in the wrong
 * place. Pure. */
internal fun trustedContactReady(value: String): Boolean = value.trim().isNotBlank()

/** Client-side mirror of the backend's method-aware validation: an email needs
 * a user@domain.tld shape; sms/phone need only phone punctuation and at least
 * seven digits. Catches the obvious slip before a round-trip and lets the form
 * say which FIELD is wrong instead of surfacing a whole-form 422 afterwards.
 * The backend stays the authority. Pure. */
internal fun trustedValueLooksValid(method: String, value: String): Boolean {
    val v = value.trim()
    if (v.isBlank()) return false
    return if (method == "email") {
        Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(v)
    } else {
        v.all { it.isDigit() || it in "+ ()-" } && v.count { it.isDigit() } >= 7
    }
}

/** Which inline error fits the method — email-shaped or number-shaped. Pure. */
@androidx.annotation.StringRes
internal fun trustedValueErrorRes(method: String): Int =
    if (method == "email") R.string.trusted_value_invalid_email else R.string.trusted_value_invalid_number

/** The way to actually reach the saved person, as a URI the system resolves:
 * dialer for phone, SMS composer for sms, mail composer for email. Pure. */
internal fun trustedReachUri(method: String, value: String): String = when (method) {
    "phone" -> "tel:$value"
    "sms" -> "smsto:$value"
    else -> "mailto:$value"
}

/** The action label for the saved-contact card, matched to the method. Pure. */
@androidx.annotation.StringRes
internal fun trustedReachLabelRes(method: String): Int = when (method) {
    "phone" -> R.string.trusted_reach_phone
    "sms" -> R.string.trusted_reach_sms
    else -> R.string.trusted_reach_email
}

/**
 * The person CereBro may reach out to if a crisis is detected.
 *
 * This screen did not exist on Android. The backend has had full CRUD since the
 * beginning, iOS and the browser client both have an editor, and even the
 * Android API layer already had `setTrustedContact` — it simply had no caller.
 * So the Crisis screen told the user "add one in Settings" and Settings had no
 * such setting: an instruction, on a crisis surface, to a place that was not
 * there.
 *
 * `notify_consent` is a switch here, defaulting OFF. `setTrustedContact` used to
 * hardcode it `true`, which would have meant that naming someone silently agreed
 * to messaging them at the worst moment of your life. Telling a third party is
 * the user's decision, taken once, in words.
 */
@Composable
fun TrustedContactScreen(onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf("email") }
    var value by rememberSaveable { mutableStateOf("") }
    var consent by rememberSaveable { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var hasSavedContact by remember { mutableStateOf(false) }
    var savedName by remember { mutableStateOf("") }
    var savedMethod by remember { mutableStateOf("email") }
    var savedValue by remember { mutableStateOf("") }
    var savedConsent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val savedMsg = stringResource(R.string.trusted_saved)
    val removedMsg = stringResource(R.string.trusted_removed)
    val failedMsg = stringResource(R.string.trusted_failed)

    LaunchedEffect(Unit) {
        runCatching { Api.trustedContact() }.onSuccess { tc ->
            if (tc != null) {
                hasSavedContact = true
                name = tc.optString("name")
                method = tc.optString("method").ifBlank { "email" }
                value = tc.optString("value")
                consent = tc.optBoolean("notify_consent")
                savedName = name
                savedMethod = method
                savedValue = value
                savedConsent = consent
            }
        }
        loaded = true
    }

    PremiumSubPage(stringResource(R.string.trusted_eyebrow), stringResource(R.string.trusted_title), onBack) {
        Text(stringResource(R.string.trusted_intro),
            style = MaterialTheme.typography.bodySmall, color = TextMuted)

        if (hasSavedContact && savedValue.isNotBlank()) {
            SectionCard(quiet = true) {
                Text(stringResource(R.string.trusted_current),
                    style = MaterialTheme.typography.labelSmall, color = Ok)
                Text(savedName.ifBlank { savedValue }, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                if (savedName.isNotBlank()) Text(savedValue, style = MaterialTheme.typography.bodySmall, color = TextSoft)
                Text(
                    stringResource(if (savedConsent) R.string.trusted_consent_on else R.string.trusted_consent_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (savedConsent) Ok else TextMuted,
                )
                // Reaching out yourself beats waiting for an escalation: the
                // saved card carries the direct action — dialer, SMS or mail
                // composer, matched to the saved method. Composer only; nothing
                // is sent or dialed without the user finishing it there.
                TextButton(onClick = {
                    runCatching {
                        val uri = android.net.Uri.parse(trustedReachUri(savedMethod, savedValue))
                        val action = if (savedMethod == "phone") android.content.Intent.ACTION_DIAL
                        else android.content.Intent.ACTION_SENDTO
                        context.startActivity(android.content.Intent(action, uri))
                    }
                }) {
                    Text(stringResource(trustedReachLabelRes(savedMethod)), color = Periwinkle)
                }
            }
        }

        SectionCard(quiet = true) {
            Text(stringResource(R.string.trusted_details_title),
                style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text(stringResource(R.string.trusted_name_label),
                style = MaterialTheme.typography.labelLarge, color = TextSoft)
            AppTextField(name, { name = it; status = null }, label = "", singleLine = true,
                placeholderText = stringResource(R.string.trusted_name_label))
            Text(stringResource(R.string.trusted_method_label),
                style = MaterialTheme.typography.labelLarge, color = TextSoft)
            ChipWrapOptions(METHODS, method) { method = it; status = null }
            Text(stringResource(R.string.trusted_value_label),
                style = MaterialTheme.typography.labelLarge, color = TextSoft)
            AppTextField(
                value, { value = it; status = null }, label = "",
                placeholderText = stringResource(R.string.trusted_value_label),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = trustedKeyboard(method)),
            )
            // Field-level guidance while typing — names the field and the shape
            // it wants, instead of a whole-form failure after the round-trip.
            if (value.isNotBlank() && !trustedValueLooksValid(method, value)) {
                Text(
                    stringResource(trustedValueErrorRes(method)),
                    style = MaterialTheme.typography.labelSmall, color = Danger,
                )
            }
        }

        // The consent gate. Off unless the user turns it on; the backend only
        // escalates when this is true (services/escalation.py::on_crisis).
        SectionCard(quiet = true) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.fillMaxWidth(0.75f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.trusted_consent_title),
                        style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(stringResource(R.string.trusted_consent_hint),
                        style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                AppSwitch(checked = consent, onCheckedChange = { consent = it }, label = stringResource(R.string.trusted_consent_title))
            }
        }

        PrimaryButton(
            text = if (busy) stringResource(R.string.common_one_moment) else stringResource(R.string.trusted_save),
            enabled = loaded && !busy && trustedValueLooksValid(method, value),
            modifier = Modifier.fillMaxWidth(),
        ) {
            busy = true; status = null
            scope.launch {
                runCatching { Api.setTrustedContact(name.trim(), method, value.trim(), consent) }
                    .onSuccess {
                        hasSavedContact = true
                        savedName = name.trim()
                        savedMethod = method
                        savedValue = value.trim()
                        savedConsent = consent
                        status = savedMsg
                    }
                    .onFailure { status = it.userMessage(failedMsg) }
                busy = false
            }
        }

        status?.let {
            SectionCard(quiet = true) {
                Text(it, style = MaterialTheme.typography.titleSmall,
                    color = if (it == savedMsg) Ok else Periwinkle)
            }
        }

        if (loaded && hasSavedContact) {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true; status = null
                    scope.launch {
                        runCatching { Api.deleteTrustedContact() }
                            .onSuccess {
                                name = ""; value = ""; consent = false
                                hasSavedContact = false
                                savedName = ""; savedValue = ""; savedConsent = false
                                status = removedMsg
                            }
                            .onFailure { status = it.userMessage(failedMsg) }
                        busy = false
                    }
                }, modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.trusted_remove), color = Danger) }
        }
    }
}
