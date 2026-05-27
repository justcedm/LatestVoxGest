package com.voxgest.dryrun

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.voxgest.app.avatar.SignAvatarView

class ListenFragment : Fragment(R.layout.fragment_listen) {

    private companion object {
        const val REQUEST_RECORD_AUDIO = 9102
        const val SEQUENCE_STEP_MS = 620L
    }

    private lateinit var avatarView: SignAvatarView
    private lateinit var avatarPanel: View
    private lateinit var responseText: TextView
    private lateinit var micStatusText: TextView
    private lateinit var statusPill: TextView
    private lateinit var phraseMatchLabel: TextView
    private lateinit var avatarSequenceText: TextView
    private lateinit var micButton: MaterialButton
    private val sequenceHandler = Handler(Looper.getMainLooper())
    private var statusPulse: ObjectAnimator? = null
    private var speechController: SpeechController? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        avatarView = view.findViewById(R.id.avatarView)
        avatarPanel = view.findViewById(R.id.avatarPanel)
        responseText = view.findViewById(R.id.responseText)
        micStatusText = view.findViewById(R.id.micStatusText)
        statusPill = view.findViewById(R.id.statusPill)
        phraseMatchLabel = view.findViewById(R.id.phraseMatchLabel)
        avatarSequenceText = view.findViewById(R.id.avatarSequenceText)
        micButton = view.findViewById(R.id.micButton)

        speechController = SpeechController(requireContext(), object : SpeechController.Listener {
            override fun onSpeechText(text: String) {
                handleAvatarInput(text, addToHistory = true)
            }

            override fun onPartialSpeechText(text: String) {
                handleAvatarInput(text, addToHistory = false)
            }

            override fun onSpeechStatus(status: String) {
                handleSpeechStatus(status)
            }
        })

        bindDemoButton(view, R.id.playNameButton, getString(R.string.avatar_demo_name))
        bindDemoButton(view, R.id.playGoButton, getString(R.string.avatar_demo_go))
        bindDemoButton(view, R.id.playHelpButton, getString(R.string.avatar_demo_help))
        bindDemoButton(view, R.id.playWaterButton, getString(R.string.avatar_demo_water))
        bindDemoButton(view, R.id.playHelloButton, getString(R.string.avatar_demo_hello))
        bindDemoButton(view, R.id.playThankyouButton, getString(R.string.avatar_demo_thankyou))
        bindDemoButton(view, R.id.playYesButton, getString(R.string.avatar_demo_yes))
        bindDemoButton(view, R.id.playNoButton, getString(R.string.avatar_demo_no))

        val fingerspellInput = view.findViewById<EditText>(R.id.fingerspellInput)
        view.findViewById<MaterialButton>(R.id.fingerspellButton).setOnClickListener {
            val text = fingerspellInput.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                handleAvatarInput(text, addToHistory = true)
            }
        }

        micButton.setOnTouchListener { button, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> pressMic(button)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> releaseMic(button)
            }
            false
        }
        micButton.setOnClickListener { startListening() }
        setReadyStatus()
    }

    override fun onDestroyView() {
        sequenceHandler.removeCallbacksAndMessages(null)
        statusPulse?.cancel()
        speechController?.shutdown()
        speechController = null
        super.onDestroyView()
    }

    @Deprecated("Deprecated in AndroidX Fragment API, still used for minSdk compatibility.")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startListening()
        }
    }

    private fun bindDemoButton(root: View, buttonId: Int, phrase: String) {
        root.findViewById<MaterialButton>(buttonId).setOnClickListener {
            handleAvatarInput(phrase, addToHistory = true)
        }
    }

    private fun handleAvatarInput(text: String, addToHistory: Boolean) {
        val mapping = AvatarPhraseMapper.map(text)
        updateResponse(mapping.displayText)
        phraseMatchLabel.text = mapping.matchLabel
        avatarSequenceText.text = mapping.sequenceLabel
        if (addToHistory) {
            ConversationHistoryManager.addHeard(mapping.displayText, mapping.sequenceLabel)
        }
        setSigningStatus()
        slideAvatarPanel()
        playSequence(mapping.sequence, mapping.displayText)
    }

    private fun playSequence(sequence: List<String>, fallbackText: String) {
        sequenceHandler.removeCallbacksAndMessages(null)
        if (sequence.isEmpty()) {
            avatarView.signText(fallbackText)
            return
        }
        sequence.forEachIndexed { index, word ->
            sequenceHandler.postDelayed({
                avatarView.signText(word)
            }, index * SEQUENCE_STEP_MS)
        }
    }

    private fun updateResponse(text: String) {
        if (!MotionSettings.animationsEnabled(requireContext())) {
            responseText.text = text
            responseText.alpha = 1f
            return
        }
        responseText.alpha = 0f
        responseText.text = text
        responseText.animate()
            .alpha(1f)
            .setDuration(200L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun slideAvatarPanel() {
        if (!MotionSettings.animationsEnabled(requireContext())) return
        avatarPanel.translationY = 32f.dp
        avatarPanel.animate()
            .translationY(0f)
            .setDuration(250L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun startListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            requireContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            setReadyStatus()
            return
        }
        setListeningStatus()
        speechController?.listenOnce()
    }

    private fun handleSpeechStatus(status: String) {
        when {
            status.contains("Listening", ignoreCase = true) || status.contains("Speech started", ignoreCase = true) -> {
                setListeningStatus()
            }
            status.contains("Processing", ignoreCase = true) -> {
                micStatusText.text = status
            }
            status.contains("error", ignoreCase = true) || status.contains("unavailable", ignoreCase = true) -> {
                micStatusText.text = status
                setReadyStatus()
            }
            status.contains("TextToSpeech", ignoreCase = true) -> {
                if (micStatusText.text.isNullOrBlank()) setReadyStatus()
            }
            else -> micStatusText.text = status
        }
    }

    private fun setReadyStatus() {
        statusPulse?.cancel()
        statusPill.alpha = 1f
        statusPill.setText(R.string.avatar_status_ready)
        micStatusText.setText(R.string.listen_status_idle)
        avatarView.setListening(false)
    }

    private fun setListeningStatus() {
        statusPill.setText(R.string.avatar_status_listening)
        micStatusText.setText(R.string.listen_status_active)
        avatarView.setListening(true)
        if (!MotionSettings.animationsEnabled(requireContext())) return
        statusPulse?.cancel()
        statusPulse = ObjectAnimator.ofFloat(statusPill, View.ALPHA, 1f, 0.5f).apply {
            duration = 800L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun setSigningStatus() {
        statusPulse?.cancel()
        statusPill.alpha = 1f
        statusPill.setText(R.string.avatar_status_signing)
        micStatusText.text = getString(R.string.avatar_match_ready)
    }

    private fun pressMic(button: View) {
        if (!MotionSettings.animationsEnabled(requireContext())) return
        button.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun releaseMic(button: View) {
        if (!MotionSettings.animationsEnabled(requireContext())) return
        SpringAnimation(button, DynamicAnimation.SCALE_X, 1f).apply {
            spring = SpringForce(1f).apply {
                stiffness = 400f
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            }
            start()
        }
        SpringAnimation(button, DynamicAnimation.SCALE_Y, 1f).apply {
            spring = SpringForce(1f).apply {
                stiffness = 400f
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            }
            start()
        }
    }

    private val Float.dp: Float
        get() = this * resources.displayMetrics.density
}
