package com.cerebrozen.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.VeilWell
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The three guided routines ported from the web app (apps/app `/sleep/ritual`,
// `/games/ritual`, `/games/imagery`), themselves adopted from the sibling
// build. Copy is hand-synced with those screens, including the deliberate
// departures from the reference recorded there:
//
//  • the wind-down settles on **in 4, out 6** rather than the reference's
//    4-7-8 — a longer exhale than inhale is the part of slow breathing with
//    real evidence, and this app doesn't ship a ratio it can't source;
//  • the ritual builder drops three of the reference's eight blocks: 4-7-8
//    breathing (same reason), Disidentification (Psychosynthesis, thin
//    evidence) and affirmation reading — generic positive self-statements
//    *lower* mood in people with low self-esteem (Wood, Perunovic & Lee,
//    Psychological Science, 2009), which is the population a wellness app
//    selects for. It adds what the reference lacks: the **cue**;
//  • guided imagery does **not** say "you are safe here, nothing can harm
//    you". Safe-place imagery is exactly where that promise can break, so the
//    screen keeps the mechanism and drops the absolute reassurance, and warns
//    on the way *in* that stopping is a normal outcome.
//
// Nothing here writes anywhere unless the user asks: written steps live in
// composable state until "Save to journal" is tapped, and say so.

// ── Pure helpers (unit-tested; no Compose) ──────────────────────────────

/** The next prompt index, or null when the sequence is finished. Shared by the
 * body scan and the imagery reel so "what happens on the last one" is decided
 * in one place rather than twice in a UI branch. */
internal fun nextPromptIndex(current: Int, total: Int): Int? =
    if (current >= total - 1) null else current + 1

/** Progress through a fixed-length ritual as 0..1 — an explicit fraction, the
 * same posture the onboarding Funnel took after matching English copy proved
 * to be a bug. */
internal fun ritualProgress(step: Int, total: Int): Float =
    if (total <= 0) 0f else ((step + 1).toFloat() / total).coerceIn(0f, 1f)

/** One selectable block of a personal ritual. Every one of these is an
 * exercise the app already ships with its own provenance — the builder
 * sequences, it never invents. */
internal data class RitualBlock(val id: String, val minutes: Int)

internal val RITUAL_BLOCKS = listOf(
    RitualBlock("settle", 1),
    RitualBlock("box", 2),
    RitualBlock("ground", 2),
    RitualBlock("scan", 1),
    RitualBlock("dump", 3),
    RitualBlock("good", 2),
    RitualBlock("intention", 1),
    RitualBlock("reflect", 5),
)

/** Stored block ids → a ritual we can actually run: unknown ids (an older or
 * newer install, a hand-edited pref) and duplicates are dropped. A duplicate
 * would render two identical rows whose reorder buttons fight over one index. */
internal fun sanitizeRitual(stored: List<String>, known: List<String> = RITUAL_BLOCKS.map { it.id }): List<String> {
    val seen = LinkedHashSet<String>()
    stored.forEach { if (it in known) seen += it }
    return seen.toList()
}

/** Swap the block at [index] with its neighbour [by] steps away; out-of-range
 * moves return the list untouched (the arrows are disabled at the ends, but a
 * fast double-tap must not throw). */
internal fun moveBlock(order: List<String>, index: Int, by: Int): List<String> {
    val to = index + by
    if (index !in order.indices || to !in order.indices) return order
    return order.toMutableList().also { it[index] = order[to]; it[to] = order[index] }
}

/** Total minutes for a chosen order — the honest "about N min" on the card. */
internal fun ritualMinutes(order: List<String>): Int =
    order.sumOf { id -> RITUAL_BLOCKS.firstOrNull { it.id == id }?.minutes ?: 0 }

// ── Local store ─────────────────────────────────────────────────────────

/** The personal ritual: which blocks, in which order, and the cue it hangs
 * off. Device-local by design — there is no server model for a ritual, and
 * inventing one to sync a list of eight ids would be the wrong trade. The
 * screen says so rather than letting the user assume it follows their account. */
internal object RitualStore {
    private const val BLOCKS = "ritual_blocks"
    private const val CUE = "ritual_cue"

    fun blocks(): List<String> = sanitizeRitual(readPrefList(BLOCKS))
    fun cue(): String = com.cerebrozen.app.net.Session.prefGet(CUE).orEmpty()

    fun save(order: List<String>, cue: String) {
        com.cerebrozen.app.net.Session.prefPut(BLOCKS, org.json.JSONArray(sanitizeRitual(order)).toString())
        com.cerebrozen.app.net.Session.prefPut(CUE, cue.trim())
    }
}

private fun readPrefList(key: String): List<String> =
    com.cerebrozen.app.net.Session.prefGet(key)
        ?.let { runCatching { org.json.JSONArray(it) }.getOrNull() }
        ?.let { a -> (0 until a.length()).map(a::getString) }
        ?: emptyList()

// ── Shared step composables ─────────────────────────────────────────────
// Each owns its mechanics and takes its words from the caller: the wind-down
// speaks to someone already in bed, the builder to someone assembling a
// routine at any hour, and that difference is the point of those screens.

@Composable
private fun StepProgress(step: Int, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { i ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(CircleShape)
                        .background(if (i <= step) Periwinkle else LineStroke),
                )
            }
        }
        Text(
            stringResource(R.string.ritual_step_of, step + 1, total),
            style = MaterialTheme.typography.labelSmall, color = TextMuted,
        )
    }
}

