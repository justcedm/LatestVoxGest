package com.voxgest.app

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.voxgest.app.adapter.HistoryType
import com.voxgest.app.adapter.VoxHistoryStore
import com.voxgest.app.camera.HandGuideOverlay
import com.voxgest.app.ml.LstmClassificationResult
import com.voxgest.app.ml.LstmClassifier
import com.voxgest.app.ml.WordProfile
import com.voxgest.app.ml.WordProfileManager
import com.voxgest.dryrun.BuildConfig
import com.voxgest.dryrun.LandmarkFrame
import com.voxgest.dryrun.LandmarkPoint
import com.voxgest.dryrun.MediaPipeLandmarkExtractor
import com.voxgest.dryrun.MotionSettings
import com.voxgest.dryrun.R
import com.voxgest.dryrun.SpeechController
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class SignFragment : Fragment(R.layout.fragment_sign) {
    private lateinit var previewView: PreviewView
    private lateinit var trackingDot: View
    private lateinit var trackingBadgeText: TextView
    private lateinit var currentWordText: TextView
    private lateinit var sentenceText: TextView
    private lateinit var handGuideOverlay: HandGuideOverlay
    private var debugGateText: TextView? = null
    private var profileButton: MaterialButton? = null
    private var flipCameraButton: ImageButton? = null

    private var speechController: SpeechController? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var landmarkExtractor: MediaPipeLandmarkExtractor? = null
    private var classifier: LstmClassifier? = null
    private val analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val frameWindow = ArrayDeque<FloatArray>()
    private val handLock = HandLock()
    private val sentenceTokens = mutableListOf<String>()

    private var currentProfile: WordProfile = WordProfileManager.currentProfile()
    private var activeLens: Int = CameraSelector.LENS_FACING_FRONT
    private var lastAnalyzeAtMs: Long = 0L
    private var lastLockedHand: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        previewView = view.findViewById(R.id.previewView)
        trackingDot = view.findViewById(R.id.trackingDot)
        currentWordText = view.findViewById(R.id.tvPrediction)
        sentenceText = view.findViewById(R.id.sentenceText)
        trackingBadgeText = (view.findViewById<LinearLayout>(R.id.trackingBadge).getChildAt(1) as TextView)
        speechController = SpeechController(requireContext(), null)

        activeLens = requireContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_CAMERA_LENS, CameraSelector.LENS_FACING_FRONT)
        currentProfile = WordProfileManager.savedOrDefaultProfile(requireContext())
        classifier = createClassifier(currentProfile)

        addFlipCameraButton(view.findViewById(R.id.topBar))
        addProfileButton(view.findViewById(R.id.actionsRow))
        addHandGuideOverlay()
        addDebugGateText()

        startTrackingPulse()
        startCameraIfAllowed()

        view.findViewById<View>(R.id.speakCurrentWordButton).setOnClickListener {
            speechController?.speak(currentWordText.text.toString().trim())
        }
        view.findViewById<View>(R.id.tapSpeakRow).setOnClickListener { speakSentence() }
        view.findViewById<View>(R.id.btnSpeak).setOnClickListener { speakSentence() }
        view.findViewById<View>(R.id.btnDelete).setOnClickListener { deleteLastToken() }
        view.findViewById<View>(R.id.btnClear).setOnClickListener { clearSentence() }
    }

    override fun onDestroyView() {
        cameraProvider?.unbindAll()
        landmarkExtractor?.close()
        classifier?.close()
        speechController?.shutdown()
        imageAnalysis = null
        cameraProvider = null
        landmarkExtractor = null
        classifier = null
        speechController = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        analyzerExecutor.shutdown()
        super.onDestroy()
    }

    private fun startCameraIfAllowed() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            return
        }
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            rebindCamera()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    fun flipCamera() {
        activeLens = if (activeLens == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_CAMERA_LENS, activeLens)
            .apply()
        rebindCamera()
    }

    private fun rebindCamera() {
        val provider = cameraProvider ?: return
        val lens = activeLens
        frameWindow.clear()
        handLock.reset()
        classifier?.reset()
        lastLockedHand = null
        handGuideOverlay.setState(HandGuideOverlay.GuideState.NO_HAND, null, currentProfile.displayName)
        landmarkExtractor?.close()
        landmarkExtractor = MediaPipeLandmarkExtractor(requireContext(), lens == CameraSelector.LENS_FACING_FRONT)

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        @Suppress("DEPRECATION")
        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(320, 240))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(analyzerExecutor) { imageProxy -> processCameraFrame(imageProxy) }
        imageAnalysis = analysis

        provider.unbindAll()
        provider.bindToLifecycle(
            this,
            CameraSelector.Builder().requireLensFacing(lens).build(),
            preview,
            analysis
        )
        previewView.scaleX = if (lens == CameraSelector.LENS_FACING_FRONT) -1f else 1f
        flipCameraButton?.contentDescription = if (lens == CameraSelector.LENS_FACING_FRONT) {
            "Switch to back camera"
        } else {
            "Switch to front camera"
        }
    }

    private fun processCameraFrame(imageProxy: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAnalyzeAtMs < ANALYZE_INTERVAL_MS) return
            lastAnalyzeAtMs = now

            val extractor = landmarkExtractor ?: return
            val frame = extractor.processFrame(imageProxy) ?: run {
                onNoHand()
                return
            }
            val detected = detectedHandedness(frame)
            val locked = handLock.update(detected)
            if (lastLockedHand != null && locked == null) {
                classifier?.reset()
                frameWindow.clear()
            }
            lastLockedHand = locked

            if (locked != null && detected != null && locked != detected) {
                return
            }

            val handedness = locked ?: detected
            val handPoints = handFor(frame, handedness)
            val handBox = handBoundingBox(handPoints)
            updateGuide(handBox, locked, null)
            if (handedness == null || handPoints == null || !handGuideOverlay.isInIdealZone(handBox)) {
                return
            }

            val features = buildOneHand162(frame, handedness) ?: return
            addFrame(features)
            if (frameWindow.size < currentProfile.inputShape[1]) return

            val result = classifier?.classify(frameWindow.toList(), wristIndexFor(handedness)) ?: return
            debugGateText?.text = result.debugText
            if (!result.accepted) {
                updateConfidence(result)
                return
            }
            acceptPrediction(result)
        } catch (_: Throwable) {
            onNoHand()
        } finally {
            imageProxy.close()
        }
    }

    private fun acceptPrediction(result: LstmClassificationResult) {
        val label = result.label.uppercase(Locale.US)
        if (label == "NOTHING" || label.isBlank()) return
        currentWordText.text = label
        sentenceTokens.add(label)
        sentenceText.text = sentenceTokens.joinToString(" ")
        updateConfidence(result)
        updateGuide(null, lastLockedHand, label)
        animateWordConfirmed()
    }

    private fun updateConfidence(result: LstmClassificationResult) {
        trackingBadgeText.text = when {
            lastLockedHand != null -> "Tracking $lastLockedHand"
            else -> "Tracking Hand"
        }
        if (BuildConfig.DEBUG) {
            debugGateText?.visibility = View.VISIBLE
        }
    }

    private fun onNoHand() {
        val locked = handLock.update(null)
        if (locked == null) {
            frameWindow.clear()
            classifier?.reset()
            lastLockedHand = null
        }
        requireActivity().runOnUiThread {
            trackingBadgeText.text = if (locked == null) "Tracking Hand" else "Tracking $locked"
            handGuideOverlay.setState(HandGuideOverlay.GuideState.NO_HAND, null, currentProfile.displayName, locked)
        }
    }

    private fun addFrame(features: FloatArray) {
        if (frameWindow.size == currentProfile.inputShape[1]) frameWindow.removeFirst()
        frameWindow.addLast(features.copyOf())
    }

    private fun detectedHandedness(frame: LandmarkFrame): String? {
        return when {
            frame.hasRightHand -> "Right"
            frame.hasLeftHand -> "Left"
            else -> null
        }
    }

    private fun handFor(frame: LandmarkFrame, handedness: String?): List<LandmarkPoint>? {
        return when (handedness) {
            "Left" -> frame.leftHandLandmarks
            "Right" -> frame.rightHandLandmarks
            else -> frame.rightHandLandmarks ?: frame.leftHandLandmarks
        }
    }

    private fun handBoundingBox(points: List<LandmarkPoint>?): RectF? {
        if (points.isNullOrEmpty()) return null
        var left = 1f
        var top = 1f
        var right = 0f
        var bottom = 0f
        points.forEach { point ->
            left = min(left, point.x)
            top = min(top, point.y)
            right = max(right, point.x)
            bottom = max(bottom, point.y)
        }
        return RectF(left.coerceIn(0f, 1f), top.coerceIn(0f, 1f), right.coerceIn(0f, 1f), bottom.coerceIn(0f, 1f))
    }

    private fun updateGuide(handBox: RectF?, locked: String?, word: String?) {
        val guideState = when {
            handBox == null -> HandGuideOverlay.GuideState.NO_HAND
            !handGuideOverlay.isInIdealZone(handBox) -> HandGuideOverlay.GuideState.WRONG_ZONE
            locked != null -> HandGuideOverlay.GuideState.HAND_LOCKED
            else -> HandGuideOverlay.GuideState.HAND_FOUND
        }
        requireActivity().runOnUiThread {
            trackingBadgeText.text = if (locked == null) "Tracking Hand" else "Tracking $locked"
            handGuideOverlay.setState(guideState, handBox, currentProfile.displayName, locked, word)
        }
    }

    private fun buildOneHand162(frame: LandmarkFrame, handedness: String): FloatArray? {
        val pose = frame.poseLandmarks ?: return null
        val hand = handFor(frame, handedness) ?: return null
        if (pose.size != 33 || hand.size != 21) return null
        val nose = pose[0]
        val output = FloatArray(162)
        val keep = poseKeepIndices(handedness)
        for (index in pose.indices) {
            if (index !in keep) continue
            val out = index * 3
            output[out] = pose[index].x - nose.x
            output[out + 1] = pose[index].y - nose.y
            output[out + 2] = pose[index].z - nose.z
        }
        for (index in hand.indices) {
            val out = 99 + index * 3
            output[out] = hand[index].x - nose.x
            output[out + 1] = hand[index].y - nose.y
            output[out + 2] = hand[index].z - nose.z
        }
        output[0] = 0f
        output[1] = 0f
        output[2] = 0f
        return output
    }

    private fun poseKeepIndices(handedness: String): Set<Int> {
        val base = (0..10).toMutableSet()
        base.addAll(listOf(11, 12, 23, 24))
        if (handedness == "Left") {
            base.addAll(listOf(11, 13, 15, 17, 19, 21))
        } else {
            base.addAll(listOf(12, 14, 16, 18, 20, 22))
        }
        return base
    }

    private fun wristIndexFor(handedness: String): Int {
        return if (handedness == "Left") 16 else 15
    }

    @Deprecated("Fragment requestPermissions callback")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCameraIfAllowed()
        }
    }

    private fun showProfileSelector() {
        val profiles = WordProfileManager.availableProfiles(requireContext())
        if (profiles.isEmpty()) return
        val dialog = BottomSheetDialog(requireContext())
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpInt, 18.dpInt, 24.dpInt, 24.dpInt)
        }
        val title = TextView(requireContext()).apply {
            text = "Word profile"
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            setPadding(0, 0, 0, 12.dpInt)
        }
        container.addView(title)
        profiles.forEach { profile ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8.dpInt, 0, 8.dpInt)
                isClickable = true
                isFocusable = true
            }
            val radio = RadioButton(requireContext()).apply {
                isChecked = profile.id == currentProfile.id
            }
            val label = TextView(requireContext()).apply {
                text = "${profile.displayName} (${WordProfileManager.wordCount(requireContext(), profile)} words)"
                textSize = 14f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            }
            row.addView(radio)
            row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.setOnClickListener {
                dialog.dismiss()
                switchProfile(profile)
            }
            container.addView(row)
        }
        dialog.setContentView(container)
        dialog.show()
    }

    private fun switchProfile(profile: WordProfile) {
        classifier?.close()
        currentProfile = profile
        WordProfileManager.saveCurrentProfile(requireContext(), profile)
        classifier = createClassifier(profile)
        profileButton?.text = profile.displayName
        clearSentence()
    }

    private fun createClassifier(profile: WordProfile): LstmClassifier? {
        return runCatching { LstmClassifier(requireContext(), profile) }.getOrNull()
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
        frameWindow.clear()
        handLock.reset()
        classifier?.reset()
        sentenceTokens.clear()
        currentWordText.setText(R.string.current_word_sample)
        sentenceText.setText(R.string.sentence_sample)
        debugGateText?.text = ""
        handGuideOverlay.setState(HandGuideOverlay.GuideState.NO_HAND, null, currentProfile.displayName)
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

    private fun addFlipCameraButton(topBar: FrameLayout) {
        flipCameraButton = ImageButton(requireContext()).apply {
            setImageResource(R.drawable.ic_flip_camera)
            setBackgroundResource(android.R.color.transparent)
            contentDescription = "Switch camera"
            setPadding(16.dpInt, 16.dpInt, 16.dpInt, 16.dpInt)
            setOnClickListener { flipCamera() }
        }
        topBar.addView(
            flipCameraButton,
            FrameLayout.LayoutParams(56.dpInt, 56.dpInt, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                marginEnd = 56.dpInt
            }
        )
    }

    private fun addProfileButton(actionsRow: LinearLayout) {
        profileButton = MaterialButton(requireContext()).apply {
            text = currentProfile.displayName
            textSize = 10f
            isAllCaps = false
            maxLines = 2
            setOnClickListener { showProfileSelector() }
        }
        actionsRow.addView(
            profileButton,
            0,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginEnd = 5.dpInt
            }
        )
    }

    private fun addHandGuideOverlay() {
        handGuideOverlay = HandGuideOverlay(requireContext())
        val cameraFrame = previewView.parent as FrameLayout
        cameraFrame.addView(
            handGuideOverlay,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    private fun addDebugGateText() {
        if (!BuildConfig.DEBUG) return
        val cameraFrame = previewView.parent as FrameLayout
        debugGateText = TextView(requireContext()).apply {
            textSize = 9f
            setTextColor(0xFF64748B.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(10.dpInt, 4.dpInt, 10.dpInt, 4.dpInt)
            setBackgroundColor(0xCCFFFFFF.toInt())
        }
        cameraFrame.addView(
            debugGateText,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = 12.dpInt
                bottomMargin = 12.dpInt
            }
        )
    }

    private val Int.dpInt: Int get() = (this * resources.displayMetrics.density).toInt()

    class HandLock {
        private var lockedHandedness: String? = null
        private var absentFrames = 0
        private val lockFrames = 3
        private val releaseFrames = 15
        private var candidateFrames = 0
        private var candidateHandedness: String? = null

        fun update(detectedHandedness: String?): String? {
            if (detectedHandedness == null) {
                absentFrames++
                if (absentFrames >= releaseFrames) {
                    lockedHandedness = null
                    candidateFrames = 0
                    candidateHandedness = null
                }
                return lockedHandedness
            }
            absentFrames = 0
            if (lockedHandedness != null) {
                return lockedHandedness
            }
            if (detectedHandedness == candidateHandedness) {
                candidateFrames++
                if (candidateFrames >= lockFrames) {
                    lockedHandedness = candidateHandedness
                }
            } else {
                candidateHandedness = detectedHandedness
                candidateFrames = 1
            }
            return lockedHandedness
        }

        fun reset() {
            lockedHandedness = null
            absentFrames = 0
            candidateFrames = 0
            candidateHandedness = null
        }
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 4242
        private const val PREFS = "voxgest_sign_preferences"
        private const val KEY_CAMERA_LENS = "voxgest_camera_lens"
        private const val ANALYZE_INTERVAL_MS = 90L
    }
}
