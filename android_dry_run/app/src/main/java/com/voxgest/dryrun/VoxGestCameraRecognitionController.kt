package com.voxgest.dryrun

import android.content.Context
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class VoxGestCameraRecognitionController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onStatus: (String) -> Unit,
    private val onAcceptedResult: (RecognitionResult) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private val gate = RecognitionGate()

    @Volatile private var cameraProvider: ProcessCameraProvider? = null
    @Volatile private var imageAnalysis: ImageAnalysis? = null
    @Volatile private var extractor: LandmarkExtractor? = null
    @Volatile private var recognizer: VoxGestTfliteRecognizer? = null
    @Volatile private var profile: RecognitionProfile? = null
    @Volatile private var sequenceBuffer: LandmarkSequenceBuffer? = null
    @Volatile private var lastAnalyzeAtMs: Long = 0L
    @Volatile private var cooldownUntilMs: Long = 0L
    @Volatile private var lastStatus: String = ""
    @Volatile private var lastStatusAtMs: Long = 0L

    fun start(previewView: PreviewView) {
        if (!running.compareAndSet(false, true)) return
        postStatus("Starting Camera")
        bindCamera(previewView)
        analyzerExecutor.execute {
            try {
                postStatus("Recognition Experimental / Calibration")
                val loadedRecognizer = VoxGestTfliteRecognizer(appContext)
                val loadedProfile = loadedRecognizer.load()
                val loadedExtractor = MediaPipeLandmarkExtractor(appContext, loadedProfile.mirroredInput)
                recognizer = loadedRecognizer
                profile = loadedProfile
                extractor = loadedExtractor
                sequenceBuffer = LandmarkSequenceBuffer(loadedProfile.sequenceLength, loadedProfile.featureSize)
                postStatus("Tracking Hand")
            } catch (exc: Throwable) {
                postStatus("Landmark profile not ready.")
                closePipeline()
            }
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) {
            postStatus("Recognition Paused")
            return
        }
        mainExecutor.execute {
            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            cameraProvider?.unbindAll()
            cameraProvider = null
        }
        analyzerExecutor.execute {
            closePipeline()
            postStatus("Recognition Paused")
        }
    }

    fun release() {
        stop()
        analyzerExecutor.shutdown()
    }

    private fun bindCamera(previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener({
            if (!running.get()) return@addListener
            val provider = providerFuture.get()
            cameraProvider = provider
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            @Suppress("DEPRECATION")
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(320, 240))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                analyzeFrame(imageProxy)
            }
            imageAnalysis = analysis
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis
            )
        }, mainExecutor)
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        try {
            if (!running.get()) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastAnalyzeAtMs < ANALYZE_INTERVAL_MS) return
            lastAnalyzeAtMs = now

            val loadedExtractor = extractor
            val loadedRecognizer = recognizer
            val loadedProfile = profile
            val buffer = sequenceBuffer
            if (loadedExtractor == null || loadedRecognizer == null || loadedProfile == null || buffer == null) {
                postStatus("Starting Camera")
                return
            }
            if (now < cooldownUntilMs) {
                postStatus("Recognition Paused")
                return
            }

            val frame = loadedExtractor.processFrame(imageProxy)
            if (frame == null || !frame.hasPose) {
                buffer.resetStability()
                postStatus("Landmark profile not ready.")
                return
            }

            val feature = buildFeature(loadedProfile, frame)
            if (feature == null) {
                buffer.resetStability()
                postStatus(if (frame.hasAnyHand) "Landmark profile not ready." else "Waiting for clearer hand")
                return
            }

            if (!buffer.markStableFrame(true)) {
                postStatus("Tracking Hand")
                return
            }
            if (!buffer.add(feature, true)) {
                buffer.resetStability()
                postStatus("Landmark profile not ready.")
                return
            }

            val collected = buffer.size()
            if (!buffer.isReady()) {
                postStatus("Collecting Sequence $collected/${loadedProfile.sequenceLength}")
                return
            }

            postStatus("Recognizing")
            val snapshot = buffer.snapshot()
            val handPresence = buffer.handPresenceRatio()
            buffer.clear()
            if (snapshot == null) {
                postStatus("Collecting")
                return
            }

            val raw = loadedRecognizer.recognize(snapshot)
            if (raw.label.uppercase(Locale.US) !in DEMO_ACCEPTED_LABELS) {
                postStatus("Waiting for clearer hand")
                return
            }
            val quality = when {
                raw.note.startsWith("Wrong input shape", ignoreCase = true) -> "WRONG_INPUT_SHAPE"
                raw.label.isBlank() -> "BAD_SEQUENCE"
                else -> "GOOD"
            }
            val gateResult = gate.evaluate(
                GateInput(
                    label = raw.label,
                    confidence = raw.confidence,
                    margin = raw.margin,
                    handPresence = handPresence,
                    qualityStatus = quality
                )
            )
            if (!gateResult.accepted) {
                postStatus(gateReasonText(gateResult.reason))
                return
            }

            cooldownUntilMs = SystemClock.elapsedRealtime() + ACCEPTED_COOLDOWN_MS
            val accepted = RecognitionResult(
                raw.label.uppercase(Locale.US),
                raw.confidence,
                raw.margin,
                true,
                gateResult.reason,
                raw.top3
            )
            mainExecutor.execute { onAcceptedResult(accepted) }
            postStatus("Recognition Paused")
        } catch (exc: Throwable) {
            sequenceBuffer?.resetStability()
            postStatus("Landmark profile not ready.")
        } finally {
            imageProxy.close()
        }
    }

    private fun buildFeature(profile: RecognitionProfile, frame: LandmarkFrame): FloatArray? {
        return if (profile.featureProfile == "fullsign225") {
            val builder = FullSign225FeatureBuilder(profile)
            if (builder.hasRequiredLandmarks(frame)) builder.build(frame) else null
        } else {
            val builder = OneHand162FeatureBuilder(profile)
            if (builder.hasRequiredLandmarks(frame)) builder.build(frame) else null
        }
    }

    private fun gateReasonText(reason: String): String {
        return when (reason) {
            "nothing_no_output" -> "Waiting for clearer hand"
            "low_confidence" -> "Low confidence"
            "low_margin" -> "Low confidence"
            "low_hand_presence" -> "Unstable landmarks"
            "bad_sequence" -> "Collecting"
            "wrong_input_shape" -> "Landmark profile not ready."
            else -> "Waiting for clearer hand"
        }
    }

    private fun postStatus(status: String) {
        val now = SystemClock.elapsedRealtime()
        if (status == lastStatus && now - lastStatusAtMs < STATUS_MIN_INTERVAL_MS) return
        lastStatus = status
        lastStatusAtMs = now
        mainExecutor.execute { onStatus(status) }
    }

    private fun closePipeline() {
        extractor?.close()
        extractor = null
        recognizer?.close()
        recognizer = null
        profile = null
        sequenceBuffer?.clear()
        sequenceBuffer = null
    }

    companion object {
        private const val ANALYZE_INTERVAL_MS = 90L
        private const val ACCEPTED_COOLDOWN_MS = 1600L
        private const val STATUS_MIN_INTERVAL_MS = 220L
        private val DEMO_ACCEPTED_LABELS = setOf("WHAT", "YOUR", "NAME", "MY", "YOU", "OKAY", "NOTHING")
    }
}