/** A writing step. The privacy posture is the point: what someone writes lives
 * in this composable until they press Save, and the screen says so before they
 * start. These prompts invite the most unguarded writing a user will do all
 * day, and silence about where it goes is not the same as discarding it. */
@Composable
private fun WritingStep(
    eyebrow: String,
    title: String,
    body: String,
    field: String,
    journalTitle: String,
    why: String,
    cta: String,
    minLines: Int = 5,
    onNext: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var saved by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val saveFailed = stringResource(R.string.common_save_failed)

    SectionCard {
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        AppTextField(text, { text = it; saved = false }, field, minLines = minLines)
        Text(stringResource(R.string.ritual_stays_local), style = MaterialTheme.typography.labelSmall, color = TextMuted)
        PrimaryButton(text = cta, modifier = Modifier.fillMaxWidth()) { onNext() }
        TextButton(
            enabled = text.isNotBlank() && !saved,
            onClick = {
                scope.launch {
                    runCatching { Api.createJournal(journalTitle, text) }
                        .onSuccess { saved = true }
                        .onFailure { status = it.message ?: saveFailed }
                }
            },
        ) {
            Text(
                if (saved) stringResource(R.string.ritual_saved_to_journal) else stringResource(R.string.ritual_save_to_journal),
                color = TextMuted,
            )
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
    }
    WhyThisWorks(why)
}

@Composable
private fun ThreeGoodThingsStep(
    eyebrow: String,
    title: String,
    body: String,
    why: String,
    cta: String,
    skip: String,
    onNext: () -> Unit,
) {
    var one by rememberSaveable { mutableStateOf("") }
    var two by rememberSaveable { mutableStateOf("") }
    var three by rememberSaveable { mutableStateOf("") }
    // Never gated on filling all three: a day where only one thing comes to
    // mind is exactly the day not to be blocked by a form.
    val any = listOf(one, two, three).any { it.isNotBlank() }

    SectionCard {
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        AppTextField(one, { one = it }, stringResource(R.string.ritual_good_thing, 1), singleLine = true)
        AppTextField(two, { two = it }, stringResource(R.string.ritual_good_thing, 2), singleLine = true)
        AppTextField(three, { three = it }, stringResource(R.string.ritual_good_thing, 3), singleLine = true)
        PrimaryButton(text = if (any) cta else skip, modifier = Modifier.fillMaxWidth()) { onNext() }
    }
    WhyThisWorks(why)
}

@Composable
private fun BodyScanStep(eyebrow: String, prompts: List<String>, why: String, cta: String, onNext: () -> Unit) {
    var i by remember { mutableIntStateOf(0) }
    val last = nextPromptIndex(i, prompts.size) == null

    LaunchedEffect(i) {
        if (last) return@LaunchedEffect
        delay(SCAN_MS)
        nextPromptIndex(i, prompts.size)?.let { i = it }
    }

    SectionCard {
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        PromptReel(prompts, i)
        // Always skippable — a body scan you're stuck inside is not relaxing.
        PrimaryButton(
            text = if (last) cta else stringResource(R.string.ritual_skip_ahead),
            modifier = Modifier.fillMaxWidth(),
        ) { onNext() }
    }
    WhyThisWorks(why)
}

/** One line at a time with a dot per prompt — used by the body scan and the
 * imagery reel. The crossfade is the only motion, and AnimatedContent honours
 * the system animator scale, so Reduce Motion needs no special case. */
@Composable
private fun PromptReel(prompts: List<String>, index: Int) {
    AnimatedContent(
        targetState = index,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "promptReel",
    ) { i ->
        Text(
            prompts[i],
            style = MaterialTheme.typography.titleMedium, color = TextSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 12.dp),
        )
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        prompts.indices.forEach { n ->
            Box(
                Modifier.size(7.dp).clip(CircleShape)
                    .background(if (n <= index) Periwinkle else LineStroke),
            )
        }
    }
}

