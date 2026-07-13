package com.cerebrozen.app.ui.screens

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
import androidx.compose.ui.res.stringResource
import com.cerebrozen.app.R
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextSoft
import java.time.LocalDate

// The honest "before" measurement — two 1–5 scales, offered from Home once a
// few real check-ins exist (mirrors iOS BaselineCheckView; local-only, never
// leaves the device). Insights' "Your starting point" card renders from this.

/** The 1–5 stress scale words, resolved from resources (also read by Insights). */
@Composable
internal fun stressWords(): List<String> = listOf(
    stringResource(R.string.baseline_stress_1), stringResource(R.string.baseline_stress_2),
    stringResource(R.string.baseline_stress_3), stringResource(R.string.baseline_stress_4),
    stringResource(R.string.baseline_stress_5),
)

/** The 1–5 sleep-feel scale words (shared with the Sleep morning check-in). */
@Composable
internal fun sleepWords(): List<String> = listOf(
    stringResource(R.string.baseline_sleep_1), stringResource(R.string.baseline_sleep_2),
    stringResource(R.string.baseline_sleep_3), stringResource(R.string.baseline_sleep_4),
    stringResource(R.string.baseline_sleep_5),
)

@Composable
fun BaselineScreen(onBack: () -> Unit) {
    var stress by remember { mutableIntStateOf(0) }   // 0 = not chosen yet
    var sleep by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf(false) }

    SubPage(stringResource(R.string.baseline_eyebrow), stringResource(R.string.insights_baseline_title), onBack) {
        Text(
            stringResource(R.string.baseline_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextMuted,
        )

        Text(stringResource(R.string.baseline_stress_q), style = MaterialTheme.typography.titleMedium, color = TextSoft)
        ScaleRow(stressWords(), stress) { stress = it }

        Text(stringResource(R.string.baseline_sleep_q), style = MaterialTheme.typography.titleMedium, color = TextSoft)
        ScaleRow(sleepWords(), sleep) { sleep = it }

        PrimaryButton(
            text = if (saved) stringResource(R.string.baseline_saved) else stringResource(R.string.baseline_save_cta),
            enabled = stress > 0 && sleep > 0 && !saved,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BaselineStore.set(stress, sleep, LocalDate.now().toString())
            saved = true
        }

        Text(
            stringResource(R.string.baseline_privacy),
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
