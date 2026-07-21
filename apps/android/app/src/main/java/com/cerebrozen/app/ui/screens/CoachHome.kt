package com.cerebrozen.app.ui.screens

/* The CereBroZen coaching surfaces: the Today home and the Actions tab.
 *
 * Actions are the product's spine (docs/COACHING_FLOW.md): every session ends
 * in a saved commitment, and this tab is where those commitments live between
 * sessions. ActionsStore keeps them on-device (Session prefs, JSON) — the
 * coach turn wiring appends cards here; the engine remains the source of
 * truth and re-syncs on session close. */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import java.util.Calendar
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Events
import com.cerebrozen.app.net.HomeCache
import com.cerebrozen.app.net.Session
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.cerebrozen.app.ui.theme.BrandPrimary
import com.cerebrozen.app.ui.theme.ChipFill
import com.cerebrozen.app.ui.theme.Accent
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.NightMid
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextMuted2
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Warm

// ── the on-device commitments store ─────────────────────────────────────────

data class ActionItem(val id: String, val text: String, val status: String)

object ActionsStore {
    private const val PREF_KEY = "coach_actions"
    val items = mutableStateListOf<ActionItem>()
    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            val arr = JSONArray(Session.prefGet(PREF_KEY)?.ifBlank { "[]" } ?: "[]")
            items.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(
                    ActionItem(
                        id = o.optString("id"),
                        text = o.optString("text"),
                        status = o.optString("status", "active"),
                    ),
                )
            }
        }
    }

    fun add(id: String, text: String) {
        load()
        if (text.isBlank() || items.any { it.id == id }) return
        items.add(0, ActionItem(id, text, "active"))
        persist()
        Events.report(Events.ACTION_SAVED)
    }

    fun setStatus(id: String, status: String) {
        load()
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) {
            items[i] = items[i].copy(status = status)
            persist()
            if (status == "done") Events.report(Events.ACTION_COMPLETED)
        }
    }

    fun openCount(): Int {
        load()
        return items.count { it.status == "active" }
    }

    private fun persist() {
        val arr = JSONArray()
        items.forEach { a ->
            arr.put(JSONObject().put("id", a.id).put("text", a.text).put("status", a.status))
        }
        runCatching { Session.prefPut(PREF_KEY, arr.toString()) }
    }
}

// ── Today: the coaching home ─────────────────────────────────────────────────

/** The living presence orb (Mira reference, coral-skinned): a slow breathing core with a
 * soft aura. Reduce Motion holds it steady at full size.
 *
 * [done] — already checked in today — holds it to a smaller, slower breath and warms the
 * core toward gold rather than the full coral: a calm visual tell that today is already
 * shown up for, without any text needing to say so twice. */
@Composable
private fun PresenceOrb(modifier: Modifier = Modifier, done: Boolean = false) {
    val reduceMotion = rememberReduceMotion()
    val scale = if (reduceMotion) 1f else {
        val breathe = rememberInfiniteTransition(label = "presence-orb")
        val s by breathe.animateFloat(
            initialValue = if (done) 0.985f else 0.94f, targetValue = if (done) 1.015f else 1.06f,
            animationSpec = infiniteRepeatable(
                tween(if (done) 4200 else 3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "orb-scale",
        )
        s
    }
    val core = if (done) androidx.compose.ui.graphics.lerp(BrandPrimary, androidx.compose.ui.graphics.Color(0xFFF3C77A), 0.5f) else BrandPrimary
    Box(modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(96.dp).scale(scale * 1.18f).clip(CircleShape)
                .background(Brush.radialGradient(listOf(core.copy(alpha = 0.35f), core.copy(alpha = 0f)))),
        )
        Box(
            Modifier.size(72.dp).scale(scale).clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(androidx.compose.ui.graphics.Color(0xFFFCD9D4), core, androidx.compose.ui.graphics.Color(0xFFB03A3A)),
                    ),
                ),
        )
    }
}

