package com.cerebrozen.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextPrimary

/** Shared page frame for the live tabs: eyebrow + serif title + scroll column. */
@Composable
internal fun Page(eyebrow: String, title: String, content: @Composable ColumnScope.() -> Unit) {
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
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        Text(title, style = MaterialTheme.typography.displaySmall, color = TextPrimary)
        content()
    }
}

/** Glass card container matching the design system. */
@Composable
internal fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = CardFill,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, LineStroke, RoundedCornerShape(20.dp)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}
