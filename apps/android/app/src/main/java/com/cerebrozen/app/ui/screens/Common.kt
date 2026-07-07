package com.cerebrozen.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.ui.theme.Ink
import com.cerebrozen.app.ui.theme.Iris
import com.cerebrozen.app.ui.theme.LineStroke
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
    val rise = remember { Animatable(26f) }
    LaunchedEffect(Unit) { rise.animateTo(0f, tween(420, easing = FastOutSlowInEasing)) }
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

/** Glass card container matching the design system. */
@Composable
internal fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass().padding(18.dp),
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
    val brush = if (enabled) {
        Brush.horizontalGradient(listOf(Periwinkle, Iris))
    } else {
        Brush.horizontalGradient(listOf(Periwinkle.copy(alpha = 0.28f), Iris.copy(alpha = 0.28f)))
    }
    Box(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(brush)
            .clickable(enabled = enabled) { onClick() }
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
    val bg = if (selected) Periwinkle else Color.White.copy(alpha = 0.06f)
    val border = if (selected) Periwinkle else LineStroke
    Box(
        Modifier
            .heightIn(min = 48.dp)   // a11y: >= 48dp touch target
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Ink else TextSoft,
        )
    }
}
