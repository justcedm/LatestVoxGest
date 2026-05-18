package com.voxgest.dryrun

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class PhrasesFragment : Fragment(R.layout.fragment_phrases) {
    private var speechController: SpeechController? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        speechController = SpeechController(requireContext(), null)
        bindPhrase(view, R.id.shortcutHelpMe, getString(R.string.phrase_help_me), emergency = true)
        bindPhrase(view, R.id.shortcutCallEmergency, getString(R.string.phrase_call_emergency), emergency = true)
        bindPhrase(view, R.id.shortcutNeedDoctorEmergency, getString(R.string.phrase_need_doctor), emergency = true)
        bindPhrase(view, R.id.shortcutHurtEmergency, getString(R.string.phrase_i_am_hurt), emergency = true)
        bindPhrase(view, R.id.shortcutCannotBreathe, getString(R.string.phrase_cannot_breathe), emergency = true)
        bindPhrase(view, R.id.shortcutLostEmergency, getString(R.string.phrase_i_am_lost), emergency = true)

        bindPhrase(view, R.id.shortcutHello, getString(R.string.phrase_hello))
        bindPhrase(view, R.id.shortcutThankYou, getString(R.string.phrase_thank_you))
        bindPhrase(view, R.id.shortcutPlease, getString(R.string.phrase_please))
        bindPhrase(view, R.id.shortcutWater, getString(R.string.phrase_water))
        bindPhrase(view, R.id.shortcutBathroom, getString(R.string.phrase_bathroom))
        bindPhrase(view, R.id.shortcutWhereGo, getString(R.string.phrase_how_much))

        bindPhrase(view, R.id.shortcutPain, "Pain")
        bindPhrase(view, R.id.shortcutSick, "Sick")
        bindPhrase(view, R.id.shortcutMedicine, "Medicine")
        bindPhrase(view, R.id.shortcutHospital, getString(R.string.phrase_hospital))
        bindPhrase(view, R.id.shortcutDoctor, "Doctor")
        bindPhrase(view, R.id.shortcutNeedHelpMedical, getString(R.string.phrase_need_help))

        bindPhrase(view, R.id.shortcutYes, getString(R.string.phrase_yes))
        bindPhrase(view, R.id.shortcutNo, getString(R.string.phrase_no))
        bindPhrase(view, R.id.shortcutUnderstand, getString(R.string.phrase_understand))
        bindPhrase(view, R.id.shortcutDontUnderstand, getString(R.string.phrase_question))
        bindPhrase(view, R.id.shortcutWait, getString(R.string.phrase_write_down))
        bindPhrase(view, R.id.shortcutRepeat, getString(R.string.phrase_repeat))
    }

    override fun onDestroyView() {
        speechController?.shutdown()
        speechController = null
        super.onDestroyView()
    }

    private fun bindPhrase(root: View, buttonId: Int, phrase: String, emergency: Boolean = false) {
        val button = root.findViewById<MaterialButton>(buttonId)
        button.contentDescription = if (emergency) "Emergency: $phrase" else phrase
        button.setOnClickListener {
            val mapping = AvatarPhraseMapper.map(phrase)
            ConversationHistoryManager.addShortcut(phrase, emergency, mapping.sequenceLabel)
            speechController?.speak(phrase)
            Toast.makeText(requireContext(), R.string.history_added_shortcut, Toast.LENGTH_SHORT).show()
        }
    }
}
