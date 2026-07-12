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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextSoft

// First-run guided tour (ref GUIDED TOUR OVERLAY): four gentle stops over
// Home, shown once per install (`tour_done` on the Store seam).

/** The four tour stops, resolved from resources in composition. */
@Composable
internal fun tourStops(): List<Pair<String, String>> = listOf(
    stringResource(R.string.tour_stop1_title) to stringResource(R.string.tour_stop1_body),
    stringResource(R.string.tour_stop2_title) to stringResource(R.string.tour_stop2_body),
    stringResource(R.string.tour_stop3_title) to stringResource(R.string.tour_stop3_body),
    stringResource(R.string.tour_stop4_title) to stringResource(R.string.tour_stop4_body),
)

internal object TourState {
    private const val KEY = "tour_done"
    fun isDone(): Boolean = Session.prefGet(KEY) == "true"
    fun markDone() = Session.prefPut(KEY, "true")
}

@Composable
internal fun GuidedTourOverlay(onDone: () -> Unit) {
    var idx by remember { mutableIntStateOf(0) }
    val stops = tourStops()
    val (label, caption) = stops[idx]
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
            Text(stringResource(R.string.tour_header, idx + 1, stops.size),
                style = MaterialTheme.typography.labelSmall, color = Cyan)
            Text(label, style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    stops.indices.forEach { i ->
                        Box(
                            Modifier.size(7.dp).clip(CircleShape)
                                .background(if (i == idx) Periwinkle else LineStroke),
                        )
                    }
                }
                Row {
                    TextButton(onClick = { TourState.markDone(); onDone() }) {
                        Text(stringResource(R.string.tour_skip), color = TextMuted)
                    }
                    TextButton(onClick = {
                        if (idx < stops.size - 1) idx++
                        else { TourState.markDone(); onDone() }
                    }) {
                        Text(
                            if (idx < stops.size - 1) stringResource(R.string.common_next) else stringResource(R.string.tour_begin),
                            color = Periwinkle,
                        )
                    }
                }
            }
        }
    }
}