@Composable
private fun BreatheStep(eyebrow: String, preset: BreathePreset, why: String, cta: String, onNext: () -> Unit) {
    SectionCard {
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        BreatheEngine(preset, Modifier.fillMaxWidth())
        PrimaryButton(text = cta, modifier = Modifier.fillMaxWidth()) { onNext() }
    }
    WhyThisWorks(why)
}

@Composable
private fun SensoryStep(why: String, cta: String, onNext: () -> Unit) {
    val steps = groundSteps()
    var step by remember { mutableIntStateOf(0) }
    val last = nextPromptIndex(step, steps.size) == null

    SectionCard {
        Text(stringResource(R.string.ground_counter), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        Text(steps[step].first, style = MaterialTheme.typography.titleMedium, color = TextSoft)
        Text(steps[step].second, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        PrimaryButton(
            text = if (last) cta else stringResource(R.string.common_next),
            modifier = Modifier.fillMaxWidth(),
        ) { if (last) onNext() else nextPromptIndex(step, steps.size)?.let { step = it } }
    }
    WhyThisWorks(why)
}

// ── Wind-down ritual ────────────────────────────────────────────────────

private const val SCAN_MS = 6_000L

@Composable
private fun windDownScanPrompts() = listOf(
    stringResource(R.string.winddown_scan_1),
    stringResource(R.string.winddown_scan_2),
    stringResource(R.string.winddown_scan_3),
    stringResource(R.string.winddown_scan_4),
    stringResource(R.string.winddown_scan_5),
    stringResource(R.string.winddown_scan_6),
)

@Composable
fun WindDownRitualScreen(onBack: () -> Unit) {
    // Survives rotation: losing a half-written brain dump to a screen turn
    // would be the worst possible moment for it.
    var step by rememberSaveable { mutableIntStateOf(0) }
    val total = 4

    PremiumSubPage(
        stringResource(R.string.winddown_eyebrow),
        stringResource(R.string.winddown_title),
        onBack,
    ) {
        if (step < total) StepProgress(step, total)
        when (step) {
            0 -> WritingStep(
                eyebrow = stringResource(R.string.winddown_dump_eyebrow),
                title = stringResource(R.string.winddown_dump_title),
                body = stringResource(R.string.winddown_dump_body),
                field = stringResource(R.string.winddown_dump_field),
                journalTitle = stringResource(R.string.winddown_journal_title),
                why = stringResource(R.string.winddown_dump_why),
                cta = stringResource(R.string.common_continue),
            ) { step = 1 }
            1 -> ThreeGoodThingsStep(
                eyebrow = stringResource(R.string.winddown_good_eyebrow),
                title = stringResource(R.string.winddown_good_title),
                body = stringResource(R.string.winddown_good_body),
                why = stringResource(R.string.winddown_good_why),
                cta = stringResource(R.string.common_continue),
                skip = stringResource(R.string.winddown_good_skip),
            ) { step = 2 }
            2 -> BodyScanStep(
                eyebrow = stringResource(R.string.winddown_scan_eyebrow),
                prompts = windDownScanPrompts(),
                why = stringResource(R.string.winddown_scan_why),
                cta = stringResource(R.string.common_continue),
            ) { step = 3 }
            3 -> BreatheStep(
                eyebrow = stringResource(R.string.winddown_settle_eyebrow),
                // The Reset preset IS in-4/out-6 (matching iOS `.reset`) — the
                // ritual doesn't define a fourth rhythm of its own.
                preset = BreathePreset.Reset,
                why = stringResource(R.string.winddown_settle_why),
                cta = stringResource(R.string.winddown_settle_cta),
            ) { step = 4 }
            else -> ClosingCard(
                stringResource(R.string.winddown_done_title),
                stringResource(R.string.winddown_done_body),
                onBack,
            )
        }
    }
}

@Composable
private fun ClosingCard(title: String, body: String, onBack: () -> Unit, extra: @Composable () -> Unit = {}) {
    SectionCard {
        Text(
            title, style = MaterialTheme.typography.titleLarge, color = TextSoft,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            body, style = MaterialTheme.typography.bodyMedium, color = TextMuted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(text = stringResource(R.string.common_done), modifier = Modifier.fillMaxWidth()) { onBack() }
        extra()
    }
}

// ── Ritual builder ──────────────────────────────────────────────────────

@Composable
private fun blockLabel(id: String): String = when (id) {
    "settle" -> stringResource(R.string.block_settle_label)
    "box" -> stringResource(R.string.block_box_label)
    "ground" -> stringResource(R.string.block_ground_label)
    "scan" -> stringResource(R.string.block_scan_label)
    "dump" -> stringResource(R.string.block_dump_label)
    "good" -> stringResource(R.string.block_good_label)
    "intention" -> stringResource(R.string.block_intention_label)
    else -> stringResource(R.string.block_reflect_label)
}

@Composable
private fun blockNote(id: String): String = when (id) {
    "settle" -> stringResource(R.string.block_settle_note)
    "box" -> stringResource(R.string.block_box_note)
    "ground" -> stringResource(R.string.block_ground_note)
    "scan" -> stringResource(R.string.block_scan_note)
    "dump" -> stringResource(R.string.block_dump_note)
    "good" -> stringResource(R.string.block_good_note)
    "intention" -> stringResource(R.string.block_intention_note)
    else -> stringResource(R.string.block_reflect_note)
}

@Composable
fun RitualBuilderScreen(onBack: () -> Unit) {
    val stringListSaver = listSaver<List<String>, String>(save = { it.toList() }, restore = { it.toList() })
    var chosen by rememberSaveable(stateSaver = stringListSaver) { mutableStateOf(RitualStore.blocks()) }
    var cue by rememberSaveable { mutableStateOf(RitualStore.cue()) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var phase by rememberSaveable { mutableStateOf("build") }
    var at by rememberSaveable { mutableIntStateOf(0) }

    when (phase) {
        "run" -> RitualRunner(
            order = chosen,
            at = at,
            onAdvance = { if (at < chosen.lastIndex) at += 1 else phase = "done" },
            onExit = { phase = "build" },
            onBack = onBack,
        )
        "done" -> PremiumSubPage(
            stringResource(R.string.builder_eyebrow), stringResource(R.string.builder_title), onBack,
        ) {
            // Deliberately quiet: no trophy, no count, no streak (F5 —
            // celebrate notable moments, not every repetition). The one thing
            // worth saying is the cue, because that is what makes the next run.
            ClosingCard(
                stringResource(R.string.builder_done_title),
                if (cue.isBlank()) stringResource(R.string.builder_done_nocue)
                else stringResource(R.string.builder_done_cue, cue.trim()),
                onBack,
            ) {
                TextButton(onClick = { phase = "build" }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.builder_change), color = TextMuted)
                }
            }
        }
        else -> PremiumSubPage(
            stringResource(R.string.builder_eyebrow), stringResource(R.string.builder_title), onBack,
        ) {
            // The cue leads. Reordering blocks is the fun part; deciding when
            // it happens is the part with the evidence behind it.
            SectionCard(quiet = true) {
                Text(stringResource(R.string.builder_cue_eyebrow).uppercase(),
                    style = MaterialTheme.typography.labelSmall, color = Periwinkle)
                Text(stringResource(R.string.builder_cue_title),
                    style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text(stringResource(R.string.builder_cue_body),
                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
                AppTextField(
                    cue, { cue = it; saved = false }, label = "", singleLine = true,
                    placeholderText = stringResource(R.string.builder_cue_field),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        R.string.builder_cue_idea_1, R.string.builder_cue_idea_2,
                        R.string.builder_cue_idea_3, R.string.builder_cue_idea_4,
                    ).forEach { res ->
                        val idea = stringResource(res)
                        PickChip(selected = cue.trim() == idea, label = idea) { cue = idea; saved = false }
                    }
                }
                if (cue.isNotBlank()) {
                    Text(
                        stringResource(R.string.builder_cue_sentence, cue.trim()),
                        style = MaterialTheme.typography.titleSmall, color = TextSoft,
                    )
                }
                // Honest about what we won't do: there is no reminder wired to
                // this, and promising one we don't send would be a fake. It is
                // also the point — the cue is meant to do the reminding.
                Text(stringResource(R.string.builder_no_nag), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            WhyThisWorks(stringResource(R.string.builder_cue_why))

            SectionCard(quiet = true) {
                Text(stringResource(R.string.builder_pick_title),
                    style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text(stringResource(R.string.builder_pick_body),
                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
                RITUAL_BLOCKS.forEach { block ->
                    val idx = chosen.indexOf(block.id)
                    val label = blockLabel(block.id)
                    BlockRow(
                        label = label,
                        note = blockNote(block.id) + " · " + stringResource(R.string.common_minutes, block.minutes),
                        position = if (idx >= 0) idx + 1 else null,
                        canMoveUp = idx > 0,
                        canMoveDown = idx >= 0 && idx < chosen.lastIndex,
                        onToggle = {
                            saved = false
                            chosen = if (idx >= 0) chosen - block.id else chosen + block.id
                        },
                        onMoveUp = { saved = false; chosen = moveBlock(chosen, idx, -1) },
                        onMoveDown = { saved = false; chosen = moveBlock(chosen, idx, 1) },
                    )
                }
                Text(
                    if (chosen.isEmpty()) stringResource(R.string.builder_empty)
                    else pluralStringResource(R.plurals.builder_summary, chosen.size, chosen.size, ritualMinutes(chosen)),
                    style = MaterialTheme.typography.bodySmall, color = TextMuted,
                )
                PrimaryButton(
                    text = stringResource(R.string.builder_start),
                    enabled = chosen.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { at = 0; phase = "run" }
                TextButton(
                    enabled = chosen.isNotEmpty() && !saved,
                    onClick = { RitualStore.save(chosen, cue); saved = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (saved) stringResource(R.string.builder_saved) else stringResource(R.string.builder_save),
                        color = TextMuted,
                    )
                }
                Text(stringResource(R.string.builder_local_note), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}

@Composable
private fun BlockRow(
    label: String,
    note: String,
    position: Int?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val toggleCd = stringResource(R.string.builder_include_cd, label)
    val selected = position != null
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth()
            // The WHOLE row toggles, not just the switch — a 40dp switch beside
            // a block name is what everyone taps second. Same posture as the
            // plan-step rows. The switch keeps its own tap (it consumes the
            // event, so this doesn't double-fire) but is cleared from the
            // semantics tree so screen readers get one control, not two.
            .clip(shape)
            .background(Periwinkle.copy(alpha = if (selected) 0.13f else 0.035f))
            .border(
                1.dp,
                if (selected) Periwinkle.copy(alpha = 0.42f) else Periwinkle.copy(alpha = 0.13f),
                shape,
            )
            .toggleable(value = selected, onValueChange = { onToggle() }, role = Role.Switch)
            .semantics { contentDescription = toggleCd }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (selected) "$position  ·  $label" else label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) TextPrimary else TextSoft,
            )
            Text(note, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        if (selected) {
            val upCd = stringResource(R.string.builder_move_earlier_cd, label)
            val downCd = stringResource(R.string.builder_move_later_cd, label)
            Row {
                IconButton(onClick = onMoveUp, enabled = canMoveUp,
                    modifier = Modifier.size(36.dp).semantics { contentDescription = upCd }) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null,
                        tint = if (canMoveUp) Periwinkle else LineStroke, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown,
                    modifier = Modifier.size(36.dp).semantics { contentDescription = downCd }) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null,
                        tint = if (canMoveDown) Periwinkle else LineStroke, modifier = Modifier.size(20.dp))
                }
            }
        }
        Box(Modifier.clearAndSetSemantics { }) {
            AppSwitch(checked = selected, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun builderScanPrompts() = listOf(
    stringResource(R.string.winddown_scan_1),
    stringResource(R.string.winddown_scan_2),
    stringResource(R.string.winddown_scan_3),
    stringResource(R.string.winddown_scan_4),
    stringResource(R.string.builder_scan_5),
    stringResource(R.string.builder_scan_6),
)

@Composable
private fun RitualRunner(
    order: List<String>,
    at: Int,
    onAdvance: () -> Unit,
    onExit: () -> Unit,
    onBack: () -> Unit,
) {
    val id = order.getOrNull(at) ?: return ClosingCard(
        stringResource(R.string.builder_done_title), stringResource(R.string.builder_done_nocue), onBack,
    )
    val last = at == order.lastIndex
    val cta = if (last) stringResource(R.string.builder_finish) else stringResource(R.string.builder_next_step)

    PremiumSubPage(stringResource(R.string.builder_eyebrow), blockLabel(id), onBack) {
        StepProgress(at, order.size)
        // Leaving is one tap at every step — a routine you can't put down
        // half-way is a trap, not a ritual.
        TextButton(onClick = onExit) { Text(stringResource(R.string.ritual_stop_here), color = TextMuted) }
        // Keyed by block: two writing steps in a row would otherwise reconcile
        // to the same composable at the same position and the second would
        // inherit the first's text — for a brain dump that is a privacy bug,
        // not a cosmetic one.
        key(id) {
            when (id) {
                "settle" -> BreatheStep(
                    stringResource(R.string.block_settle_label), BreathePreset.Reset,
                    stringResource(R.string.winddown_settle_why), cta, onAdvance,
                )
                "box" -> BreatheStep(
                    stringResource(R.string.block_box_label), BreathePreset.Box,
                    stringResource(R.string.breathe_why), cta, onAdvance,
                )
                "ground" -> SensoryStep(stringResource(R.string.ground_why), cta, onAdvance)
                "scan" -> BodyScanStep(
                    stringResource(R.string.block_scan_label), builderScanPrompts(),
                    stringResource(R.string.winddown_scan_why), cta, onAdvance,
                )
                "dump" -> WritingStep(
                    eyebrow = stringResource(R.string.block_dump_label),
                    title = stringResource(R.string.builder_dump_title),
                    body = stringResource(R.string.builder_dump_body),
                    field = stringResource(R.string.winddown_dump_field),
                    journalTitle = stringResource(R.string.builder_journal_title),
                    why = stringResource(R.string.builder_dump_why),
                    cta = cta,
                    onNext = onAdvance,
                )
                "good" -> ThreeGoodThingsStep(
                    eyebrow = stringResource(R.string.block_good_label),
                    title = stringResource(R.string.builder_good_title),
                    body = stringResource(R.string.winddown_good_body),
                    why = stringResource(R.string.winddown_good_why),
                    cta = cta,
                    skip = stringResource(R.string.builder_good_skip),
                    onNext = onAdvance,
                )
                "intention" -> WritingStep(
                    eyebrow = stringResource(R.string.block_intention_label),
                    title = stringResource(R.string.builder_intention_title),
                    body = stringResource(R.string.builder_intention_body),
                    field = stringResource(R.string.builder_intention_field),
                    journalTitle = stringResource(R.string.builder_intention_journal),
                    why = stringResource(R.string.builder_intention_why),
                    cta = cta,
                    minLines = 3,
                    onNext = onAdvance,
                )
                else -> WritingStep(
                    eyebrow = stringResource(R.string.block_reflect_label),
                    title = stringResource(R.string.builder_reflect_title),
                    body = stringResource(R.string.builder_reflect_body),
                    field = stringResource(R.string.winddown_dump_field),
                    journalTitle = stringResource(R.string.builder_journal_title),
                    why = stringResource(R.string.builder_reflect_why),
                    cta = cta,
                    minLines = 7,
                    onNext = onAdvance,
                )
            }
        }
    }
}

// ── Guided imagery ──────────────────────────────────────────────────────

private const val IMAGERY_LINE_SECONDS = 15

@Composable
private fun imageryLines() = listOf(
    stringResource(R.string.imagery_line_1),
    stringResource(R.string.imagery_line_2),
    stringResource(R.string.imagery_line_3),
    stringResource(R.string.imagery_line_4),
    stringResource(R.string.imagery_line_5),
    stringResource(R.string.imagery_line_6),
    stringResource(R.string.imagery_line_7),
    stringResource(R.string.imagery_line_8),
)

@Composable
fun GuidedImageryScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val lines = imageryLines()
    var started by rememberSaveable { mutableStateOf(false) }
    var done by rememberSaveable { mutableStateOf(false) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var left by rememberSaveable { mutableIntStateOf(IMAGERY_LINE_SECONDS) }
    var paused by rememberSaveable { mutableStateOf(false) }

    // Countdown and advance are separate effects on purpose — the same shape
    // the web version settled on, so a tick never also has to decide whether
    // the reel is over.
    LaunchedEffect(started, done, paused) {
        if (!started || done || paused) return@LaunchedEffect
        while (true) {
            delay(1_000)
            if (left > 0) left -= 1 else break
        }
    }
    LaunchedEffect(left, index, started, done) {
        if (!started || done || left > 0) return@LaunchedEffect
        val next = nextPromptIndex(index, lines.size)
        if (next == null) {
            done = true
        } else {
            index = next
            left = IMAGERY_LINE_SECONDS
        }
    }

    PremiumSubPage(
        stringResource(R.string.imagery_eyebrow),
        stringResource(R.string.imagery_title),
        onBack,
    ) {
        when {
            done -> ImageryClosing(onBack) {
                index = 0
                left = IMAGERY_LINE_SECONDS
                paused = false
                done = false
            }
            !started -> ImageryIntro(onOpen) { started = true }
            else -> {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 600.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Keep the prompt in the visual centre while the controls
                    // settle near the bottom instead of leaving a dead lower half.
                    Box(
                        Modifier.fillMaxWidth().weight(1f).padding(bottom = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ImageryStage(lines, index)
                    }
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                            .background(CardFill).border(1.dp, LineStroke, RoundedCornerShape(22.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.tipp_progress, index + 1, lines.size),
                                style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(
                                if (paused) stringResource(R.string.imagery_paused)
                                else stringResource(R.string.imagery_seconds, left),
                                style = MaterialTheme.typography.labelLarge, color = Periwinkle,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrimaryButton(
                                if (paused) stringResource(R.string.common_resume) else stringResource(R.string.common_pause_label),
                                modifier = Modifier.weight(1f),
                            ) { paused = !paused }
                            Box(
                                Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(26.dp))
                                    .background(VeilWell).border(1.dp, LineStroke, RoundedCornerShape(26.dp))
                                    .clickable {
                                        val next = nextPromptIndex(index, lines.size)
                                        if (next == null) done = true else { index = next; left = IMAGERY_LINE_SECONDS }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(stringResource(R.string.ritual_skip_ahead),
                                    style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            }
                        }
                        TextButton(
                            onClick = { started = false; index = 0; left = IMAGERY_LINE_SECONDS; paused = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.ritual_stop_here), color = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageryIntro(onOpen: (String) -> Unit, onBegin: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 600.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = 58.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.imagery_intro),
                    style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                val shape = RoundedCornerShape(18.dp)
                Column(
                    Modifier.fillMaxWidth().clip(shape)
                        .background(Color(0x10FF6B81)).border(1.dp, Color(0x35FF6B81), shape)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(stringResource(R.string.imagery_caution_title), style = MaterialTheme.typography.titleSmall, color = TextSoft)
                    Text(stringResource(R.string.imagery_caution_body), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    TextButton(onClick = { onOpen("toolkit") }) {
                        Text(stringResource(R.string.imagery_caution_ground), color = Periwinkle)
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PrimaryButton(text = stringResource(R.string.imagery_begin), modifier = Modifier.fillMaxWidth()) { onBegin() }
            WhyThisWorks(stringResource(R.string.imagery_why))
        }
    }
}

@Composable
private fun ImageryClosing(onBack: () -> Unit, onAgain: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 600.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = 22.dp), contentAlignment = Alignment.Center) {
            SectionCard(quiet = true) {
                Text(
                    stringResource(R.string.imagery_closing_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.imagery_closing_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(stringResource(R.string.common_done), modifier = Modifier.fillMaxWidth(), onClick = onBack)
            TextButton(onClick = onAgain, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.imagery_again), color = TextMuted)
            }
        }
    }
}

/** The dusk stage — constant-dark in both themes, the rule content art follows
 * (a warm-white version of "somewhere calm at dusk" is a different exercise). */
@Composable
private fun ImageryStage(lines: List<String>, index: Int) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 248.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF292451), Color(0xFF100E29))))
            .border(1.dp, Periwinkle.copy(alpha = 0.28f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0x405FCEFF), Color.Transparent)),
                radius = size.minDimension * 0.72f,
                center = Offset(size.width * 0.25f, size.height * 0.18f),
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0x407A5CFF), Color.Transparent)),
                radius = size.minDimension * 0.62f,
                center = Offset(size.width * 0.82f, size.height * 0.82f),
            )
        }
        AnimatedContent(
            targetState = index,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "imageryLine",
        ) { i ->
            Text(
                lines[i],
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(28.dp),
            )
        }
    }
}
