package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.Danger
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextSoft
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** The six Stanley-Brown sections, in the order the user meets them. */
internal val SAFETY_PLAN_FIELDS = listOf(
    "warning_signs",
    "internal_coping",
    "social_distractors",
    "social_support",
    "professionals",
    "means_safety",
    "notes",
)

internal fun parseSafetyPlan(payload: JSONObject?): Map<String, String> {
    if (payload == null) return emptyMap()
    return SAFETY_PLAN_FIELDS.associateWith { payload.optString(it) }
}

/** Round-trips the section texts through one JSON string so the whole plan
 * survives activity recreation — a theme switch, resize or process death must
 * never discard the user's words on this screen of all screens. */
internal val SAFETY_PLAN_VALUES_SAVER = Saver<Map<String, String>, String>(
    save = { JSONObject(it).toString() },
    restore = { raw -> runCatching { parseSafetyPlan(JSONObject(raw)) }.getOrDefault(emptyMap()) },
)

/**
 * A personal safety plan, in the user's own words.
 *
 * Two rules this screen exists to keep:
 *
 * 1. The user writes it — no section is pre-filled by the model.
 * 2. It opens without a network. `Session.api` already serves the last cached
 *    GET when a read fails, which is exactly the behaviour this screen needs:
 *    a plan you can only read online is worthless at the moment it matters.
 *    `Session.servedStale` says so honestly rather than hiding it.
 */
