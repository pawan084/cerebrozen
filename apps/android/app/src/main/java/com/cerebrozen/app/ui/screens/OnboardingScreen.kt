package com.cerebrozen.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.BrandMark
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class OStep { Welcome, Age, Disclosure, Language, State, Reset, Consent, Notify, SignUp }

private val LANGUAGES = listOf("English", "Hindi", "Hinglish", "Punjabi", "Tamil")
private val FEELINGS = listOf("Calm", "Good", "Anxious", "Low", "Tired", "Stressed")
private val NOTIFY = listOf("Morning", "Midday", "Evening", "Off")
private val CONSENT_ROWS = listOf(
    "mood_history" to "Remember my mood history",
    "ai_memory" to "Let the AI remember context",
    "journal_memory" to "Remember journal entries",
    "sleep_history" to "Keep my sleep history",
    "voice_storage" to "Store voice clips",
    "model_training" to "Help improve the models",
)

/**
 * Value-first onboarding funnel — the adult gate, honesty disclosure, a first
 * calming reset, then account + consent. New users flow through here; returning
 * users tap through to the existing [AuthScreen]. Consent/notification prefs are
 * collected locally and applied right after sign-up so the session flips once.
 */
@Composable
fun Onboarding() {
    var signIn by remember { mutableStateOf(false) }
    if (signIn) { AuthScreen(); return }

    var step by remember { mutableStateOf(OStep.Welcome) }
    val order = OStep.entries
    fun next() { val i = order.indexOf(step); if (i < order.lastIndex) step = order[i + 1] }
    fun back() { val i = order.indexOf(step); if (i > 0) step = order[i - 1] }

    var language by remember { mutableStateOf("English") }
    var feeling by remember { mutableStateOf<String?>(null) }
    var notify by remember { mutableStateOf("Evening") }
    val consent = remember {
        mutableStateMapOf(
            "mood_history" to true, "ai_memory" to true, "journal_memory" to true,
            "sleep_history" to true, "voice_storage" to false, "model_training" to false,
        )
    }

    when (step) {
        OStep.Welcome -> Welcome(onStart = { next() }, onSignIn = { signIn = true })

        OStep.Age -> Funnel(
            "For adults only", "A quick check",
            "CereBro is built for adults. It's wellness support — not a medical or crisis service.",
            "I'm 18 or older", onBack = { back() }, onPrimary = { next() },
        ) {}

        OStep.Disclosure -> Funnel(
            "Honesty first", "What CereBro is — and isn't",
            "", "I understand", onBack = { back() }, onPrimary = { next() },
        ) {
            SectionCard {
                Text("Can help", style = MaterialTheme.typography.titleMedium, color = Cyan)
                Text("A calm space to reflect, breathe, sleep better and talk things through.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            SectionCard {
                Text("Can't do", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text("It isn't a therapist or crisis line. In an emergency, contact local services.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
        }

        OStep.Language -> Funnel(
            "Speak your language", "Choose your language", "",
            "Continue", onBack = { back() }, onPrimary = { next() },
        ) {
            ChipWrap(LANGUAGES, language) { language = it }
        }

        OStep.State -> Funnel(
            "One tap is enough", "What feels most true right now?", "",
            "Continue", primaryEnabled = feeling != null, onBack = { back() }, onPrimary = { next() },
        ) {
            ChipWrap(FEELINGS, feeling) { feeling = it }
        }

        OStep.Reset -> ResetStep(onDone = { next() }, onBack = { back() })

        OStep.Consent -> Funnel(
            "Privacy choices", "What CereBro remembers",
            "Everything's opt-in and changeable later in Settings.",
            "Looks good", onBack = { back() }, onPrimary = { next() },
        ) {
            SectionCard {
                CONSENT_ROWS.forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                        Switch(
                            checked = consent[key] == true,
                            onCheckedChange = { consent[key] = it },
                        )
                    }
                }
            }
        }

        OStep.Notify -> Funnel(
            "Gentle reminders", "When should we nudge you?", "",
            "Continue", onBack = { back() }, onPrimary = { next() },
        ) {
            ChipWrap(NOTIFY, notify) { notify = it }
        }

        OStep.SignUp -> SignUpStep(
            feeling = feeling,
            consent = { JSONObject().apply { consent.forEach { (k, v) -> put(k, v) } } },
            onBack = { back() },
        )
    }
}

@Composable
private fun Welcome(onStart: () -> Unit, onSignIn: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(size = 120.dp)
        Spacer(Modifier.height(28.dp))
        Text("Welcome to\nCereBro", style = MaterialTheme.typography.displaySmall,
            color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Your quiet space for daily mental fitness, better sleep, and calmer focus.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("Private by design — nothing is ever shared.",
            style = MaterialTheme.typography.labelSmall, color = TextMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        PrimaryButton(text = "Try a 2-minute reset", modifier = Modifier.fillMaxWidth()) { onStart() }
        TextButton(onClick = onSignIn) { Text("I already have an account", color = TextMuted) }
    }
}

@Composable
private fun ResetStep(onDone: () -> Unit, onBack: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = 0.78f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(3400), RepeatMode.Reverse), label = "scale",
    )
    Funnel(
        "Your first reset", "Let's steady your body",
        "Follow the orb for a few slow breaths — or skip ahead if now isn't the moment.",
        "I feel steadier", onBack = onBack, onPrimary = onDone,
        secondary = { TextButton(onClick = onDone) { Text("Skip for now", color = TextMuted) } },
    ) {
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(150.dp).scale(scale).background(
                Brush.radialGradient(listOf(androidx.compose.ui.graphics.Color.White, Cyan, Periwinkle)),
                CircleShape,
            ))
        }
        Text("Breathe with the orb", style = MaterialTheme.typography.titleMedium,
            color = TextSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SignUpStep(feeling: String?, consent: () -> JSONObject, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Funnel(
        "Save your space", "Create your account",
        "One account across iOS, Android and the web.",
        if (busy) "One moment…" else "Create my account",
        primaryEnabled = !busy && email.isNotBlank() && password.length >= 8,
        onBack = onBack,
        onPrimary = {
            busy = true; error = null
            scope.launch {
                try {
                    Session.signUp(email.trim(), password, name.trim())
                    // Best-effort personalization — never blocks entering the app.
                    runCatching { Api.attest() }
                    runCatching { Api.updateConsent(consent()) }
                    if (feeling != null) runCatching {
                        Api.checkIn(feeling, "From onboarding", "sparkles", 3)
                    }
                } catch (e: Exception) {
                    error = e.message ?: "Couldn't create your account."; busy = false
                }
            }
        },
    ) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password (8+ characters)") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

// ── Shared bits ──────────────────────────────────────────────────────────
@Composable
private fun Funnel(
    eyebrow: String,
    title: String,
    sub: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onBack: (() -> Unit)?,
    primaryEnabled: Boolean = true,
    secondary: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (onBack != null) TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("‹ Back", color = TextMuted)
        }
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        Text(title, style = MaterialTheme.typography.displaySmall, color = TextPrimary)
        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        content()
        Spacer(Modifier.height(4.dp))
        PrimaryButton(text = primaryLabel, enabled = primaryEnabled, modifier = Modifier.fillMaxWidth()) { onPrimary() }
        secondary?.invoke()
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipWrap(options: List<String>, selected: String?, onPick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { opt ->
            PickChip(selected = selected == opt, label = opt) { onPick(opt) }
        }
    }
}

@Composable
private fun Orb(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).background(
            Brush.radialGradient(listOf(androidx.compose.ui.graphics.Color.White, Periwinkle, androidx.compose.ui.graphics.Color(0xFF5B52C9))),
            CircleShape,
        ),
    )
}
