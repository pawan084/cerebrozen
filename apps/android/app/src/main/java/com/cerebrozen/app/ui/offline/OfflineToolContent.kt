package com.cerebrozen.app.ui.offline

import androidx.annotation.StringRes
import com.cerebrozen.app.R

data class GuidedJourney(
    val id: String,
    @StringRes val titleRes: Int,
    val glyph: String,
    val steps: List<Int>,
    @StringRes val endingRes: Int,
)

data class BodyScanStep(@StringRes val partRes: Int, @StringRes val instructionRes: Int, val seconds: Int = 15)
data class GroundingStep(val count: Int, @StringRes val senseRes: Int, @StringRes val promptRes: Int)
data class InsightCard(@StringRes val kickerRes: Int, @StringRes val titleRes: Int, @StringRes val bodyRes: Int)

enum class OfflineProgramId { CbtI, Mbct }
data class OfflineModule(@StringRes val titleRes: Int, @StringRes val bodyRes: Int, @StringRes val practiceRes: Int)
data class OfflineProgram(
    val id: OfflineProgramId,
    @StringRes val eyebrowRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val modules: List<OfflineModule>,
)

object OfflineToolContent {
    val journeys = listOf(
        GuidedJourney("forest", R.string.oi_forest, "🌲", listOf(R.string.oi_forest_1, R.string.oi_forest_2, R.string.oi_forest_3, R.string.oi_forest_4, R.string.oi_forest_5), R.string.oi_forest_end),
        GuidedJourney("ocean", R.string.oi_ocean, "🌊", listOf(R.string.oi_ocean_1, R.string.oi_ocean_2, R.string.oi_ocean_3, R.string.oi_ocean_4, R.string.oi_ocean_5), R.string.oi_ocean_end),
        GuidedJourney("mountain", R.string.oi_mountain, "⛰", listOf(R.string.oi_mountain_1, R.string.oi_mountain_2, R.string.oi_mountain_3, R.string.oi_mountain_4, R.string.oi_mountain_5), R.string.oi_mountain_end),
        GuidedJourney("meadow", R.string.oi_meadow, "🌸", listOf(R.string.oi_meadow_1, R.string.oi_meadow_2, R.string.oi_meadow_3, R.string.oi_meadow_4, R.string.oi_meadow_5), R.string.oi_meadow_end),
    )

    val bodyScan = listOf(
        BodyScanStep(R.string.obs_feet, R.string.obs_feet_body),
        BodyScanStep(R.string.obs_legs, R.string.obs_legs_body),
        BodyScanStep(R.string.obs_pelvis, R.string.obs_pelvis_body),
        BodyScanStep(R.string.obs_belly, R.string.obs_belly_body),
        BodyScanStep(R.string.obs_chest, R.string.obs_chest_body),
        BodyScanStep(R.string.obs_hands, R.string.obs_hands_body),
        BodyScanStep(R.string.obs_shoulders, R.string.obs_shoulders_body),
        BodyScanStep(R.string.obs_face, R.string.obs_face_body),
    )

    val grounding = listOf(
        GroundingStep(5, R.string.ocg_see, R.string.ocg_see_body),
        GroundingStep(4, R.string.ocg_touch, R.string.ocg_touch_body),
        GroundingStep(3, R.string.ocg_hear, R.string.ocg_hear_body),
        GroundingStep(2, R.string.ocg_smell, R.string.ocg_smell_body),
        GroundingStep(1, R.string.ocg_taste, R.string.ocg_taste_body),
    )

    val insights = listOf(
        InsightCard(R.string.oir_insight, R.string.oir_title_1, R.string.oir_body_1),
        InsightCard(R.string.oir_reflect, R.string.oir_title_2, R.string.oir_body_2),
        InsightCard(R.string.oir_insight, R.string.oir_title_3, R.string.oir_body_3),
        InsightCard(R.string.oir_reflect, R.string.oir_title_4, R.string.oir_body_4),
        InsightCard(R.string.oir_insight, R.string.oir_title_5, R.string.oir_body_5),
        InsightCard(R.string.oir_reflect, R.string.oir_title_6, R.string.oir_body_6),
    )

    val cbtI = OfflineProgram(
        OfflineProgramId.CbtI, R.string.ocbti_eyebrow, R.string.ocbti_title, R.string.ocbti_subtitle,
        listOf(
            OfflineModule(R.string.ocbti_m1, R.string.ocbti_m1_body, R.string.ocbti_m1_practice),
            OfflineModule(R.string.ocbti_m2, R.string.ocbti_m2_body, R.string.ocbti_m2_practice),
            OfflineModule(R.string.ocbti_m3, R.string.ocbti_m3_body, R.string.ocbti_m3_practice),
            OfflineModule(R.string.ocbti_m4, R.string.ocbti_m4_body, R.string.ocbti_m4_practice),
            OfflineModule(R.string.ocbti_m5, R.string.ocbti_m5_body, R.string.ocbti_m5_practice),
            OfflineModule(R.string.ocbti_m6, R.string.ocbti_m6_body, R.string.ocbti_m6_practice),
        ),
    )

    val mbct = OfflineProgram(
        OfflineProgramId.Mbct, R.string.ombct_eyebrow, R.string.ombct_title, R.string.ombct_subtitle,
        listOf(
            OfflineModule(R.string.ombct_m1, R.string.ombct_m1_body, R.string.ombct_m1_practice),
            OfflineModule(R.string.ombct_m2, R.string.ombct_m2_body, R.string.ombct_m2_practice),
            OfflineModule(R.string.ombct_m3, R.string.ombct_m3_body, R.string.ombct_m3_practice),
            OfflineModule(R.string.ombct_m4, R.string.ombct_m4_body, R.string.ombct_m4_practice),
            OfflineModule(R.string.ombct_m5, R.string.ombct_m5_body, R.string.ombct_m5_practice),
            OfflineModule(R.string.ombct_m6, R.string.ombct_m6_body, R.string.ombct_m6_practice),
            OfflineModule(R.string.ombct_m7, R.string.ombct_m7_body, R.string.ombct_m7_practice),
            OfflineModule(R.string.ombct_m8, R.string.ombct_m8_body, R.string.ombct_m8_practice),
        ),
    )
}
