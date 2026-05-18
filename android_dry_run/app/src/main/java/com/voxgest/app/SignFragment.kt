package com.voxgest.app

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.voxgest.app.adapter.HistoryType
import com.voxgest.app.adapter.VoxHistoryStore
import com.voxgest.dryrun.MotionSettings
import com.voxgest.dryrun.R
import com.voxgest.dryrun.SpeechController

class SignFragment : Fragment(R.layout.fragment_sign) {
    private var speechController: SpeechController? = null
    private lateinit var previewView: PreviewView
    private lateinit var trackingDot: View
    private lateinit var currentWordText: TextView
    private lateinit var sentenceText: TextView

    private val acceptedDemoTokens = listOf("HELLO 👋", "I", "NEED", "WATER")
    private val sentenceTokens = mutableListOf("HELLO", "I", "NEED", "WATER")
    private var tokenIndex = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        previewView = view.findViewById(R.id.previewView)
        trackingDot = view.findViewById(R.id.trackingDot)
        currentWordText = view.findViewById(R.id.tvPrediction)
        sentenceText = view.findViewById(R.id.sentenceText)
        speechController = SpeechController(requireContext(), null)

        startTrackingPulse()
        startCameraIfAllowed()

        view.findViewById<View>(R.id.cameraCard).setOnClickListener { acceptNextDemoToken() }
        view.findViewById<View>(R.id.speakCurrentWordButton).setOnClickListener {
            speechController?.speak(currentWordText.text.toString().replace("👋", "").trim())
        }
        view.findViewById<View>(R.id.tapSpeakRow).setOnClickListener { speakSentence() }
        view.findViewById<View>(R.id.btnSpeak).setOnClickListener { speakSentence() }
        view.findViewById<View>(R.id.btnDelete).setOnClickListener { deleteLastToken() }
        view.findViewById<View>(R.id.btnClear).setOnClickListener { clearSentence() }
    }

    override fun onDestroyView() {
        speechController?.shutdown()
        speechController = null
        super.onDestroyView()
    }

    private fun startCameraIfAllowed() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 4242)
            return
        }
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview)
            previewView.scaleX = -1f
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @Deprecated("Fragment requestPermissions callback")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 4242 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCameraIfAllowed()
        }
    }

    private fun acceptNextDemoToken() {
        val token = acceptedDemoTokens[tokenIndex % acceptedDemoTokens.size]
        tokenIndex += 1
        if (token.equals("NOTHING", ignoreCase = true)) return

        currentWordText.text = token
        if (!token.contains("HELLO")) {
            sentenceTokens.add(token)
        }
        sentenceText.text = sentenceTokens.joinToString(", ").replace(", I, NEED, WATER", ", I NEED WATER")
        animateWordConfirmed()
    }

    private fun speakSentence() {
        val sentence = sentenceText.text.toString().trim()
        if (sentence.isBlank() || sentence.equals("NOTHING", ignoreCase = true)) return
        speechController?.speak(sentence)
        VoxHistoryStore.add(HistoryType.Sign, sentence, getString(R.string.history_spoken))
    }

    private fun deleteLastToken() {
        if (sentenceTokens.isNotEmpty()) sentenceTokens.removeAt(sentenceTokens.lastIndex)
        sentenceText.text = sentenceTokens.joinToString(" ").ifBlank { getString(R.string.sentence_sample) }
    }

    private fun clearSentence() {
        sentenceTokens.clear()
        currentWordText.setText(R.string.current_word_sample)
        sentenceText.setText(R.string.sentence_sample)
    }

    private fun animateWordConfirmed() {
        if (!MotionSettings.animationsEnabled(requireContext())) return
        currentWordText.alpha = 0f
        currentWordText.translationY = -8f * resources.displayMetrics.density
        currentWordText.animate().alpha(1f).translationY(0f).setDuration(150L).start()
        ObjectAnimator.ofPropertyValuesHolder(
            currentWordText,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.12f, 1f),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.12f, 1f)
        ).apply {
            duration = 180L
            interpolator = OvershootInterpolator(1.2f)
            start()
        }
    }

    private fun startTrackingPulse() {
        if (!MotionSettings.animationsEnabled(requireContext())) return
        ObjectAnimator.ofFloat(trackingDot, View.ALPHA, 1f, 0.3f).apply {
            duration = 800L
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }
}
