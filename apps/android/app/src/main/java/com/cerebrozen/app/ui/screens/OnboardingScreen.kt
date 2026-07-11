package com.cerebrozen.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ArrowCircleRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.cerebrozen.app.ui.theme.Iris
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Warm
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONObject

private enum class OStep { Welcome, Age, Disclosure, Language, State, Reset, Consent, Plan, Notify, SignUp }

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
private val NOTIFY = listOf("Morning 9 AM", "Evening 7 PM", "Private previews", "No reminders")
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
    if (signIn) { AuthScreen(onBack = { signIn = false }); return }

    var step by remember { mutableStateOf(OStep.Welcome) }
    val order = OStep.entries
    fun next() { val i = order.indexOf(step); if (i < order.lastIndex) step = order[i + 1] }
    fun back() { val i = order.indexOf(step); if (i > 0) step = order[i - 1] }

    // First-party funnel counts (anonymous install id, opt-out; mirrors iOS).
    LaunchedEffect(step) { Analytics.track("onboarding_step", funnelStepName(step.name)) }

    var language by remember { mutableStateOf("English") }
    var state by remember { mutableStateOf<StateOption?>(null) }
    var notify by remember { mutableStateOf("Evening 7 PM") }
    // Private by default: NOTHING pre-ticked — consent must be an action
    // (EDPB/ICO; matches iOS ConsentScreen + web onboarding).
    val consent = remember {
        mutableStateMapOf(
            "mood_history" to true, "ai_memory" to true, "journal_memory" to false,
            "sleep_history" to false, "voice_storage" to false, "model_training" to false,
        )
    }

    when (step) {
        OStep.Welcome -> Welcome(onStart = { next() }, onSignIn = { signIn = true })

        OStep.Age -> Funnel(
            "For adults only", "A quick check",
            "CereBro is built for adults. A quick check keeps the experience safe and appropriate.",
            "Continue", onBack = { back() }, onPrimary = { next() },
        ) {
            ReferenceCard(borderColor = Warm.copy(alpha = 0.5f), fill = Color(0xFF493453)) {
                Text("Wellness support, not emergency care.",
                    style = MaterialTheme.typography.titleMedium, color = Warm)
                Text("If you are in immediate danger, call emergency services now.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            ReferenceCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF5A547F)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("Confirmed: I am 18 or older", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text("Thank you", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC9C5DA))
                    }
                }
            }
        }

        OStep.Disclosure -> Funnel(
            "Honesty first", "What CereBro is — and isn't",
            "Here's exactly what your AI companion can and can't do for you.", "Continue", onBack = { back() }, onPrimary = { next() },
        ) {
            // Two-up "can help / can't do" tiles (fork look), on our glass tokens.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DisclosureTile(
                    "Can help", Cyan,
                    "Listen, reflect, guide tools, suggest a plan.",
                    Modifier.weight(1f),
                )
                DisclosureTile(
                    "Can't do", TextSoft,
                    "Diagnose, prescribe, replace therapy, or handle emergencies.",
                    Modifier.weight(1f),
                )
            }
        }

        OStep.Language -> Funnel(
            "Speak your language", "Language", "Talk and reflect in the language you think in. Mix more than one if that's you.",
            "Continue", onBack = { back() }, onPrimary = { next() },
        ) {
            ChipWrap(LANGUAGES, language) { language = it }
        }

        OStep.State -> Funnel(
            "One tap is enough", "What feels most true right now?",
            "No questionnaire — just pick the one that fits today. CereBro shapes your first reset and plan around it.",
            "Continue", primaryEnabled = state != null, onBack = { back() }, onPrimary = { next() },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                STATE_OPTIONS.forEach { option ->
                    StateOptionRow(option.label, state?.label == option.label) { state = option }
                }
            }
        }

        OStep.Reset -> ResetStep(onDone = { next() }, onBack = { back() })

        OStep.Plan -> Funnel(
            "Made around you", "First Plan", "",
            "Keep going", onBack = { back() }, onPrimary = { next() },
        ) {
            PlanHero(planTitleFor(state?.goal))
            PlanActionCard(Icons.Outlined.Air, "Breathing reset", "3 min · recommended now")
            PlanActionCard(Icons.AutoMirrored.Outlined.MenuBook, "Night journal", "5 min reflection")
        }

        OStep.Consent -> Funnel(
            "Privacy choices", "What CereBro remembers",
            "Private by default — CereBro remembers nothing you don't switch on. Change any of this later in Settings.",
            "Continue", onBack = { back() }, onPrimary = { next() },
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF302D54))
                    .border(1.dp, Color(0xFF514C73), RoundedCornerShape(18.dp)),
            ) {
                listOf(
                    Triple("mood_history", "Mood history", "Used for insights"),
                    Triple("ai_memory", "AI memory", "Goals and preferences"),
                    Triple("voice_storage", "Voice storage", "Off by default"),
                ).forEachIndexed { index, (key, label, hint) ->
                    Row(
                        Modifier.fillMaxWidth().height(75.dp).padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(hint, style = MaterialTheme.typography.bodySmall, color = Color(0xFFCBC7D8))
                        }
                        AppSwitch(checked = consent[key] == true, onCheckedChange = { consent[key] = it })
                    }
                    if (index < 2) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF464166)))
                }
            }
        }

        OStep.Notify -> Funnel(
            "Gentle reminders", "Notifications",
            "You've had your first win — want a quiet nudge to come back tomorrow? Never noisy, always easy to turn off.",
            "Enter CereBro", onBack = { back() }, onPrimary = { next() },
        ) {
            ChipWrap(NOTIFY, notify) { notify = it }
        }

        OStep.SignUp -> AuthScreen(
            initialCreating = true,
            onBack = { back() },
            onAccountCreated = {
                Analytics.track("onboarding_done")
                runCatching { Api.attest() }
                runCatching { Api.updateConsent(JSONObject().apply { consent.forEach { (k, v) -> put(k, v) } }) }
                val selectedState = state
                if (selectedState != null) {
                    runCatching {
                        Api.updateProfile(
                            JSONObject()
                                .put("goals", org.json.JSONArray().put(selectedState.goal))
                                .put("motivations", org.json.JSONArray().put(selectedState.motivation)),
                        )
                    }
                    runCatching { Api.checkIn(selectedState.mood, "From onboarding", "sparkles", 3) }
                }
            },
        )
    }
}

