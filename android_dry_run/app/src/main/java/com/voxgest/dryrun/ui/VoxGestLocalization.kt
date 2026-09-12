package com.voxgest.dryrun.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.voxgest.dryrun.VoxGestAppLanguage
import com.voxgest.dryrun.VoxGestMessageLanguage
import java.util.Locale

internal data class VoxGestCopy(
    val sign: String,
    val conversation: String,
    val listen: String,
    val guide: String,
    val settings: String,
    val ready: String,
    val holdSignClearly: String,
    val start: String,
    val stop: String,
    val switchCamera: String,
    val expand: String,
    val minimize: String,
    val recognizedSign: String,
    val waitingForSign: String,
    val message: String,
    val noMessage: String,
    val undo: String,
    val speak: String,
    val clear: String,
    val currentExchange: String,
    val noMessages: String,
    val noMessagesHint: String,
    val newExchange: String,
    val fslUser: String,
    val hearingUser: String,
    val spokenAloud: String,
    val shownAsSigns: String,
    val listenReady: String,
    val tapMic: String,
    val speakClearly: String,
    val transcript: String,
    val replay: String,
    val play: String,
    val searchSigns: String,
    val supportedGuide: String,
    val all: String,
    val words: String,
    val phrases: String,
    val about: String,
    val done: String,
    val apply: String,
    val cancel: String,
    val appLanguage: String,
    val messageLanguage: String,
    val appearance: String,
    val reducedMotion: String,
    val translationUnavailable: String,
    val english: String,
    val filipino: String,
    val both: String
) {
    companion object {
        val English = VoxGestCopy(
            sign = "Sign", conversation = "Conversation", listen = "Listen", guide = "Guide",
            settings = "Settings", ready = "READY", holdSignClearly = "HOLD SIGN CLEARLY",
            start = "Start", stop = "Stop", switchCamera = "Switch", expand = "Expand",
            minimize = "Minimize", recognizedSign = "RECOGNIZED SIGN", waitingForSign = "Waiting for an accepted sign",
            message = "MESSAGE", noMessage = "No message yet.", undo = "Undo", speak = "Speak", clear = "Clear",
            currentExchange = "CURRENT EXCHANGE", noMessages = "No messages in this session yet.",
            noMessagesHint = "Use Sign to speak an accepted FSL message or Listen to capture speech.",
            newExchange = "New Exchange", fslUser = "FSL USER", hearingUser = "HEARING USER",
            spokenAloud = "Spoken aloud", shownAsSigns = "Shown as signs", listenReady = "Ready to listen",
            tapMic = "Tap the microphone and speak.", speakClearly = "Speak clearly", transcript = "SPEECH TRANSCRIPT",
            replay = "Replay", play = "Play", searchSigns = "Search supported signs", supportedGuide = "FSL Guide",
            all = "All", words = "Words", phrases = "Phrases", about = "About VoxGest", done = "Done",
            apply = "Apply", cancel = "Cancel", appLanguage = "App language",
            messageLanguage = "Message output language", appearance = "Appearance", reducedMotion = "Reduced motion",
            translationUnavailable = "Filipino translation is not available offline for this message.",
            english = "English", filipino = "Filipino", both = "Both"
        )

        val Filipino = VoxGestCopy(
            sign = "Senyas", conversation = "Usapan", listen = "Makinig", guide = "Gabay",
            settings = "Mga Setting", ready = "HANDA", holdSignClearly = "IPAKITA NANG MALINAW ANG SENYAS",
            start = "Simulan", stop = "Ihinto", switchCamera = "Palit", expand = "Palakihin",
            minimize = "Paliitin", recognizedSign = "NAKILALANG SENYAS", waitingForSign = "Naghihintay ng tinanggap na senyas",
            message = "MENSAHE", noMessage = "Wala pang mensahe.", undo = "I-undo", speak = "Bigkasin", clear = "Burahin",
            currentExchange = "KASALUKUYANG USAPAN", noMessages = "Wala pang mensahe sa usapang ito.",
            noMessagesHint = "Gamitin ang Senyas para bigkasin ang tinanggap na FSL o Makinig para kumuha ng pananalita.",
            newExchange = "Bagong Usapan", fslUser = "GUMAGAMIT NG FSL", hearingUser = "NAKAKARINIG",
            spokenAloud = "Binigkas", shownAsSigns = "Ipinakita bilang senyas", listenReady = "Handang makinig",
            tapMic = "Pindutin ang mikropono at magsalita.", speakClearly = "Magsalita nang malinaw", transcript = "TRANSKRIP NG PANANALITA",
            replay = "Ulitin", play = "I-play", searchSigns = "Maghanap sa suportadong senyas", supportedGuide = "Gabay sa FSL",
            all = "Lahat", words = "Mga Salita", phrases = "Mga Parirala", about = "Tungkol sa VoxGest", done = "Tapos",
            apply = "Ilapat", cancel = "Kanselahin", appLanguage = "Wika ng app",
            messageLanguage = "Wika ng mensahe", appearance = "Hitsura", reducedMotion = "Bawas galaw",
            translationUnavailable = "Walang offline na salin sa Filipino para sa mensaheng ito.",
            english = "English", filipino = "Filipino", both = "Pareho"
        )
    }
}

internal val LocalVoxGestCopy = staticCompositionLocalOf { VoxGestCopy.English }

internal data class BilingualMessage(
    val sourceTokens: String,
    val english: String,
    val filipino: String?
) {
    fun visibleLines(language: VoxGestMessageLanguage): List<Pair<String, String>> = when (language) {
        VoxGestMessageLanguage.ENGLISH -> listOf("EN" to english)
        VoxGestMessageLanguage.FILIPINO -> filipino?.let { listOf("FIL" to it) }.orEmpty()
        VoxGestMessageLanguage.BOTH -> buildList {
            add("EN" to english)
            filipino?.let { add("FIL" to it) }
        }
    }
}

/** Presentation-only, deterministic offline wording. Recognition tokens are retained verbatim. */
internal object VoxGestOfflineMessagePresenter {
    private val tokenTranslations = mapOf(
        "WHAT" to "Ano",
        "YOUR" to "Iyong",
        "NAME" to "Pangalan",
        "MY" to "Aking"
    )

    private val phraseTranslations = mapOf(
        "WHAT YOUR NAME" to "Ano ang pangalan mo?",
        "WHAT IS YOUR NAME" to "Ano ang pangalan mo?",
        "MY NAME" to "Ang pangalan ko"
    )

    fun present(raw: String): BilingualMessage {
        val source = raw.trim().replace(Regex("\\s+"), " ")
        if (source.isBlank()) return BilingualMessage("", "", null)
        val normalized = source.uppercase(Locale.US)
        val english = source.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
        val filipino = phraseTranslations[normalized] ?: normalized.split(' ')
            .map { tokenTranslations[it] }
            .takeIf { translated -> translated.all { it != null } }
            ?.filterNotNull()
            ?.joinToString(" ")
        return BilingualMessage(source, english, filipino)
    }

    fun speechText(raw: String, language: VoxGestMessageLanguage): String {
        val message = present(raw)
        return when (language) {
            VoxGestMessageLanguage.FILIPINO -> message.filipino ?: message.english
            VoxGestMessageLanguage.ENGLISH,
            VoxGestMessageLanguage.BOTH -> message.english
        }
    }

}

internal fun copyFor(language: VoxGestAppLanguage): VoxGestCopy = when (language) {
    VoxGestAppLanguage.ENGLISH -> VoxGestCopy.English
    VoxGestAppLanguage.FILIPINO -> VoxGestCopy.Filipino
}
