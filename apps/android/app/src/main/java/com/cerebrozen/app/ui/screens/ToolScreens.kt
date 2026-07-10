package com.cerebro.app.ui.screens

import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerebro.app.net.Api
import com.cerebro.app.ui.theme.Ink
import com.cerebro.app.ui.theme.Cyan
import com.cerebro.app.ui.theme.Ok
import com.cerebro.app.ui.theme.Periwinkle
import com.cerebro.app.ui.theme.TextMuted
import com.cerebro.app.ui.theme.TextPrimary
import com.cerebro.app.ui.theme.TextSoft
import com.cerebro.app.ui.theme.Warm
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Native tools (iOS ToolsViews + MicroActivities parity): CBT reframe,
// one good thing, intention set, DBT TIPP. Written work mirrors to the
// journal so it feeds reflections/insights like iOS.

@Composable
fun BreathingScreen(onBack: () -> Unit) {
    var elapsed by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf(false) }
    val totalSeconds = 120
    val phases = listOf("Breathe in", "Hold", "Breathe out", "Hold")
    val phase = phases[(elapsed / 4) % phases.size]
    val count = 4 - (elapsed % 4)
    val transition = rememberInfiniteTransition(label = "breathing-orb")
    val orbScale by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
        label = "breathing-orb-scale",
    )

    LaunchedEffect(Unit) {
        while (elapsed < totalSeconds) {
            delay(1000)
            elapsed += 1
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF6657AA), Color(0xFF2B1E5C), Color(0xFF120820)))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = pageHorizontalPadding(), vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("A MINUTE TO BREATHE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.42f))
                    Text("Breathing", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFD46C4D), Color.White.copy(alpha = 0.10f))))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.28f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Air, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Text(
                    "Follow the orb — in for four, hold, out\nfor four.",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(phase, style = MaterialTheme.typography.displaySmall, color = TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))

            Box(Modifier.fillMaxWidth().height(236.dp), contentAlignment = Alignment.Center) {
                repeat(3) { index ->
                    Box(
                        Modifier
                            .size((118 + index * 56).dp)
                            .clip(CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.09f + index * 0.04f), CircleShape),
                    )
                }
                Box(
                    Modifier
                        .size(144.dp)
                        .scale(orbScale)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color.White, Color(0xFF7EE6E8), Color(0xFF68D8DB)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(count.toString(), style = MaterialTheme.typography.displayLarge, color = Ink)
                }
            }

            Spacer(Modifier.height(6.dp))
            Text("Follow the orb", style = MaterialTheme.typography.titleSmall, color = TextMuted, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(22.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(67.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.085f))
                    .clickable { saved = !saved }
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Book, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (saved) "Reflection saved" else "Save reflection", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text("Add result to private journal", style = MaterialTheme.typography.labelSmall, color = TextSoft)
                }
                Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(26.dp))
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White)
                    .clickable { onBack() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2B214E), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Continue", style = MaterialTheme.typography.titleMedium, color = Color(0xFF2B214E), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(112.dp))
        }
    }
}

