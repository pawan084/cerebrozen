package com.cerebro.app.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cerebro.app.ui.theme.TextMuted
import com.cerebro.app.ui.theme.TextSoft
import java.time.LocalDate

// The honest "before" measurement — two 1–5 scales, offered from Home once a
// few real check-ins exist (mirrors iOS BaselineCheckView; local-only, never
// leaves the device). Insights' "Your starting point" card renders from this.

internal val STRESS_WORDS = listOf("Very calm", "Mostly calm", "Managing", "Stretched", "Overwhelmed")
internal val SLEEP_WORDS = listOf("Rough", "Poor", "Okay", "Good", "Rested")

@Composable
fun BaselineScreen(onBack: () -> Unit) {
    var stress by remember { mutableIntStateOf(0) }   // 0 = not chosen yet
    var sleep by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf(false) }

    SubPage("Two quick scales", "Your starting point", onBack) {
        Text(
            "Where things stand these days. Saving this lets future insights show real change instead of guesses.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted,
        )

        Text("How stressed have you felt lately?", style = MaterialTheme.typography.titleMedium, color = TextSoft)
        ScaleRow(STRESS_WORDS, stress) { stress = it }

        Text("How has sleep felt lately?", style = MaterialTheme.typography.titleMedium, color = TextSoft)
        ScaleRow(SLEEP_WORDS, sleep) { sleep = it }

        PrimaryButton(
            text = if (saved) "Saved — thank you" else "Save my starting point",
            enabled = stress > 0 && sleep > 0 && !saved,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BaselineStore.set(stress, sleep, LocalDate.now().toString())
            saved = true
        }

        Text(
            "Stays on this device — used only to show your progress in Insights.",
            style = MaterialTheme.typography.labelSmall, color = TextMuted,
        )
    }
}

/** Five tappable levels — reuses the onboarding chip idiom. */
@Composable
private fun ScaleRow(words: List<String>, chosen: Int, onPick: (Int) -> Unit) {
    ChipWrap(words, if (chosen > 0) words[chosen - 1] else null) { picked ->
        onPick(words.indexOf(picked) + 1)
    }
}
