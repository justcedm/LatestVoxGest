package com.voxgest.dryrun.ui

import android.content.Context
import org.json.JSONObject

internal enum class FslGuideCategory(val displayName: String) {
    CALENDAR("Calendar & time"),
    COLORS("Colors"),
    FAMILY_PEOPLE("Family & people"),
    FOOD_DRINK("Food & drink"),
    NUMBERS("Numbers"),
    CONVERSATION("Conversation"),
    OTHER("Everyday signs")
}

internal data class FslGuideEntry(
    val label: String,
    val filipinoTranslation: String?,
    val category: FslGuideCategory,
    val datasetStatus: String = "DATASET VOCABULARY",
    val liveValidationStatus: String = "NOT YET LIVE VALIDATED",
    val tutorialStatus: String = "TUTORIAL MEDIA NOT PACKAGED",
    val source: String = "FSL-105 selected runtime label asset"
)

/** Source-record facts verified against the Mendeley Data version-of-record page. */
internal object Fsl105DatasetFacts {
    const val TITLE = "FSL-105: A dataset for recognizing 105 Filipino sign language videos"
    const val CONTRIBUTOR = "Isaiah Jassen Tupal"
    const val INSTITUTION = "De La Salle University"
    const val SOURCE = "Mendeley Data"
    const val PUBLISHED = "7 March 2023"
    const val DOI = "10.17632/48y2y99mb9.1"
    const val VERSION = "Version 1"
    const val LICENSE = "CC BY 4.0"
    const val CLASS_COUNT = 105
    const val VIDEO_COUNT = 2_130
    const val CLIP_DURATION_SECONDS = 4
}

/**
 * Presentation catalogue backed by the exact labels packaged with the selected FSL-105 runtime.
 * This is intentionally independent of the smaller live/demo allowlist: dataset membership does
 * not claim that a sign has passed a Samsung live-camera acceptance test.
 */
internal object Fsl105GuideCatalog {
    const val ASSET_PATH =
        "model/fsl_fullsign225_20f_105_v1/class_labels_fsl105_fullsign225_v1.json"
    const val EXPECTED_FEATURE_VERSION = "fullsign225_20f_v1"
    const val EXPECTED_LABEL_COUNT = 105

    fun load(context: Context): List<FslGuideEntry> =
        context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { reader ->
            parse(reader.readText())
        }

    internal fun parse(json: String): List<FslGuideEntry> {
        val root = JSONObject(json)
        require(root.getString("feature_version") == EXPECTED_FEATURE_VERSION) {
            "Guide feature version does not match the selected FSL-105 runtime"
        }
        require(root.getInt("class_count") == EXPECTED_LABEL_COUNT) {
            "Guide manifest class count must be $EXPECTED_LABEL_COUNT"
        }
        val labelsJson = root.getJSONArray("labels")
        val labels = List(labelsJson.length()) { index -> labelsJson.getString(index) }
        require(labels.size == EXPECTED_LABEL_COUNT) {
            "Guide inventory must contain exactly $EXPECTED_LABEL_COUNT labels"
        }
        require(labels.distinct().size == EXPECTED_LABEL_COUNT) {
            "Guide inventory contains duplicate labels"
        }
        require(labels.all { it.isNotBlank() }) { "Guide inventory contains a blank label" }

        return labels.map { label ->
            FslGuideEntry(
                label = label,
                // No verified Filipino translation map is packaged for the 105-class vocabulary.
                // Keep this null rather than presenting an invented or machine-translated gloss.
                filipinoTranslation = null,
                category = categoryFor(label)
            )
        }
    }

    private fun categoryFor(label: String): FslGuideCategory = when (label) {
        in CALENDAR_LABELS -> FslGuideCategory.CALENDAR
        in COLOR_LABELS -> FslGuideCategory.COLORS
        in FAMILY_PEOPLE_LABELS -> FslGuideCategory.FAMILY_PEOPLE
        in FOOD_DRINK_LABELS -> FslGuideCategory.FOOD_DRINK
        in NUMBER_LABELS -> FslGuideCategory.NUMBERS
        in CONVERSATION_LABELS -> FslGuideCategory.CONVERSATION
        else -> FslGuideCategory.OTHER
    }

    private val CALENDAR_LABELS = setOf(
        "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY",
        "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER",
        "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY",
        "SUNDAY", "TODAY", "TOMORROW", "YESTERDAY"
    )
    private val COLOR_LABELS = setOf(
        "BLACK", "BLUE", "BROWN", "DARK", "GRAY", "GREEN", "LIGHT", "ORANGE",
        "PINK", "RED", "VIOLET", "WHITE", "YELLOW"
    )
    private val FAMILY_PEOPLE_LABELS = setOf(
        "AUNTIE", "BLIND", "BOY", "COUSIN", "DAUGHTER", "DEAF", "DEAF BLIND",
        "FATHER", "GIRL", "GRANDFATHER", "GRANDMOTHER", "HARD OF HEARING", "MAN",
        "MARRIED", "MOTHER", "PARENTS", "SON", "UNCLE", "WEELCHAIR PERSON", "WOMAN"
    )
    private val FOOD_DRINK_LABELS = setOf(
        "BEER", "BREAD", "CHICKEN", "COFFEE", "CRAB", "EGG", "FISH", "JUICE",
        "LONGANISA", "MEAT", "MILK", "NO SUGAR", "RICE", "SHRIMP", "SPAGHETTI",
        "SUGAR", "TEA", "WINE"
    )
    private val NUMBER_LABELS = setOf(
        "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN"
    )
    private val CONVERSATION_LABELS = setOf(
        "CORRECT", "DON’T KNOW", "DON’T UNDERSTAND", "GOOD AFTERNOON", "GOOD EVENING",
        "GOOD MORNING", "HELLO", "HOW ARE YOU", "IM FINE", "KNOW", "NICE TO MEET YOU",
        "NO", "SEE YOU TOMORROW", "THANK YOU", "UNDERSTAND", "WRONG", "YES", "YOURE WELCOME"
    )
}
