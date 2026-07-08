package com.cerebrozen.app.audio

/**
 * Narration audio plumbing, kept as a plain object (no Android/Compose types)
 * so it is JVM unit-testable.
 *
 * The server catalogue carries `audio_url` per item — relative "/media/…"
 * (backend-minted, resolved against the API base) or absolute (admin-pasted).
 * Screens register the resolved URL by title as they parse the catalogue;
 * [Player] looks it up when starting playback. Unknown titles resolve to ""
 * → [AmbientService] plays the bundled ambient bed, exactly as before.
 */
object MediaUrls {
    private val registry = mutableMapOf<String, String>()

    /** Relative "/media/…" → prefix the API base; absolute URLs pass through. */
    fun resolve(audioUrl: String?, base: String): String {
        val url = audioUrl?.trim().orEmpty()
        if (url.isEmpty()) return ""
        if (!url.startsWith("/")) return url
        return base.trimEnd('/') + url
    }

    fun register(title: String, url: String) {
        if (url.isBlank()) registry.remove(title) else registry[title] = url
    }

    /** "" when the title has no narration — the ambient bed is the fallback. */
    fun urlFor(title: String): String = registry[title] ?: ""

    fun clear() = registry.clear()
}