@Composable
private fun Welcome(onStart: () -> Unit, onSignIn: () -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF3B3474), Color(0xFF12102F))))
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        val compact = maxHeight < 720.dp
        WelcomeOrb(
            Modifier.align(Alignment.TopCenter).padding(top = if (compact) 92.dp else 146.dp),
            if (compact) 170.dp else 190.dp,
        )

        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 42.dp)
                .offset(y = if (compact) 40.dp else 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Welcome to\nCereBro",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 40.sp, lineHeight = 41.sp, letterSpacing = (-0.6).sp,
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(23.dp))
            Text(
                "Your quiet space for daily mental fitness,\nbetter sleep, and calmer focus.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.5.sp, lineHeight = 24.sp),
                color = Color(0xFFD8D5E5),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(15.dp))
            Text(
                "Private by design — nothing is ever shared.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = Color(0xFFBDB8D0),
                textAlign = TextAlign.Center,
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFFFCFBFF)).clickable(onClick = onStart),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Air, null, tint = Color(0xFF211C50), modifier = Modifier.size(19.dp))
                Spacer(Modifier.size(12.dp))
                Text("Try a 2-minute reset", style = MaterialTheme.typography.titleMedium, color = Color(0xFF211C50))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSignIn) {
                Text("I already have an account", style = MaterialTheme.typography.titleSmall, color = Color(0xFFE4E1EC))
            }
        }
    }
}

@Composable
private fun WelcomeOrb(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension * 0.38f
        drawCircle(
            Brush.radialGradient(
                0.55f to Color(0x334F46B9), 0.82f to Color(0x224D45A7), 1f to Color.Transparent,
                center = center, radius = this.size.minDimension / 2f,
            ),
            this.size.minDimension / 2f,
            center,
        )
        drawCircle(Color(0x183F3889), radius * 1.27f, center)
        drawCircle(
            Brush.radialGradient(
                0f to Color.White, 0.22f to Color(0xFFF4F1FF), 1f to Color(0xFFC9C3FF),
                center = Offset(center.x - radius * 0.2f, center.y - radius * 0.27f),
                radius = radius * 1.55f,
            ),
            radius,
            center,
        )
    }
}

