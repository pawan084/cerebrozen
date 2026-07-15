package com.cerebrozen.app.ui.screens

/* Wind-down (Mira reference): a guided four-step pre-sleep routine. Each
 * step is one card and one action — put the day down, dim the world, one
 * minute of breathing (the real BreatheEngine, compact), then settle into
 * sound or straight to sleep. No timers racing the user; every step waits. */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.ui.theme.BrandPrimary
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.VeilWell

private data class WindStep(val eyebrow: String, val title: String, val body: String, val cta: String)

private val STEPS = listOf(
    WindStep(
        "Step 1 · Put the day down",
        "Name tomorrow's first thing",
        "Out loud or in your head: the first task you'll pick up tomorrow. Naming it once is what lets your mind stop rehearsing it tonight.",
        "Named it",
    ),
    WindStep(
        "Step 2 · Dim the world",
        "Lights low, phone quiet",
        "Dim the room if you can, and let notifications wait until morning. The next two steps work better in the dark.",
        "Done",
    ),
    WindStep(
        "Step 3 · Breathe",
        "One slow minute",
        "Follow the circle — in, hold, out. Six rounds is about a minute; stay longer if it's working.",
        "I'm slower now",
    ),
    WindStep(
        "Step 4 · Settle",
        "Into sound, or straight to sleep",
        "A quiet soundscape can hold the room while you drift — or skip it and let the dark do the work. Either way: the day is done.",
        "Good night",
    ),
)

@Composable
fun WindDownScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    var step by remember { mutableIntStateOf(-1) }  // -1 = intro
    // SubPage (not Page): this is a pushed screen with no tab bar, so it needs a visible
    // back button — otherwise a user mid-routine can only leave by finishing or by the
    // system gesture, and it reads as a dead-end. Back leaves the routine, matching the
    // system-back gesture and every other sub-screen.
    SubPage(
        eyebrow = "Wind-down",
        title = if (step < 0) "Close the day gently" else STEPS[step].title,
        onBack = onBack,
    ) {
        if (step < 0) {
            SectionCard {
                Text(
                    "Four small steps, a few minutes: put the day down, dim the world, breathe, settle. Nothing is timed — each step waits for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSoft,
                )
                STEPS.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.width(22.dp).height(22.dp).clip(CircleShape).background(VeilWell),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        Text(
                            s.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
                PrimaryButton("Begin") { step = 0 }
            }
        } else {
            val s = STEPS[step]
            // progress dots
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                STEPS.indices.forEach { i ->
                    Box(
                        Modifier
                            .height(7.dp)
                            .width(if (i == step) 20.dp else 7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i <= step) BrandPrimary else VeilWell),
                    )
                }
            }
            SectionCard {
                Text(s.eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(s.body, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                if (step == 2) {
                    BreatheEngine(
                        preset = BreathePreset.Reset,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        compact = true,
                    )
                }
                if (step == 3) {
                    Text(
                        "Sleep timers fade sound out on their own — nothing to remember.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Ok,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            PrimaryButton("Sounds on") { onOpen("sounds/mixer") }
                        }
                        Box(Modifier.weight(1f)) {
                            PrimaryButton(s.cta) { onBack() }
                        }
                    }
                } else {
                    PrimaryButton(s.cta) {
                        if (step < STEPS.size - 1) step += 1 else onBack()
                    }
                }
            }
            if (step < STEPS.size - 1) {
                Text(
                    "No rush — this step waits for you.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}
