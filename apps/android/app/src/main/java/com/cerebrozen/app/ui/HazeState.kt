package com.cerebrozen.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.HazeState

/**
 * The app-wide Haze blur source (the aurora backdrop). Provided at the app root;
 * the shared `glass()` modifier reads it to give cards a real frosted backdrop
 * blur (API 31+) over the aurora. Null → no blur (the gradient fill carries it).
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }
