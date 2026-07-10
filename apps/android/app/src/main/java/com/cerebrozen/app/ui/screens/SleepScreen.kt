package com.cerebro.app.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cerebro.app.net.Api
import com.cerebro.app.ui.theme.Ink
import com.cerebro.app.ui.theme.Periwinkle
import com.cerebro.app.ui.theme.TextMuted
import com.cerebro.app.ui.theme.TextPrimary
import com.cerebro.app.ui.theme.TextSoft
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.LocalDate
import java.util.Locale

private val QUALITY_WORDS = listOf("Rough", "Poor", "Okay", "Good", "Rested")

internal fun minutesToLabel(total: Int): String = "%dh %02dm".format(total / 60, total % 60)

internal fun hhmm(minutes: Int): String {
    val m = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60)
    return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
}

private fun timeLabel(minutes: Int): String {
    val m = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60)
    val hour24 = m / 60
    val minute = m % 60
    val suffix = if (hour24 < 12) "AM" else "PM"
    val hour12 = (hour24 % 12).let { if (it == 0) 12 else it }
    return String.format(Locale.US, "%d:%02d %s", hour12, minute, suffix)
}

internal data class Night(val date: String, val duration: Int, val quality: Int)

internal fun parseNights(rows: JSONArray): List<Night> =
    (0 until rows.length()).map { i ->
        val n = rows.getJSONObject(i)
        Night(n.getString("date"), n.optInt("duration_min"), n.optInt("quality"))
    }

@Composable
fun SleepScreen(onOpen: (String) -> Unit = {}, onBack: () -> Unit = {}) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF201A55), Color(0xFF20164B), Color(0xFF120820)))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(376.dp),
            ) {
                AsyncImage(
                    model = HeroImg.sleep,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.58f)))),
                )
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = pageHorizontalPadding(), vertical = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("PREMIUM SLEEP", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.70f))
                        Text("Sleep", style = MaterialTheme.typography.displayLarge, color = TextPrimary)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.22f))
                                .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 17.dp, vertical = 8.dp),
                        ) {
                            Text("TONIGHT", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                        }
                        Text("Rain over quiet hills", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
                        Text("A calming sleep story to slow a racing mind.", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White)
                                .clickable { onOpen("sounds") }
                                .padding(horizontal = 22.dp, vertical = 12.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("▶", style = MaterialTheme.typography.titleSmall, color = Color(0xFF2B214E))
                                Text("Play", style = MaterialTheme.typography.titleMedium, color = Color(0xFF2B214E), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Column(
                Modifier.padding(horizontal = pageHorizontalPadding()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SleepNavCard("♫", "Sound library", "Sleep stories & soundscapes") { onOpen("sounds") }
                Text("☼ Your mornings", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                SleepNavCard("☼", "How did you sleep?", "A 20-second morning check-in") { onOpen("morningcheckin") }
                LastNightsCard()
                SleepNavCard("▯", "Sleep diary", "3 mornings logged") { onOpen("morningcheckin") }
                Spacer(Modifier.height(112.dp))
            }
        }
    }
}

@Composable
private fun SleepNavCard(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.12f), Color(0xFF5C75A4).copy(alpha = 0.26f))))
            .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(17.dp))
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Periwinkle.copy(alpha = 0.33f)), contentAlignment = Alignment.Center) {
            Text(icon, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        Text(">", style = MaterialTheme.typography.headlineSmall, color = TextMuted)
    }
}

@Composable
private fun LastNightsCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("☾  Last 7 nights", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text("avg 7h 45m", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.Bottom) {
            listOf(12, 12, 12, 54, 66, 58, 12).forEachIndexed { index, size ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(size.dp)
                            .clip(CircleShape)
                            .background(if (size > 20) Periwinkle else Color.Transparent)
                            .border(1.dp, if (size > 20) Periwinkle else Color.White.copy(alpha = 0.28f), CircleShape),
                    )
                    Text(listOf("S", "S", "M", "T", "W", "T", "F")[index], style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
    }
}

@Composable
fun MorningCheckInScreen(onOpen: (String) -> Unit = {}, onBack: () -> Unit = {}) {
    var quality by remember { mutableIntStateOf(0) }
    var bed by remember { mutableIntStateOf(23 * 60) }
    var wake by remember { mutableIntStateOf(6 * 60 + 45) }
    var wakes by remember { mutableIntStateOf(0) }
    var health by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF6657AA), Color(0xFF2B1E5C), Color(0xFF120820)))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = pageHorizontalPadding(), vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("HOW YOU SLEPT, NOT A MEASUREMENT", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.42f))
                    Text("Morning Check-in", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color(0xFF211A52))
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Periwinkle.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Text("☾", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }
                Text(
                    "A 20-second reflection on last night\nshapes today's plan.",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }

            Text("How rested do you feel?", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUALITY_WORDS.forEachIndexed { index, word ->
                    RestedChip(word, selected = quality == index + 1, modifier = Modifier.weight(1f)) { quality = index + 1 }
                }
            }

            Text("Your night", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.10f), Color(0xFF5C75A4).copy(alpha = 0.24f))))
                    .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(18.dp)),
            ) {
                SleepTimeRow("♧", "In bed around", timeLabel(bed)) { bed += 30 }
                DividerLine()
                SleepTimeRow("☼", "Woke up around", timeLabel(wake)) { wake += 15 }
                DividerLine()
                Row(
                    Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⌁", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("Woke during the\nnight", style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                    Row(
                        Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.12f)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("−", modifier = Modifier.clickable { wakes = (wakes - 1).coerceAtLeast(0) }.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Text(wakes.toString(), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("+", modifier = Modifier.clickable { wakes += 1 }.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(94.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.075f))
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Pre-fill from Apple Health", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("Reads last night's times — you still confirm. Off\nby default; never written back.", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
                TogglePill(health) { health = !health }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White)
                    .clickable(enabled = !busy) {
                        val savedQuality = if (quality > 0) quality else 5
                        busy = true
                        status = null
                        scope.launch {
                            runCatching { Api.logSleep(LocalDate.now().toString(), hhmm(bed), hhmm(wake), savedQuality) }
                            busy = false
                            onOpen("sleep")
                        }
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("☼", style = MaterialTheme.typography.titleMedium, color = Color(0xFF2B214E))
                Spacer(Modifier.width(10.dp))
                Text(if (busy) "Saving..." else "Save check-in", style = MaterialTheme.typography.titleMedium, color = Color(0xFF2B214E), fontWeight = FontWeight.Bold)
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
            Spacer(Modifier.height(112.dp))
        }
    }
}

@Composable
private fun RestedChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(70.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.0f else 0.14f), RoundedCornerShape(12.dp))
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("☾", style = MaterialTheme.typography.titleSmall, color = if (selected) Ink else TextPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) Ink else TextPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SleepTimeRow(icon: String, label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(value, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DividerLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.055f)))
}

@Composable
private fun TogglePill(on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .width(54.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (on) Periwinkle else Color.White.copy(alpha = 0.16f))
            .clickable { onClick() }
            .padding(horizontal = 4.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White))
    }
}
