package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.LineStroke
import kotlinx.coroutines.launch
import com.cerebrozen.app.ui.theme.Warm
import com.cerebrozen.app.ui.theme.WarmSoft
import com.cerebrozen.app.ui.theme.TextMuted


/** The first level under Explore's Calm now card: broad practice families,
 * before the user commits to a particular exercise. */
@Composable
fun PracticeLibraryScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFFFFCF8), Color(0xFFF6F0EA))),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().height(76.dp).background(CardFill.copy(alpha = .94f))
                .border(.5.dp, LineStroke.copy(alpha = .65f)).padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            CircleAction(Color(0xFFF3EDF7), onBack) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = Color(0xFF6E376B), modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    "Practice library", maxLines = 1,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = serif, fontWeight = FontWeight.SemiBold, lineHeight = 25.sp,
                    ),
                    color = Color(0xFF292323),
                )
                Text(
                    "Five clear families", maxLines = 1,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp),
                    color = Color(0xFF6F6666),
                )
            }
            CircleAction(Color(0xFFFFE8E6), { onOpen("crisis") }) {
                Icon(Icons.Outlined.WarningAmber, "Urgent support", tint = Color(0xFFE34B4B), modifier = Modifier.size(23.dp))
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp).padding(top = 15.dp, bottom = 20.dp),
        ) {
            Text(
                "FIVE PRACTICE FAMILIES",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = .7.sp),
                color = Color(0xFFB13D57),
            )
            Text(
                "Choose\nby what\nyou need.",
                modifier = Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = serif, fontWeight = FontWeight.Normal, fontSize = 43.sp, lineHeight = 39.sp,
                ),
                color = Color(0xFF292323),
            )
            Text(
                "Every family is short, offline-friendly and stops\nwhenever you do.",
                modifier = Modifier.padding(top = 14.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp),
                color = Color(0xFF655C5C),
            )
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                PracticeFamilyRow(Icons.Outlined.Spa, "Breathe", "Slow and regulate", Color(0xFFE5F0E9), Color(0xFF4B775E)) { onOpen("breathing-intro") }
                PracticeFamilyRow(Icons.Outlined.HealthAndSafety, "Ground", "Return attention to your surroundings", Color(0xFFF9E7EC), Color(0xFFC75270)) { onOpen("groundingintro") }
                PracticeFamilyRow(Icons.Outlined.FavoriteBorder, "Reset your body", "TIPP and sensory skills", Color(0xFFF9E7EC), Color(0xFFC75270)) { onOpen("tipp") }
                PracticeFamilyRow(Icons.Outlined.Psychology, "Work with thoughts", "Guided reframing", Color(0xFFF9E7EC), Color(0xFFC75270)) { onOpen("cbt") }
                PracticeFamilyRow(Icons.Outlined.Bedtime, "Prepare for sleep", "Body scan and imagery", Color(0xFFE5F0E9), Color(0xFF4B775E)) { onOpen("bodyscan") }
                PracticeFamilyRow(Icons.Outlined.AutoAwesome, "Positive reflection", "Gratitude without pressure", Color(0xFFF9E7EC), Color(0xFFC75270)) { onOpen("gratitude") }
            }
        }
    }
}

