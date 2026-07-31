package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.OnPrimary
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.PeriwinkleDeep
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextSoft

/**
 * Where a day sits relative to the day the enrollment is on.
 *
 * Deliberately NOT "done / locked". A [ProgramEnrollment][com.cerebrozen.app]
 * counts days from its start date and records nothing per day, so the app does
 * not know whether anyone actually did Tuesday — only that Tuesday has been and
 * gone. [PASSED] says exactly that much and no more; calling it "completed"
 * would be the app congratulating a user for the passage of time.
 *
 * And nothing is ever [locked]. A program here is a suggested order, not a
 * syllabus: every day opens, in any order, on any day. Withholding a coping
 * practice until someone has earned it is the opposite of what this product is
 * for — the person who needs Friday's wind-down tonight is often exactly the
 * person who has not opened Monday.
 */
enum class DayState { PASSED, TODAY, AHEAD }

/** Pure: where day [day] (1-based) sits when the enrollment is on [currentDay]. */
internal fun dayState(day: Int, currentDay: Int): DayState = when {
    day < currentDay -> DayState.PASSED
    day == currentDay -> DayState.TODAY
    else -> DayState.AHEAD
}

/**
 * The serpentine offset of a node, as a -1..1 horizontal bias.
 *
 * A four-phase cycle (centre → right → centre → left) rather than a plain
 * left/right alternation, so the connecting line reads as one continuous
 * meander instead of a zig-zag. Pure, so the geometry is testable and the
 * Canvas and the nodes cannot drift apart — they both derive from this.
 */
internal fun nodeBias(index: Int): Float = when (index % 4) {
    0 -> 0f
    1 -> 0.62f
    2 -> 0f
    else -> -0.62f
}

/**
 * A program's days as a winding path — the whole week visible at once.
 *
 * Every node is tappable and opens that day's guide inline. There is no lock,
 * no streak, no score: see [DayState] for why. The one thing the path asserts
 * is where today falls, which is the one thing the data actually knows.
 */
@Composable
internal fun JourneyPath(
    guides: List<Pair<String, String>>,
    currentDay: Int,
    modifier: Modifier = Modifier,
) {
    if (guides.isEmpty()) return
    var selected by remember(currentDay) { mutableIntStateOf(currentDay.coerceIn(1, guides.size)) }

    // Tuned on a 720x1604 device: at 96dp a seven-day program was 672dp of path
    // and filled the screen on its own, so the week could not be taken in at a
    // glance — which is the entire reason the path exists.
    val stepHeight = 74.dp
    val nodeSize = 50.dp
    val density = LocalDensity.current

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(stepHeight * guides.size)) {
            // The connector, drawn from the same nodeBias() the nodes align to.
            Canvas(Modifier.fillMaxSize()) {
                val step = with(density) { stepHeight.toPx() }
                val inset = with(density) { nodeSize.toPx() } / 2f + 8f
                fun centre(i: Int) = Offset(
                    x = size.width / 2f + nodeBias(i) * (size.width / 2f - inset),
                    y = i * step + step / 2f,
                )
                val path = Path().apply {
                    moveTo(centre(0).x, centre(0).y)
                    for (i in 1 until guides.size) {
                        val a = centre(i - 1)
                        val b = centre(i)
                        // Control point on a's column at the midpoint height: the
                        // line leaves each node vertically and arrives vertically,
                        // so nodes sit ON the path rather than beside it.
                        quadraticTo(a.x, (a.y + b.y) / 2f, b.x, b.y)
                    }
                }
                drawPath(
                    path,
                    color = LineStroke,
                    style = Stroke(width = with(density) { 3.dp.toPx() }, cap = StrokeCap.Round),
                )
            }
            guides.forEachIndexed { i, _ ->
                val day = i + 1
                val state = dayState(day, currentDay)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(stepHeight * guides.size)
                        .padding(top = stepHeight * i),
                ) {
                    Box(
                        Modifier
                            .align(BiasAlignment(nodeBias(i), -1f))
                            .padding(top = (stepHeight - nodeSize) / 2)
                            .size(nodeSize),
                    ) {
                        DayNode(day = day, state = state, selected = day == selected) { selected = day }
                    }
                }
            }
        }

        // The selected day's guide. Always present, so a tap never leads nowhere.
        val (title, body) = guides[(selected - 1).coerceIn(guides.indices)]
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CardFill)
                .border(1.dp, LineStroke, RoundedCornerShape(18.dp))
                .padding(16.dp),
        ) {
            Column {
                Text(
                    dayEyebrow(selected, currentDay),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
                if (title.isNotBlank()) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
                }
                if (body.isNotBlank()) {
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
            }
        }
    }
}

/** "Day 3 · today" / "Day 1 · earlier this week" / "Day 5 · coming up". */
@Composable
private fun dayEyebrow(day: Int, currentDay: Int): String = stringResource(
    when (dayState(day, currentDay)) {
        DayState.TODAY -> R.string.programs_path_day_today
        DayState.PASSED -> R.string.programs_path_day_passed
        DayState.AHEAD -> R.string.programs_path_day_ahead
    },
    day,
)

@Composable
private fun DayNode(day: Int, state: DayState, selected: Boolean, onClick: () -> Unit) {
    // Today is the only filled node — the one fact the enrollment actually
    // knows. Days that have been and gone are outlined, not ticked.
    val fill = when {
        state == DayState.TODAY -> Periwinkle
        selected -> PeriwinkleDeep
        else -> CardFill
    }
    val ink = if (state == DayState.TODAY) OnPrimary else TextSoft
    val ring = when {
        selected -> Periwinkle
        state == DayState.PASSED -> LineStroke
        else -> LineStroke.copy(alpha = 0.55f)
    }
    val label = stringResource(
        when (state) {
            DayState.TODAY -> R.string.programs_path_a11y_today
            DayState.PASSED -> R.string.programs_path_a11y_passed
            DayState.AHEAD -> R.string.programs_path_a11y_ahead
        },
        day,
    )
    Box(
        Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, ring, CircleShape)
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (state == DayState.AHEAD && !selected) TextMuted else ink,
        )
    }
}
