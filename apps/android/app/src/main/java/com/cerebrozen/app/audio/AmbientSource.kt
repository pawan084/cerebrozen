package com.cerebrozen.app.audio

/**
 * Where a looping ambient bed's audio comes from.
 *
 * One seam, used by every looper ([AmbientService], [SoundscapeService],
 * [ToolAmbience]): prefer the server asset the catalogue holds for [key], and fall
 * back to the bundled raw resource when it has none. That fallback is the shipping
 * default — the four soundscape loops and the ambient bed are in the APK and always
 * work — so uploading an asset to a key is a pure upgrade, never a dependency.
 *
 * Unlike the one-shots in [Sfx], loops are *streamed* rather than pre-downloaded:
 * a bed runs for 45 minutes and pulling it into memory would cost tens of megabytes.
 * The first seconds may buffer; a bed is allowed to fade in, a tap is not.
 *
 * Takes the package name as a plain String rather than a Context, so the resolution
 * rule itself is unit-testable.
 */
fun ambientUri(packageName: String, key: String, rawRes: Int): String =
    MediaCatalog.urlFor(key).ifBlank { "android.resource://$packageName/$rawRes" }
