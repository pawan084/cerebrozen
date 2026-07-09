package com.cerebro.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerebro.app.net.Analytics
import com.cerebro.app.net.Api
import com.cerebro.app.net.Session
import com.cerebro.app.net.funnelStepName
import com.cerebro.app.ui.BrandMark
import com.cerebro.app.ui.theme.Cyan
import com.cerebro.app.ui.theme.Ink
import com.cerebro.app.ui.theme.Night
import com.cerebro.app.ui.theme.NightMid
import com.cerebro.app.ui.theme.Periwinkle
import com.cerebro.app.ui.theme.TextMuted
import com.cerebro.app.ui.theme.TextPrimary
import com.cerebro.app.ui.theme.TextSoft
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

        OStep.Age -> AgeCheck(onPrimary = { next() }) /*
            "For adults only", "A quick check",
            "CereBro is built for adults. It's wellness support — not a medical or crisis service.",
            "I'm 18 or older", onBack = { back() }, onPrimary = { next() },
        ) {} */

        OStep.Disclosure -> Funnel(
            "Honesty first", "What CereBro is — and isn't",
            "Here's exactly what your AI companion can and can't do for you.", "Continue", onBack = { back() }, onPrimary = { next() }, progress = 0.25f,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OnboardingTile(
                    title = "Can help",
                    body = "Listen, reflect, guide tools, suggest a plan.",
                    modifier = Modifier.weight(1f),
                )
                OnboardingTile(
                    title = "Can't do",
                    body = "Diagnose, prescribe, replace therapy, or handle emergencies.",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        OStep.Language -> Funnel(
            "Speak your language", "Language", "Talk and reflect in the language you think in. Mix more than one if that's you.",
            "Continue", onBack = { back() }, onPrimary = { next() }, progress = 0.38f,
        ) {
            LanguageOptions(selected = language) { language = it }
        }

        OStep.State -> Funnel(
            "One tap is enough", "What feels most true right now?",
            "No questionnaire — just pick the one that fits today. CereBro shapes your first reset and plan around it.",
            "Continue", primaryEnabled = state != null, onBack = { back() }, onPrimary = { next() }, progress = 0.50f,
        ) {
            StateOptionList(selected = state?.label) { picked ->
                state = STATE_OPTIONS.first { it.label == picked }
            }
        }

        OStep.Reset -> ResetStep(onDone = { next() }, onBack = { back() })

        OStep.Plan -> FirstPlan(onPrimary = { next() }) /*
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
                        Text(emoji, style = MaterialTheme.typography.titleLarge)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(step.first, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                            Text(step.second, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }
            }
        } */

        OStep.Consent -> MemoryChoices(
            moodHistory = consent["mood_history"] == true,
            aiMemory = consent["ai_memory"] == true,
            voiceStorage = consent["voice_storage"] == true,
            onMoodHistory = { consent["mood_history"] = it },
            onAiMemory = { consent["ai_memory"] = it },
            onVoiceStorage = { consent["voice_storage"] = it },
            onPrimary = { next() },
        ) /*
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
                            Switch(
                                checked = consent[key] == true,
                                onCheckedChange = { consent[key] = it },
                            )
                        }
                    }
                }
            }
        } */

        OStep.Notify -> NotificationsChoice(
            selected = notify,
            onPick = { notify = it },
            onPrimary = { Session.completeLocalOnboarding() },
        )

        OStep.SignUp -> SignUpStep(
            state = state,
            consent = { JSONObject().apply { consent.forEach { (k, v) -> put(k, v) } } },
            onBack = { back() },
        )
    }
}

