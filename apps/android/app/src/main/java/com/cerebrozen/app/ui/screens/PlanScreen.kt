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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.cerebro.app.net.Api
import com.cerebro.app.ui.theme.Periwinkle
import com.cerebro.app.ui.theme.TextMuted
import com.cerebro.app.ui.theme.TextPrimary
import com.cerebro.app.ui.theme.TextSoft
import kotlinx.coroutines.launch
import org.json.JSONObject

internal data class PlanStep(val id: String, val title: String, val detail: String, val order: Int, val done: Boolean)

internal fun parsePlanSteps(plan: JSONObject): List<PlanStep> {
    val arr = plan.optJSONArray("steps") ?: return emptyList()
    return (0 until arr.length()).map { i ->
        val s = arr.getJSONObject(i)
        PlanStep(s.optString("id"), s.optString("title"), s.optString("detail"), s.optInt("order"), s.optBoolean("done"))
    }.sortedBy { it.order }
}

@Composable
fun PlanScreen(onBack: () -> Unit) {
    var plan by remember { mutableStateOf<JSONObject?>(null) }
    var steps by remember { mutableStateOf(defaultPlanSteps) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun adopt(p: JSONObject?) {
        plan = p
        steps = p?.let(::parsePlanSteps)?.takeIf { it.isNotEmpty() } ?: defaultPlanSteps
    }

    LaunchedEffect(Unit) {
        runCatching { adopt(Api.activePlan()) }
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("SHAPED AROUND TODAY'S CHECK-IN", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.42f))
                    Text("Today's Plan", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF62B7BE), Color.White.copy(alpha = 0.10f))))
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.20f)), contentAlignment = Alignment.Center) {
                    Text("+", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }
                Text(
                    plan?.optString("rationale")?.ifBlank {
                        "You felt clear and steady - let's protect it - a light plan to keep the calm."
                    } ?: "You felt clear and steady - let's protect it - a light plan to keep the calm.",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }

            steps.take(3).forEachIndexed { index, step ->
                PlanStepCard(
                    label = when (index) {
                        0 -> "NOW"
                        1 -> "MIDDAY"
                        else -> "TONIGHT"
                    },
                    step = step,
                    onToggle = {
                        steps = steps.map { if (it.id == step.id) it.copy(done = !it.done) else it }
                        if (step.id.isNotBlank() && !step.id.startsWith("fallback")) {
                            scope.launch {
                                runCatching { adopt(Api.togglePlanStep(step.id, !step.done)) }
                                    .onFailure { status = it.message ?: "Couldn't update." }
                            }
                        }
                    },
                )
            }

            Spacer(Modifier.height(2.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White)
                    .clickable(enabled = !busy) {
                        val first = steps.firstOrNull { !it.done } ?: steps.firstOrNull()
                        if (first != null) {
                            busy = true
                            steps = steps.map { if (it.id == first.id) it.copy(done = true) else it }
                            scope.launch {
                                if (first.id.isNotBlank() && !first.id.startsWith("fallback")) {
                                    runCatching { adopt(Api.togglePlanStep(first.id, true)) }
                                        .onFailure { status = it.message ?: "Couldn't update." }
                                }
                                busy = false
                            }
                        }
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFF2B214E), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (busy) "Starting..." else "Start step one", style = MaterialTheme.typography.titleMedium, color = Color(0xFF2B214E), fontWeight = FontWeight.Bold)
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
            Spacer(Modifier.height(112.dp))
        }
    }
}

@Composable
private fun PlanStepCard(label: String, step: PlanStep, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.12f), Color(0xFF5C75A4).copy(alpha = 0.24f))))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(17.dp))
            .clickable { onToggle() }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(Periwinkle.copy(alpha = 0.32f)), contentAlignment = Alignment.Center) {
            Text("◷", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.38f))
            Text(
                step.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                textDecoration = if (step.done) TextDecoration.LineThrough else null,
            )
            Text(step.detail, style = MaterialTheme.typography.bodyMedium, color = TextSoft, maxLines = 1)
        }
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (step.done) Periwinkle else Color.Transparent)
                .border(1.dp, if (step.done) Periwinkle else Color.White.copy(alpha = 0.32f), CircleShape),
        )
    }
}

private val defaultPlanSteps = listOf(
    PlanStep("fallback-gratitude", "Gratitude note", "2 min - name one good thing", 1, false),
    PlanStep("fallback-pause", "Mindful pause", "A 60-second reset between tasks", 2, false),
    PlanStep("fallback-rain", "Rain over quiet hills", "Wind down and lock in the calm", 3, false),
)
