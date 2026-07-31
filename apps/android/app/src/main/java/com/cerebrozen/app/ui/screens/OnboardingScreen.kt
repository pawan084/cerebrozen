package com.cerebrozen.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerebrozen.app.R
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
import com.cerebrozen.app.ui.theme.ChipFill
import com.cerebrozen.app.ui.theme.EyebrowMuted
import com.cerebrozen.app.ui.theme.GratitudeCardFill
import com.cerebrozen.app.ui.theme.GratitudeAvatarFill
import com.cerebrozen.app.ui.theme.GratitudeCaption
import com.cerebrozen.app.ui.theme.InfoCardFill
import com.cerebrozen.app.ui.theme.InfoCardStroke
import com.cerebrozen.app.ui.theme.InfoCardHint
import com.cerebrozen.app.ui.theme.InfoCardDivider
import com.cerebrozen.app.ui.theme.WelcomeGradientTop
import com.cerebrozen.app.ui.theme.WelcomeGradientBottom
import com.cerebrozen.app.ui.theme.WelcomeTitleText
import com.cerebrozen.app.ui.theme.WelcomeSubtitleText
import com.cerebrozen.app.ui.theme.WelcomeSecondaryText
import com.cerebrozen.app.ui.theme.WelcomeOrbMid
import com.cerebrozen.app.ui.theme.WelcomeOrbEdge
import com.cerebrozen.app.ui.theme.WelcomeOrbHaloInner
import com.cerebrozen.app.ui.theme.WelcomeOrbHaloOuter
import com.cerebrozen.app.ui.theme.WelcomeOrbHaloDisc
import com.cerebrozen.app.ui.theme.PrimaryButtonFill
import com.cerebrozen.app.ui.theme.PrimaryButtonInk
import com.cerebrozen.app.ui.theme.PrimaryButtonDisabledFill
import com.cerebrozen.app.ui.theme.ResetDoneFill
import com.cerebrozen.app.ui.theme.FunnelHeaderTop
import com.cerebrozen.app.ui.theme.FunnelHeaderBottom
import com.cerebrozen.app.ui.theme.FunnelBodyText
import com.cerebrozen.app.ui.theme.ProgressTrack
import com.cerebrozen.app.ui.theme.PickRowSelectedFill
import com.cerebrozen.app.ui.theme.PickRowFill
import com.cerebrozen.app.ui.theme.PickRowStroke
import com.cerebrozen.app.ui.theme.PickRowChevron
import com.cerebrozen.app.ui.theme.PickCardStroke
import com.cerebrozen.app.ui.theme.DotUnselectedFill
import kotlinx.coroutines.launch
import org.json.JSONObject

internal enum class OStep { Welcome, Disclosure, Language, State, Reset, Consent, Notify, SignUp }

/** How far along the funnel a step sits. Keyed off the step itself — never off
 * the eyebrow copy, which is translated (a Hindi device used to fall through to
 * 1f and snap the bar to full from the Language step on). Pure + unit-tested. */
internal fun funnelProgress(step: OStep): Float = when (step) {
    OStep.Welcome -> 0f
    OStep.Disclosure -> 0.25f
    OStep.Language -> 0.38f
    OStep.State -> 0.50f
    OStep.Reset -> 0.62f
    OStep.Consent -> 0.75f
    OStep.Notify -> 0.88f
    OStep.SignUp -> 1f
}

/** One feeling tap is the whole "assessment" — each maps into the shared
 * motivation/goal taxonomy (cross-stack: iOS StateCheckScreen.states ⇄ web
 * lib/onboarding.FEELINGS) so plans and conversation starters ground on it.
 * `mood` keys the first check-in into the shared mood taxonomy.
 *
 * [id] is our own stable key (saver / selection identity); [labelRes] is the
 * display copy and localizes freely. motivation/goal/mood are WIRE VALUES of the
 * cross-stack taxonomy — never translate them. */
internal data class StateOption(
    val id: String,
    @androidx.annotation.StringRes val labelRes: Int,
    val motivation: String,
    val goal: String,
    val mood: String,
)

