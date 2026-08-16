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
import com.cerebrozen.app.ui.theme.ModalScrim
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextSoft

// V2-e: the 4-stop tour overlay (tourStops + GuidedTourOverlay) left with the
// tour — Today's one-line hint replaced it (V2-b). TourState survives as the
// hint's once-per-install memory.

internal object TourState {
    private const val KEY = "tour_done"
    fun isDone(): Boolean = Session.prefGet(KEY) == "true"
    fun markDone() = Session.prefPut(KEY, "true")

    /** For You → "Take the tour again": re-arm the first-run hint. */
    fun reset() = Session.prefPut(KEY, "false")
}
