package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.audio.ToolAmbience
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextSoft

/** Play a soft looping ambient bed for the lifetime of this screen — fades in on
 * enter, out on leave. Drop it at the top of a calming tool/game composable. */
@Composable
internal fun ToolAmbienceEffect(rawRes: Int) {
    val context = LocalContext.current
    DisposableEffect(rawRes) {
        ToolAmbience.start(context, rawRes)
        onDispose { ToolAmbience.stop() }
    }
}

/** A small pill toggle that mutes/unmutes the tool ambience. */
@Composable
internal fun AmbienceToggle(modifier: Modifier = Modifier) {
    val muted = ToolAmbience.muted
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(CardFill)
            .border(1.dp, LineStroke, RoundedCornerShape(50))
            .clickable { ToolAmbience.toggleMute() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = if (muted) "Turn ambience on" else "Mute ambience",
            tint = if (muted) TextMuted else Cyan,
            modifier = Modifier.size(16.dp),
        )
        Text(
            if (muted) "Sound off" else "Ambience",
            style = MaterialTheme.typography.labelSmall,
            color = if (muted) TextMuted else TextSoft,
        )
    }
}
