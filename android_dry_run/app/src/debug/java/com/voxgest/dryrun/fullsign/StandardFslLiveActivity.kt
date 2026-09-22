package com.voxgest.dryrun.fullsign

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.voxgest.dryrun.GradingProfileActivationGate
import com.voxgest.dryrun.GradingProfileActivationState
import com.voxgest.dryrun.StandardFslArtifactGate
import com.voxgest.dryrun.StandardFslDiagnostics
import com.voxgest.dryrun.StandardFslRuntimeScaffold
import com.voxgest.dryrun.StandardFslTfliteRuntime
import com.voxgest.dryrun.StandardFslCameraPipeline
import com.voxgest.dryrun.StandardFslParityHarness
import com.voxgest.dryrun.StandardFslParityStatus
import com.voxgest.dryrun.StandardFslRuntimePolicy
import com.voxgest.dryrun.StandardFslGateDecision
import com.voxgest.dryrun.StandardFslInference
import com.voxgest.dryrun.StandardFullSign225WindowQuality
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Debug-only Samsung evidence activity for the finalized Standard FSL bundle.
 * It deliberately has no legacy-model fallback: a failed Standard gate leaves
 * this activity blocked rather than silently loading OneHand162.
 */
class StandardFslLiveActivity : ComponentActivity() {
    private val running = AtomicBoolean(false)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val uiExecutor by lazy { ContextCompat.getMainExecutor(this) }
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startAfterPermission()
        } else {
            blocked("CAMERA_PERMISSION BLOCKED")
        }
    }

    private lateinit var previewView: PreviewView
    private lateinit var diagnosticsView: TextView
    private var trialButton: Button? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var extractor: AutoCloseable? = null
    private var runtime: StandardFslTfliteRuntime? = null
    private var scaffold: StandardFslRuntimeScaffold? = null
    private var lastEvidenceAtMs = 0L
    private var lastGateEvidenceAtMs = 0L
    private var lastGateReason = ""
    private var trackingReported = false
    private var bufferReported = false
    private var trialRecorder: FullSign225LiveTrialRecorder? = null
    @Volatile private var activeTrial: ActiveTrial? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        trialRecorder = runCatching {
            FullSign225LiveTrialRecorder.fromIntent(applicationContext, intent)
        }.onFailure { error ->
            Log.e(TAG, "LIVE_TRIAL_SETUP BLOCKED", error)
        }.getOrNull()
        createContent()
        Log.i(TAG, "APP_LAUNCH PASS activity=StandardFslLiveActivity profile=STANDARD_FSL_FULLSIGN225")
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startAfterPermission()
        } else {
            diagnostics("CAMERA_PERMISSION pending")
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        stopLivePipeline()
        analysisExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun createContent() {
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            // UX only. ImageAnalysis receives the original, unmirrored camera frame.
            scaleX = -1f
        }
        diagnosticsView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0xB3000000.toInt())
            textSize = 12f
            setPadding(24, 20, 24, 20)
            text = "STANDARD_FSL_FULLSIGN225\nPreparing verified runtime…"
        }
        setContentView(FrameLayout(this).apply {
            addView(previewView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(diagnosticsView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ))
            trialRecorder?.let { recorder ->
                trialButton = Button(this@StandardFslLiveActivity).apply {
                    text = "ARM ${recorder.request.expectedLabel} TRIAL"
                    setOnClickListener { armTrial() }
                }
                addView(trialButton, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = 24
                    rightMargin = 24
                    bottomMargin = 24
                })
            }
        })
    }

    private fun startAfterPermission() {
        if (!running.compareAndSet(false, true)) return
        diagnostics("Verifying Standard FullSign225 artifacts and Android parity…")
        analysisExecutor.execute {
            try {
                val parity = StandardFslParityHarness(applicationContext).run()
                Log.i(TAG, "${parity.feature.marker} ${parity.feature.status} ${parity.feature.evidence}")
                Log.i(TAG, "${parity.tflite.marker} ${parity.tflite.status} ${parity.tflite.evidence}")
                val artifacts = StandardFslArtifactGate.inspect(applicationContext)
                val activation = GradingProfileActivationGate.standard(
                    artifacts.state,
                    featureParityPassed = parity.feature.status == StandardFslParityStatus.PASS,
                    tfliteParityPassed = parity.tflite.status == StandardFslParityStatus.PASS
                )
                check(activation.state == GradingProfileActivationState.READY) {
                    "STANDARD_FSL activation blocked: ${activation.evidence} ${artifacts.evidence}"
                }

                runtime = StandardFslTfliteRuntime(applicationContext)
                extractor = StandardFslCameraPipeline.createLandmarkExtractor(applicationContext)
                scaffold = StandardFslRuntimeScaffold(StandardFslRuntimePolicy.REJECTION_CONFIG)
                Log.i(
                    TAG,
                    "STANDARD_MODEL_LOAD PASS active_profile=STANDARD_FSL_FULLSIGN225 " +
                        "input=[1,20,225] output=[1,105] feature_version=fullsign225_20f_v1 " +
                        "model_input=unmirrored ${artifacts.evidence}"
                )
                Log.i(TAG, "ONEHAND162_FALLBACK PASS preserved=true loaded=false reason=standard_profile_is_ready")
                runOnUiThread { bindFrontCamera() }
            } catch (error: Throwable) {
                Log.e(TAG, "STANDARD_MODEL_LOAD BLOCKED", error)
                running.set(false)
                blocked("STANDARD_MODEL_LOAD BLOCKED\n${error.javaClass.simpleName}: ${error.message}")
                closeRuntime()
            }
        }
    }

    private fun bindFrontCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(applicationContext)
        providerFuture.addListener({
            if (!running.get()) return@addListener
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(192, 144))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { image -> analyze(image) }
                imageAnalysis = analysis
                provider.unbindAll()
                provider.bindToLifecycle(
                    this@StandardFslLiveActivity,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )
                Log.i(
                    TAG,
                    "FRONT_CAMERA PASS lens=FRONT preview_mirrored=true " +
                        "analysis_mirrored=false model_input=unmirrored"
                )
                diagnostics(
                    "STANDARD_FSL_FULLSIGN225\nFront camera tracking started\n" +
                        "Preview mirrored; model input unmirrored" +
                        trialRecorder?.let { "\nTrial ${it.request.trialId}: tap ARM when the target is visible" }.orEmpty()
                )
            } catch (error: Throwable) {
                Log.e(TAG, "FRONT_CAMERA BLOCKED", error)
                running.set(false)
                blocked("FRONT_CAMERA BLOCKED\n${error.javaClass.simpleName}: ${error.message}")
            }
        }, uiExecutor)
    }

    private fun analyze(image: androidx.camera.core.ImageProxy) {
        try {
            if (!running.get()) return
            val frame = (extractor as? com.voxgest.dryrun.LandmarkExtractor)?.processFrame(image) ?: return
            val localScaffold = scaffold ?: return
            var diagnostics = localScaffold.append(frame).diagnostics
            val window = localScaffold.snapshotForInference()
            if (window != null) {
                if (!bufferReported) {
                    bufferReported = true
                    Log.i(TAG, "ROLLING_BUFFER_[20,225] PASS capacity=20 frame_features=225 input=[1,20,225]")
                }
                val inference = runtime?.infer(window) ?: return
                val decision = localScaffold.evaluate(inference, SystemClock.elapsedRealtime())
                diagnostics = decision.diagnostics
                reportGateDecision(decision.decision.accepted, decision.decision.reason, decision.decision.displayText,
                    decision.decision.stableWindowCount, diagnostics)
                recordArmedTrialIfTerminal(
                    decision = decision.decision,
                    inference = inference,
                    diagnostics = diagnostics,
                    quality = localScaffold.windowQuality()
                )
            }
            reportLiveState(frame, diagnostics)
        } catch (error: Throwable) {
            Log.e(TAG, "LIVE_STANDARD_FSL ERROR", error)
            diagnostics("LIVE_STANDARD_FSL ERROR\n${error.javaClass.simpleName}: ${error.message}")
        } finally {
            image.close()
        }
    }

    private fun reportLiveState(
        frame: com.voxgest.dryrun.LandmarkFrame,
        evidence: StandardFslDiagnostics
    ) {
        val now = SystemClock.elapsedRealtime()
        if (!trackingReported && (frame.hasPose || frame.hasAnyHand)) {
            trackingReported = true
            Log.i(
                TAG,
                "MEDIAPIPE_TRACKING PASS pose=${frame.hasPose} left=${frame.hasLeftHand} right=${frame.hasRightHand}"
            )
        }
        if (now - lastEvidenceAtMs < EVIDENCE_INTERVAL_MS) return
        lastEvidenceAtMs = now
        val observed = frame.handObservations.joinToString(prefix = "[", postfix = "]") {
            "slot=${it.slot},reported=${it.mediaPipeHandedness},${it.physicalSideEstimate}"
        }
        Log.i(
            TAG,
            "ANATOMICAL_LEFT_RIGHT PASS slot_policy=pose99_then_anatomical_left63_then_anatomical_right63_never_swapped " +
                "analysis_mirrored=false observations=$observed ${evidence.toLogLine()}"
        )
        val top = evidence.top1Label ?: "<collecting>"
        val confidence = evidence.top1Confidence?.let { String.format(Locale.US, "%.4f", it) } ?: "<pending>"
        val margin = evidence.top2Margin?.let { String.format(Locale.US, "%.4f", it) } ?: "<pending>"
        diagnostics(
            "STANDARD_FSL_FULLSIGN225\n" +
                "pose=${frame.hasPose} left=${frame.hasLeftHand} right=${frame.hasRightHand}\n" +
                "buffer=${evidence.bufferFrames}/20  top1=$top  confidence=$confidence  margin=$margin\n" +
                "gate=${evidence.rejectionReason ?: if (evidence.accepted) "ACCEPTED" else "collecting"}\n" +
                "Preview mirrored; model data unmirrored/anatomical"
        )
    }

    private fun reportGateDecision(
        accepted: Boolean,
        reason: String,
        display: String,
        stableWindows: Int,
        evidence: StandardFslDiagnostics
    ) {
        val now = SystemClock.elapsedRealtime()
        if (!accepted && reason == lastGateReason && now - lastGateEvidenceAtMs < EVIDENCE_INTERVAL_MS) return
        lastGateEvidenceAtMs = now
        lastGateReason = reason
        Log.i(
            TAG,
            "REJECTION_GATE ${if (accepted) "ACCEPT" else "REJECT"} reason=$reason display=$display " +
                "stable_windows=$stableWindows ${evidence.toLogLine()}"
        )
    }

    /** Arms one explicit trial after the operator has placed the target in the front-camera view. */
    private fun armTrial() {
        val recorder = trialRecorder ?: return
        analysisExecutor.execute {
            val localScaffold = scaffold
            if (!running.get() || localScaffold == null) {
                Log.e(TAG, "LIVE_TRIAL_ARM BLOCKED runtime_not_ready trial=${recorder.request.trialId}")
                diagnostics("Trial cannot arm until the verified runtime is ready")
                return@execute
            }
            if (activeTrial != null) return@execute
            localScaffold.reset()
            activeTrial = ActiveTrial(SystemClock.elapsedRealtime())
            Log.i(
                TAG,
                "LIVE_TRIAL_ARMED mode=${recorder.request.mode} trial=${recorder.request.trialId} " +
                    "expected=${recorder.request.expectedLabel} model_input=unmirrored"
            )
            runOnUiThread {
                trialButton?.isEnabled = false
                trialButton?.text = "RECORDING ${recorder.request.expectedLabel}…"
            }
        }
    }

    /**
     * Records the first terminal gate decision after explicit arming.
     * TEMPORAL_STABILITY stays provisional until a further window or timeout.
     */
    private fun recordArmedTrialIfTerminal(
        decision: StandardFslGateDecision,
        inference: StandardFslInference,
        diagnostics: StandardFslDiagnostics,
        quality: StandardFullSign225WindowQuality
    ) {
        val active = activeTrial ?: return
        val elapsedMs = SystemClock.elapsedRealtime() - active.armedAtMs
        val terminal = decision.accepted ||
            decision.reason != "TEMPORAL_STABILITY" ||
            elapsedMs >= TRIAL_TIMEOUT_MS
        if (!terminal) return
        activeTrial = null
        val recorder = trialRecorder ?: return
        try {
            val result = recorder.record(
                FullSign225LiveTrialRecorder.Observation(
                    predictedLabel = inference.top1.label,
                    accepted = decision.accepted,
                    confidence = inference.top1.probability,
                    top2Label = inference.top2.label,
                    top2Confidence = inference.top2.probability,
                    margin = inference.margin,
                    rejectionReason = if (decision.accepted) null else decision.reason,
                    stableWindows = decision.stableWindowCount,
                    posePresent = diagnostics.posePresent,
                    leftHandPresent = diagnostics.leftHandPresent,
                    rightHandPresent = diagnostics.rightHandPresent,
                    quality = quality,
                    inferenceLatencyMs = inference.latencyMs,
                    durationMs = elapsedMs
                )
            )
            Log.i(
                TAG,
                "LIVE_TRIAL_RECORDED file=${result.file.absolutePath} count=${result.totalTrials} " +
                    "trial=${recorder.request.trialId} expected=${recorder.request.expectedLabel} " +
                    "predicted=${inference.top1.label} accepted=${decision.accepted} " +
                    "reason=${if (decision.accepted) "ACCEPTED" else decision.reason}"
            )
            runOnUiThread {
                trialButton?.text = "RECORDED ${if (decision.accepted) "ACCEPT" else "REJECT"}"
                diagnosticsView.append(
                    "\nTrial ${recorder.request.trialId}: ${if (decision.accepted) "ACCEPT" else "REJECT"} " +
                        "${inference.top1.label} (${if (decision.accepted) "accepted" else decision.reason})"
                )
            }
        } catch (error: Throwable) {
            Log.e(TAG, "LIVE_TRIAL_RECORD BLOCKED", error)
            runOnUiThread {
                trialButton?.text = "TRIAL RECORD BLOCKED"
                diagnosticsView.append("\nTrial record blocked: ${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }

    private fun stopLivePipeline() {
        running.set(false)
        activeTrial = null
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        closeRuntime()
    }

    private fun closeRuntime() {
        runCatching { extractor?.close() }
        extractor = null
        runCatching { runtime?.close() }
        runtime = null
        scaffold = null
    }

    private fun diagnostics(value: String) {
        runOnUiThread { diagnosticsView.text = value }
    }

    private fun blocked(value: String) {
        Log.e(TAG, value)
        diagnostics(value)
    }

    companion object {
        private const val TAG = "VoxGestFullSign225"
        private const val EVIDENCE_INTERVAL_MS = 1_000L
        private const val TRIAL_TIMEOUT_MS = 6_000L

    }

    private data class ActiveTrial(val armedAtMs: Long)
}