internal val STATE_OPTIONS = listOf(
    StateOption("stressed", R.string.ob_state_opt_stressed, "Calm", "Reduce stress", "Anxious"),
    StateOption("night", R.string.ob_state_opt_night, "Calm", "Sleep better", "Tired"),
    StateOption("overthinking", R.string.ob_state_opt_overthinking, "Focus", "Stop overthinking", "Anxious"),
    StateOption("doubt", R.string.ob_state_opt_doubt, "Confidence", "Build confidence", "Low"),
    StateOption("distant", R.string.ob_state_opt_distant, "Connection", "Feel less alone", "Low"),
    StateOption("consistency", R.string.ob_state_opt_consistent, "Discipline", "Strengthen willpower", "Okay"),
)

/** A pickable chip: a stable [id] the code branches on, plus localizable copy. */
internal data class PickOption(val id: String, @androidx.annotation.StringRes val labelRes: Int)

private val LANGUAGES = listOf(
    PickOption("English", R.string.ob_lang_english),
    PickOption("Hindi", R.string.ob_lang_hindi),
    PickOption("Hinglish", R.string.ob_lang_hinglish),
    PickOption("Punjabi", R.string.ob_lang_punjabi),
    PickOption("Tamil", R.string.ob_lang_tamil),
)
/** When the daily reminder fires. Single-select, so every option here must be a
 * TIME — anything else silently means "none" once [applyReminderChoice] falls
 * through its `when`.
 *
 * A "Private previews" chip used to sit in this group. Nothing read the value: it
 * was never persisted, no preview setting exists anywhere in the app, and the
 * reminder it would have hidden says only "A moment for you". What it actually
 * did was turn reminders off — so a user who tapped it, wanting a *discreet*
 * daily nudge, got no nudge at all and was told nothing. */
internal val NOTIFY = listOf(
    PickOption("morning", R.string.ob_notify_morning),
    PickOption("evening", R.string.ob_notify_evening),
    PickOption("none", R.string.ob_notify_none),
)

/** The hour a reminder option schedules, or null for "no reminder".
 *
 * Pure and internal so the invariant is a TEST rather than a comment: every id
 * in [NOTIFY] must resolve here. When it was an inline `when` with a silent
 * `else`, an option was added to the chip group that this mapping had never
 * heard of, and choosing it quietly meant "off". */
internal fun reminderHourFor(option: String): Int? = when (option) {
    "morning" -> 9
    "evening" -> 19
    "none" -> null
    else -> null
}
// Consent rows render from the localized notice (DPDP s.5(3) — ConsentNotice.kt).

/** Set when the post-sign-up consent write never reached the server, so Privacy
 * can say so instead of letting a failed write pass silently. */
internal const val CONSENT_SYNC_FAILED_KEY = "consent_sync_failed"

// Savers so a rotation / process death mid-funnel keeps the user's place and their
// selections instead of dropping them back to Welcome.
private val StateOptionSaver = Saver<StateOption?, String>(
    save = { it?.id ?: "" },
    restore = { id -> STATE_OPTIONS.firstOrNull { it.id == id } },
)
private val ConsentSaver = mapSaver(
    save = { it.toMap() },
    restore = { restored ->
        mutableStateMapOf<String, Boolean>().apply {
            restored.forEach { (k, v) -> put(k, v as Boolean) }
        }
    },
)

/** Whether the soft keyboard is showing.
 *
 * Every BackHandler in this funnel gates on it. Back's FIRST job on Android is
 * to dismiss the keyboard, and a handler that is unconditionally enabled eats
 * that press: on the sign-in step it navigated away instead, so a user closing
 * the keyboard was thrown back to Welcome with the email and password they had
 * just typed. Caught on a device the same evening the BackHandler was added. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun imeVisible(): Boolean = WindowInsets.isImeVisible

/**
 * Value-first onboarding funnel — the adult gate, honesty disclosure, a first
 * calming reset, then account + consent. New users flow through here; returning
 * users tap through to the existing [AuthScreen]. Consent/notification prefs are
 * collected locally and applied right after sign-up so the session flips once.
 */