/** Time- and state-aware presence copy: one greeting, one line, two actions.
 *  `internal` (not private) so the ordering/copy rules are unit-testable off-device. */
internal fun presenceLines(
    name: String?,
    openActions: Int,
    hour: Int,
    resumable: Boolean = false,
): Pair<String, String> {
    val who = name?.takeIf { it.isNotBlank() }?.split(" ")?.first()
    val greeting = when {
        hour < 5 -> if (who != null) "Still up, $who?" else "Still up?"
        hour < 12 -> if (who != null) "Good morning, $who" else "Good morning"
        hour < 17 -> if (who != null) "Good afternoon, $who" else "Good afternoon"
        else -> if (who != null) "Good evening, $who" else "Good evening"
    }
    val say = when {
        // A resumable coaching session outranks the generic prompts — it's a real, specific
        // thing waiting, not a suggestion (HOME_SPEC #25). Commitments still take priority:
        // finishing a conversation matters less than a concrete step already promised.
        resumable && openActions == 0 -> "Pick up where you left off, whenever you're ready."
        openActions == 1 -> "One commitment is still open — want to check it off, or talk through what's in the way?"
        openActions > 1 -> "$openActions commitments are open. Want to talk one through, or take a minute to reset first?"
        hour < 5 -> "Whatever has you up — we can think it through, or just breathe for a minute."
        hour >= 17 -> "Anything from today worth a few minutes before tomorrow?"
        else -> "What's the moment in front of you? Two minutes of prep changes how it goes."
    }
    return greeting to say
}

/** A faint hour-tinted wash behind Today — the same signal the splash's sky now carries
 *  (dawn rose / day periwinkle / dusk violet / deep night), so the brand moment the splash
 *  earned doesn't stop the instant it hands off to the first real screen. Deliberately
 *  subtle: a hint at the top of the page, resolving to the ordinary theme background —
 *  Today is a place to work, not a rendering of the sky. */
private fun homeSkyBrush(hour: Int): Brush {
    val accent = when (hour) {
        in 5..7 -> androidx.compose.ui.graphics.Color(0xFFB56B7A)
        in 8..16 -> androidx.compose.ui.graphics.Color(0xFF6C7BD8)
        in 17..20 -> androidx.compose.ui.graphics.Color(0xFF7A5AA8)
        else -> androidx.compose.ui.graphics.Color(0xFF2A2E5C)
    }
    val top = androidx.compose.ui.graphics.lerp(NightMid, accent, 0.16f)
    return Brush.verticalGradient(listOf(top, Night, Night))
}

/** A light entrance cascade for Today's top-level cards — the orb, then the check-in, then
 *  each door, roughly 45ms apart — so the page arrives as a considered sequence instead of
 *  the single shared fade [Page] already gives the whole column. Reduce Motion shows the
 *  final frame immediately. */
@Composable
private fun StaggerItem(index: Int, content: @Composable () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val appear = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) { appear.snapTo(1f); return@LaunchedEffect }
        delay(index * 45L)
        appear.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }
    Box(Modifier.graphicsLayer { translationY = (1f - appear.value) * 18f; alpha = appear.value }) {
        content()
    }
}

/** One Today "door" — icon, copy, accent, and a RELEVANCE score, so the stack can be sorted
 *  by what's actually true right now instead of a fixed source order. A person who never
 *  opens Journeys was seeing it ranked above Rest & recovery every day, forever; this is the
 *  fix (backlog HOME_SPEC #1). `internal` so the ordering rules are testable off-device. */
internal data class Door(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val desc: String,
    val accent: Color,
    val priority: Int,
    val route: String,
)

