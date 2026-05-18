package com.voxgest.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.voxgest.app.adapter.PhraseItem
import com.voxgest.app.adapter.PhrasesAdapter
import com.voxgest.app.adapter.VoxHistoryStore
import com.voxgest.dryrun.R
import com.voxgest.dryrun.SpeechController

class PhrasesFragment : Fragment(R.layout.fragment_phrases) {
    private var speechController: SpeechController? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        speechController = SpeechController(requireContext(), null)
        val phrases = listOf(
            PhraseItem(getString(R.string.phrase_need_help), R.drawable.ic_warning, R.drawable.bg_icon_orange, R.color.emergency_orange, emergency = true),
            PhraseItem(getString(R.string.phrase_call_doctor), R.drawable.ic_medical, R.drawable.bg_icon_teal, R.color.medical_teal),
            PhraseItem(getString(R.string.phrase_need_water), R.drawable.ic_water_drop, R.drawable.bg_icon_blue, R.color.water_blue),
            PhraseItem(getString(R.string.phrase_stop), R.drawable.ic_hand_gesture, R.drawable.bg_icon_red, R.color.no_red, emergency = true),
            PhraseItem(getString(R.string.phrase_wait), R.drawable.ic_clock, R.drawable.bg_icon_yellow, R.color.amber_clock),
            PhraseItem(getString(R.string.phrase_thank_you), R.drawable.ic_hand_gesture, R.drawable.bg_icon_purple, R.color.purple_hands),
            PhraseItem(getString(R.string.phrase_yes), R.drawable.ic_check_circle, R.drawable.bg_icon_green, R.color.yes_green),
            PhraseItem(getString(R.string.phrase_no), R.drawable.ic_no_circle, R.drawable.bg_icon_red, R.color.no_red)
        )
        view.findViewById<RecyclerView>(R.id.phrasesRecyclerView).apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = PhrasesAdapter(phrases) { phrase ->
                VoxHistoryStore.addPhrase(phrase.text, phrase.emergency)
                speechController?.speak(phrase.text)
                Toast.makeText(requireContext(), R.string.history_added_shortcut, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        speechController?.shutdown()
        speechController = null
        super.onDestroyView()
    }
}
