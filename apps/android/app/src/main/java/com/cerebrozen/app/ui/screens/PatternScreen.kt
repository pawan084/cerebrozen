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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cerebro.app.net.Api
import com.cerebro.app.ui.theme.Danger
import com.cerebro.app.ui.theme.TextMuted
import com.cerebro.app.ui.theme.TextPrimary
import com.cerebro.app.ui.theme.TextSoft
import kotlinx.coroutines.launch
import org.json.JSONObject

internal data class Learned(val statement: String, val basis: String)

internal fun parsePatterns(payload: JSONObject): List<Learned> {
    val arr = payload.optJSONArray("patterns") ?: return emptyList()
    return (0 until arr.length()).map { i ->
        val p = arr.getJSONObject(i)
        Learned(p.optString("statement"), p.optString("basis"))
    }
}

@Composable
fun PatternScreen(onBack: () -> Unit) {
    var learned by remember { mutableStateOf<List<Learned>?>(null) }
    var confirming by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { runCatching { learned = parsePatterns(Api.patterns()) } }

    val rows = when {
        learned == null -> fallbackPatterns
        learned!!.isEmpty() -> fallbackPatterns
        else -> learned!!
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF6657AA), Color(0xFF2B1E5C), Color(0xFF120820)),
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = pageHorizontalPadding(), vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("TRANSPARENT AI MEMORY", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.42f))
                    Text("Pattern Dashboard", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
                }
            }

            Text(
                "Everything CereBro has learned about you — visible, editable, and yours to delete.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSoft,
            )

            Text("WHAT CEREBRO REMEMBERS", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.58f))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.take(6).forEach { p ->
                    MemoryRow(p.statement.ifBlank { "A pattern is still forming" })
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Transparent)
                    .border(1.dp, Danger.copy(alpha = 0.70f), RoundedCornerShape(14.dp))
                    .clickable {
                        if (!confirming) {
                            confirming = true
                        } else {
                            scope.launch {
                                runCatching { Api.deleteMemory() }
                                    .onSuccess {
                                        confirming = false
                                        learned = emptyList()
                                        status = "Memory cleared - ${it.optInt("chat_messages")} messages and ${it.optInt("insights")} insights forgotten."
                                    }
                                    .onFailure { status = it.message ?: "Couldn't delete - try again." }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = Color(0xFFFFA08E), modifier = Modifier.size(18.dp))
                    Text(if (confirming) "Tap again to confirm" else "Delete all memory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFFA08E))
                }
            }

            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
            Spacer(Modifier.height(112.dp))
        }
    }
}

@Composable
private fun MemoryRow(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.07f))))
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.Edit, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}

private val fallbackPatterns = listOf(
    Learned("Evenings are your hardest time of day", ""),
    Learned("Journaling before bed lowers your stress", ""),
    Learned("You prefer short sessions under 5 minutes", ""),
)
