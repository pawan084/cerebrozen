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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.net.Analytics
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.net.funnelStepName
import com.cerebrozen.app.ui.BrandMark
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Danger
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Warm
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class OStep { Welcome, Age, Disclosure, Language, State, Reset, Plan, Consent, Notify, SignUp }

/** One feeling tap is the whole "assessment" — each maps into the shared
 * motivation/goal taxonomy (cross-stack: iOS StateCheckScreen.states ⇄ web
 * lib/onboarding.FEELINGS) so plans and conversation starters ground on it.
 * `mood` keys the first check-in into the shared mood taxonomy. */
internal data class StateOption(val label: String, val motivation: String, val goal: String, val mood: String)

internal val STATE_OPTIONS = listOf(
    StateOption("Stressed and tense", "Calm", "Reduce stress", "Anxious"),
    StateOption("Can't switch off at night", "Calm", "Sleep better", "Tired"),
    StateOption("Overthinking everything", "Focus", "Stop overthinking", "Anxious"),
    StateOption("Doubting myself", "Confidence", "Build confidence", "Low"),
    StateOption("Feeling distant from people", "Connection", "Feel less alone", "Low"),
    StateOption("Can't stay consistent", "Discipline", "Strengthen willpower", "Okay"),
)

/** Headline the first plan around the chosen goal (mirrors iOS/web planTitle). */
internal fun planTitleFor(goal: String?): String = when (goal) {
    "Sleep better" -> "Sleep deeper"
    "Reduce stress" -> "Ease today's stress"
    "Stop overthinking" -> "Quiet the noise"
    "Build confidence" -> "Steady confidence"
    "Feel less alone" -> "Feel more connected"
    "Strengthen willpower" -> "Small promises, kept"
    else -> "A calmer day"
}

private val LANGUAGES = listOf("English", "Hindi", "Hinglish", "Punjabi", "Tamil")
private val NOTIFY = listOf("Morning", "Midday", "Evening", "Off")
// Consent rows render from the localized notice (DPDP s.5(3) — ConsentNotice.kt).

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

    // First-party funnel counts (anonymous install id, opt-out; mirrors iOS).
    LaunchedEffect(step) { Analytics.track("onboarding_step", funnelStepName(step.name)) }

    var language by remember { mutableStateOf("English") }
    var state by remember { mutableStateOf<StateOption?>(null) }
    var notify by remember { mutableStateOf("Evening") }
    // Private by default: NOTHING pre-ticked — consent must be an action
    // (EDPB/ICO; matches iOS ConsentScreen + web onboarding).
    val consent = remember {
        mutableStateMapOf(
            "mood_history" to false, "ai_memory" to false, "journal_memory" to false,
            "sleep_history" to false, "voice_storage" to false, "model_training" to false,
        )
    }

    when (step) {
        OStep.Welcome -> Welcome(onStart = { next() }, onSignIn = { signIn = true })

        OStep.Age -> Funnel(
            "For adults only", "A quick check",
            "CereBro is built for adults. It's wellness support — not a medical or crisis service.",
            "I'm 18 or older", onBack = { back() }, onPrimary = { next() },
        ) {
            // Set expectations up front: this is not emergency care.
            SectionCard {
                Text("Wellness support, not emergency care.",
                    style = MaterialTheme.typography.titleMedium, color = Warm)
                Text("If you are in immediate danger, call your local emergency services now.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
        }

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
            "One tap is enough", "What feels most true right now?",
            "No questionnaire — just pick the one that fits today. CereBro shapes your first reset and plan around it.",
            "Continue", primaryEnabled = state != null, onBack = { back() }, onPrimary = { next() },
        ) {
            ChipWrap(STATE_OPTIONS.map { it.label }, state?.label) { picked ->
                state = STATE_OPTIONS.first { it.label == picked }
            }
        }

        OStep.Reset -> ResetStep(onDone = { next() }, onBack = { back() })

        OStep.Plan -> Funnel(
            "Your first plan", planTitleFor(state?.goal),
            "Built from your one tap — it adapts every time you check in.",
            "Looks right", onBack = { back() }, onPrimary = { next() },
        ) {
            SectionCard {
                listOf(
                    "🌬️" to ("Breathing reset" to "3 min · recommended now"),
                    "📖" to ("Night journal" to "5 min reflection"),
                    "🔔" to ("Reminder timing" to "Evening private nudge"),
                ).forEach { (emoji, step) ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(emoji, style = MaterialTheme.typography.headlineSmall)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(step.first, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                            Text(step.second, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }
            }
        }

        OStep.Consent -> {
            // DPDP s.5(3): the notice itself is readable in English or an
            // Eighth-Schedule language, seeded from the language step's choice.
            var noticeLang by remember(language) { mutableStateOf(defaultNoticeCode(language)) }
            val notice = noticeFor(noticeLang)
            Funnel(
                "Privacy choices", notice.title,
                notice.caption,
                "Looks good", onBack = { back() }, onPrimary = { next() },
            ) {
                ChipWrap(NOTICE_CODES.map { noticeFor(it).nativeName }, notice.nativeName) { picked ->
                    noticeLang = NOTICE_CODES.first { noticeFor(it).nativeName == picked }
                }
                val layoutDir = if (noticeLang == "ur") LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                SectionCard {
                    CONSENT_KEY_ORDER.forEach { key ->
                        val cat = notice.categories.getValue(key)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(cat.label, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                                Text(cat.hint, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                            AppSwitch(
                                checked = consent[key] == true,
                                onCheckedChange = { consent[key] = it },
                            )
                        }
                    }
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
            state = state,
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
private fun SignUpStep(state: StateOption?, consent: () -> JSONObject, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val canSubmit = !busy && email.isNotBlank() && password.length >= 8

    fun submit() {
        if (!canSubmit) return
        focus.clearFocus()
        busy = true; error = null
        scope.launch {
            try {
                Session.signUp(email.trim(), password, name.trim())
                Analytics.track("onboarding_done")
                // Best-effort personalization — never blocks entering the app.
                runCatching { Api.attest() }
                runCatching { Api.updateConsent(consent()) }
                if (state != null) {
                    // The one tap grounds server personalization: plans key
                    // off goals, conversation starters off motivations.
                    runCatching {
                        Api.updateProfile(
                            JSONObject()
                                .put("goals", org.json.JSONArray().put(state.goal))
                                .put("motivations", org.json.JSONArray().put(state.motivation)),
                        )
                    }
                    runCatching { Api.checkIn(state.mood, "From onboarding", "sparkles", 3) }
                }
            } catch (e: Exception) {
                error = e.message ?: "Couldn't create your account."; busy = false
            }
        }
    }

    Funnel(
        "Save your space", "Create your account",
        "One account across iOS, Android and the web.",
        if (busy) "One moment…" else "Create my account",
        primaryEnabled = canSubmit,
        onBack = onBack,
        onPrimary = { submit() },
    ) {
        AppTextField(name, { name = it }, "Name", singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }))
        AppTextField(email, { email = it }, "Email", singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }))
        AppTextField(password, { password = it }, "Password (8+ characters)", singleLine = true,
            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                IconButton(onClick = { showPw = !showPw }) {
                    Icon(
                        if (showPw) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showPw) "Hide password" else "Show password",
                        tint = TextMuted,
                    )
                }
            })
        error?.let { Text(it, color = Danger) }
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
internal fun ChipWrap(options: List<String>, selected: String?, onPick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { opt ->
            PickChip(selected = selected == opt, label = opt) { onPick(opt) }
        }
    }
}
