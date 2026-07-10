package com.cerebro.app.ui.screens

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.cerebro.app.net.Api
import com.cerebro.app.net.Session
import com.cerebro.app.ui.theme.LineStroke
import com.cerebro.app.ui.theme.Night
import com.cerebro.app.ui.theme.Periwinkle
import com.cerebro.app.ui.theme.TextMuted
import com.cerebro.app.ui.theme.TextPrimary
import com.cerebro.app.ui.theme.TextSoft
import kotlinx.coroutines.launch
import org.json.JSONArray

private enum class JournalMode { Home, Entry, History, Private }

internal data class Entry(val title: String, val body: String, val date: String, val risk: String)

internal fun parseEntries(rows: JSONArray): List<Entry> =
    (0 until rows.length()).map { i ->
        val e = rows.getJSONObject(i)
        Entry(
            e.getString("title"),
            e.getString("body"),
            e.getString("created_at").take(10),
            e.optString("risk_level", "none"),
        )
    }

private fun requestJournalUnlock(activity: FragmentActivity?, onResult: (Boolean) -> Unit) {
    if (activity == null) { onResult(true); return }
    val auths = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    if (BiometricManager.from(activity).canAuthenticate(auths) != BiometricManager.BIOMETRIC_SUCCESS) {
        onResult(true); return
    }
    BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
        },
    ).authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock journal")
            .setSubtitle("Private entries stay behind your screen lock")
            .setAllowedAuthenticators(auths)
            .build(),
    )
}

internal fun filterEntries(entries: List<Entry>, query: String): List<Entry> {
    val q = query.trim()
    if (q.isEmpty()) return entries
    return entries.filter { it.title.contains(q, ignoreCase = true) || it.body.contains(q, ignoreCase = true) }
}

@Composable
fun JournalScreen() {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(listOf<Entry>()) }
    var showSupport by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(JournalMode.Home) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lockOn = Session.prefGet("journal_locked") == "true"
    var unlocked by remember { mutableStateOf(!lockOn) }

    LaunchedEffect(Unit) { runCatching { entries = parseEntries(Api.journal()) } }

    if (!unlocked) {
        JournalSurface {
            JournalHeader()
            JournalActionRow(
                icon = Icons.Outlined.Lock,
                title = "Journal is locked",
                subtitle = "Unlock with your screen lock",
                onClick = { requestJournalUnlock(activity) { ok -> if (ok) unlocked = true } },
            )
        }
        return
    }

    when (mode) {
        JournalMode.Entry -> {
            JournalEntryScreen(
                title = title,
                body = body,
                busy = busy,
                onTitle = { title = it },
                onBody = { body = it },
                onBack = { mode = JournalMode.Home },
                onSave = {
                    busy = true
                    status = null
                    scope.launch {
                        try {
                            val saved = Api.createJournal(title.ifBlank { "Journal entry" }.trim(), body.trim())
                            showSupport = saved.optString("risk_level", "none") !in listOf("none", "low")
                            title = ""
                            body = ""
                            mode = JournalMode.Home
                            status = "Saved - private to you."
                            runCatching { entries = parseEntries(Api.journal()) }
                        } catch (e: Exception) {
                            status = e.message ?: "Couldn't save."
                        } finally {
                            busy = false
                        }
                    }
                },
            )
            return
        }
        JournalMode.History -> {
            JournalHistoryScreen(entries = entries, onBack = { mode = JournalMode.Home })
            return
        }
        JournalMode.Private -> {
            JournalPrivateScreen(onBack = { mode = JournalMode.Home })
            return
        }
        JournalMode.Home -> Unit
    }

    JournalSurface {
        JournalHeader()
        JournalHero {
            title = "Name the worry"
            body = ""
            mode = JournalMode.Entry
        }

        JournalActionRow(Icons.Outlined.Edit, "New entry", "Private writing with consent") {
            title = "Name the worry"
            body = ""
            mode = JournalMode.Entry
        }
        JournalActionRow(Icons.Outlined.History, "History", "Past entries and tags") {
            mode = JournalMode.History
        }
        JournalActionRow(Icons.Outlined.Lock, "Private mode", "Choose what AI can read") {
            mode = JournalMode.Private
        }

        if (showSupport) {
            JournalPanel {
                Text("You don't have to carry this alone", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("That entry sounded heavy. If things feel urgent, real people can help right now.", color = TextSoft)
            }
        }

        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
    }
}

private fun toggleJournalLock(@Suppress("UNUSED_PARAMETER") context: Context) {
    val next = Session.prefGet("journal_locked") != "true"
    Session.prefPut("journal_locked", next.toString())
}