@Composable
fun PracticeBreathingScreen(onBack: () -> Unit, onUrgent: () -> Unit, onBegin: () -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    var chime by remember { mutableStateOf(true) }
    var haptics by remember { mutableStateOf(true) }
    var keepAwake by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().background(Color(0xFFFBF7F1))) {
        Row(
            Modifier.fillMaxWidth().height(76.dp).background(CardFill.copy(alpha = .94f))
                .border(.5.dp, LineStroke.copy(alpha = .65f)).padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            CircleAction(Color(0xFFF3EDF7), onBack) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = Color(0xFF6E376B), modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Breathing", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = serif), color = Color(0xFF292323))
                Text("Prepare session", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6F6666))
            }
            CircleAction(Color(0xFFFFE8E6), onUrgent) {
                Icon(Icons.Outlined.WarningAmber, "Urgent support", tint = Color(0xFFE34B4B), modifier = Modifier.size(23.dp))
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 15.dp),
        ) {
            Text("BREATHING", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = .7.sp), color = Color(0xFFB13D57))
            Text(
                "In for four,\nout for six.", modifier = Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.displayLarge.copy(fontFamily = serif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 39.sp),
                color = Color(0xFF292323),
            )
            Text(
                "A longer exhale may help settle physical activation. Stop if you feel dizzy or uncomfortable.",
                modifier = Modifier.padding(top = 13.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp), color = Color(0xFF655C5C),
            )
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)).background(Color(0xFFF5EDF7)).padding(horizontal = 18.dp),
            ) {
                BreathingSetting("Soft chime", "A quiet cue at each transition.", chime) { chime = it }
                BreathingSetting("Haptics", "Gentle vibration at phase changes.", haptics) { haptics = it }
                BreathingSetting("Keep screen awake", "Prevents dimming during this session.", keepAwake, showDivider = false) { keepAwake = it }
            }
            Box(
                Modifier.fillMaxWidth().padding(top = 11.dp).height(49.dp).clip(RoundedCornerShape(25.dp))
                    .background(Color(0xFF854078)).clickable(onClick = onBegin),
                contentAlignment = Alignment.Center,
            ) {
                Text("Begin 2-minute breathing", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun PracticeTippScreen(onBack: () -> Unit, onUrgent: () -> Unit, onTryStep: () -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    Column(Modifier.fillMaxSize().background(Color(0xFFFBF7F1))) {
        PracticeHeader("TIPP", "Body reset", serif, onBack, onUrgent)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 23.dp, vertical = 15.dp),
        ) {
            Text("TIPP SKILL", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = .7.sp), color = Color(0xFFB13D57))
            Text(
                "Reset\nintense body\nactivation.", modifier = Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.displayLarge.copy(fontFamily = serif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 39.sp),
                color = Color(0xFF292323),
            )
            Text(
                "Choose one safe step. Avoid cold exposure if a health condition makes it unsafe.",
                modifier = Modifier.padding(top = 13.dp, bottom = 17.dp),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp), color = Color(0xFF655C5C),
            )
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                TippStep(1, "Temperature", "Use cool water on your face or hold a cool cloth.", onTryStep)
                TippStep(2, "Intense movement", "Move vigorously for 30–60 seconds if safe.", onTryStep)
                TippStep(3, "Paced breathing", "Make the exhale longer than the inhale.", onTryStep)
                TippStep(4, "Paired relaxation", "Tense, then gently release muscle groups.", onTryStep)
            }
        }
    }
}

@Composable
private fun TippStep(number: Int, title: String, subtitle: String, onTryStep: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF6C2768)), contentAlignment = Alignment.Center) {
            Text(number.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Column(
            Modifier.weight(1f).height(136.dp).clip(RoundedCornerShape(25.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFF7EDF7), Color(0xFFF3EBF7))))
                .padding(horizontal = 19.dp, vertical = 17.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF292323))
            Text(subtitle, modifier = Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = Color(0xFF74666F))
            Text(
                "Try this step →", modifier = Modifier.padding(top = 22.dp).clickable(onClick = onTryStep),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF6C2768),
            )
        }
    }
}

@Composable
fun NoticeChangeScreen(onBack: () -> Unit, onUrgent: () -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    Column(Modifier.fillMaxSize().background(Color(0xFFFBF7F1))) {
        PracticeHeader("Notice the change", "Private reflection", serif, onBack, onUrgent)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 19.dp, vertical = 15.dp),
        ) {
            Text("PRIVATE REFLECTION", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = .7.sp), color = Color(0xFFB13D57))
            Text(
                "What do you\nnotice now?", modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.displayLarge.copy(fontFamily = serif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 39.sp),
                color = Color(0xFF292323),
            )
            Text(
                "This is not a test. “No change” is useful information too.",
                modifier = Modifier.padding(top = 13.dp, bottom = 17.dp),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp), color = Color(0xFF655C5C),
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NoticeChoice("↘", "A little more settled")
                NoticeChoice("—", "About the same")
                NoticeChoice("↗", "More activated", "CereBro will suggest a different next step")
                NoticeChoice("…", "Not sure")
            }
        }
    }
}

