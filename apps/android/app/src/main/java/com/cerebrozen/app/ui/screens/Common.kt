package com.cerebrozen.app.ui.screens

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.ui.theme.Danger
import com.cerebrozen.app.ui.theme.Ink
import com.cerebrozen.app.ui.theme.Iris
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextMuted2
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft

private val CardShape = RoundedCornerShape(20.dp)

/** The shared "glass" surface treatment — a top-lit gradient fill, a hairline
 * border, and a soft lift — so cards read as raised panes on the dark ground
 * instead of the near-invisible 5%-white fill they were before. */
internal fun Modifier.glass(shape: Shape = CardShape): Modifier = this
    .shadow(14.dp, shape, clip = false, ambientColor = Color(0x40000000), spotColor = Color(0x40000000))
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.035f))))
    .border(1.dp, Color.White.copy(alpha = 0.14f), shape)

/** True when the user has asked the system to minimise animations ("Remove
 * animations" / animator duration scale = 0) — the Android analogue of iOS's
 * Reduce Motion. Entrances and looping animations honour this; discrete press /
 * selection feedback stays (matching the iOS policy). */
@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/** A soft press-in: the target scales down slightly while held and springs back on
 * release. Keeps taps tactile without being bouncy — calm, not playful. Feed it a
 * [pressed] flag from an interaction source. */
@Composable
internal fun Modifier.pressScale(pressed: Boolean, down: Float = 0.96f): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (pressed) down else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** A gentle one-shot entry: content rises a few dp and fades in on first
 * composition. Pass an [index] to stagger siblings into a soft cascade. */
@Composable
internal fun Modifier.appear(index: Int = 0, rise: Float = 20f): Modifier {
    val reduceMotion = rememberReduceMotion()
    val anim = remember { Animatable(0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) { anim.snapTo(1f); return@LaunchedEffect }  // no entrance — settle instantly
        kotlinx.coroutines.delay(index * 55L)
        anim.animateTo(1f, tween(440, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        translationY = (1f - anim.value) * rise
        alpha = anim.value
    }
}

/** Shared page frame for the live tabs: eyebrow + serif title + scroll column.
 * [trailing] renders as a soft icon well top-right — quiet ornamentation
 * mirroring iOS ScreenScaffold's trailingSystemImage, not a control. */
@Composable
internal fun Page(
    eyebrow: String,
    title: String,
    trailing: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Gentle content-rise on entry (complements the NavHost cross-fade).
    val reduceMotion = rememberReduceMotion()
    val rise = remember { Animatable(26f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) rise.snapTo(0f) else rise.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .graphicsLayer { translationY = rise.value }
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
                Text(title, style = MaterialTheme.typography.displaySmall, color = TextPrimary)
            }
            trailing?.let { icon ->
                Box(
                    Modifier.padding(top = 6.dp).size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, LineStroke, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = TextSoft, modifier = Modifier.size(19.dp))
                }
            }
        }
        content()
    }
}

/** Glass card container matching the design system. Pass [onClick] to make the
 * whole card a single tappable, accessible surface. */
@Composable
internal fun SectionCard(
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val mod = if (onClick != null) {
        // A whisper of press-in — cards are large, so the scale is tiny on purpose.
        Modifier.fillMaxWidth()
            .pressScale(pressed, down = 0.985f)
            .glass()
            .clickable(interactionSource = interaction, indication = null) { onClick() }
    } else {
        Modifier.fillMaxWidth().glass()
    }
    Column(
        mod.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/** Primary CTA — a gradient lavender pill with dark, bold text. Reads as the one
 * action that matters; the dimmed disabled state still looks intentional. */
@Composable
internal fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val brush = if (enabled) {
        Brush.horizontalGradient(listOf(Periwinkle, Iris))
    } else {
        Brush.horizontalGradient(listOf(Periwinkle.copy(alpha = 0.28f), Iris.copy(alpha = 0.28f)))
    }
    Box(
        modifier
            .pressScale(pressed, down = 0.97f)
            .clip(RoundedCornerShape(26.dp))
            .background(brush)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 28.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Ink else TextMuted2,
        )
    }
}

/** Text field in the app's language — rounded, a faint glass fill, and a lavender
 * focus border/label. Replaces the default Material OutlinedTextField. */
@Composable
internal fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Periwinkle,
            unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
            focusedContainerColor = Color.White.copy(alpha = 0.05f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.035f),
            cursorColor = Periwinkle,
            focusedLabelColor = Periwinkle,
            unfocusedLabelColor = TextMuted,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
        ),
    )
}

/** Selectable pill — filled lavender + dark text when chosen, a soft glassy
 * outline otherwise. Replaces the low-contrast Material FilterChip. */
@Composable
internal fun PickChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // The fill/border/text cross-fade on selection instead of snapping — the chip
    // feels like it lights up rather than flicking to a new state.
    val bg by animateColorAsState(
        if (selected) Periwinkle else Color.White.copy(alpha = 0.06f), tween(220), label = "chipBg",
    )
    val border by animateColorAsState(
        if (selected) Periwinkle else LineStroke, tween(220), label = "chipBorder",
    )
    val fg by animateColorAsState(if (selected) Ink else TextSoft, tween(220), label = "chipFg")
    Box(
        Modifier
            .heightIn(min = 48.dp)   // a11y: >= 48dp touch target
            .pressScale(pressed)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = fg,
        )
    }
}

/** Brand-tinted switch — lavender when on, glassy grey when off — so toggles match
 * the design system instead of the unconfigured Material default colours. */
@Composable
internal fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Ink,
            checkedTrackColor = Periwinkle,
            checkedBorderColor = Periwinkle,
            uncheckedThumbColor = TextSoft,
            uncheckedTrackColor = Color.White.copy(alpha = 0.06f),
            uncheckedBorderColor = LineStroke,
        ),
    )
}

/** Destructive CTA — same pill geometry as [PrimaryButton] but a Danger-tinted fill,
 * so irreversible actions (delete account, delete forever) read as dangerous. */
@Composable
internal fun DangerButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val brush = if (enabled) {
        Brush.horizontalGradient(listOf(Danger, Danger))
    } else {
        Brush.horizontalGradient(listOf(Danger.copy(alpha = 0.28f), Danger.copy(alpha = 0.28f)))
    }
    Box(
        modifier
            .pressScale(pressed, down = 0.97f)
            .clip(RoundedCornerShape(26.dp))
            .background(brush)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 28.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Night else TextMuted2,
        )
    }
}
