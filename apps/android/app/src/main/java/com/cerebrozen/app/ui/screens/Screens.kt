package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.CereBroTheme
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import androidx.compose.material3.MaterialTheme

/** Common page frame: uppercase eyebrow + serif title + scrolling content. */
@Composable
private fun ScreenScaffold(
    eyebrow: String,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        Text(title, style = MaterialTheme.typography.displaySmall, color = TextPrimary)
        content()
    }
}

/** A glass card matching the iOS design system. */
@Composable
private fun GlassCard(title: String, body: String) {
    Surface(
        color = CardFill,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, LineStroke, RoundedCornerShape(20.dp)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
    }
}

@Composable
fun HomeScreen() = ScreenScaffold("Today · your goal", "Good day,\nfriend") {
    GlassCard("How are you, really?", "A 20-second check-in shapes today's plan.")
    GlassCard("Your streak", "Show up once a day — gentle, no pressure.")
    GlassCard("For now", "A calm reset tuned to the time of day.")
}

@Composable
fun SleepScreen() = ScreenScaffold("Premium sleep hub", "Sleep") {
    GlassCard("Layered soundscapes", "Blend rain, ocean, wind and a soft drone.")
    GlassCard("Sleep-safe timer", "Fades out on its own so you can drift off.")
}

@Composable
fun TalkScreen() = ScreenScaffold("AI voice companion", "Your voice companion") {
    GlassCard("Listens, then talks it through", "A warm guide — never a diagnosis, always a next step.")
    GlassCard("Text mode", "Prefer typing? Chat is always there.")
}

@Composable
fun JournalScreen() = ScreenScaffold("Journal hub", "Journal") {
    GlassCard("Release the day", "Guided prompts and emotion tags, private to you.")
    GlassCard("Locked if you like", "Optional biometric lock keeps reflections yours.")
}

@Composable
fun YouScreen() = ScreenScaffold("Settings and support", "You") {
    GlassCard("Privacy & memory", "Control what CereBro remembers. Export or delete anytime.")
    GlassCard("Urgent support", "Locale-aware crisis resources, always a tap away.")
    androidx.compose.material3.TextButton(onClick = { com.cerebrozen.app.net.Session.signOut() }) {
        Text("Sign out", color = TextMuted)
    }
    Text(
        "Wellness support, not emergency care.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Preview
@Composable
private fun HomePreview() {
    CereBroTheme { HomeScreen() }
}
