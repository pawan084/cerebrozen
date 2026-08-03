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
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.CardFill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun gameAccent(category: GameCategory): Color = when (category) {
    GameCategory.Focus -> Color(0xFF22D3EE)
    GameCategory.Memory -> Color(0xFFA78BFA)
    GameCategory.Resilience -> Color(0xFF34D399)
    GameCategory.Flexibility -> Color(0xFFF59E0B)
    GameCategory.Calm -> Color(0xFF818CF8)
}

@Composable
fun MindfulGamesScreen(onBack: () -> Unit, onOpenGame: (String) -> Unit) {
    SubPage(stringResource(R.string.mg_subtitle), stringResource(R.string.mg_title), onBack) {
        Text(stringResource(R.string.mg_subtitle), color = TextMuted)
        GameCategory.entries.forEach { category ->
            Text(stringResource(category.titleRes).uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = gameAccent(category))
            MindfulGameRegistry.games.filter { it.category == category }.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { game ->
                    val name = stringResource(game.nameRes)
                    val description = stringResource(game.descriptionRes)
                    val accent = gameAccent(game.category)
                    Box(Modifier.weight(1f).height(210.dp)) {
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
                            Text(stringResource(game.buildsRes), style = MaterialTheme.typography.labelSmall, color = accent,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
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