@Composable
fun Onboarding() {
    var signIn by rememberSaveable { mutableStateOf(false) }
    if (signIn) {
        androidx.activity.compose.BackHandler(enabled = !imeVisible()) { signIn = false }
        AuthScreen(onBack = { signIn = false })
        return
    }

    var step by rememberSaveable { mutableStateOf(OStep.Welcome) }
    val order = OStep.entries
    fun next() { val i = order.indexOf(step); if (i < order.lastIndex) step = order[i + 1] }
    fun back() { val i = order.indexOf(step); if (i > 0) step = order[i - 1] }

    // The system back gesture walks the funnel, exactly like the on-screen back.
    // Without this it fell through to the Activity and FINISHED it: found on a
    // device, a back swipe from any of the eight steps dropped the user onto the
    // launcher, and relaunching restarted at Welcome — language, feeling and
    // consent choices all gone, because rememberSaveable cannot survive an
    // activity that was destroyed rather than recreated. Back is the most-used
    // navigation control on Android; it was a trapdoor out of onboarding.
    // Disabled on Welcome so back there still leaves the app, as expected, and
    // while the keyboard is up so back still closes the keyboard first.
    androidx.activity.compose.BackHandler(enabled = step != OStep.Welcome && !imeVisible()) { back() }

    // First-party funnel counts (anonymous install id, opt-out; mirrors iOS).
    LaunchedEffect(step) { Analytics.track("onboarding_step", funnelStepName(step.name)) }

    var language by rememberSaveable { mutableStateOf("English") }
    var state by rememberSaveable(stateSaver = StateOptionSaver) { mutableStateOf<StateOption?>(null) }
    var notify by rememberSaveable { mutableStateOf("evening") }
    // Did they actually breathe, or press "Skip for now"? The Notify step used
    // to congratulate everyone on "your first win" either way — telling a user
    // who skipped that they had achieved something they had just declined.
    var resetDone by rememberSaveable { mutableStateOf(false) }
    // Private by default: NOTHING pre-ticked — consent must be an action
    // (EDPB/ICO; matches iOS ConsentScreen + web onboarding).
    val consent = rememberSaveable(saver = ConsentSaver) {
        mutableStateMapOf(
            "mood_history" to false, "ai_memory" to false, "journal_memory" to false,
            "sleep_history" to false, "voice_storage" to false, "model_training" to false,
        )
    }

    // Apply the onboarding reminder choice for real: persist it, schedule the daily
    // alarm at the chosen hour, and ask for notification permission (Android 13+).
    // Without this the Notify step's selection did nothing.
    val context = LocalContext.current
    val notifyPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun applyReminderChoice() {
        val prefs = context.getSharedPreferences("cerebro", Context.MODE_PRIVATE)
        val hour = reminderHourFor(notify)
            ?: run { prefs.edit().putBoolean("reminder_on", false).apply(); return }
        prefs.edit().putBoolean("reminder_on", true).apply()
        com.cerebrozen.app.notify.Reminders.schedule(context, hour)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifyPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // W10: calm step transitions — a 250ms fade with a slight directional slide
    // (forward slides in from the right, back from the left). Reduce Motion
    // snaps between steps instead (no transition, never blank).
    val reduceMotion = rememberReduceMotion()
    val slidePx = with(LocalDensity.current) { 24.dp.roundToPx() }
    AnimatedContent(
        targetState = step,
        transitionSpec = {
            if (reduceMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val forward = targetState.ordinal >= initialState.ordinal
                (fadeIn(tween(250)) + slideInHorizontally(tween(250)) { if (forward) slidePx else -slidePx })
                    .togetherWith(fadeOut(tween(250)))
            }
        },
        label = "onboarding-step",
    ) { current ->
        when (current) {
        OStep.Welcome -> Welcome(onStart = { next() }, onSignIn = { signIn = true })

        OStep.Disclosure -> Funnel(
            OStep.Disclosure,
            stringResource(R.string.ob_disclosure_eyebrow), stringResource(R.string.ob_disclosure_title),
            stringResource(R.string.ob_disclosure_sub),
            stringResource(R.string.ob_disclosure_cta), onBack = { back() }, onPrimary = { next() },
        ) {
            ReferenceCard(borderColor = Warm.copy(alpha = 0.5f), fill = GratitudeCardFill) {
                Text(stringResource(R.string.common_wellness_footer),
                    style = MaterialTheme.typography.titleMedium, color = Warm)
                Text(stringResource(R.string.ob_danger_line),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            // The age gate STATES the requirement. It used to show a tick with
            // "Confirmed: I am 18 or older / Thank you" the instant the step
            // opened — a compliance surface asserting a confirmation the user had
            // not made, and thanking them for it. The confirmation is the CTA.
            ReferenceCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(GratitudeAvatarFill), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Person, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text(stringResource(R.string.ob_confirmed_18), style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(stringResource(R.string.ob_thank_you), style = MaterialTheme.typography.bodySmall, color = GratitudeCaption)
                    }
                }
            }
            // Two-up "can help / can't do" tiles (fork look), on our glass tokens.
            // IntrinsicSize.Min sizes the Row to the taller tile and both fill it,
            // so they stay a matched pair without either one cropping its body.
            Row(
                Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DisclosureTile(
                    stringResource(R.string.ob_can_help_title), Cyan,
                    stringResource(R.string.ob_can_help_body),
                    Modifier.weight(1f).fillMaxHeight(),
                )
                DisclosureTile(
                    stringResource(R.string.ob_cant_do_title), Warm,
                    stringResource(R.string.ob_cant_do_body),
                    Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }

        OStep.Language -> Funnel(
            OStep.Language,
            stringResource(R.string.ob_language_eyebrow), stringResource(R.string.ob_language_title),
            stringResource(R.string.ob_language_sub),
            stringResource(R.string.common_continue), onBack = { back() }, onPrimary = { next() },
        ) {
            ChipWrapOptions(LANGUAGES, language) { language = it }
        }

        OStep.State -> Funnel(
            OStep.State,
            stringResource(R.string.ob_state_eyebrow), stringResource(R.string.ob_state_title),
            stringResource(R.string.ob_state_sub),
            stringResource(R.string.common_continue), primaryEnabled = state != null, onBack = { back() }, onPrimary = { next() },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                STATE_OPTIONS.forEach { option ->
                    StateOptionRow(stringResource(option.labelRes), state?.id == option.id) { state = option }
                }
            }
        }

        OStep.Reset -> ResetStep(
            onDone = { resetDone = true; next() },
            onSkip = { next() },
            onBack = { back() },
        )

        OStep.Consent -> Funnel(
            OStep.Consent,
            stringResource(R.string.ob_consent_eyebrow), stringResource(R.string.ob_consent_title),
            stringResource(R.string.ob_consent_sub),
            stringResource(R.string.common_continue), onBack = { back() },
            // Passing the consent step unlocks anonymous telemetry (DPDP posture:
            // nothing is counted before this moment — Analytics.track no-ops).
            onPrimary = { Analytics.unlock(); next() },
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(InfoCardFill)
                    .border(1.dp, InfoCardStroke, RoundedCornerShape(18.dp)),
            ) {
                // All six categories, every time — DPDP "specific and informed":
                // nothing collected under a switch the user never saw.
                // Keys are the consent contract; labels/hints are display copy.
                val rows = listOf(
                    Triple("mood_history", stringResource(R.string.ob_consent_mood), stringResource(R.string.ob_consent_mood_hint)),
                    Triple("sleep_history", stringResource(R.string.ob_consent_sleep), stringResource(R.string.ob_consent_sleep_hint)),
                    Triple("journal_memory", stringResource(R.string.ob_consent_journal), stringResource(R.string.ob_consent_journal_hint)),
                    Triple("ai_memory", stringResource(R.string.ob_consent_ai), stringResource(R.string.ob_consent_ai_hint)),
                    Triple("voice_storage", stringResource(R.string.ob_consent_voice), stringResource(R.string.ob_consent_voice_hint)),
                    Triple("model_training", stringResource(R.string.ob_consent_training), stringResource(R.string.ob_consent_training_hint)),
                )
                rows.forEachIndexed { index, (key, label, hint) ->
                    Row(
                        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(
                                hint, style = MaterialTheme.typography.bodySmall, color = InfoCardHint,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        AppSwitch(checked = consent[key] == true, onCheckedChange = { consent[key] = it })
                    }
                    if (index < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(InfoCardDivider))
                }
            }
        }

        OStep.Notify -> Funnel(
            OStep.Notify,
            stringResource(R.string.ob_notify_eyebrow), stringResource(R.string.ob_notify_title),
            stringResource(if (resetDone) R.string.ob_notify_sub else R.string.ob_notify_sub_skipped),
            stringResource(R.string.ob_notify_cta), onBack = { back() }, onPrimary = { applyReminderChoice(); next() },
        ) {
            ChipWrapOptions(NOTIFY, notify) { notify = it }
        }

        OStep.SignUp -> AuthScreen(
            initialCreating = true,
            onBack = { back() },
            onAccountCreated = {
                Analytics.track("onboarding_done")
                runCatching { Api.attest() }
                // Consent is the one write here that must never fail silently
                // (DPDP integrity — we may not imply choices the server never
                // took). The funnel's own composition is gone by now, so: retry
                // once, and if it still doesn't land, flag it so Privacy tells
                // the user their setup choices didn't save.
                val consentBody = JSONObject().apply { consent.forEach { (k, v) -> put(k, v) } }
                val consentSaved = runCatching { Api.updateConsent(consentBody) }
                    .recoverCatching { Api.updateConsent(consentBody) }
                    .isSuccess
                runCatching { Session.prefPut(CONSENT_SYNC_FAILED_KEY, (!consentSaved).toString()) }
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
}

@Composable
private fun Welcome(onStart: () -> Unit, onSignIn: () -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(WelcomeGradientTop, WelcomeGradientBottom)))
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
                stringResource(R.string.ob_welcome_title),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 40.sp, lineHeight = 41.sp, letterSpacing = (-0.6).sp,
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(23.dp))
            Text(
                stringResource(R.string.ob_welcome_sub),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.5.sp, lineHeight = 24.sp),
                color = WelcomeTitleText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(15.dp))
            Text(
                stringResource(R.string.ob_welcome_privacy),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = WelcomeSubtitleText,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
                    .background(PrimaryButtonFill).clickable(onClick = onStart),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Air, null, tint = PrimaryButtonInk, modifier = Modifier.size(19.dp))
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.ob_welcome_cta), style = MaterialTheme.typography.titleMedium, color = PrimaryButtonInk)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSignIn) {
                Text(stringResource(R.string.auth_have_account), style = MaterialTheme.typography.titleSmall, color = WelcomeSecondaryText)
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
                0.55f to WelcomeOrbHaloInner, 0.82f to WelcomeOrbHaloOuter, 1f to Color.Transparent,
                center = center, radius = this.size.minDimension / 2f,
            ),
            this.size.minDimension / 2f,
            center,
        )
        drawCircle(WelcomeOrbHaloDisc, radius * 1.27f, center)
        drawCircle(
            Brush.radialGradient(
                0f to Color.White, 0.22f to WelcomeOrbMid, 1f to WelcomeOrbEdge,
                center = Offset(center.x - radius * 0.2f, center.y - radius * 0.27f),
                radius = radius * 1.55f,
            ),
            radius,
            center,
        )
    }
}