@Composable
fun SafetyPlanScreen(onBack: () -> Unit) {
    var values by rememberSaveable(stateSaver = SAFETY_PLAN_VALUES_SAVER) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    // True once the first server read has run — after recreation the restored
    // (possibly unsaved) texts must NOT be clobbered by a refetch.
    var hydrated by rememberSaveable { mutableStateOf(false) }
    var version by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var savingField by remember { mutableStateOf<String?>(null) }
    var savedField by remember { mutableStateOf<String?>(null) }
    // Keyed BY FIELD. A save error used to render once at the top of the page
    // while the "Saved" confirmation rendered beside the button — so on a screen
    // seven sections long, success was local and failure was somewhere off
    // screen above. On a safety plan that is exactly backwards: the outcome you
    // must not miss is the one that did not work.
    var error by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Distinguishes "your plan is empty" from "we could not load it". Without
    // this the failure path set values to emptyMap() and said nothing, so a user
    // opening the screen offline before it had ever cached would be shown seven
    // blank boxes where their plan should be — and could only conclude it was
    // gone.
    var loadFailed by remember { mutableStateOf(false) }
    // Whether THIS read came from the cache, captured at the moment it returned.
    // `Session.servedStale` is a global flag about the last GET anywhere in the
    // app, so reading it here announced "showing the copy saved on this device"
    // because Home had served stale — even when this screen had no copy at all
    // and was showing seven empty boxes. Two contradictory banners at once, on
    // the safety plan. Seen on device.
    var servedFromCache by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val saveFailed = stringResource(R.string.safetyplan_save_failed)

    val labels = mapOf(
        "warning_signs" to stringResource(R.string.safetyplan_warning_signs),
        "internal_coping" to stringResource(R.string.safetyplan_internal_coping),
        "social_distractors" to stringResource(R.string.safetyplan_social_distractors),
        "social_support" to stringResource(R.string.safetyplan_social_support),
        "professionals" to stringResource(R.string.safetyplan_professionals),
        "means_safety" to stringResource(R.string.safetyplan_means_safety),
        "notes" to stringResource(R.string.safetyplan_notes),
    )
    val hints = mapOf(
        "warning_signs" to stringResource(R.string.safetyplan_warning_signs_hint),
        "internal_coping" to stringResource(R.string.safetyplan_internal_coping_hint),
        "social_distractors" to stringResource(R.string.safetyplan_social_distractors_hint),
        "social_support" to stringResource(R.string.safetyplan_social_support_hint),
        "professionals" to stringResource(R.string.safetyplan_professionals_hint),
        "means_safety" to stringResource(R.string.safetyplan_means_safety_hint),
        "notes" to stringResource(R.string.safetyplan_notes_hint),
    )

    LaunchedEffect(Unit) {
        if (hydrated) { loading = false; return@LaunchedEffect }
        runCatching { Api.safetyPlan() }
            .onSuccess { plan ->
                values = parseSafetyPlan(plan)
                version = plan?.optInt("version")
                servedFromCache = Session.servedStale
            }
            .onFailure { values = emptyMap(); loadFailed = true; servedFromCache = false }
        hydrated = true
        loading = false
    }

    fun reload() {
        loading = true; loadFailed = false
        scope.launch {
            runCatching { Api.safetyPlan() }
                .onSuccess { plan ->
                    values = parseSafetyPlan(plan)
                    version = plan?.optInt("version")
                    servedFromCache = Session.servedStale
                }
                .onFailure { values = emptyMap(); loadFailed = true; servedFromCache = false }
            loading = false
        }
    }

    LaunchedEffect(savedField) {
        if (savedField != null) { delay(2500); savedField = null }
    }

    SubPage(
        stringResource(R.string.safetyplan_eyebrow),
        stringResource(R.string.safetyplan_title),
        onBack,
    ) {
        Text(stringResource(R.string.safetyplan_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Text(stringResource(R.string.safetyplan_privacy_note),
            style = MaterialTheme.typography.bodySmall, color = TextMuted)

        if (servedFromCache) {
            Text(stringResource(R.string.safetyplan_offline),
                style = MaterialTheme.typography.bodySmall, color = Periwinkle)
        }
        if (loadFailed) {
            SectionCard {
                Text(stringResource(R.string.safetyplan_load_failed_title),
                    style = MaterialTheme.typography.titleMedium, color = Danger)
                Text(stringResource(R.string.safetyplan_load_failed_body),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                TextButton(onClick = { reload() }) {
                    Text(stringResource(R.string.common_try_again), color = Periwinkle)
                }
            }
        }

        if (loading) {
            Text(stringResource(R.string.patterns_loading),
                style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        } else {
            SAFETY_PLAN_FIELDS.forEach { field ->
                SectionCard {
                    Text(labels[field] ?: field,
                        style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    Text(hints[field] ?: "",
                        style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    // B66: AppTextField (the app's own field chrome), with
                    // the section name as a REAL label — TalkBack used to hear
                    // a bare "edit box", and field identity is safety-relevant.
                    AppTextField(
                        value = values[field] ?: "",
                        onValueChange = { values = values + (field to it) },
                        label = labels[field] ?: field,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(
                            enabled = savingField != field,
                            onClick = {
                                savingField = field
                                error = null
                                scope.launch {
                                    runCatching {
                                        Api.saveSafetyPlan(
                                            JSONObject().put(field, values[field] ?: ""),
                                        )
                                    }
                                        .onSuccess {
                                            version = it.optInt("version")
                                            savedField = field
                                        }
                                        .onFailure { error = field to (it.userMessage(saveFailed)) }
                                    savingField = null
                                }
                            },
                        ) {
                            Text(
                                if (savingField == field) stringResource(R.string.common_one_moment)
                                else stringResource(R.string.patterns_save),
                                color = Periwinkle,
                            )
                        }
                        if (savedField == field) {
                            Text(stringResource(R.string.safetyplan_saved),
                                style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                    // Beside the section it belongs to, not at the top of the page.
                    error?.takeIf { it.first == field }?.let { (_, message) ->
                        Text(message, style = MaterialTheme.typography.bodySmall, color = Danger)
                    }
                }
            }
            version?.let {
                Text(stringResource(R.string.safetyplan_version, it),
                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            // Seven per-section saves need one ending — without this the only
            // way out of the screen is the back arrow, which reads as
            // abandoning rather than finishing (CTA audit H16).
            PrimaryButton(
                text = stringResource(R.string.safetyplan_done),
                modifier = Modifier.fillMaxWidth(),
            ) { onBack() }
        }
    }
}