@Composable
fun ToolsScreen(onOpen: (String) -> Unit, onBack: () -> Unit) = SubPage("Small resets", "Tools", onBack) {
    Text("Two-minute practices for the moment you're in.",
        style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    NavRow("Untangle a thought", "CBT reframe — from stuck to balanced", icon = Icons.Outlined.Psychology) { onOpen("cbt") }
    NavRow("One good thing", "Name something that went right", icon = Icons.Outlined.Star) { onOpen("onegoodthing") }
    NavRow("Tomorrow's intention", "Set one clear point for tomorrow", icon = Icons.Outlined.WbTwilight) { onOpen("intention") }
    NavRow("TIPP skill", "DBT reset for very intense moments", icon = Icons.Outlined.SelfImprovement) { onOpen("tipp") }
}

/** A small tool that writes its result to the journal (title + composed body). */
@Composable
private fun JournalingTool(
    eyebrow: String,
    title: String,
    intro: String,
    journalTitle: String,
    onBack: () -> Unit,
    compose: (List<String>) -> String,
    fields: List<Pair<String, String>>,   // label → placeholder-ish hint
) {
    val values = remember { mutableStateOf(List(fields.size) { "" }) }
    var saved by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    SubPage(eyebrow, title, onBack) {
        Text(intro, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        fields.forEachIndexed { i, (label, _) ->
            AppTextField(
                values.value[i],
                { v -> values.value = values.value.toMutableList().also { it[i] = v } },
                label,
                minLines = 2,
            )
        }
        PrimaryButton(
            text = if (saved) "Saved to your journal" else "Save to journal",
            enabled = !saved && values.value.all { it.isNotBlank() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            scope.launch {
                runCatching { Api.createJournal(journalTitle, compose(values.value)) }
                    .onSuccess { saved = true }
                    .onFailure { status = it.message ?: "Couldn't save." }
            }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
    }
}

@Composable
fun CbtReframeScreen(onBack: () -> Unit) = JournalingTool(
    eyebrow = "Untangle a thought",
    title = "From stuck to balanced",
    intro = "Catch the thought, look at it honestly, and write the fairer version. This is the whole skill.",
    journalTitle = "Balanced thought",
    onBack = onBack,
    compose = { v ->
        "The thought: ${v[0]}\n\nWhat supports it: ${v[1]}\n\nWhat doesn't: ${v[2]}\n\nA more balanced thought: ${v[3]}"
    },
    fields = listOf(
        "The thought that's looping" to "",
        "Evidence it's true" to "",
        "Evidence it's not the whole story" to "",
        "A more balanced thought" to "",
    ),
)

@Composable
fun OneGoodThingScreen(onBack: () -> Unit) = JournalingTool(
    eyebrow = "One good thing",
    title = "Name a small win",
    intro = "Anything counts — a kind word, a finished task, a decent cup of tea. Naming it makes it stick.",
    journalTitle = "One good thing",
    onBack = onBack,
    compose = { v -> "One good thing today: ${v[0]}" },
    fields = listOf("What went right?" to ""),
)

@Composable
fun IntentionScreen(onBack: () -> Unit) = JournalingTool(
    eyebrow = "Tomorrow's intention",
    title = "One clear point",
    intro = "Not a to-do list — one thing that would make tomorrow feel steadier.",
    journalTitle = "Intention",
    onBack = onBack,
    compose = { v -> "Tomorrow I will: ${v[0]}" },
    fields = listOf("Tomorrow I will…" to ""),
)

/** DBT TIPP — a guided walkthrough, no data collected. */
@Composable
fun TippScreen(onBack: () -> Unit) {
    val steps = listOf(
        Triple("T — Temperature", "Hold something cold, splash cool water on your face, or step into cooler air.", "Cold triggers the dive reflex and slows your heart rate fast."),
        Triple("I — Intense exercise", "60–90 seconds of anything vigorous: star jumps, fast stairs, a brisk walk.", "Burns off the adrenaline that keeps the alarm ringing."),
        Triple("P — Paced breathing", "Exhale longer than you inhale: in for 4, out for 6–8, for a minute.", "Long exhales switch on the calming branch of your nervous system."),
        Triple("P — Paired muscle relaxation", "Tense a muscle group as you inhale, release completely as you exhale.", "Teaches the body the difference between holding and letting go."),
    )
    var idx by remember { mutableIntStateOf(0) }
    val (heading, how, why) = steps[idx]
    SubPage("For very intense moments", "TIPP", onBack) {
        Text(
            "A DBT skill for when emotion is at a 9 or 10 and thinking feels impossible. Work the four steps in order.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted,
        )
        SectionCard {
            Text(heading, style = MaterialTheme.typography.titleMedium, color = Cyan)
            Text(how, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
            Text(why, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = idx > 0, onClick = { idx-- }) { Text("Previous", color = TextMuted) }
            Text("${idx + 1} of ${steps.size}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            if (idx < steps.size - 1) {
                TextButton(onClick = { idx++ }) { Text("Next", color = Periwinkle) }
            } else {
                TextButton(onClick = onBack) { Text("Done — steadier", color = Ok) }
            }
        }
        Text("If the urge to hurt yourself is present, please also reach out — Urgent support lives in the You tab.",
            style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}
