package com.voxgest.dryrun.fsl

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.voxgest.dryrun.LandmarkFrame
import com.voxgest.dryrun.LandmarkPoint
import com.voxgest.dryrun.MediaPipeLandmarkExtractor
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Debug-only, deliberately launched 64-class FSL diagnostic surface.
 *
 * This activity is isolated from MainActivity and the stable recognition controller. It performs
 * golden feature and TFLite parity first; a failure prevents camera binding and live inference.
 */
class FslExperimentalActivity : ComponentActivity() {
    private val analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private val packets = ArrayDeque<FramePacket>(FslContract.SEQUENCE_LENGTH)
    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var detailText: TextView
    private lateinit var probeButton: Button
    private var cameraProvider: ProcessCameraProvider? = null
    private var extractor: MediaPipeLandmarkExtractor? = null
    private var runtime: FslTfliteRuntime? = null
    private var gate: FslDiagnosticGate? = null
    private var probeRecorder: FslProbeRecorder? = null
    private var expectedLabel: String? = null
    private var signerId: String = ""
    private var sessionId: String = ""
    private var exportWindows: Boolean = true
    @Volatile private var probeComplete: Boolean = false
    @Volatile private var probeArmed: Boolean = false
    private var lastAnalyzedAtMs = 0L
    private var parityReport: FslParityReport? = null

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) bindCamera() else updateStatus("Camera permission denied", "PARITY_PASS_CAMERA_BLOCKED")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createUi()

        val requestedProfile = intent.getStringExtra(EXTRA_PROFILE).orEmpty()
        if (requestedProfile != FslContract.PROFILE_ID) {
            val evidence = "requested_profile=${requestedProfile.ifBlank { "<missing>" }} required=${FslContract.PROFILE_ID}"
            Log.e(TAG, "EXPERIMENTAL_PROFILE_NOT_SELECTED $evidence")
            updateStatus("Experimental profile not selected", evidence)
            return
        }

        expectedLabel = intent.getStringExtra(EXTRA_EXPECTED_LABEL)
            ?.trim()
            ?.takeIf(String::isNotBlank)
        sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: defaultSessionId()
        signerId = intent.getStringExtra(EXTRA_SIGNER_ID).orEmpty().trim()
        if (expectedLabel != null && signerId.isBlank()) {
            updateStatus("Probe signer ID required", "Pass --es $EXTRA_SIGNER_ID <SIGNER_ID>")
            return
        }
        exportWindows = intent.getBooleanExtra(EXTRA_EXPORT_WINDOWS, true)
        updateStatus("Running mandatory golden parity", "profile=$requestedProfile")

        analyzerExecutor.execute {
            val report = FslGoldenParityRunner(applicationContext).run()
            parityReport = report
            if (!report.passed) {
                updateStatus(
                    "Parity failed — live FSL blocked",
                    "${report.feature.marker}=${report.feature.passed}\n${report.tflite.marker}=${report.tflite.passed}"
                )
                return@execute
            }

            val loadedRuntime = runCatching { FslTfliteRuntime(applicationContext) }
                .getOrElse { error ->
                    Log.e(TAG, "FSL_RUNTIME_LOAD_FAIL", error)
                    updateStatus("FSL runtime load failed", error.message ?: error.javaClass.simpleName)
                    return@execute
                }
            val resolvedExpected = resolveExpectedLabel(expectedLabel, loadedRuntime.contract)
            if (expectedLabel != null && resolvedExpected == null) {
                loadedRuntime.close()
                updateStatus("Unsupported expected label", "expected=$expectedLabel")
                return@execute
            }
            expectedLabel = resolvedExpected
            runtime = loadedRuntime
            gate = FslDiagnosticGate(loadedRuntime.contract)
            if (resolvedExpected != null) {
                probeRecorder = FslProbeRecorder(
                    context = applicationContext,
                    maxAttemptsPerExpected = PROBE_ATTEMPTS,
                    exportPerWindowJson = exportWindows
                )
                probeComplete = probeRecorder
                    ?.recordedCount(sessionId, resolvedExpected)
                    ?.let { it >= PROBE_ATTEMPTS }
                    ?: false
                updateProbeButton()
            }
            Log.i(
                TAG,
                "FSL_PROFILE_READY profile=${loadedRuntime.contract.profileId} model=${FslContract.MODEL_ASSET} labels=${FslContract.LABELS_ASSET} model_sha256=${loadedRuntime.contract.modelSha256} deployment_eligible=${loadedRuntime.contract.deploymentEligible}"
            )

            if (intent.getBooleanExtra(EXTRA_GOLDEN_ONLY, false)) {
                updateStatus(
                    "Golden parity passed",
                    "ANDROID_FEATURE_PARITY=PASS\nANDROID_TFLITE_PARITY=PASS\nprofile=${FslContract.PROFILE_ID}"
                )
                return@execute
            }
            if (probeComplete) {
                updateStatus(
                    "Probe already complete: $resolvedExpected",
                    "session=$sessionId attempts=$PROBE_ATTEMPTS/$PROBE_ATTEMPTS\nUse a new session ID to repeat."
                )
                return@execute
            }
            runOnUiThread { startCameraAfterParity() }
        }
    }

    override fun onDestroy() {
        running.set(false)
        cameraProvider?.unbindAll()
        cameraProvider = null
        analyzerExecutor.execute {
            extractor?.close()
            extractor = null
            runtime?.close()
            runtime = null
        }
        analyzerExecutor.shutdown()
        super.onDestroy()
    }

    private fun createUi() {
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        statusText = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC102A43.toInt())
            textSize = 17f
            setPadding(18.dp, 14.dp, 18.dp, 10.dp)
            text = "VoxGest Experimental FSL"
        }
        detailText = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC0B1F33.toInt())
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(18.dp, 10.dp, 18.dp, 14.dp)
            text = "profile=${FslContract.PROFILE_ID}"
        }
        val closeButton = Button(this).apply {
            text = "Close experimental mode"
            setOnClickListener { finish() }
        }
        probeButton = Button(this).apply {
            text = "Capture probe attempt"
            visibility = View.GONE
            setOnClickListener {
                if (probeComplete) return@setOnClickListener
                analyzerExecutor.execute {
                    packets.clear()
                    gate?.reset()
                    probeArmed = true
                    updateStatus(
                        "Capture armed: $expectedLabel",
                        "Perform one complete sign now. readiness=0/${FslContract.SEQUENCE_LENGTH}"
                    )
                }
            }
        }
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(statusText, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(detailText, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(probeButton, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(closeButton, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(previewView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(
                overlay,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP)
            )
        }
        setContentView(root)
    }

    private fun startCameraAfterParity() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            bindCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun bindCamera() {
        if (!running.compareAndSet(false, true)) return
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            if (!running.get()) return@addListener
            val provider = providerFuture.get()
            cameraProvider = provider
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            @Suppress("DEPRECATION")
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(320, 240))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analyzerExecutor.execute {
                extractor?.close()
                // MediaPipe Hands handedness assumes selfie/mirrored input. The fixed "right"
                // output slot is therefore anatomical right for the front-camera probe.
                extractor = MediaPipeLandmarkExtractor(applicationContext, true)
            }
            analysis.setAnalyzer(analyzerExecutor) { image -> analyze(image) }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            updateStatus(
                if (expectedLabel == null) "Experimental FSL live diagnostic" else "Probe: $expectedLabel",
                "profile=${FslContract.PROFILE_ID}\nready=0/${FslContract.SEQUENCE_LENGTH}\nactivity_state=UNVALIDATED_NO_BACKGROUND_CLASS"
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAnalyzedAtMs < ANALYZE_INTERVAL_MS) return
            lastAnalyzedAtMs = now
            val frame = extractor?.processFrame(image)
            val canonical = FslCanonicalFeatureBuilder.build(frame)
            if (probeComplete) return
            if (expectedLabel != null && !probeArmed) return

            // Do not begin a window from a completely empty scene. Once either pose or the
            // anatomical-right hand exists, missing parts retain their canonical zero slots.
            if (packets.isEmpty() && !canonical.posePresent && !canonical.handPresent) {
                postDiagnostic(canonical, null, null, "WAITING_FOR_LANDMARKS")
                return
            }
            val priorTimestamp = packets.lastOrNull()?.frame?.timestampMs ?: -1L
            val packetTimestamp = maxOf(frame?.timestampMs ?: now, priorTimestamp + 1L)
            val sourceFrame = (frame ?: LandmarkFrame(null, null, null, packetTimestamp, emptyList()))
                .copy(timestampMs = packetTimestamp)
            if (packets.size == FslContract.SEQUENCE_LENGTH) packets.removeFirst()
            packets.addLast(FramePacket(sourceFrame, canonical))

            if (packets.size < FslContract.SEQUENCE_LENGTH) {
                postDiagnostic(canonical, null, null, "COLLECTING")
                return
            }

            val localRuntime = runtime ?: return
            val inference = localRuntime.infer(packets.map { it.canonical.vector })
            val decision = gate?.evaluate(inference) ?: return
            postDiagnostic(canonical, inference, decision, decision.state)
            recordProbeIfEnabled(inference, decision)
        } catch (error: Throwable) {
            Log.e(TAG, "FSL_LIVE_ERROR", error)
            packets.clear()
            gate?.reset()
            probeArmed = false
            updateProbeButton()
            updateStatus("Experimental FSL error", error.message ?: error.javaClass.simpleName)
        } finally {
            image.close()
        }
    }

    private fun recordProbeIfEnabled(inference: FslInference, decision: FslDiagnosticDecision) {
        val expected = expectedLabel ?: return
        val recorder = probeRecorder ?: return
        val attempt = FslProbeAttempt(
            metadata = FslProbeMetadata(
                expected = expected,
                predicted = inference.top1.label,
                top1 = FslProbeRank(inference.top1.label, inference.top1.probability.coerceIn(0f, 1f)),
                top2 = FslProbeRank(inference.top2.label, inference.top2.probability.coerceIn(0f, 1f)),
                margin = inference.margin,
                latencyMs = inference.latencyMs.toFloat(),
                accepted = decision.accepted,
                state = "${decision.state}:${decision.reason}",
                timestampMs = System.currentTimeMillis(),
                signerId = signerId,
                sessionId = sessionId,
                deviceId = deviceId(),
                modelId = "${FslContract.MODEL_ASSET.substringAfterLast('/')}@${runtime?.contract?.modelSha256?.take(12)}"
            ),
            window = FslProbeWindow(
                rawFrames = packets.map { packet -> packet.frame.toProbeRawFrame() },
                canonicalFeatures = packets.map { packet -> packet.canonical.vector.copyOf() }
            )
        )
        val result = recorder.record(attempt)
        Log.i(
            TAG,
            "FSL_PROBE_RECORD recorded=${result.recorded} expected=$expected attempt=${result.attemptForExpected}/${result.maxAttemptsPerExpected} total=${result.totalSessionAttempts} csv=${result.paths.csvPath} jsonl=${result.paths.jsonlPath} window=${result.windowJsonPath} reason=${result.rejectionReason}"
        )
        packets.clear()
        probeArmed = false
        if (result.attemptForExpected >= PROBE_ATTEMPTS) {
            probeComplete = true
            updateStatus(
                "Probe complete: $expected ${result.attemptForExpected}/$PROBE_ATTEMPTS",
                "csv=${result.paths.csvPath}\njsonl=${result.paths.jsonlPath}\nUse adb run-as export command from the integration guide."
            )
        } else if (result.recorded) {
            updateStatus(
                "Attempt ${result.attemptForExpected}/$PROBE_ATTEMPTS saved: $expected",
                "Tap Capture probe attempt when ready for the next sign."
            )
        }
        updateProbeButton()
    }

    private fun postDiagnostic(
        current: FslCanonicalFrame,
        inference: FslInference?,
        decision: FslDiagnosticDecision?,
        state: String
    ) {
        val poseRatio = packets.count { it.canonical.posePresent }.toFloat() / packets.size.coerceAtLeast(1)
        val handRatio = packets.count { it.canonical.handPresent }.toFloat() / packets.size.coerceAtLeast(1)
        val top1 = inference?.top1
        val top2 = inference?.top2
        val line = buildString {
            append("profile=${FslContract.PROFILE_ID}")
            append(" class=${top1?.label ?: "<pending>"}")
            append(" top1=${top1?.let { "${it.label}:${format(it.probability)}" } ?: "<pending>"}")
            append(" top2=${top2?.let { "${it.label}:${format(it.probability)}" } ?: "<pending>"}")
            append(" margin=${inference?.let { format(it.margin) } ?: "<pending>"}")
            append(" hand_present=${current.handPresent}")
            append(" pose_present=${current.posePresent}")
            append(" hand_ratio=${format(handRatio)}")
            append(" pose_ratio=${format(poseRatio)}")
            append(" readiness=${packets.size}/${FslContract.SEQUENCE_LENGTH}")
            append(" latency_ms=${inference?.let { "%.3f".format(Locale.US, it.latencyMs) } ?: "<pending>"}")
            append(" accepted=${decision?.accepted ?: false}")
            append(" state=$state")
            append(" reason=${decision?.reason ?: "NONE"}")
            append(" activity_state=UNVALIDATED_NO_BACKGROUND_CLASS")
            append(" selected_hand=anatomical_right")
        }
        Log.i(TAG, "FSL_LIVE_DIAGNOSTIC $line")
        val title = when {
            top1 == null -> if (expectedLabel == null) "Collecting FSL window" else "Probe $expectedLabel"
            decision?.accepted == true -> "Diagnostic: ${top1.label}"
            else -> "Diagnostic rejected: ${top1.label}"
        }
        updateStatus(title, line.replace(' ', '\n'))
        updateProbeButton()
    }

    private fun resolveExpectedLabel(requested: String?, contract: FslRuntimeContract): String? {
        if (requested == null) return null
        val token = FslContract.androidToken(requested)
        val index = contract.androidTokens.indexOf(token)
        return contract.labels.getOrNull(index)
    }

    private fun LandmarkFrame.toProbeRawFrame(): FslProbeRawFrame {
        return FslProbeRawFrame(
            timestampMs = timestampMs,
            poseLandmarks = poseLandmarks?.map { it.toProbeLandmark() },
            rightHandLandmarks = rightHandLandmarks?.map { it.toProbeLandmark() }
        )
    }

    private fun LandmarkPoint.toProbeLandmark(): FslProbeLandmark {
        return FslProbeLandmark(x, y, z)
    }

    private fun deviceId(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(androidId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(12)
        return "${Build.MANUFACTURER}_${Build.MODEL}_android_id_sha256_$digest"
    }

    private fun defaultSessionId(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        return "android_fsl_probe_$stamp"
    }

    private fun updateStatus(title: String, detail: String) {
        runOnUiThread {
            statusText.text = title
            detailText.text = detail
        }
    }

    private fun updateProbeButton() {
        runOnUiThread {
            probeButton.visibility = if (expectedLabel == null) View.GONE else View.VISIBLE
            probeButton.isEnabled = !probeComplete && !probeArmed
            val count = expectedLabel?.let { label -> probeRecorder?.recordedCount(sessionId, label) } ?: 0
            probeButton.text = if (probeComplete) {
                "Probe complete $count/$PROBE_ATTEMPTS"
            } else if (probeArmed) {
                "Capturing ${packets.size}/${FslContract.SEQUENCE_LENGTH}"
            } else {
                "Capture probe attempt ${count + 1}/$PROBE_ATTEMPTS"
            }
        }
    }

    private fun format(value: Float): String = "%.4f".format(Locale.US, value)
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private data class FramePacket(
        val frame: LandmarkFrame,
        val canonical: FslCanonicalFrame
    )

    companion object {
        const val EXTRA_PROFILE = "voxgest.profile"
        const val EXTRA_GOLDEN_ONLY = "voxgest.golden_only"
        const val EXTRA_EXPECTED_LABEL = "voxgest.expected_label"
        const val EXTRA_SIGNER_ID = "voxgest.signer_id"
        const val EXTRA_SESSION_ID = "voxgest.session_id"
        const val EXTRA_EXPORT_WINDOWS = "voxgest.export_windows"
        private const val TAG = "VoxGestFsl"
        private const val ANALYZE_INTERVAL_MS = 90L
        private const val PROBE_ATTEMPTS = 5
    }
}
