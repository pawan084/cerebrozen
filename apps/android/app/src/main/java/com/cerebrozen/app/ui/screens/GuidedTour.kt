package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextSoft

// First-run guided tour (ref GUIDED TOUR OVERLAY): four gentle stops over
// Home, shown once per install (`tour_done` on the Store seam).

internal val TOUR_STOPS = listOf(
    "Check in daily" to "One tap tells CereBro how you're arriving — plans, insights and starters all personalize from it.",
    "Your plan adapts" to "Three small steps a day, rebuilt from your check-ins and sleep diary. Tap the hero any time.",
    "Talk it through" to "A voice companion that listens first. It's AI — never a therapist, and always honest about that.",
    "Private by default" to "Nothing is remembered without your say-so. Change anything under You → Privacy & memory.",
)

internal object TourState {
    private const val KEY = "tour_done"
    fun isDone(): Boolean = Session.prefGet(KEY) == "true"
    fun markDone() = Session.prefPut(KEY, "true")
}

@Composable
internal fun GuidedTourOverlay(onDone: () -> Unit) {
    var idx by remember { mutableIntStateOf(0) }
    val (label, caption) = TOUR_STOPS[idx]
    Box(
        Modifier.fillMaxSize().background(Night.copy(alpha = 0.82f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .padding(16.dp)
                .glass(RoundedCornerShape(22.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("GUIDED TOUR · ${idx + 1} OF ${TOUR_STOPS.size}",
                style = MaterialTheme.typography.labelSmall, color = Cyan)
            Text(label, style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TOUR_STOPS.indices.forEach { i ->
                        Box(
                            Modifier.size(7.dp).clip(CircleShape)
                                .background(if (i == idx) Periwinkle else LineStroke),
                        )
                    }
                }
                Row {
                    TextButton(onClick = { TourState.markDone(); onDone() }) {
                        Text("Skip", color = TextMuted)
                    }
                    TextButton(onClick = {
                        if (idx < TOUR_STOPS.size - 1) idx++
                        else { TourState.markDone(); onDone() }
                    }) {
                        Text(if (idx < TOUR_STOPS.size - 1) "Next" else "Let's begin", color = Periwinkle)
                    }
                }
            }
        }
    }
}
