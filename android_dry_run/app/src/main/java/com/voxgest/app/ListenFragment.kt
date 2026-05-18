package com.voxgest.app

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.voxgest.app.adapter.HistoryType
import com.voxgest.app.adapter.VoxHistoryStore
import com.voxgest.app.avatar.AvatarView
import com.voxgest.app.views.WaveformView
import com.voxgest.dryrun.AvatarPhraseMapper
import com.voxgest.dryrun.MotionSettings
import com.voxgest.dryrun.R
import com.voxgest.dryrun.SpeechController

class ListenFragment : Fragment(R.layout.fragment_listen) {
    private var speechController: SpeechController? = null
    private lateinit var responseText: TextView
    private lateinit var statusPill: TextView
    private lateinit var micStatusText: TextView
    private lateinit var avatarView: AvatarView
    private lateinit var avatarPanel: View
    private lateinit var waveformLeft: WaveformView
    private lateinit var waveformRight: WaveformView
    private var lastSpeech = "Hello, how can I help you?"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        responseText = view.findViewById(R.id.responseText)
        statusPill = view.findViewById(R.id.statusPill)
        micStatusText = view.findViewById(R.id.micStatusText)
        avatarView = view.findViewById(R.id.avatarView)
        avatarPanel = view.findViewById(R.id.avatarPanel)
        waveformLeft = view.findViewById(R.id.waveformLeft)
        waveformRight = view.findViewById(R.id.waveformRight)

        speechController = SpeechController(requireContext(), object : SpeechController.Listener {
            override fun onSpeechText(text: String) = handleSpeech(text, final = true)
            override fun onPartialSpeechText(text: String) = handleSpeech(text, final = false)
            override fun onSpeechStatus(status: String) = updateSpeechStatus(status)
        })

        val micButton = view.findViewById<View>(R.id.micButton)
        micButton.setOnTouchListener { pressed, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> pressed.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100L).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pressed.animate().scaleX(1f).scaleY(1f)
                    .setInterpolator(OvershootInterpolator(2f)).setDuration(200L).start()
            }
            false
        }
        micButton.setOnClickListener { startListening() }
        view.findViewById<View>(R.id.replayButton).setOnClickListener { handleSpeech(lastSpeech, final = false) }
        view.findViewById<View>(R.id.playSignsButton).setOnClickListener { handleSpeech(lastSpeech, final = true) }
        view.findViewById<View>(R.id.speechSpeakerButton).setOnClickListener { speechController?.speak(lastSpeech) }
        avatarView.signText("HELLO")
    }

    override fun onDestroyView() {
        speechController?.shutdown()
        waveformLeft.setListening(false)
        waveformRight.setListening(false)
        super.onDestroyView()
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 4343)
            return
        }
        waveformLeft.setListening(true)
        waveformRight.setListening(true)
        micStatusText.setText(R.string.listen_status_active)
        pulseStatus()
        speechController?.listenOnce()
    }

    @Deprecated("Fragment requestPermissions callback")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 4343 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
    }

    private fun handleSpeech(text: String, final: Boolean) {
        val clean = text.trim().ifBlank { return }
        lastSpeech = clean
        responseText.text = clean
        if (MotionSettings.animationsEnabled(requireContext())) {
            responseText.alpha = 0f
            responseText.translationX = 24f * resources.displayMetrics.density
            responseText.animate().alpha(1f).translationX(0f).setDuration(120L).setInterpolator(DecelerateInterpolator()).start()
            avatarPanel.translationY = 32f * resources.displayMetrics.density
            avatarPanel.animate().translationY(0f).setDuration(250L).setInterpolator(DecelerateInterpolator()).start()
        }
        val mapping = AvatarPhraseMapper.map(clean)
        statusPill.setText(R.string.avatar_analyzing)
        avatarView.signText(mapping.sequence.firstOrNull() ?: clean)
        if (final) VoxHistoryStore.add(HistoryType.Speech, clean, getString(R.string.history_shown_signs))
    }

    private fun updateSpeechStatus(status: String) {
        micStatusText.text = if (status.contains("error", true)) getString(R.string.listen_subtext) else status
        if (status.contains("Processing", true) || status.contains("error", true)) {
            waveformLeft.setListening(false)
            waveformRight.setListening(false)
        }
    }

    private fun pulseStatus() {
        if (!MotionSettings.animationsEnabled(requireContext())) return
        ObjectAnimator.ofFloat(statusPill, View.ALPHA, 1f, 0.5f).apply {
            duration = 800L
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }
}
