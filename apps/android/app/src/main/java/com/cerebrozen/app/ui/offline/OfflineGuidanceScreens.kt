package com.cerebrozen.app.ui.offline

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cerebrozen.app.R
import com.cerebrozen.app.ui.screens.AppSwitch
import com.cerebrozen.app.ui.screens.AppTextField
import com.cerebrozen.app.ui.screens.AmbienceToggle
import com.cerebrozen.app.ui.screens.PrimaryButton
import com.cerebrozen.app.ui.screens.SectionCard
import com.cerebrozen.app.ui.screens.SubPage
import com.cerebrozen.app.ui.screens.ToolAmbienceEffect
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Danger
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextMuted2
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.VeilWell
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
private fun LocalTtsCue(text: String?, enabled: Boolean, paused: Boolean = false) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var ready by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val holder = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(context, owner) {
        val engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) holder[0]?.language = Locale.getDefault()
        }
        holder[0] = engine
        val observer = LifecycleEventObserver { _, event ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (event == Lifecycle.Event.ON_STOP) engine.stop()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            engine.stop()
            engine.shutdown()
            holder[0] = null
        }
    }
    LaunchedEffect(text, enabled, paused, ready, resumed) {
        val engine = holder[0]
        if (enabled && !paused && ready && resumed && !text.isNullOrBlank()) {
            engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "offline-guide")
        } else engine?.stop()
    }
}

@Composable
fun GuidedImageryScreen(onBack: () -> Unit) {
    var journeyId by rememberSaveable { mutableStateOf<String?>(null) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var complete by rememberSaveable { mutableStateOf(false) }
    var voice by rememberSaveable { mutableStateOf(true) }
    var paused by rememberSaveable { mutableStateOf(false) }
    val journey = OfflineToolContent.journeys.firstOrNull { it.id == journeyId }
    val spoken = journey?.steps?.getOrNull(index)?.let { stringResource(it) }
    LocalTtsCue(spoken, voice, paused || complete)

    BackHandler(enabled = journey != null) {
        if (complete) complete = false else if (index > 0) index-- else journeyId = null
    }
    if (journey == null) {
        SubPage(stringResource(R.string.oi_title), stringResource(R.string.oi_title), onBack) {
            Text(stringResource(R.string.oi_subtitle), color = TextMuted)
            OfflineToolContent.journeys.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { item ->
                        Box(Modifier.weight(1f)) {
                            SectionCard(onClick = { journeyId = item.id; index = 0; complete = false }) {
                                Box(Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(18.dp))) {
                                    Image(
                                        painterResource(when (item.id) {
                                            "ocean" -> R.drawable.guided_ocean
                                            "mountain" -> R.drawable.guided_mountain
                                            "meadow" -> R.drawable.guided_meadow
                                            else -> R.drawable.guided_forest
                                        }),
                                        null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                    )
                                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3050B12)))))
                                    Text(item.glyph, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
                                }
                                Text(stringResource(item.titleRes), style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 2)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        return
    }
    if (complete) {
        CompletionPanel(journey.glyph, stringResource(R.string.oi_complete), stringResource(journey.endingRes)) {
            journeyId = null; index = 0; complete = false
        }
        return
    }
    ImmersiveJourneyPage(
            journeyId = journey.id, title = stringResource(journey.titleRes), glyph = journey.glyph,
            progress = index + 1, total = journey.steps.size, body = stringResource(journey.steps[index]),
            voice = voice, paused = paused, onVoice = { voice = !voice }, onPause = { paused = !paused },
            onPrevious = { if (index > 0) index-- else journeyId = null },
            onNext = { if (index == journey.steps.lastIndex) complete = true else index++ },
            onExit = { journeyId = null; index = 0 },
    )
}

@Composable
fun BodyScanScreen(onBack: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var started by rememberSaveable { mutableStateOf(false) }
    var complete by rememberSaveable { mutableStateOf(false) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var auto by rememberSaveable { mutableStateOf(false) }
    var voice by rememberSaveable { mutableStateOf(true) }
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val step = OfflineToolContent.bodyScan[index]
    val spoken = if (started && !complete) "${stringResource(step.partRes)}. ${stringResource(step.instructionRes)}" else null
    LocalTtsCue(spoken, voice, paused)
    LaunchedEffect(started, complete, auto, paused, index, resumed) {
        if (started && !complete && auto && !paused && resumed) {
            delay(step.seconds * 1_000L)
            if (index == OfflineToolContent.bodyScan.lastIndex) complete = true else index++
        }
    }
    if (!started) {
        SubPage(stringResource(R.string.obs_title), stringResource(R.string.obs_title), onBack) {
            Text(stringResource(R.string.obs_subtitle), color = TextMuted)
            OfflineToolContent.bodyScan.forEach { item ->
                SectionCard { Text(stringResource(item.partRes), color = TextPrimary); Text(stringResource(item.instructionRes), color = TextMuted) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.offline_auto), color = TextSoft); AppSwitch(auto, { auto = it })
            }
            PrimaryButton(stringResource(R.string.offline_begin), modifier = Modifier.fillMaxWidth()) { started = true }
        }
        return
    }
    if (complete) {
        CompletionPanel("◯", stringResource(R.string.obs_complete), stringResource(R.string.obs_subtitle)) { onBack() }
        return
    }
    GuidancePage(
        title = stringResource(R.string.obs_title), progress = index + 1, total = OfflineToolContent.bodyScan.size,
        glyph = "◯", body = "${stringResource(step.partRes)}\n\n${stringResource(step.instructionRes)}",
        voice = voice, paused = paused, onVoice = { voice = !voice }, onPause = { paused = !paused },
        onPrevious = { if (index > 0) index-- }, onNext = { if (index == OfflineToolContent.bodyScan.lastIndex) complete = true else index++ },
        onExit = { started = false; index = 0; paused = false },
    )
}

