package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextSoft
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

private val QUALITY_WORDS = listOf("Rough", "Poor", "Okay", "Good", "Rested")

private fun minutesToLabel(total: Int): String = "%dh %02dm".format(total / 60, total % 60)

private fun hhmm(minutes: Int): String {
    val m = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60)
    return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
}

private data class Night(val date: String, val duration: Int, val quality: Int)

private fun parseNights(rows: JSONArray): List<Night> =
    (0 until rows.length()).map { i ->
        val n = rows.getJSONObject(i)
        Night(n.getString("date"), n.optInt("duration_min"), n.optInt("quality"))
    }

/** Sleep diary: morning check-in + honest weekly summary + history — the same
 * non-diagnostic "awareness, not measurement" framing as iOS/web. */
@Composable
fun SleepScreen() {
    var quality by remember { mutableIntStateOf(0) }
    var bed by remember { mutableIntStateOf(23 * 60) }
    var wake by remember { mutableIntStateOf(7 * 60) }
    var summary by remember { mutableStateOf<JSONObject?>(null) }
    var nights by remember { mutableStateOf(listOf<Night>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        runCatching { summary = Api.sleepSummary() }
        runCatching { nights = parseNights(Api.sleepLogs()) }
    }

    LaunchedEffect(Unit) { reload() }

    Page("How you slept, not a measurement", "Sleep") {
        SectionCard {
            Text("Morning check-in", style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text("How rested do you feel?", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUALITY_WORDS.forEachIndexed { i, word ->
                    FilterChip(selected = quality == i + 1, onClick = { quality = i + 1 }, label = { Text(word) })
                }
            }
            TimeRow("In bed around", bed, { bed = it })
            TimeRow("Woke up around", wake, { wake = it })
            Button(
                enabled = quality > 0 && !busy,
                onClick = {
                    busy = true; status = null
                    scope.launch {
                        try {
                            Api.logSleep(LocalDate.now().toString(), hhmm(bed), hhmm(wake), quality)
                            status = "Logged — one honest night at a time."
                            quality = 0
                            reload()
                        } catch (e: Exception) {
                            status = e.message ?: "Couldn't log."
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(if (busy) "One moment…" else "Save night") }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
        }

        summary?.let { s ->
            SectionCard {
                Text("This week", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                if (s.optBoolean("enough_data")) {
                    Text(
                        "${minutesToLabel(s.optInt("avg_duration_min"))} avg · feeling ${"%.1f".format(s.optDouble("avg_quality"))}/5 · trend ${s.optString("trend")}",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                } else {
                    Text(
                        "Log a few more nights and honest averages appear here — no guesses.",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                }
            }
        }

        if (nights.isNotEmpty()) {
            SectionCard {
                Text("Diary", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                nights.take(7).forEach { n ->
                    Text(
                        "${n.date} · ${minutesToLabel(n.duration)} · felt ${n.quality}/5",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeRow(label: String, minutes: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        TextButton(onClick = { onChange(minutes - 30) }) { Text("−30m") }
        Text(hhmm(minutes), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        TextButton(onClick = { onChange(minutes + 30) }) { Text("+30m") }
    }
}
