package com.cerebrozen.app.ui.screens

/* Pre-permission notification screen (Mira reference): show exactly what a
 * check-in notification looks like BEFORE triggering the one-shot OS prompt.
 * The user decides on the real thing, not on an abstract permission string —
 * and "Maybe later" costs nothing (the OS prompt is never burned). Shown
 * once per install, only on Android 13+ while the permission is ungranted. */

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.ui.theme.BrandPrimary
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft

@Composable
fun NotificationPrePermission(onDone: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> onDone() } // either answer: the gate never shows again

    Column(
        Modifier.fillMaxSize().statusBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Can your coach check in?",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                "One quiet follow-up when a commitment comes due — never marketing, never noise. This is what it looks like:",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, bottom = 22.dp),
            )
            // The sample notification — the real decision object.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(CardFill).padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(BrandPrimary, BrandPrimary.copy(alpha = 0.6f)))),
                )
                Column(Modifier.padding(start = 10.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "CereBroZen",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text("now", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                    Text(
                        "Tuesday's commitment — how did the conversation go? Two minutes to close the loop.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSoft,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        PrimaryButton("Allow check-ins") {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        TextButton(onClick = onDone) {
            Text("Maybe later", color = TextMuted)
        }
    }
}
