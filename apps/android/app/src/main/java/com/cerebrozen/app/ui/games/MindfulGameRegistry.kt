package com.cerebrozen.app.ui.games

import androidx.annotation.StringRes
import com.cerebrozen.app.R

enum class GameCategory(@StringRes val titleRes: Int) {
    Focus(R.string.mg_cat_focus), Memory(R.string.mg_cat_memory),
    Resilience(R.string.mg_cat_resilience), Flexibility(R.string.mg_cat_flexibility),
    Calm(R.string.mg_cat_calm),
}

/**
 * One mechanic per game, and no two games share one.
 *
 * The previous list had 23 games over 7 mechanics — twelve of them were the
 * same function with a different emoji. Variety in a menu that resolves to the
 * same tap is not variety; it is a longer menu. Each entry below is a distinct
 * thing to do, and the ones that were duplicates are gone rather than reskinned.
 */
enum class GameMechanic {
    /** Name a colour, find it in a growing field. */
    ColorTap,
    /** Colour word vs ink colour; the ink wins. */
    Stroop,
    /** Go / no-go, tightly timed. */
    GoNoGo,
    /** Watch a sequence of cells, repeat it. Span grows. */
    SequenceRecall,
    /** The rule flips unpredictably; follow the current one. */
    RuleSwitch,
    /** Sort a written thought as a familiar unhelpful pattern or a workable one. */
    ThoughtSort,
    /** Paced breathing, unscored. */
    BreathPace,
    /** Hold attention on one point, unscored. */
    StillPoint,
}

data class MindfulGame(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    /** What the game ASKS OF YOU ("Hold a sequence in mind"), never what it
     * claims to build. This field arrived as `buildsRes` carrying the reference
     * catalogue's faculty tags — "Working memory", "Selective attention",
     * "Cognitive Flexibility" — which is the unevidenced cognitive-training
     * claim class this product has removed twice already (REDESIGN §2.2, the
     * Thought Sort adoption) and the FTC fined Lumosity $2M over in 2016.
     * A tag describing the activity is honest; a tag naming the faculty it
     * trains is a claim we cannot back. `scripts/check-claims.mjs` now bans
     * the old vocabulary so it cannot come back through a third door. */
    @StringRes val practiceRes: Int,
    val category: GameCategory,
    val glyph: String,
    val mechanic: GameMechanic,
)

object MindfulGameRegistry {
    /**
     * Eight distinct games, one mechanic each.
     *
     * Removed rather than kept as filler (2026-08-01): `sound-hunter` and
     * `distraction-dodge` (Color Tap with different props), `emotion-match` and
     * `number-bloom` (Pattern Recall), `shape-shift` and `multi-task-relay`
     * (Rule Switch with a boolean), `growth-garden`, `emotion-compass` and
     * `storm-balance` (one reflective screen with different words), `cloud-drift`
     * and `who-are-you` (one calm screen with different words). Their ids are
     * remapped in [find], so an old deeplink or a saved shortcut lands on the
     * game that absorbed them instead of a blank screen.
     */
    val games = listOf(
        game("color-tap", R.string.mg_color_tap, R.string.mg_color_tap_desc, R.string.mg_selective_attention, GameCategory.Focus, "🎯", GameMechanic.ColorTap),
        game("stroop-flow", R.string.mg_stroop_flow, R.string.mg_stroop_flow_desc, R.string.mg_inhibitory_control, GameCategory.Focus, "S", GameMechanic.Stroop),
        game("freeze-switch", R.string.mg_freeze_switch, R.string.mg_freeze_switch_desc, R.string.mg_reaction_control, GameCategory.Focus, "🛑", GameMechanic.GoNoGo),
        game("pattern-recall", R.string.mg_pattern_recall, R.string.mg_pattern_recall_desc, R.string.mg_working_memory, GameCategory.Memory, "🔢", GameMechanic.SequenceRecall),
        game("thought-sort", R.string.mg_thought_sort, R.string.mg_thought_sort_desc, R.string.mg_cognitive_reframing, GameCategory.Resilience, "🧩", GameMechanic.ThoughtSort),
        game("rule-switch", R.string.mg_rule_switch, R.string.mg_rule_switch_desc, R.string.mg_mental_flexibility, GameCategory.Flexibility, "🔀", GameMechanic.RuleSwitch),
        game("breathing-rhythm", R.string.mg_breathing_rhythm, R.string.mg_breathing_rhythm_desc, R.string.mg_calm_focus, GameCategory.Calm, "◯", GameMechanic.BreathPace),
        game("still-point", R.string.mg_still_point, R.string.mg_still_point_desc, R.string.mg_relaxation, GameCategory.Calm, "•", GameMechanic.StillPoint),
    )

    /** Retired ids -> the game that absorbed them. Tidying the catalogue must
     * not turn someone's saved shortcut into a blank screen. */
    internal val retired = mapOf(
        "sound-hunter" to "color-tap",
        "distraction-dodge" to "color-tap",
        "emotion-match" to "pattern-recall",
        "number-bloom" to "pattern-recall",
        "shape-shift" to "rule-switch",
        "multi-task-relay" to "rule-switch",
        "growth-garden" to "thought-sort",
        "emotion-compass" to "thought-sort",
        "storm-balance" to "thought-sort",
        "cloud-drift" to "still-point",
        "who-are-you" to "still-point",
        "object-tray" to "pattern-recall",
        "path-memory" to "pattern-recall",
        "mirror-tap" to "rule-switch",
        "zen-sand" to "still-point",
    )

    fun find(id: String?): MindfulGame? =
        games.firstOrNull { it.id == id }
            ?: retired[id]?.let { successor -> games.firstOrNull { it.id == successor } }

    private fun game(id: String, name: Int, description: Int, practice: Int, category: GameCategory, glyph: String, mechanic: GameMechanic) =
        MindfulGame(id, name, description, practice, category, glyph, mechanic)
}