@Composable
fun UntangleThoughtScreen(onBack: () -> Unit, onUrgent: () -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    var thought by remember { mutableStateOf("") }
    var supports by remember { mutableStateOf("") }
    var against by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Color(0xFFFBF7F1))) {
        PracticeHeader("Untangle a thought", "Guided reflection", serif, onBack, onUrgent)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 23.dp, vertical = 15.dp),
        ) {
            Text("UNTANGLE A THOUGHT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = .7.sp), color = Color(0xFFB13D57))
            Text(
                "Separate the\nthought from\nthe facts.", modifier = Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.displayLarge.copy(fontFamily = serif, fontWeight = FontWeight.Normal, fontSize = 39.sp, lineHeight = 38.sp),
                color = Color(0xFF292323),
            )
            Text(
                "Write freely. This stays on this device unless you choose to save it elsewhere.",
                modifier = Modifier.padding(top = 13.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp), color = Color(0xFF655C5C),
            )
            ThoughtField("What is the thought?", "I am going to fail at everything...", thought) { thought = it }
            ThoughtField("What facts support it?", "Only what you can verify...", supports) { supports = it }
            ThoughtField("What facts do not support it?", "Evidence that the thought may be incomplete...", against) { against = it }
            Box(
                Modifier.fillMaxWidth().padding(top = 4.dp).height(51.dp).clip(RoundedCornerShape(25.dp)).background(Color(0xFF854078)),
                contentAlignment = Alignment.Center,
            ) { Text("Create a balanced statement", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White) }
        }
    }
}

@Composable
private fun ThoughtField(label: String, placeholder: String, value: String, onValueChange: (String) -> Unit) {
    Text(label, modifier = Modifier.padding(start = 3.dp, bottom = 6.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFF756D73))
    OutlinedTextField(
        value = value, onValueChange = onValueChange, placeholder = { Text(placeholder, color = Color(0xFF817980)) },
        modifier = Modifier.fillMaxWidth().height(78.dp),
        shape = RoundedCornerShape(18.dp), minLines = 2, maxLines = 3,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFFFFFDFC), unfocusedContainerColor = Color(0xFFFFFDFC),
            focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
        ),
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
fun PracticeBodyScanScreen(onBack: () -> Unit, onUrgent: () -> Unit, onTranscript: () -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    Column(Modifier.fillMaxSize().background(Color(0xFFFBF7F1))) {
        PracticeHeader("Body scan", "Audio practice", serif, onBack, onUrgent)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 30.dp, vertical = 15.dp),
        ) {
            Text("BODY SCAN", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = .7.sp), color = Color(0xFFB13D57))
            Text(
                "Notice\nwithout\nneeding\nto change.", modifier = Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.displayLarge.copy(fontFamily = serif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 39.sp),
                color = Color(0xFF292323),
            )
            // This screen is a static layout: no timer runs, no audio plays and
            // nothing is cached, so "12 minutes · Audio and text · Available
            // offline" described a feature that isn't here. The body scan that
            // DOES work offline is ui/offline/BodyScanScreen, which this route
            // displaced — see docs/TODO.md. Restore the strapline when the
            // mechanism behind it is restored.
            Spacer(Modifier.height(13.dp))
            Column(
                Modifier.fillMaxWidth().height(296.dp).shadow(8.dp, RoundedCornerShape(25.dp), ambientColor = Color.Black.copy(alpha = .06f))
                    .clip(RoundedCornerShape(25.dp)).background(Color(0xFFFFFDFC)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.padding(top = 42.dp).size(120.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 10.dp.toPx()
                        drawCircle(Color(0xFFF1E9F3), style = Stroke(stroke))
                        drawArc(
                            color = Color(0xFF6C2768),
                            startAngle = -90f,
                            sweepAngle = 180f,
                            useCenter = false,
                            style = Stroke(stroke),
                        )
                    }
                    Text("2:41", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF201C20))
                }
                Text(
                    "Bring attention to your jaw. Notice pressure, warmth or\nmovement.",
                    modifier = Modifier.padding(top = 48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF74666F),
                )
            }
            Spacer(Modifier.height(12.dp))
            PracticeButton("Play body scan", filled = true) { }
            Spacer(Modifier.height(3.dp))
            PracticeButton("View transcript", filled = false, onClick = onTranscript)
        }
    }
}

