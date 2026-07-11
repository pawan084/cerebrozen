package com.cerebrozen.app.ui.screens

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerebrozen.app.ui.Haptics
import com.cerebrozen.app.ui.LocalHazeState
import com.cerebrozen.app.ui.theme.Accent
import com.cerebrozen.app.ui.theme.Gradients
import com.cerebrozen.app.ui.theme.Radius
import com.cerebrozen.app.ui.theme.Stroke
import com.cerebrozen.app.ui.theme.Danger
import dev.chrisbanes.haze.hazeEffect
import com.cerebrozen.app.ui.theme.Ink
import com.cerebrozen.app.ui.theme.Iris
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextMuted2
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft

private val CardShape = RoundedCornerShape(Radius.card)

// Responsive sizing helpers — pages and cards breathe a little tighter on small
// phones and a touch more generously on large ones, instead of one fixed inset.
@Composable
internal fun isCompactWidth(): Boolean = LocalConfiguration.current.screenWidthDp < 380

@Composable
internal fun pageHorizontalPadding() = when {
    LocalConfiguration.current.screenWidthDp < 360 -> 14.dp
    LocalConfiguration.current.screenWidthDp < 420 -> 16.dp
    else -> 20.dp
}

@Composable
internal fun cardPadding() = when {
    LocalConfiguration.current.screenWidthDp < 360 -> 14.dp
    LocalConfiguration.current.screenWidthDp < 420 -> 16.dp
    else -> 18.dp
}

/** The shared "frosted glass" surface treatment: a top-lit translucent fill that
 * lets the aurora glow through, a soft lift, and a *bevelled* hairline — the border
 * is a vertical gradient (bright at the top edge, fading down) so the pane catches
 * light like a real bevelled edge rather than reading as a flat outline. Mirrors the
 * iOS glass stroke (white 28%→5%). */
internal fun Modifier.glass(shape: Shape = CardShape): Modifier = composed {
    val hazeState = LocalHazeState.current
    var m = this
        .shadow(8.dp, shape, clip = false, ambientColor = Color(0x26000000), spotColor = Color(0x30000000))
        .clip(shape)
    // Real backdrop blur of the aurora when a haze source is present (API 31+);
    // the translucent tint fill + bevel then sit on top of the frosted glass.
    // backgroundColor is required — it's what the blur composites against.
    if (hazeState != null) m = m.hazeEffect(hazeState) { backgroundColor = Night }
    m
        .background(Gradients.glass)
        .border(1.dp, Stroke.bevel, shape)
}

/** True when the user has asked the system to minimise animations ("Remove
 * animations" / animator duration scale = 0) — the Android analogue of iOS's
 * Reduce Motion. Entrances and looping animations honour this; discrete press /
 * selection feedback stays (matching the iOS policy). */
/** Pure predicate seam for [rememberReduceMotion]: animations are "reduced" when
 * the system animator duration scale is 0 ("Remove animations"). Kept separate so
 * the branch is unit-testable without a composition. */
internal fun reduceMotionFromScale(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        reduceMotionFromScale(
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ),
        )
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
    accent: Color = Accent.default,
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
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color(0xFFAAA3D0))
                Text(
                    title,
                    // A soft accent glow behind the serif title — subtle depth,
                    // tinted per section (mirrors the iOS accent-tinted title shadow).
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 38.sp,
                        lineHeight = 40.sp,
                        shadow = Shadow(accent.copy(alpha = 0.35f), Offset.Zero, 22f),
                    ),
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
            .clickable(interactionSource = interaction, indication = null) { Haptics.soft(0.4f); onClick() }
    } else {
        Modifier.fillMaxWidth().glass()
    }
    Column(
        mod.padding(cardPadding()),
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
        Gradients.primary
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF777486), Color(0xFF777486)))
    }
    Box(
        modifier
            .pressScale(pressed, down = 0.97f)
            .clip(RoundedCornerShape(Radius.pill))
            .background(brush)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) { Haptics.soft(0.6f); onClick() }
            .padding(horizontal = 28.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
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
    placeholderText: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = if (label.isNotBlank()) ({ Text(label) }) else null,
        placeholder = placeholderText?.let { { Text(it) } },
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(Radius.field),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Periwinkle,
            unfocusedBorderColor = Color(0xFF514B76),
            focusedContainerColor = Color(0xFF302B55),
            unfocusedContainerColor = Color(0xFF29254D),
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
    val shape = RoundedCornerShape(Radius.round)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // The fill/border/text cross-fade on selection instead of snapping — the chip
    // feels like it lights up rather than flicking to a new state.
    val bg by animateColorAsState(
        if (selected) Color.White else Color(0xFF39355F), tween(220), label = "chipBg",
    )
    val border by animateColorAsState(
        if (selected) Color.White else LineStroke, tween(220), label = "chipBorder",
    )
    val fg by animateColorAsState(if (selected) Ink else TextSoft, tween(220), label = "chipFg")
    Box(
        Modifier
            .heightIn(min = 48.dp)   // a11y: >= 48dp touch target
            .pressScale(pressed)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(interactionSource = interaction, indication = null) { Haptics.selection(); onClick() }
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
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) { Haptics.warning(); onClick() }
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

/** A shimmering skeleton placeholder for content that's loading — a soft fill with
 * a highlight sweeping across it (holds a static fill under Reduce Motion). Mirrors
 * the iOS Shimmer loader; use in place of a bare "Loading…" line. */
@Composable
internal fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    val reduceMotion = rememberReduceMotion()
    val base = modifier.clip(shape).background(Color.White.copy(alpha = 0.06f))
    if (reduceMotion) {
        Box(base)
        return
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        -1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "sweep",
    )
    Box(
        base.drawWithContent {
            drawContent()
            val sweepWidth = size.width * 0.6f
            val startX = x * size.width
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.14f), Color.Transparent),
                    startX = startX,
                    endX = startX + sweepWidth,
                ),
            )
        },
    )
}
