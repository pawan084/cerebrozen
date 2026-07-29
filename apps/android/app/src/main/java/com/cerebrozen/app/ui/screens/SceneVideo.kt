package com.cerebrozen.app.ui.screens

import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * A looping, muted, decorative scene video behind a screen — the night lake.
 *
 * **It renders nothing at all unless a video actually exists.** [url] is empty until
 * an admin uploads a `scene.*` asset, and we ship none, because we own no footage.
 * In that state the caller's [AuroraBackground] is simply what the user sees — the
 * honest fallback, not a black rectangle. So this always sits *behind* that art.
 *
 * ## Why a bare TextureView, and not PlayerView
 *
 * This took two wrong turns worth recording, because both *looked* like they worked:
 * the decoder logs were healthy and ExoPlayer reported playing in every version.
 *
 *  1. `PlayerView(ctx)` gives you a **SurfaceView**. SurfaceView is composited by
 *     SurfaceFlinger *behind* the app window, not inside it. Our window is opaque, so
 *     the video decoded flawlessly and was covered by the window itself. No amount of
 *     making the UI on top more transparent can fix that — the frames are not in the
 *     window at all.
 *  2. Inflating a PlayerView from XML (to get `surface_type=texture_view`, which has
 *     no setter) with a `null` root **drops the root's layout params**, so the view
 *     has no size to lay out into.
 *
 * A [TextureView] is an ordinary view: it draws inside the hierarchy, so Compose can
 * layer the aurora and the cards over it, and [AndroidView] gives it the modifier's
 * size directly. It costs a little more GPU than a SurfaceView — irrelevant for one
 * muted background loop, and correctness beats it.
 *
 * The scene is authored portrait (see the generator), so it fills the screen without
 * cropping; TextureView scales to the view's bounds.
 *
 * Muted and non-interactive by construction: it is wallpaper. It holds no audio focus
 * — the ambient bed owns the sound, and a decorative loop must never duck it.
 */
@Composable
fun SceneVideo(url: String, modifier: Modifier = Modifier) {
    // Nothing to play, or a preview/inspection pass with no media stack.
    if (url.isBlank() || LocalInspectionMode.current) return

    val context = LocalContext.current
    // Guarded like every other player in the app: a device with no media stack falls
    // through to null and simply shows the art beneath.
    val player = remember(url) {
        runCatching {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus = */ false,
                )
                setWakeMode(C.WAKE_MODE_NONE)   // wallpaper must not keep the CPU up
                prepare()
                playWhenReady = true
            }
        }.getOrNull()
    } ?: return

    DisposableEffect(player) {
        onDispose { runCatching { player.release() } }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
                isFocusable = false
                isClickable = false
                player.setVideoTextureView(this)
            }
        },
    )
}