@Composable
private fun PracticeButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(49.dp).clip(RoundedCornerShape(25.dp))
            .background(if (filled) Color(0xFF854078) else Color(0xFFFFFDFC))
            .then(if (filled) Modifier else Modifier.border(1.dp, Color(0xFFE0D6D0), RoundedCornerShape(25.dp)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (filled) Color.White else Color(0xFF6C2768)) }
}

@Composable
fun BodyScanContentDetailScreen(onBack: () -> Unit, onUrgent: () -> Unit, onBegin: () -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    Column(Modifier.fillMaxSize().background(Color(0xFFFBF7F1))) {
        PracticeHeader("Content detail", "Purpose, format and access", serif, onBack, onUrgent)
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 15.dp)) {
            Text("PRACTICE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = .7.sp), color = Color(0xFFB13D57))
            Text("Body scan", modifier = Modifier.padding(top = 7.dp), style = MaterialTheme.typography.displayLarge.copy(fontFamily = serif, fontWeight = FontWeight.Normal, fontSize = 40.sp), color = Color(0xFF292323))
            Text("A carefully structured wellness experience with a clear purpose, duration and privacy boundary.", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp), color = Color(0xFF655C5C))
            Column(Modifier.fillMaxWidth().padding(top = 17.dp).clip(RoundedCornerShape(25.dp)).background(Color(0xFFF5EDF7)).padding(horizontal = 18.dp)) {
                DetailRow("Estimated time", "12 min")
                DetailRow("Offline", "Available")
                DetailRow("Audio", "Included", divider = false)
            }
            Spacer(Modifier.height(12.dp))
            PracticeButton("Begin", filled = true, onClick = onBegin)
            Spacer(Modifier.height(3.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { PracticeButton("Remove favourite", false) { } }
                Box(Modifier.weight(1f)) { PracticeButton("Download", false) { } }
            }
        }
    }
}

@Composable
fun GratitudeReflectionScreen(onBack: () -> Unit, onUrgent: () -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    var reflection by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(Color(0xFFFBF7F1))) {
        PracticeHeader("Gratitude", "Positive reflection", serif, onBack, onUrgent)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 15.dp),
        ) {
            Text(
                "GRATITUDE REFLECTION",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = .7.sp),
                color = Color(0xFFB13D57),
            )
            Text(
                "Notice one\nthing—not\neverything.",
                modifier = Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = serif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 39.sp,
                ),
                color = Color(0xFF292323),
            )
            Text(
                "This is not about forcing positivity. A small neutral or supportive detail is enough.",
                modifier = Modifier.padding(top = 13.dp, bottom = 17.dp),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
                color = Color(0xFF655C5C),
            )
            OutlinedTextField(
                value = reflection,
                onValueChange = { reflection = it; status = null },
                placeholder = { Text("One thing that made today slightly easier...", color = Color(0xFF817980)) },
                modifier = Modifier.fillMaxWidth().height(78.dp),
                shape = RoundedCornerShape(18.dp),
                minLines = 2,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFFFFDFC), unfocusedContainerColor = Color(0xFFFFFDFC),
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                ),
            )
            // The reflection is a real journal entry, not a local flag: this
            // button used to set `saved = true` and relabel itself "Saved
            // privately" while the text was never written anywhere, so the
            // promise was false and the writing was lost on navigation.
            val entryTitle = stringResource(R.string.practice_gratitude_entry_title)
            val savedStatus = stringResource(R.string.journal_saved)
            val queuedStatus = stringResource(R.string.practice_gratitude_queued)
            val saveFailed = stringResource(R.string.common_save_failed)
            val enabled = !busy && reflection.isNotBlank()
            Box(
                Modifier.fillMaxWidth().padding(top = 17.dp).height(49.dp).clip(RoundedCornerShape(25.dp))
                    .background(if (enabled) Color(0xFF854078) else Color(0xFFB89AB2))
                    .clickable(enabled = enabled) {
                        busy = true; status = null
                        scope.launch {
                            try {
                                // Null = queued by the outbox with no connection.
                                // Saying "saved" flatly would re-tell the old lie
                                // in a quieter way, so the two outcomes read
                                // differently.
                                val entry = Api.createJournal(entryTitle, reflection.trim())
                                status = if (entry != null) savedStatus else queuedStatus
                                reflection = ""
                            } catch (e: Exception) {
                                status = e.message ?: saveFailed
                            } finally {
                                busy = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (busy) stringResource(R.string.common_one_moment)
                    else stringResource(R.string.practice_gratitude_save),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White,
                )
            }
            status?.let {
                Text(
                    it, modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFF655C5C),
                )
            }
            Text(
                stringResource(R.string.practice_gratitude_skip),
                modifier = Modifier.padding(start = 7.dp, top = 20.dp).clickable(onClick = onBack),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF5F255D),
            )
        }
    }
}