@Composable
private fun AgeCheck(onPrimary: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF3A3471), NightMid, Night))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp, vertical = 34.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                "FOR ADULTS ONLY",
                style = MaterialTheme.typography.labelSmall,
                color = Periwinkle,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "A quick check",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp, lineHeight = 42.sp),
                color = TextPrimary,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "CereBro is built for adults. A quick check keeps the experience safe and appropriate.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
                color = TextSoft,
            )
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF493352).copy(alpha = 0.82f))
                    .border(1.dp, Color(0xFFC96D74).copy(alpha = 0.62f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Wellness support, not emergency care.",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB38F),
                    )
                    Text(
                        "If you are in immediate danger, call emergency services now.",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                        color = TextSoft,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Confirmed: I am 18 or older",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text(
                        "Thank you",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TimelineBar(0.13f)
            Spacer(Modifier.height(18.dp))
            OnboardingCta("Continue", onClick = onPrimary)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun Welcome(onStart: () -> Unit, onSignIn: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF3A3471), NightMid, Night))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1.05f))
            BrandMark(size = 156.dp, showGlow = false)
            Spacer(Modifier.height(24.dp))
            Text(
                "Welcome to\nCereBro",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp, lineHeight = 42.sp),
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(25.dp))
            Text(
                "Your quiet space for daily mental fitness,\nbetter sleep, and calmer focus.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
                color = TextSoft,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Text(
                "Private by design - nothing is ever shared.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = TextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1.25f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable { onStart() },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.SelfImprovement,
                        contentDescription = null,
                        tint = Ink,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "Try a 2-minute reset",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onSignIn) {
                Text(
                    "I already have an account",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextSoft,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    return

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
private fun WelcomeOrb() {
    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(198.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color.White.copy(alpha = 0.10f),
                            Periwinkle.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .size(145.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White, Color(0xFFE5DFFF), Color(0xFFC7BEF6)),
                        center = androidx.compose.ui.geometry.Offset(45f, 48f),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape),
        )
    }
}

@Composable
private fun ResetStep(onDone: () -> Unit, onBack: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = 0.78f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(3400), RepeatMode.Reverse), label = "scale",
    )
    var timer by remember { mutableStateOf(5) }
    LaunchedEffect(Unit) {
        while (true) {
            for (value in 5 downTo 0) {
                timer = value
                delay(if (value == 0) 650 else 1250)
            }
        }
    }
    Funnel(
        "Your first reset", "Let's steady your body",
        "Follow the orb for a few slow breaths — or skip ahead if now isn't the moment.",
        "I feel steadier", onBack = onBack, onPrimary = onDone, progress = 0.63f,
        secondary = { SkipButton(onClick = onDone) },
    ) {
        Text(
            "Breathe in",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp, lineHeight = 34.sp),
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
            BreathOrb(scale = scale, timer = timer)
        }
        Text("Follow the orb", style = MaterialTheme.typography.titleMedium,
            color = TextSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MemoryChoices(
    moodHistory: Boolean,
    aiMemory: Boolean,
    voiceStorage: Boolean,
    onMoodHistory: (Boolean) -> Unit,
    onAiMemory: (Boolean) -> Unit,
    onVoiceStorage: (Boolean) -> Unit,
    onPrimary: () -> Unit,
) {
    Funnel(
        "Privacy choices",
        "What CereBro\nremembers",
        "Private by default - CereBro remembers nothing you don't switch on. Change any of this later in Settings.",
        "Continue",
        onBack = null,
        onPrimary = onPrimary,
        progress = 0.75f,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF332D58))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp)),
        ) {
            MemoryRow("Mood history", "Used for insights", moodHistory, onMoodHistory)
            MemoryDivider()
            MemoryRow("AI memory", "Goals and preferences", aiMemory, onAiMemory)
            MemoryDivider()
            MemoryRow("Voice storage", "Off by default", voiceStorage, onVoiceStorage)
        }
    }
}

@Composable
private fun MemoryRow(title: String, body: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF9277F4),
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF5B5675),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun MemoryDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.06f)),
    )
}

@Composable
private fun FirstPlan(onPrimary: () -> Unit) {
    Funnel(
        "Made around you",
        "First Plan",
        "",
        "Keep going",
        onBack = null,
        onPrimary = onPrimary,
        progress = 0.88f,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(186.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF63718B), Color(0xFF1D2840))))
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.20f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                    .padding(horizontal = 15.dp, vertical = 6.dp),
            ) {
                Text(
                    "TODAY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = TextPrimary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Ease today's stress",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp, lineHeight = 30.sp),
                    color = TextPrimary,
                )
                Text(
                    "A light plan: one thing now, one tonight, one tomorrow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                )
            }
        }
        PlanRow("Breathing reset", "3 min - recommended now", "~")
        PlanRow("Night journal", "5 min reflection", "|")
    }
}

