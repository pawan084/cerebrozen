package com.cerebrozen.app.ui.games

import android.media.AudioManager
import android.media.ToneGenerator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.ui.Haptics
import com.cerebrozen.app.ui.screens.Celebrations
import com.cerebrozen.app.ui.screens.AmbienceToggle
import com.cerebrozen.app.ui.screens.PrimaryButton
import com.cerebrozen.app.ui.screens.SectionCard
import com.cerebrozen.app.ui.screens.SubPage
import com.cerebrozen.app.ui.screens.ToolAmbienceEffect
import com.cerebrozen.app.ui.screens.rememberReduceMotion
import com.cerebrozen.app.ui.theme.Accent2
import com.cerebrozen.app.ui.theme.Amber
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.CardFill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Per-category accent — the tonal roles, so each stays legible as the category
 * heading and the "practice" line it is printed in, in both themes. */
internal fun gameAccent(category: GameCategory): Color = when (category) {
    GameCategory.Focus -> Cyan
    GameCategory.Memory -> Periwinkle
    GameCategory.Resilience -> Ok
    GameCategory.Flexibility -> Amber
    GameCategory.Calm -> Accent2
}

@Composable
fun MindfulGamesScreen(onBack: () -> Unit, onOpenGame: (String) -> Unit) {
    // Register B72: `mg_subtitle` was passed as the SubPage EYEBROW — where it
    // is uppercased, letter-spaced and ellipsised to one line — and then
    // printed again verbatim as the first body line. The eyebrow now has its
    // own short label and the sentence is said once.
    SubPage(stringResource(R.string.mg_eyebrow), stringResource(R.string.mg_title), onBack) {
        Text(stringResource(R.string.mg_subtitle), color = TextMuted)
        GameCategory.entries.forEach { category ->
            Text(stringResource(category.titleRes).uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = gameAccent(category))
            MindfulGameRegistry.games.filter { it.category == category }.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { game ->
                    val name = stringResource(game.nameRes)
                    val description = stringResource(game.descriptionRes)
                    val accent = gameAccent(game.category)
                    Box(Modifier.weight(1f).heightIn(min = 210.dp)) {   // B39: grows at large font
                    SectionCard(onClick = { onOpenGame(game.id) }, modifier = Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(accent.copy(alpha = 0.75f)))
                            Box(
                                Modifier.size(50.dp).clip(RoundedCornerShape(17.dp))
                                    .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.42f), accent.copy(alpha = 0.10f))))
                                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(game.glyph, style = MaterialTheme.typography.headlineMedium,
                                    modifier = Modifier.semantics { contentDescription = name })
                            }
                            Text(name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(description, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.weight(1f))
                            // Two lines, not one. This is the "what this
                            // practises" line — the only thing on the card that
                            // says why the game is here — and at maxLines = 1 it
                            // clipped mid-word on every card in the grid ("Find
                            // one thing amon…", "Hold back the easy a…"), which
                            // is worse than omitting it.
                            Text(stringResource(game.practiceRes), style = MaterialTheme.typography.labelSmall, color = accent,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