@Composable
fun UrgentSupportScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    val region by rememberCrisisRegion()
    val regional = crisisLinesFor(region)
    val emergency = regional.firstOrNull { it.target in setOf("112", "911", "999", "000", "111") } ?: regional.first()
    val mental = primaryCrisisLine(region)
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(Color(0xFFFBF7F1))) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).background(CardFill.copy(alpha = .96f)).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFF3EDF7)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = Color(0xFFA52F50), modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Urgent support", style = MaterialTheme.typography.titleLarge.copy(fontFamily = serif), color = Color(0xFF292323))
                Text("Human help comes first", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6F6666))
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFFFFE4E1)).padding(18.dp)) {
                Text("If there is immediate danger, call ${emergency.target} now.", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF292323))
                Text("CereBro is not an emergency service and cannot monitor your safety.", style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp), color = Color(0xFF542D34))
            }
            Spacer(Modifier.height(6.dp))
            // A badge is a CLAIM. This one used to render unconditionally, so
            // every region wore "Verified" — the exact bug the ref/ audit
            // found (Indian numbers badged Verified for every country). India
            // is the region whose numbers we have actually checked against a
            // named source; everywhere else says so plainly rather than
            // borrowing India's badge.
            val verifiedRegion = region == "IN"
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${stringResource(regionLabelRes(region)).uppercase()} · ENGLISH", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = .8.sp), color = Color(0xFFB13D57))
                Box(
                    Modifier.clip(CircleShape)
                        .background(if (verifiedRegion) Color(0xFFE7F1E8) else WarmSoft)
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                ) {
                    Text(
                        stringResource(if (verifiedRegion) R.string.crisis_verified else R.string.crisis_not_verified),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = if (verifiedRegion) Color(0xFF3D684D) else Warm,
                    )
                }
            }
            if (!verifiedRegion) {
                Text(
                    stringResource(R.string.crisis_unverified_note),
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp), color = TextMuted,
                )
            }
            Text("Human help\ncomes first.", style = MaterialTheme.typography.displayLarge.copy(fontFamily = serif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 39.sp), color = Color(0xFF292323))
            // "Reach VERIFIED human support" was a third copy of the same
            // unearned claim — true in India, not everywhere the screen renders.
            Text(
                if (verifiedRegion) "Reach verified human support quickly with offline fallbacks."
                else "Reach human support quickly, with offline fallbacks.",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp), color = Color(0xFF655C5C),
            )
            UrgentAction("Call emergency services", "${emergency.target} · police, fire, health and other emergencies", Icons.Outlined.Call, primary = true) { openSupportTarget(context, emergency.target) }
            // Outside India this said "· verified mental-health support" — the
            // same unearned claim as the badge, on the line carrying the number
            // someone would actually dial.
            UrgentAction(
                "Call ${stringResource(mental.nameRes)}",
                mental.target + if (verifiedRegion) " · free 24/7 mental-health support in 20 languages"
                else " · " + stringResource(R.string.crisis_mental_generic),
                Icons.Outlined.FavoriteBorder,
            ) { openSupportTarget(context, mental.target) }
            UrgentAction("Contact my trusted person", "Someone you selected; CereBro never contacts them automatically", Icons.Outlined.FavoriteBorder) { onOpen("trustedcontact") }
            UrgentAction("I cannot call", "See text, in-person and grounding alternatives", Icons.Outlined.Spa, sage = true) { onOpen("crisisgrounding") }
            androidx.compose.material3.HorizontalDivider(color = LineStroke.copy(alpha = .7f))
            Text("Cached support details", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF6F6666))
            Text("Sources: Government and verified regional support directories. When offline, confirm details if you can because services may change.", style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp), color = Color(0xFF776E6E))
            Text("Change region", modifier = Modifier.padding(7.dp).clickable { onOpen("crisisregion") }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF6C2768))
            Box(
                Modifier.fillMaxWidth().height(49.dp).clip(RoundedCornerShape(25.dp)).background(Color(0xFFFFFDFC))
                    .border(1.dp, LineStroke, RoundedCornerShape(25.dp)).clickable { onOpen("safetyplan") },
                contentAlignment = Alignment.Center,
            ) { Text("Open my safety plan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF6C2768)) }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun UrgentAction(title: String, detail: String, icon: ImageVector, primary: Boolean = false, sage: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (primary) 86.dp else 94.dp)
            .shadow(7.dp, shape, ambientColor = Color.Black.copy(alpha = .06f)).clip(shape)
            .background(if (primary) Color(0xFFD93B36) else Color(0xFFFFFDFC))
            .then(if (primary) Modifier else Modifier.border(.5.dp, LineStroke.copy(alpha = .7f), shape))
            .clickable(onClick = onClick).padding(horizontal = 17.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(43.dp).clip(CircleShape).background(if (primary) Color(0xFFFFE9E7) else if (sage) Color(0xFFE6F0E8) else Color(0xFFFFE8E9)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (primary) Color(0xFFE34B4B) else if (sage) Color(0xFF4B775E) else Color(0xFFD45369), modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (primary) Color.White else Color(0xFF292323))
            Text(detail, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp), color = if (primary) Color.White.copy(alpha = .92f) else Color(0xFF74666F))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, divider: Boolean = true) {
    Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4F474D))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF292323))
    }
}

