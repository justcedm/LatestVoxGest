package com.voxgest.dryrun.ui

import android.content.Context
import com.voxgest.dryrun.GradingRecognitionProfiles
import org.json.JSONObject
import java.security.MessageDigest

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
    val canonicalToken: String,
    val englishDisplay: String,
    val filipinoDisplay: String,
    val category: FslGuideCategory,
    val datasetStatus: String = "FSL-105 MODEL VOCABULARY",
    val liveValidationStatus: String = "NOT YET DEVICE-QUALIFIED",
    val tutorialStatus: String = "TUTORIAL MEDIA NOT PACKAGED",
    val source: String = "FSL-105 selected runtime label asset"
) {
    val label: String get() = canonicalToken
    val filipinoTranslation: String get() = filipinoDisplay
}

internal data class Fsl105PresentationEntry(
    val canonicalToken: String,
    val englishDisplay: String,
    val filipinoDisplay: String
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
    private const val ASSET_ROOT = "model/fsl_fullsign225_20f_105_v1"
    const val MANIFEST_ASSET_PATH = "$ASSET_ROOT/runtime_manifest.json"
    const val ASSET_PATH =
        "$ASSET_ROOT/class_labels_fsl105_fullsign225_v1.json"
    const val EXPECTED_FEATURE_VERSION = "fullsign225_20f_v1"
    const val EXPECTED_LABEL_COUNT = 105
    const val PRESENTATION_ASSET_PATH =
        "$ASSET_ROOT/presentation_fsl105_bilingual_v1.json"

    fun load(context: Context): List<FslGuideEntry> {
        return Fsl105PresentationCatalog.load(context).map { presentation ->
            FslGuideEntry(
                canonicalToken = presentation.canonicalToken,
                englishDisplay = presentation.englishDisplay,
                filipinoDisplay = presentation.filipinoDisplay,
                category = categoryFor(presentation.canonicalToken)
            )
        }
    }

    internal fun parse(labelsJson: String, presentationJson: String): List<FslGuideEntry> {
        val root = JSONObject(labelsJson)
        require(root.getString("feature_version") == EXPECTED_FEATURE_VERSION) {
            "Guide feature version does not match the selected FSL-105 runtime"
        }
        require(root.getInt("class_count") == EXPECTED_LABEL_COUNT) {
            "Guide manifest class count must be $EXPECTED_LABEL_COUNT"
        }
        val labelsArray = root.getJSONArray("labels")
        val labels = List(labelsArray.length()) { index -> labelsArray.getString(index) }
        require(labels.size == EXPECTED_LABEL_COUNT) {
            "Guide inventory must contain exactly $EXPECTED_LABEL_COUNT labels"
        }
        require(labels.distinct().size == EXPECTED_LABEL_COUNT) {
            "Guide inventory contains duplicate labels"
        }
        require(labels.all { it.isNotBlank() }) { "Guide inventory contains a blank label" }

        val presentations = Fsl105PresentationCatalog.parse(labelsJson, presentationJson)
        return presentations.map { presentation ->
            FslGuideEntry(
                canonicalToken = presentation.canonicalToken,
                englishDisplay = presentation.englishDisplay,
                filipinoDisplay = presentation.filipinoDisplay,
                category = categoryFor(presentation.canonicalToken)
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

/** Exact one-to-one semantic presentation map for the selected 105 canonical model tokens. */
internal object Fsl105PresentationCatalog {
    fun load(context: Context): List<Fsl105PresentationEntry> {
        val manifest = context.assets.open(Fsl105GuideCatalog.MANIFEST_ASSET_PATH)
            .bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        val root = GradingRecognitionProfiles.STANDARD_ASSET_ROOT
        val labelsAsset = "$root/${manifest.getString("labels_filename")}"
        val presentationAsset = "$root/${manifest.getString("presentation_filename")}"
        val labelsJson = context.assets.open(labelsAsset)
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val presentationBytes = context.assets.open(presentationAsset).use { it.readBytes() }
        val expectedSha = manifest.getJSONObject("artifacts")
            .getJSONObject("presentation").getString("sha256")
        val actualSha = MessageDigest.getInstance("SHA-256").digest(presentationBytes)
            .joinToString("") { "%02x".format(it) }
        require(actualSha.equals(expectedSha, ignoreCase = true)) {
            "FSL-105 presentation asset SHA-256 mismatch"
        }
        val presentationJson = presentationBytes.toString(Charsets.UTF_8)
        return parse(labelsJson, presentationJson)
    }

    fun parse(labelsJson: String, presentationJson: String): List<Fsl105PresentationEntry> {
        val labelsRoot = JSONObject(labelsJson)
        val canonicalLabels = labelsRoot.getJSONArray("labels").let { array ->
            List(array.length()) { array.getString(it) }
        }
        val root = JSONObject(presentationJson)
        require(root.getInt("schema_version") == 1)
        require(root.getString("profile_id") == "STANDARD_FSL_FULLSIGN225")
        require(root.getBoolean("semantic_presentation_only"))
        val values = root.getJSONArray("entries")
        val entries = List(values.length()) { index ->
            val value = values.getJSONObject(index)
            Fsl105PresentationEntry(
                canonicalToken = value.getString("canonical_token"),
                englishDisplay = value.getString("english_display"),
                filipinoDisplay = value.getString("filipino_display")
            )
        }
        require(entries.size == Fsl105GuideCatalog.EXPECTED_LABEL_COUNT)
        require(entries.map { it.canonicalToken }.distinct().size == entries.size) {
            "Bilingual presentation map contains duplicate canonical tokens"
        }
        require(entries.all { it.englishDisplay.isNotBlank() }) {
            "Bilingual presentation map contains a missing English display"
        }
        require(entries.all { it.filipinoDisplay.isNotBlank() }) {
            "Bilingual presentation map contains a missing Filipino display"
        }
        require(entries.map { it.canonicalToken } == canonicalLabels) {
            "Bilingual presentation keys must exactly match canonical label order"
        }
        return entries
    }
}