/** Builds Today's doors and their relevance for THIS moment. Pure and deterministic — no
 *  Compose/coroutine dependency — so the ordering/state-aware-copy rules are unit-testable.
 *
 *  - Commitments rises with how many are open (#1).
 *  - Journeys carries "Day X of Y" and outranks the generic doors once a program is active,
 *    instead of staying silent about it (#2, #5).
 *  - Wind-down is PROMOTED near the top in the evening window, not merely appended (#3).
 *  - Need a human rises when several commitments are open — a real (if simple) signal that
 *    more support might help — never on urgency-styling, only on ordering (#4). */
internal fun buildDoors(hour: Int, open: Int, activeProgram: JSONObject?): List<Door> {
    val programDay = activeProgram?.optInt("day") ?: 0
    val programDays = activeProgram?.optInt("days") ?: 0
    val programTitle = activeProgram?.optString("title").orEmpty()
    val programDesc = if (activeProgram != null && programDay > 0 && programDays > 0 && programTitle.isNotBlank()) {
        "Day $programDay of $programDays — $programTitle"
    } else null

    val doors = listOf(
        Door(
            key = "actions", icon = Icons.Outlined.TaskAlt, title = "Commitments",
            desc = when {
                open == 0 -> "Nothing open — your next session ends with one concrete step."
                open == 1 -> "1 open commitment waiting on you."
                else -> "$open open commitments waiting on you."
            },
            accent = BrandPrimary, priority = 40 + open * 15, route = "actions",
        ),
        Door(
            key = "journeys", icon = Icons.Outlined.Explore,
            title = if (programDesc != null) "Continue your journey" else "Journeys",
            desc = programDesc
                ?: "Multi-week practice for the skills that used to take a decade: feedback, delegation, influence.",
            // `Ok` (mint), not `Periwinkle` — device-verified 2026-07-20 that "Periwinkle" in
            // THIS app's night palette (Color.kt NightPalette.periwinkle = 0xFFF58A8A) is
            // actually a coral-pink, not the cool blue-violet its name suggests — it read as
            // visually identical to Commitments' coral and, worse, to Wind-down's original
            // "fix" below (HOME_SPEC #26 corrected).
            accent = Ok, priority = if (programDesc != null) 70 else 35, route = "journeys",
        ),
        Door(
            key = "toolkit", icon = Icons.Outlined.SelfImprovement, title = "Reset toolkit",
            desc = "Two minutes between meetings: breathe, ground, or play something calm.",
            accent = Cyan, priority = 30, route = "toolkit",
        ),
        Door(
            key = "sleep", icon = Icons.Outlined.Bedtime, title = "Rest & recovery",
            desc = "Sleep scenes, soundscapes, and the mixer — sustainable performance is rested performance.",
            // A deeper cyan, not the SAME cyan as Reset toolkit — the two used to be
            // distinguishable only by icon (HOME_SPEC #26).
            accent = androidx.compose.ui.graphics.lerp(Cyan, Night, 0.28f), priority = 28, route = "sleep",
        ),
    ) + (
        if (hour >= 20 || hour < 3) {
            listOf(
                Door(
                    key = "winddown", icon = Icons.Outlined.NightsStay, title = "Wind down for tonight",
                    desc = "Four small steps to close the day — a few unhurried minutes.",
                    // A genuine indigo-blue — NOT `Periwinkle`/`Violet` (both resolve to
                    // coral-family hues in this app's actual night palette; device-verified
                    // 2026-07-20 that the original fix here was visually identical to
                    // Journeys' coral). Reuses the exact hex the splash's day-sky uses
                    // (ui/Brand.kt:190, mirrored by this file's own homeSkyBrush) — the one
                    // evening/night-appropriate cool hue already established elsewhere in the
                    // app (HOME_SPEC #26 corrected). NOTE: that makes 0xFF6C7BD8 a literal in
                    // three places now; it wants promoting to a named token in Color.kt so the
                    // token-drift test can see it (docs/ANDROID_QA.md).
                    accent = Color(0xFF6C7BD8), priority = 85, route = "winddown",
                ),
            )
        } else emptyList()
    ) + listOf(
        Door(
            key = "human", icon = Icons.Outlined.Diversity3, title = "Need a human?",
            desc = "Talking to a person is always an option — support paths live here.",
            accent = Warm, priority = if (open >= 3) 45 else 20, route = "humansupport",
        ),
    )
    // sortedByDescending is a STABLE sort — equal-priority doors keep their listed
    // (semantically deliberate) relative order rather than reshuffling on every render.
    //
    // NOTE: this was chained directly onto the `return` expression above once (`... ) +
    // listOf(human)).sortedByDescending { ... }` on one statement) and silently did NOTHING —
    // `.` binds tighter than `+`, so it sorted only the trailing one-Door list, a no-op, while
    // every other door kept its plain concatenation order. Caught by
    // CoachHomeTest — assigning the concatenation to `doors` first, THEN sorting as its own
    // statement, makes the precedence unambiguous.
    return doors.sortedByDescending { it.priority }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayHome(onOpen: (String) -> Unit) {
    ActionsStore.load()
    val open = ActionsStore.openCount()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    // HomeCache is warmed during the splash's Session.warmBoot() so this composition usually
    // arrives with real data already in hand. This is only the COLD fallback — a config
    // change re-entry, or a warmBoot() that timed out — so the greeting is never permanently
    // nameless. See net/HomeCache.kt.
    LaunchedEffect(Unit) {
        if (HomeCache.name == null && HomeCache.moods == null && HomeCache.streak == null) {
            HomeCache.warm()
        }
    }
    val userName = HomeCache.name
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val (greeting, say) = presenceLines(userName, open, hour, HomeCache.resumable)
    // Whether today is already checked in — read from the SAME shared cache CheckInCard
    // reads, so the presence orb's "done" tell (a calmer, warmer glow) and the check-in
    // card never disagree with each other.
    val doneToday = remember(HomeCache.moods) {
        HomeCache.moods?.let { checkedInToday(checkInDates(it), java.time.LocalDate.now()) } ?: false
    }
    val doors = remember(open, hour, HomeCache.activeProgram) { buildDoors(hour, open, HomeCache.activeProgram) }
    // The first-ever visit to Today gets one small, dismissable orientation line; every
    // return visit — the overwhelming majority — sees the ordinary screen (HOME_SPEC #6).
    var showIntro by remember { mutableStateOf(Session.prefGet("home_intro_seen") == null) }

    Box(Modifier.fillMaxSize().background(homeSkyBrush(hour))) {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                HomeCache.warm()
                refreshing = false
            }
        },
    ) {
    Page(eyebrow = "CereBroZen", title = greeting, leadingMark = true) {
        // Every call that fed Home failed and there is NOTHING cached to show in its place —
        // a single flaky endpoint must not blank a screen that still has other data; this is
        // the genuine "we have nothing" floor, with the one action that helps.
        if (HomeCache.failed && userName == null && HomeCache.moods == null) {
            SectionCard {
                Text("Couldn't load Today", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "Check your connection and try again.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                )
                TextButton(onClick = { scope.launch { HomeCache.warm() } }) {
                    Text("Try again", color = BrandPrimary)
                }
            }
        }
        var stagger = 0
        // The first-ever visit gets one small, dismissable orientation line — every return
        // visit (almost every visit) sees the ordinary screen (HOME_SPEC #6).
        if (showIntro) {
            StaggerItem(stagger++) {
                SectionCard {
                    Text("This is your Today", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        "Check in, talk it through, or just breathe — every day starts here.",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                    TextButton(onClick = { Session.prefPut("home_intro_seen", "1"); showIntro = false }) {
                        Text("Got it", color = BrandPrimary)
                    }
                }
            }
        }
        // The daily check-in. Api.checkIn had exactly one caller — onboarding — so the app
        // asked how you were at signup and never again, while PRODUCT.md ships check-ins as
        // v1. Absent entirely (not disabled) if they declined mood history.
        StaggerItem(stagger++) { CheckInCard() }
        StaggerItem(stagger++) { FocusCard {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PresenceOrb(done = doneToday)
                Text(
                    say,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        PrimaryButton(if (HomeCache.resumable) "Continue talking" else "Talk it through") {
                            onOpen("coach")
                        }
                    }
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(999.dp))
                            .background(ChipFill)
                            .clickable { onOpen("breathe/reset") }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Breathe", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    }
                }
            }
        } }
        // Sorted by relevance, not fixed source order — see buildDoors (HOME_SPEC #1-#5).
        // On a tablet/unfolded foldable (>= 600dp, Material's medium breakpoint) the doors
        // run two-up instead of stretching a single column across the whole width
        // (HOME_SPEC #28). Order is untouched — pairs are just adjacent rows, side by side.
        val wide = LocalConfiguration.current.screenWidthDp >= 600
        if (wide) {
            doors.chunked(2).forEach { pair ->
                StaggerItem(stagger++) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        pair.forEach { door ->
                            Box(Modifier.weight(1f)) {
                                DoorCard(icon = door.icon, title = door.title, desc = door.desc, accent = door.accent) {
                                    onOpen(door.route)
                                }
                            }
                        }
                        // An odd door out stays half-width, not stretched to fill the row.
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        } else {
            doors.forEach { door ->
                StaggerItem(stagger++) {
                    DoorCard(icon = door.icon, title = door.title, desc = door.desc, accent = door.accent) {
                        onOpen(door.route)
                    }
                }
            }
        }
    }
    }
    }
}