@Composable
private fun ResetStep(onDone: () -> Unit, onSkip: () -> Unit, onBack: () -> Unit) {
    // The orb, count and Reduce-Motion behaviour all come from the shared
    // BreatheEngine (Reset preset: four in, four out, no holds) — the same
    // engine every breathe surface in the app hosts.
    Funnel(
        OStep.Reset,
        stringResource(R.string.ob_reset_eyebrow), stringResource(R.string.ob_reset_title),
        stringResource(R.string.ob_reset_sub),
        stringResource(R.string.ob_reset_cta), onBack = onBack, onPrimary = onDone,
        titleCentered = true,
        secondary = {
            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(26.dp))
                    .background(ResetDoneFill).clickable(onClick = onSkip),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.ob_reset_skip), style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        },
    ) {
        BreatheEngine(BreathePreset.Reset, Modifier.fillMaxWidth())
    }
}

// ── Shared bits ──────────────────────────────────────────────────────────

/** The last funnel fraction shown, so the next step's bar animates from it
 * (each step is a separate composition inside AnimatedContent). Cosmetic only. */
private object FunnelProgressMemory { var last = 0f }

@Composable
private fun Funnel(
    step: OStep,
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
    val progress = funnelProgress(step)
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(FunnelHeaderTop, FunnelHeaderBottom)))
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
                color = EyebrowMuted,
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
                color = FunnelBodyText,
            )
            Spacer(Modifier.height(6.dp))
            content()
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 23.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // W10: the fill glides from the previous step's fraction to this one
            // instead of jumping. Each step is a fresh composition inside
            // AnimatedContent, so the bar seeds from a small cross-step memory and
            // then animates to its own fraction. Reduce Motion keeps the honest
            // instant snap.
            val reduceMotion = rememberReduceMotion()
            var barTarget by remember { mutableStateOf(FunnelProgressMemory.last) }
            LaunchedEffect(progress) {
                barTarget = progress
                FunnelProgressMemory.last = progress
            }
            val animatedProgress by animateFloatAsState(
                targetValue = barTarget,
                animationSpec = if (reduceMotion) snap() else tween(350),
                label = "funnel-progress",
            )
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(ProgressTrack)) {
                Box(Modifier.fillMaxWidth(animatedProgress).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(29.dp))
                    .background(if (primaryEnabled) PrimaryButtonFill else PrimaryButtonDisabledFill)
                    .clickable(enabled = primaryEnabled, onClick = onPrimary),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.CheckCircle, null, tint = PrimaryButtonInk, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(11.dp))
                Text(primaryLabel, style = MaterialTheme.typography.titleMedium, color = PrimaryButtonInk)
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
    fill: Color = ChipFill,
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
            .background(if (selected) PickRowSelectedFill else PickRowFill)
            .border(1.dp, PickRowStroke, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Color.White)
        Icon(Icons.Outlined.ChevronRight, null, tint = PickRowChevron, modifier = Modifier.size(22.dp))
    }
}