@Composable
fun CrisisGroundingScreen(onBack: () -> Unit, onOpenBreathLoops: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("offline_tools", Context.MODE_PRIVATE) }
    var name by rememberSaveable { mutableStateOf(prefs.getString("emergency_name", "").orEmpty()) }
    var number by rememberSaveable { mutableStateOf(prefs.getString("emergency_number", "").orEmpty()) }
    var saved by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var groundingIndex by rememberSaveable { mutableIntStateOf(0) }
    val grounding = OfflineToolContent.grounding[groundingIndex]
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.ocg_confirm_title)) },
            text = { Text(stringResource(R.string.ocg_confirm_body, number)) },
            confirmButton = { TextButton(onClick = {
                confirm = false
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number.trim())}")))
            }) { Text(stringResource(R.string.ocg_confirm_open)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.offline_cancel)) } },
        )
    }
    SubPage(stringResource(R.string.ocg_title), stringResource(R.string.ocg_title), onBack) {
        Text(stringResource(R.string.ocg_subtitle), color = TextMuted)
        SectionCard {
            Text(stringResource(R.string.ocg_emergency_disclaimer), color = Danger, style = MaterialTheme.typography.bodyMedium)
        }
        SectionCard {
            Text("${grounding.count} · ${stringResource(grounding.senseRes)}", style = MaterialTheme.typography.headlineSmall, color = Cyan)
            Text(stringResource(grounding.promptRes), color = TextSoft)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(enabled = groundingIndex > 0, onClick = { groundingIndex-- }) { Text(stringResource(R.string.offline_previous)) }
                Text(stringResource(R.string.offline_progress, groundingIndex + 1, OfflineToolContent.grounding.size), color = TextMuted)
                TextButton(onClick = { if (groundingIndex < OfflineToolContent.grounding.lastIndex) groundingIndex++ else groundingIndex = 0 }) { Text(stringResource(R.string.common_next)) }
            }
        }
        SectionCard {
            Text(stringResource(R.string.ocg_slow_breath), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(stringResource(R.string.ocg_slow_breath_body), color = TextMuted)
            TextButton(onClick = onOpenBreathLoops) { Text(stringResource(R.string.ocg_open_breath), color = Periwinkle) }
        }
        SectionCard {
            Text(stringResource(R.string.ocg_contact_title), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            AppTextField(name, { name = it; saved = false }, stringResource(R.string.ocg_contact_name), singleLine = true)
            AppTextField(number, { number = it; saved = false }, stringResource(R.string.ocg_contact_number), singleLine = true)
            PrimaryButton(stringResource(R.string.ocg_contact_save), enabled = number.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                prefs.edit().putString("emergency_name", name.trim()).putString("emergency_number", number.trim()).apply(); saved = true
            }
            if (saved) Text(stringResource(R.string.offline_saved), color = Ok)
            TextButton(enabled = number.isNotBlank(), onClick = { confirm = true }) {
                Icon(Icons.Outlined.Phone, null); Text(stringResource(R.string.ocg_call))
            }
            if (number.isBlank()) Text(stringResource(R.string.ocg_contact_required), color = TextMuted2, style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.offline_disclaimer), color = TextMuted2, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun InsightReelScreen(onBack: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var auto by rememberSaveable { mutableStateOf(true) }
    var paused by rememberSaveable { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(index, auto, paused, resumed) {
        if (auto && !paused && resumed) { delay(8_000); index = (index + 1) % OfflineToolContent.insights.size }
    }
    val card = OfflineToolContent.insights[index]
    SubPage(stringResource(R.string.oir_title), stringResource(R.string.oir_title), onBack) {
        Text(stringResource(R.string.oir_subtitle), color = TextMuted)
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(VeilWell)) {
            Box(Modifier.fillMaxWidth((index + 1f) / OfflineToolContent.insights.size).height(5.dp).background(Periwinkle))
        }
        AnimatedContent(card, transitionSpec = { androidx.compose.animation.fadeIn(tween(250)) togetherWith androidx.compose.animation.fadeOut(tween(180)) }, label = "insight") { item ->
            Column(
                Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Periwinkle.copy(alpha = 0.18f), Cyan.copy(alpha = 0.12f))))
                    .border(1.dp, LineStroke, RoundedCornerShape(26.dp)).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(item.kickerRes), color = Periwinkle, style = MaterialTheme.typography.labelSmall)
                Text(stringResource(item.titleRes), color = TextPrimary, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                Text(stringResource(item.bodyRes), color = TextSoft, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { index = (index - 1 + OfflineToolContent.insights.size) % OfflineToolContent.insights.size }) { Text(stringResource(R.string.offline_previous)) }
            Text(stringResource(R.string.offline_progress, index + 1, OfflineToolContent.insights.size), color = TextMuted)
            TextButton(onClick = { index = (index + 1) % OfflineToolContent.insights.size }) { Text(stringResource(R.string.common_next)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.offline_auto), color = TextSoft); AppSwitch(auto, { auto = it }) }
            TextButton(onClick = { paused = !paused }) { Text(stringResource(if (paused) R.string.offline_resume else R.string.offline_pause)) }
        }
    }
}