@Composable
private fun NotificationsChoice(selected: String, onPick: (String) -> Unit, onPrimary: () -> Unit) {
    Funnel(
        "Gentle reminders",
        "Notifications",
        "You've had your first win - want a quiet nudge to come back tomorrow? Never noisy, always easy to turn off.",
        "Enter CereBro",
        onBack = null,
        onPrimary = onPrimary,
        progress = 1f,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NotifyPill("Morning 9 AM", selected == "Morning", modifier = Modifier.weight(1f)) { onPick("Morning") }
                NotifyPill("Evening 7 PM", selected == "Evening", modifier = Modifier.weight(1f)) { onPick("Evening") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NotifyPill("Private previews", selected == "Midday", modifier = Modifier.weight(1f)) { onPick("Midday") }
                NotifyPill("No reminders", selected == "Off", modifier = Modifier.weight(1f)) { onPick("Off") }
            }
        }
    }
}

@Composable
private fun NotifyPill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.10f))
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (selected) Ink else TextPrimary,
        )
    }
}

@Composable
private fun PlanRow(title: String, body: String, mark: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Periwinkle.copy(alpha = 0.24f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(mark, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
            )
        }
    }
}

@Composable
private fun SignUpStep(state: StateOption?, consent: () -> JSONObject, onBack: () -> Unit) {
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
        },
    ) {
        AppTextField(name, { name = it }, "Name", singleLine = true)
        AppTextField(email, { email = it }, "Email", singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        AppTextField(password, { password = it }, "Password (8+ characters)", singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
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
    progress: Float = 0.50f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF3A3471), NightMid, Night))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp, vertical = 34.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
        if (false && onBack != null) TextButton(onClick = { onBack?.invoke() }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("‹ Back", color = TextMuted)
        }
                Text(
                    eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Periwinkle,
                    letterSpacing = 3.sp,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 40.sp),
                    color = TextPrimary,
                )
                if (sub.isNotBlank()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 23.sp),
                        color = TextSoft,
                    )
                }
                content()
            }
            TimelineBar(progress)
            Spacer(Modifier.height(18.dp))
            OnboardingCta(primaryLabel, enabled = primaryEnabled, onClick = onPrimary)
            secondary?.invoke()
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun TimelineBar(targetProgress: Float) {
    var started by remember(targetProgress) { mutableStateOf(false) }
    LaunchedEffect(targetProgress) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) targetProgress.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 5000),
        label = "onboardingTimeline",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.20f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White),
        )
    }
}

@Composable
private fun OnboardingCta(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.35f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(18.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Ink,
            )
        }
    }
}

@Composable
private fun OnboardingTile(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(114.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
            color = TextSoft,
        )
    }
}

@Composable
private fun LanguageOptions(selected: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LanguagePill("English", selected == "English", onPick)
            LanguagePill("Hindi", selected == "Hindi", onPick)
            LanguagePill("Hinglish", selected == "Hinglish", onPick)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LanguagePill("Punjabi", selected == "Punjabi", onPick)
            LanguagePill("Tamil", selected == "Tamil", onPick)
        }
    }
}

@Composable
private fun LanguagePill(label: String, selected: Boolean, onPick: (String) -> Unit) {
    val width = when (label) {
        "Hindi", "Tamil" -> 78.dp
        "Hinglish", "Punjabi" -> 94.dp
        else -> 90.dp
    }
    Box(
        Modifier
            .size(width = width, height = 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.10f))
            .clickable { onPick(label) }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (selected) Ink else TextPrimary,
        )
    }
}

@Composable
private fun BreathOrb(scale: Float, timer: Int) {
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(220.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Cyan.copy(alpha = 0.18f), Periwinkle.copy(alpha = 0.06f), Color.Transparent),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
        )
        Box(
            Modifier
                .size(166.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
        )
        Box(
            Modifier
                .size(144.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White, Color(0xFFD8FBFF), Color(0xFF70D5D8)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                timer.toString(),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 46.sp, lineHeight = 48.sp),
                color = Ink,
            )
        }
    }
}

@Composable
private fun SkipButton(onClick: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.10f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Skip for now",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
    }
}

@Composable
private fun StateOptionList(selected: String?, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        STATE_OPTIONS.forEach { option ->
            val isSelected = selected == option.label
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = if (isSelected) 0.13f else 0.075f))
                    .border(
                        1.dp,
                        if (isSelected) Periwinkle else Color.White.copy(alpha = 0.10f),
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { onPick(option.label) }
                    .padding(horizontal = 17.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
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

@Composable
private fun Orb(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).background(
            Brush.radialGradient(listOf(androidx.compose.ui.graphics.Color.White, Periwinkle, androidx.compose.ui.graphics.Color(0xFF5B52C9))),
            CircleShape,
        ),
    )
}
