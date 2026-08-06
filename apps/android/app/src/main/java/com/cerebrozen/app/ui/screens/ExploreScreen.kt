package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cerebrozen.app.R
import com.cerebrozen.app.ui.theme.Accent
import com.cerebrozen.app.ui.theme.TextSecondary
import com.cerebrozen.app.ui.theme.Warm

/**
 * Explore — the need-based hub that took Sleep's slot in the tab bar.
 *
 * Owner ruling (docs/REDESIGN_V2.md §6.1): the tabs become the spec's five —
 * Today · Explore · Talk · Journal · You — and Sleep is reached from here
 * rather than from a tab of its own. That demotion is a real product cost
 * (sleep is this product's evidenced flagship, g=0.71) and the ruling records
 * it as decided rather than drifted into; if sleep engagement drops, this is
 * the first thing to re-examine.
 *
 * Every row lands on a destination that already exists — this screen adds a
 * door, never a feature. It is deliberately a flat list of six practice
 * families plus the support door: a hub whose job is "what do you need right
 * now" fails the moment it needs its own sub-navigation.
 *
 * Built entirely from the shared frames ([Page], [SectionCard] via [NavRow]),
 * so it inherits the entry rise, the Reduce Motion branch, the 48dp targets
 * and the token contrast gate rather than re-implementing any of them.
 */
@Composable
fun ExploreScreen(onOpen: (String) -> Unit) {
    Page(
        eyebrow = stringResource(R.string.explore_eyebrow),
        title = stringResource(R.string.explore_title),
        accent = Accent.default,
    ) {
        Text(
            stringResource(R.string.explore_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        SectionGap()

        // Descending daily relevance, and the two evidenced pillars first.
        ExploreRow(
            R.string.explore_sleep_title, R.string.explore_sleep_subtitle,
            Icons.Outlined.Bedtime, "sleep", onOpen,
        )
        ExploreRow(
            R.string.explore_calm_title, R.string.explore_calm_subtitle,
            Icons.Outlined.Air, "breathe/reset", onOpen,
        )
        ExploreRow(
            R.string.explore_sounds_title, R.string.explore_sounds_subtitle,
            Icons.Outlined.GraphicEq, "sounds", onOpen,
        )
        ExploreRow(
            R.string.explore_thought_title, R.string.explore_thought_subtitle,
            Icons.Outlined.Psychology, "cbt", onOpen,
        )
        ExploreRow(
            R.string.explore_mindful_title, R.string.explore_mindful_subtitle,
            Icons.Outlined.Spa, "toolkit", onOpen,
        )
        ExploreRow(
            R.string.explore_programmes_title, R.string.explore_programmes_subtitle,
            Icons.Outlined.Route, "programs", onOpen,
        )

        // The support door. It is not one of the six families and does not
        // dress like one — it is here because a hub that asks "what do you need
        // right now" has to have an answer for the hardest version of that
        // question, and because losing the Sleep tab must not lengthen anybody's
        // path to a helpline. (The You tab's Support card is the other door;
        // between them, urgent support stays two taps from every tab.)
        SectionGap()
        NavRow(
            title = stringResource(R.string.explore_support_title),
            subtitle = stringResource(R.string.explore_support_subtitle),
            icon = Icons.Outlined.HealthAndSafety,
            tint = Warm,
        ) { onOpen("crisis") }
    }
}

/** One practice family: the shared [NavRow], nothing bespoke. */
@Composable
private fun ColumnScope.ExploreRow(
    @androidx.annotation.StringRes titleRes: Int,
    @androidx.annotation.StringRes subtitleRes: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    route: String,
    onOpen: (String) -> Unit,
) = NavRow(
    title = stringResource(titleRes),
    subtitle = stringResource(subtitleRes),
    icon = icon,
) { onOpen(route) }