@Composable
private fun JournalEntryScreen(
    title: String,
    body: String,
    busy: Boolean,
    onTitle: (String) -> Unit,
    onBody: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    JournalSurface {
        JournalDetailHeader("PRIVATE WRITING WITH CONSENT", "Journal Entry", onBack)
        Text(
            "\"  What would you tell a friend who had your exact\nday?",
            style = MaterialTheme.typography.titleMedium,
            color = TextSoft,
        )
        JournalFreeTextField(value = body, onValueChange = onBody)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            listOf("Work", "Sleep", "Gratitude", "Calm").forEach { JournalTag(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            listOf("Anxious", "Sad", "Hopeful", "Tired").forEach { JournalTag(it) }
        }
        JournalSaveButton(
            text = if (busy) "Saving..." else "Save entry",
            enabled = body.isNotBlank() && !busy,
            onClick = onSave,
        )
        Spacer(Modifier.height(220.dp))
    }
}

@Composable
private fun JournalFreeTextField(value: String, onValueChange: (String) -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), shape)
            .padding(horizontal = 16.dp, vertical = 17.dp),
        textStyle = TextStyle(
            color = TextPrimary,
            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            fontWeight = FontWeight.Medium,
        ),
        decorationBox = { innerTextField ->
            Box(Modifier.fillMaxSize()) {
                if (value.isBlank()) {
                    Text("Write freely...", style = MaterialTheme.typography.bodyLarge, color = TextMuted)
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun JournalSaveButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(10.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                    .background(Night),
            )
            Text(text, style = MaterialTheme.typography.titleSmall, color = Night, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun JournalHistoryScreen(entries: List<Entry>, onBack: () -> Unit) {
    val sample = entries.ifEmpty {
        listOf(
            Entry("Release the day", "", "Today", "Calm"),
            Entry("Meeting went better than feared", "", "Yesterday", "Work"),
            Entry("Couldn't switch off", "", "Tue", "Anxious"),
            Entry("Grateful for a slow morning", "", "Mon", "Gratitude"),
        )
    }
    JournalSurface {
        JournalDetailHeader("PAST ENTRIES AND TAGS", "History", onBack)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("12", "entries", Modifier.weight(1f))
            StatTile("4", "day streak", Modifier.weight(1f))
            StatTile("Calm", "top mood", Modifier.weight(1f))
        }
        sample.take(8).forEachIndexed { index, entry ->
            val colors = listOf(Color(0xFF8A75F6), Color(0xFF66C7A4), Color(0xFFE09968), Color(0xFFD9D19C))
            HistoryEntryRow(
                day = entry.date,
                title = entry.title,
                tag = entry.risk.takeIf { it !in listOf("none", "low") } ?: when (index) {
                    0 -> "Calm"
                    1 -> "Work"
                    2 -> "Anxious"
                    else -> "Gratitude"
                },
                accent = colors[index % colors.size],
            )
        }
    }
}

@Composable
private fun JournalPrivateScreen(onBack: () -> Unit) {
    var mood by remember { mutableStateOf(true) }
    var memory by remember { mutableStateOf(true) }
    var voice by remember { mutableStateOf(false) }
    JournalSurface {
        JournalDetailHeader("CHOOSE WHAT AI CAN READ", "Private mode", onBack)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = TextPrimary)
            Text(
                "When on, entries stay on-device and are never\nused to personalize AI.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSoft,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, LineStroke, RoundedCornerShape(20.dp)),
        ) {
            PrivacyRow("Mood history", "Used for insights", mood) { mood = it }
            PrivacyDivider()
            PrivacyRow("AI memory", "Goals and preferences", memory) { memory = it }
            PrivacyDivider()
            PrivacyRow("Voice storage", "Off by default", voice) { voice = it }
        }
        Text(
            "You can export or delete everything at any time from the\nPattern dashboard.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )
    }
}

@Composable
private fun JournalDetailHeader(eyebrow: String, title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Text("<", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        }
        Column {
            Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.35f))
            Text(title, style = MaterialTheme.typography.displaySmall, color = TextPrimary)
        }
    }
}

@Composable
private fun JournalTag(label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 15.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = TextSoft)
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(74.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF7F6ED2).copy(alpha = 0.60f), Color(0xFF50458B).copy(alpha = 0.48f))))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSoft)
    }
}

@Composable
private fun HistoryEntryRow(day: String, title: String, tag: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF51488D).copy(alpha = 0.75f), Color(0xFF272047).copy(alpha = 0.78f))))
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(42.dp).clip(RoundedCornerShape(3.dp)).background(accent))
        Column(Modifier.weight(1f)) {
            Text(day, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text(tag, style = MaterialTheme.typography.labelMedium, color = Night)
        }
    }
}

@Composable
private fun PrivacyRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSoft)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun PrivacyDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
}

@Composable
private fun JournalSurface(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF6B5BBC), Color(0xFF241956), Color(0xFF120B2B), Color(0xFF35183D)),
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 18.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun JournalHeader() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column {
            Text("JOURNAL HUB", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.35f))
            Text("Journal", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
        }
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun JournalHero(onWrite: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape),
    ) {
        AsyncImage(model = HeroImg.journal, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.58f)),
                    ),
                ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("FOR A TENSE DAY", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Name the worry", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Text(
                    "Write the thought that keeps circling - then one\ntruer thought beside it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSoft,
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .clickable { onWrite() }
                        .padding(horizontal = 19.dp, vertical = 11.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Night, modifier = Modifier.size(16.dp))
                        Text("Write", style = MaterialTheme.typography.labelLarge, color = Night)
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF4C4486).copy(alpha = 0.82f), Color(0xFF272047).copy(alpha = 0.88f)),
                ),
            )
            .border(1.dp, LineStroke, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Periwinkle.copy(alpha = 0.28f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSoft)
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun JournalPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, LineStroke, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}