@Composable
private fun ResetStep(onDone: () -> Unit, onBack: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = 0.94f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "scale",
    )
    var breatheIn by remember { mutableStateOf(true) }
    var count by remember { mutableStateOf(4) }
    LaunchedEffect(Unit) {
        while (true) {
            count = 4
            repeat(4) {
                delay(1_000)
                count = if (count == 1) 4 else count - 1
            }
            breatheIn = !breatheIn
        }
    }
    Funnel(
        "Your first reset", "Let's steady your body",
        "Two minutes of guided breathing — follow the orb for a few cycles, or skip ahead if now isn't the moment.",
        "I feel steadier", onBack = onBack, onPrimary = onDone,
        titleCentered = true,
        secondary = {
            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF302D50)).clickable(onClick = onDone),
                contentAlignment = Alignment.Center,
            ) {
                Text("Skip for now", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        },
    ) {
        Text(
            if (breatheIn) "Breathe in" else "Breathe out",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 31.sp),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
            ResetBreathingOrb(count = count, scale = scale)
        }
        Text("Follow the orb", style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFBDB9D0), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ResetBreathingOrb(count: Int, scale: Float) {
    Box(Modifier.size(230.dp).scale(scale), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val core = size.minDimension * 0.335f
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0x442FCED8), 0.58f to Color(0x222FCED8), 1f to Color.Transparent,
                    center = center, radius = size.minDimension / 2f,
                ),
                size.minDimension / 2f,
                center,
            )
            drawCircle(Color(0x225EE5EA), core * 1.48f, center)
            drawCircle(Color(0x335EE5EA), core * 1.16f, center)
            drawCircle(
                Brush.linearGradient(
                    listOf(Color(0xFFE5FCFF), Color(0xFF71E5EA), Color(0xFF43CAD5)),
                    start = Offset(center.x - core, center.y - core),
                    end = Offset(center.x + core, center.y + core),
                ),
                core,
                center,
            )
        }
        Text(
            count.toString(),
            style = MaterialTheme.typography.displaySmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Default, fontSize = 47.sp,
            ),
            color = Color(0xFF11142D),
        )
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

@Composable
private fun PlanHero(title: String) {
    Column(
        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF657895), Color(0xFF1E293C))))
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.18f))
                .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text("TODAY", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp), color = Color.White)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp), color = Color.White)
            Text("A light plan: one thing now, one tonight, one tomorrow.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = Color.White)
        }
    }
}

@Composable
private fun PlanActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF2C294F)).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF7163BA), Color(0xFF40386F))))
                .border(1.dp, Color(0xFF8075C2), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFFC7C3D5))
        }
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
    titleCentered: Boolean = false,
    secondary: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val progress = when (eyebrow.lowercase()) {
        "for adults only" -> 0.12f
        "honesty first" -> 0.25f
        "speak your language" -> 0.38f
        "one tap is enough" -> 0.50f
        "your first reset" -> 0.62f
        "made around you" -> 0.88f
        "privacy choices" -> 0.75f
        "gentle reminders" -> 0.91f
        else -> 1f
    }
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF393270), Color(0xFF11102E))))
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 145.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, letterSpacing = 1.8.sp),
                color = Color(0xFFAAA3D0),
            )
            Text(
                title,
                modifier = if (titleCentered) Modifier.fillMaxWidth() else Modifier,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp, lineHeight = 39.sp),
                color = Color.White,
                textAlign = if (titleCentered) TextAlign.Center else TextAlign.Start,
            )
            if (sub.isNotBlank()) Text(
                sub,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.5.sp, lineHeight = 24.sp),
                color = Color(0xFFD0CCDE),
            )
            Spacer(Modifier.height(6.dp))
            content()
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 23.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF484361))) {
                Box(Modifier.fillMaxWidth(progress).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(29.dp))
                    .background(if (primaryEnabled) Color(0xFFFCFBFF) else Color(0xFF9998A7))
                    .clickable(enabled = primaryEnabled, onClick = onPrimary),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (primaryLabel == "Keep going") Icons.Outlined.ArrowCircleRight else Icons.Outlined.CheckCircle,
                    null, tint = Color(0xFF211C50), modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(11.dp))
                Text(primaryLabel, style = MaterialTheme.typography.titleMedium, color = Color(0xFF211C50))
            }
            if (secondary != null) {
                Spacer(Modifier.height(12.dp))
                secondary.invoke()
            }
        }
    }
}

@Composable
private fun ReferenceCard(
    borderColor: Color = Color.Transparent,
    fill: Color = Color(0xFF39355F),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(fill)
            .border(1.dp, borderColor, RoundedCornerShape(17.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        content = content,
    )
}

@Composable
private fun StateOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(55.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFF4A456F) else Color(0xFF302C56))
            .border(1.dp, Color(0xFF504B74), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Color.White)
        Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFF9993B4), modifier = Modifier.size(22.dp))
    }
}

/** One side of the two-up disclosure — a glass tile with an accent heading. */
@Composable
private fun DisclosureTile(
    title: String,
    accent: androidx.compose.ui.graphics.Color,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.height(129.dp).clip(RoundedCornerShape(17.dp)).background(Color(0xFF39355F))
            .border(1.dp, Color(0xFF575178), RoundedCornerShape(17.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 19.sp), color = Color.White)
        Text(body, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp), color = Color(0xFFD0CCDE))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun ChipWrap(options: List<String>, selected: String?, onPick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        options.forEach { opt ->
            val isSelected = selected == opt
            Box(
                Modifier.height(47.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) Color.White else Color(0xFF3B3766))
                    .clickable { onPick(opt) }.padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    opt,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) Color(0xFF211C50) else Color.White,
                )
            }
        }
    }
}