/** One side of the two-up disclosure — a glass tile with an accent heading.
 *
 * Grows to fit its body, never crops it. It used to be a fixed `.height(129.dp)`,
 * and on a 720px device the "Can't do" tile cut the word "emergencies" in half —
 * the single most important limitation on the screen whose entire job is stating
 * limitations, silently truncated, in the shortest locale we ship. The caller
 * pairs these in a Row at [IntrinsicSize.Min] so both sides still match height.
 */
@Composable
private fun DisclosureTile(
    title: String,
    accent: androidx.compose.ui.graphics.Color,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.heightIn(min = 129.dp).clip(RoundedCornerShape(17.dp)).background(ChipFill)
            .border(1.dp, PickCardStroke, RoundedCornerShape(17.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // `accent` tints the heading — it was accepted and then never applied, so
        // "Can help" and "Can't do" rendered identically in plain white despite
        // the call site passing Cyan and TextSoft to tell them apart.
        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 19.sp), color = accent)
        Text(body, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp), color = FunnelBodyText)
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
                Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) Color.White else DotUnselectedFill)
                    .clickable { onPick(opt) }.padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    opt,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) PrimaryButtonInk else Color.White,
                )
            }
        }
    }
}

/** [ChipWrap] over stable-id options: the chips show localized copy while the
 * caller keeps branching on the id (reminder hour, language code). */
@Composable
internal fun ChipWrapOptions(options: List<PickOption>, selectedId: String?, onPick: (String) -> Unit) {
    val labels = options.map { stringResource(it.labelRes) }
    val selectedLabel = options.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }?.let { labels[it] }
    ChipWrap(labels, selectedLabel) { label ->
        labels.indexOf(label).takeIf { it >= 0 }?.let { onPick(options[it].id) }
    }
}