@Composable
private fun ImmersiveJourneyPage(
    journeyId: String, title: String, glyph: String,
    progress: Int, total: Int, body: String, voice: Boolean, paused: Boolean,
    onVoice: () -> Unit, onPause: () -> Unit, onPrevious: () -> Unit,
    onNext: () -> Unit, onExit: () -> Unit,
) {
    val imageRes = when (journeyId) {
        "ocean" -> R.drawable.guided_ocean
        "mountain" -> R.drawable.guided_mountain
        "meadow" -> R.drawable.guided_meadow
        else -> R.drawable.guided_forest
    }
    val accent = when (journeyId) {
        "ocean" -> Color(0xFF67E8F9)
        "mountain" -> Color(0xFFC4B5FD)
        "meadow" -> Color(0xFFF9A8D4)
        else -> Color(0xFF6EE7B7)
    }
    ToolAmbienceEffect(if (journeyId == "ocean") R.raw.ocean else R.raw.wind)
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(imageRes), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66040B12), Color(0x33040B12), Color(0xD9081118)))))
        Column(
            Modifier.fillMaxSize().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onExit) { Text(stringResource(R.string.offline_exit), color = Color.White.copy(alpha = 0.78f)) }
                AmbienceToggle()
            }
            Text(title, style = MaterialTheme.typography.displaySmall, color = Color.White)
            Text(stringResource(R.string.offline_progress, progress, total), color = accent)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(total) { step ->
                    Box(Modifier.weight(1f).height(6.dp).clip(CircleShape)
                        .background(if (step < progress) accent else Color.White.copy(alpha = 0.13f)))
                }
            }
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                targetState = progress,
                transitionSpec = { androidx.compose.animation.fadeIn(tween(420)) togetherWith androidx.compose.animation.fadeOut(tween(220)) },
                label = "forest-step",
            ) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.07f))))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(30.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(glyph, style = MaterialTheme.typography.displayMedium)
                    Text(body, style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onVoice) {
                    Icon(if (voice) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff, null, tint = accent)
                    Text(stringResource(R.string.offline_voice), color = Color.White)
                }
                TextButton(onClick = onPause) { Text(stringResource(if (paused) R.string.offline_resume else R.string.offline_pause), color = Color.White) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onPrevious) { Text(stringResource(R.string.offline_previous), color = Color.White.copy(alpha = 0.78f)) }
                PrimaryButton(stringResource(if (progress == total) R.string.offline_complete else R.string.common_next), onClick = onNext)
            }
        }
    }
}

@Composable
private fun GuidancePage(
    title: String, progress: Int, total: Int, glyph: String, body: String,
    voice: Boolean, paused: Boolean, onVoice: () -> Unit, onPause: () -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit, onExit: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Text(stringResource(R.string.offline_progress, progress, total), color = TextMuted)
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(VeilWell)) {
            Box(Modifier.fillMaxWidth(progress.toFloat() / total).height(5.dp).background(Periwinkle))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(glyph, style = MaterialTheme.typography.displaySmall)
            Text(body, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onVoice) { Icon(if (voice) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff, null); Text(stringResource(R.string.offline_voice)) }
            TextButton(onClick = onPause) { Text(stringResource(if (paused) R.string.offline_resume else R.string.offline_pause)) }
            TextButton(onClick = onExit) { Text(stringResource(R.string.offline_exit), color = TextMuted) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onPrevious) { Text(stringResource(R.string.offline_previous)) }
            TextButton(onClick = onNext) { Text(stringResource(if (progress == total) R.string.offline_complete else R.string.common_next)) }
        }
    }
}

@Composable
private fun CompletionPanel(glyph: String, title: String, body: String, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(glyph, style = MaterialTheme.typography.displaySmall)
        Box(Modifier.size(72.dp).clip(CircleShape).background(Ok), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Check, null, tint = Color.White) }
        Text(title, style = MaterialTheme.typography.displaySmall, color = TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp))
        Text(body, color = TextMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 16.dp))
        PrimaryButton(stringResource(R.string.common_done), modifier = Modifier.fillMaxWidth(), onClick = onDone)
    }
}
