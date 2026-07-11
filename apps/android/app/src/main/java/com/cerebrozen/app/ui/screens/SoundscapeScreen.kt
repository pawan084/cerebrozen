package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.audio.SoundscapeMixer
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft

private fun layerIcon(symbol: String): ImageVector = when (symbol) {
    "rain" -> Icons.Outlined.Grain
    "ocean" -> Icons.Outlined.Waves
    "wind" -> Icons.Outlined.Air
    else -> Icons.Outlined.GraphicEq
}

/** Mix-your-own ambient soundscape — blend rain, ocean, wind and a soft drone,
 * each with its own volume, into a personal calm. Parity with the iOS sleep
 * player's mixer; the four loops play gaplessly and keep going while you use it. */
@Composable
fun SoundscapeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val playing = SoundscapeMixer.isPlaying
    SubPage("Mix your own", "Soundscape", onBack) {
        Text("Blend rain, ocean, wind and a soft drone into your own calm — nudge each one to taste.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)

        PrimaryButton(text = if (playing) "Pause" else "Play", modifier = Modifier.fillMaxWidth()) {
            SoundscapeMixer.toggle(context)
        }

        SectionCard {
            Text("Master volume", style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Slider(
                value = SoundscapeMixer.master,
                onValueChange = { SoundscapeMixer.setMasterVolume(context, it) },
                valueRange = 0f..1f,
                colors = mixSliderColors(),
            )
        }

        SoundscapeMixer.layers.forEachIndexed { i, layer ->
            val vol = SoundscapeMixer.volumes[i]
            val on = vol > 0.02f
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Tap the tinted well to toggle the layer on/off.
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(
                                if (on) Brush.verticalGradient(listOf(Periwinkle.copy(alpha = 0.34f), Periwinkle.copy(alpha = 0.14f)))
                                else Brush.verticalGradient(listOf(CardFill, CardFill)),
                            )
                            .border(1.dp, if (on) Periwinkle.copy(alpha = 0.4f) else LineStroke, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(layerIcon(layer.symbol), contentDescription = null,
                            tint = if (on) Periwinkle else TextMuted, modifier = Modifier.size(20.dp))
                    }
                    Text(layer.name, style = MaterialTheme.typography.titleMedium,
                        color = if (on) TextPrimary else TextMuted, modifier = Modifier.weight(1f))
                    TextButton(onClick = { SoundscapeMixer.toggleLayer(context, i) }) {
                        Text(if (on) "On" else "Off", color = if (on) Cyan else TextMuted)
                    }
                }
                Slider(
                    value = vol,
                    onValueChange = { SoundscapeMixer.setLayerVolume(context, i, it) },
                    valueRange = 0f..1f,
                    colors = mixSliderColors(),
                )
            }
        }

        // Sleep auto-stop: off → 15 → 30 → 45 → 60, fades out then stops.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                Text("Sleep timer", style = MaterialTheme.typography.bodyMedium, color = TextSoft)
            }
            TextButton(onClick = { SoundscapeMixer.cycleTimer(context) }) {
                Text(
                    if (SoundscapeMixer.timerMinutes > 0) "${SoundscapeMixer.timerMinutes} min" else "Off",
                    color = if (SoundscapeMixer.timerMinutes > 0) Cyan else TextMuted,
                )
            }
        }
        SoundscapeMixer.remainingText()?.let {
            Text("Fades out in $it — drift off, it stops itself.",
                style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

@Composable
private fun mixSliderColors() = SliderDefaults.colors(
    thumbColor = Periwinkle,
    activeTrackColor = Periwinkle,
    inactiveTrackColor = CardFill,
)