/** A Today "door": a leading icon in a tinted circle, the title + line, and a chevron —
 * so the home cards read as tappable and carry the same icon language as the You tab,
 * instead of being bare text blocks. */
@Composable
private fun DoorCard(
    icon: ImageVector,
    title: String,
    desc: String,
    accent: Color,
    onClick: () -> Unit,
) {
    SectionCard(onClick = onClick) {
        Row(
            // The title + description merge into ONE spoken announcement for the row
            // (HOME_SPEC #29) — without this, TalkBack read the title, the description, and
            // then a bare "›" glyph as three disconnected fragments instead of "opens X".
            modifier = Modifier.semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(46.dp)
                    .background(accent.copy(alpha = 0.13f), CircleShape)
                    .border(1.dp, accent.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                // Commitments' count-bearing line used to hard-swap the instant a commitment
                // was completed elsewhere and Home recomposed — a counted change crossfades
                // in rather than jump-cutting (HOME_SPEC #21).
                androidx.compose.animation.Crossfade(targetState = desc, label = "door-desc") { d ->
                    Text(d, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
            }
            // Purely decorative now that the row itself carries the merged announcement —
            // otherwise TalkBack read this glyph out as its own, meaningless fragment.
            Text(
                "›", style = MaterialTheme.typography.titleLarge, color = TextMuted2,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

// ── Actions: the commitments tab ─────────────────────────────────────────────

@Composable
fun ActionsScreen(onOpen: (String) -> Unit) {
    ActionsStore.load()
    val items = ActionsStore.items
    Page(eyebrow = "Follow-through", title = "Actions") {
        if (items.isEmpty()) {
            SectionCard {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EmptyStateArt(kind = "journal")
                    Text(
                        "No commitments yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Text(
                        "Every coaching session ends with one small, concrete step. It lands here — and your coach asks how it went.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                    )
                    PrimaryButton("Start a session") { onOpen("coach") }
                }
            }
        } else {
            items.forEach { action ->
                SectionCard(
                    onClick = {
                        ActionsStore.setStatus(
                            action.id,
                            if (action.status == "active") "done" else "active",
                        )
                    },
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            action.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (action.status == "done") TextMuted else TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (action.status == "done") "done" else "open",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (action.status == "done") Ok else Accent.talk,
                        )
                    }
                }
            }
            Text(
                "Tap a commitment to mark it done — your coach will ask about open ones at your next check-in.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
    }
}