@Composable
private fun NoticeChoice(symbol: String, title: String, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().height(if (subtitle == null) 72.dp else 82.dp)
            .shadow(7.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = .06f))
            .clip(RoundedCornerShape(22.dp)).background(Color(0xFFFFFDFC)).padding(horizontal = 27.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(21.dp),
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium, color = Color(0xFF6C2768))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF292323))
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF74666F)) }
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = Color(0xFF955386))
    }
}

@Composable
private fun PracticeHeader(
    title: String,
    subtitle: String,
    serif: FontFamily,
    onBack: () -> Unit,
    onUrgent: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(76.dp).background(CardFill.copy(alpha = .94f))
            .border(.5.dp, LineStroke.copy(alpha = .65f)).padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        CircleAction(Color(0xFFF3EDF7), onBack) {
            Icon(Icons.Outlined.ArrowBack, "Back", tint = Color(0xFF6E376B), modifier = Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = serif), color = Color(0xFF292323))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6F6666))
        }
        CircleAction(Color(0xFFFFE8E6), onUrgent) {
            Icon(Icons.Outlined.WarningAmber, "Urgent support", tint = Color(0xFFE34B4B), modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun BreathingSetting(
    title: String,
    subtitle: String,
    checked: Boolean,
    showDivider: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(73.dp).then(if (showDivider) Modifier.border(0.dp, Color.Transparent) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF292323))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF8A637E))
        }
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF71306C)),
        )
    }
}

@Composable
private fun CircleAction(fill: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(47.dp).clip(CircleShape).background(fill).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun PracticeFamilyRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconFill: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(77.dp)
            .shadow(9.dp, RoundedCornerShape(23.dp), ambientColor = Color.Black.copy(alpha = .08f))
            .clip(RoundedCornerShape(23.dp)).background(Color(0xFFFFFDFC))
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(Modifier.size(43.dp).clip(CircleShape).background(iconFill), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF292323))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF776E6E), maxLines = 1)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = Color(0xFF955386))
    }
}
