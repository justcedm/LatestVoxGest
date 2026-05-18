package com.voxgest.dryrun

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class SignFragment : Fragment(R.layout.fragment_sign) {

    private lateinit var tvPrediction: TextView
    private lateinit var confidenceText: TextView
    private lateinit var modeTag: TextView
    private lateinit var stabilityProgress: ProgressBar
    private lateinit var sentencePanel: View
    private lateinit var wordBufferText: TextView
    private lateinit var sentenceText: TextView
    private var speechController: SpeechController? = null

    private val letters = StringBuilder()
    private val sentence = mutableListOf<String>()
    private val demoTokens = listOf("Y", "E", "S", "WATER", "HELLO", "THANKYOU", "NO", "PLEASE", "HELP")
    private var demoIndex = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvPrediction = view.findViewById(R.id.tvPrediction)
        confidenceText = view.findViewById(R.id.confidenceText)
        modeTag = view.findViewById(R.id.modeTag)
        stabilityProgress = view.findViewById(R.id.stabilityProgress)
        sentencePanel = view.findViewById(R.id.sentencePanel)
        wordBufferText = view.findViewById(R.id.wordBufferText)
        sentenceText = view.findViewById(R.id.sentenceText)
        speechController = SpeechController(requireContext(), null)

        view.findViewById<View>(R.id.previewView).setOnClickListener { playNextDemoToken() }
        view.findViewById<View>(R.id.cameraCard).setOnClickListener { playNextDemoToken() }
        view.findViewById<MaterialButton>(R.id.btnClear).setOnClickListener { clearSentence() }
        view.findViewById<MaterialButton>(R.id.btnSpeak).setOnClickListener {
            val spoken = composedSentence()
            if (spoken.isNotBlank()) {
                speechController?.speak(spoken)
                ConversationHistoryManager.addSigned(spoken)
            }
        }

        updateComposerText()
        animateProgressTo(64)
    }

    override fun onDestroyView() {
        speechController?.shutdown()
        speechController = null
        super.onDestroyView()
    }

    private fun playNextDemoToken() {
        val token = demoTokens[demoIndex % demoTokens.size]
        demoIndex += 1
        if (token.length == 1) {
            confirmLetter(token)
        } else {
            addWordToken(token)
        }
    }

    private fun confirmLetter(letter: String) {
        letters.append(letter)
        tvPrediction.text = letter
        tvPrediction.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_sign))
        confidenceText.text = "96% confidence"
        modeTag.setText(R.string.mode_letter)
        modeTag.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_sign))
        animatePredictionConfirmed()
        animateProgressTo(100)
        updateComposerText()
    }

    private fun addWordToken(word: String) {
        flushLettersToSentence()
        sentence.add(word)
        tvPrediction.text = "[$word]"
        tvPrediction.setTextColor(ContextCompat.getColor(requireContext(), R.color.word_detected))
        confidenceText.text = "98% confidence"
        modeTag.setText(R.string.mode_word_sign)
        modeTag.setTextColor(ContextCompat.getColor(requireContext(), R.color.word_detected))
        animatePredictionConfirmed()
        animateProgressTo(100)
        flashWordAdded()
        updateComposerText()
    }

    private fun animatePredictionConfirmed() {
        if (!MotionSettings.animationsEnabled(requireContext())) return
        tvPrediction.scaleX = 1.06f
        tvPrediction.scaleY = 1.06f
        val springX = SpringAnimation(tvPrediction, DynamicAnimation.SCALE_X, 1f)
        val springY = SpringAnimation(tvPrediction, DynamicAnimation.SCALE_Y, 1f)
        listOf(springX, springY).forEach { animation ->
            animation.spring = SpringForce(1f).apply {
                stiffness = 400f
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            }
            animation.start()
        }
    }

    private fun animateProgressTo(target: Int) {
        if (!MotionSettings.animationsEnabled(requireContext())) {
            stabilityProgress.progress = target.coerceIn(0, 100)
            return
        }
        ObjectAnimator.ofInt(stabilityProgress, "progress", stabilityProgress.progress, target.coerceIn(0, 100)).apply {
            duration = 220L
            interpolator = OvershootInterpolator(0.8f)
            start()
        }
    }

    private fun flashWordAdded() {
        val context = requireContext()
        val accent = ContextCompat.getColor(context, R.color.word_detected)
        val surface = ContextCompat.getColor(context, R.color.bg_card)
        if (!MotionSettings.animationsEnabled(context)) {
            sentencePanel.setBackgroundResource(R.drawable.bg_card_16)
            return
        }
        sentencePanel.setBackgroundColor(accent)
        ValueAnimator.ofObject(ArgbEvaluator(), accent, surface).apply {
            startDelay = 150L
            duration = 150L
            addUpdateListener { sentencePanel.setBackgroundColor(it.animatedValue as Int) }
            doOnFinished {
                sentencePanel.setBackgroundResource(R.drawable.bg_card_16)
            }
            start()
        }
    }

    private fun ValueAnimator.doOnFinished(action: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                action()
            }
        })
    }

    private fun flushLettersToSentence() {
        if (letters.isNotEmpty()) {
            sentence.add(letters.toString())
            letters.clear()
        }
    }

    private fun clearSentence() {
        letters.clear()
        sentence.clear()
        tvPrediction.setText(R.string.prediction_letter_sample)
        tvPrediction.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_sign))
        confidenceText.setText(R.string.prediction_confidence_sample)
        modeTag.setText(R.string.mode_motion)
        modeTag.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_sign))
        animateProgressTo(35)
        updateComposerText()
    }

    private fun updateComposerText() {
        wordBufferText.text = if (letters.isEmpty()) "Building: |" else "Building: $letters|"
        sentenceText.text = composedSentence().ifBlank { getString(R.string.sign_sentence_sample) }
    }

    private fun composedSentence(): String {
        val parts = sentence.toMutableList()
        if (letters.isNotEmpty()) parts.add(letters.toString())
        return parts.joinToString(" ").trim()
    }
}
