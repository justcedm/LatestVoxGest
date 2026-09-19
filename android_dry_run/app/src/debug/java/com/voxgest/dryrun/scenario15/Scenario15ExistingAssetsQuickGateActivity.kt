package com.voxgest.dryrun.scenario15

import android.Manifest
import android.content.Context
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
import com.voxgest.dryrun.FullSign225FeatureBuilder
import com.voxgest.dryrun.LandmarkFrame
import com.voxgest.dryrun.LandmarkSequenceBuffer
import com.voxgest.dryrun.MediaPipeLandmarkExtractor
import com.voxgest.dryrun.RecognitionProfile
import com.voxgest.dryrun.StandardFslArtifactGate
import com.voxgest.dryrun.StandardFslParityHarness
import com.voxgest.dryrun.StandardFslParityStatus
import com.voxgest.dryrun.StandardFslRuntimePolicy
import com.voxgest.dryrun.StandardFslRuntimeScaffold
import com.voxgest.dryrun.StandardFslTfliteRuntime
import com.voxgest.dryrun.TfliteModelLoader
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Debug-only, manually armed smoke gate for the two preserved Scenario-15 assets.
 * It never changes either model, its thresholds, or the production camera route.
 */
class Scenario15ExistingAssetsQuickGateActivity : ComponentActivity() {
    private val running = AtomicBoolean(false)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val uiExecutor by lazy { ContextCompat.getMainExecutor(this) }
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startAfterPermission() else blocked("CAMERA_PERMISSION BLOCKED") }

    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private lateinit var armButton: Button
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var extractor: MediaPipeLandmarkExtractor? = null
    private var phraseRuntime: PhraseRuntime? = null
    private var phraseProfile: RecognitionProfile? = null
    private val phraseBuffer = LandmarkSequenceBuffer(PHRASE_FRAMES, FEATURE_SIZE)
    private var standardRuntime: StandardFslTfliteRuntime? = null
    private val standardScaffold = StandardFslRuntimeScaffold(StandardFslRuntimePolicy.REJECTION_CONFIG)
    private val trials = buildTrials()
    private var trialIndex = 0
    @Volatile private var armedAtMs = 0L
    private var observedFrames = 0
    private var poseFrames = 0
    private var leftFrames = 0
    private var rightFrames = 0
    private var anyHandFrames = 0
    private var sessionId = ""
    private lateinit var outputFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionId = intent.getStringExtra(EXTRA_SESSION_ID)?.trim().orEmpty()
            .ifBlank { "scenario15-quick-${System.currentTimeMillis()}" }
        val directory = getExternalFilesDir(OUTPUT_DIRECTORY) ?: File(filesDir, OUTPUT_DIRECTORY)
        require(directory.exists() || directory.mkdirs()) { "Cannot create ${directory.absolutePath}" }
        outputFile = File(directory, "$sessionId.json")
        createContent()
        Log.i(TAG, "SCENARIO15_QUICK_GATE_LAUNCH session=$sessionId trials=${trials.size}")
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startAfterPermission()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        running.set(false)
        analysis?.clearAnalyzer()
        provider?.unbindAll()
        runCatching { extractor?.close() }
        runCatching { phraseRuntime?.close() }
        runCatching { standardRuntime?.close() }
        analysisExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun createContent() {
        preview = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleX = -1f
        }
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0xB3000000.toInt())
            textSize = 14f
            setPadding(24, 20, 24, 20)
            text = "SCENARIO15 QUICK GATE\nLoading preserved assets..."
        }
        armButton = Button(this).apply {
            text = "WAITING FOR VERIFIED RUNTIMES"
            isEnabled = false
            setOnClickListener { armCurrentTrial() }
        }
        setContentView(FrameLayout(this).apply {
            addView(preview, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(status, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ))
            addView(armButton, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                leftMargin = 24
                rightMargin = 24
                bottomMargin = 24
            })
        })
    }

    private fun startAfterPermission() {
        if (!running.compareAndSet(false, true)) return
        analysisExecutor.execute {
            try {
                val profile = RecognitionProfile.load(applicationContext, PHRASE_PROFILE)
                check(profile.inputShape.contentEquals(intArrayOf(1, PHRASE_FRAMES, FEATURE_SIZE)))
                phraseProfile = profile
                phraseRuntime = PhraseRuntime(applicationContext, profile)

                val parity = StandardFslParityHarness(applicationContext).run()
                check(parity.feature.status == StandardFslParityStatus.PASS) { parity.feature.evidence }
                check(parity.tflite.status == StandardFslParityStatus.PASS) { parity.tflite.evidence }
                val artifacts = StandardFslArtifactGate.inspect(applicationContext)
                check(artifacts.canOpenRuntimeForParity) { artifacts.evidence }
                standardRuntime = StandardFslTfliteRuntime(applicationContext)

                configureExtractor(Lane.PHRASE)
                Log.i(TAG, "${parity.feature.marker} ${parity.feature.status} ${parity.feature.evidence}")
                Log.i(TAG, "${parity.tflite.marker} ${parity.tflite.status} ${parity.tflite.evidence}")
                Log.i(
                    TAG,
                    "SCENARIO15_EXISTING_ASSETS_READY phrase_profile=$PHRASE_PROFILE " +
                        "phrase_input=${profile.shapeText()} phrase_output=[1,${profile.labels.size}] " +
                        "phrase_analysis_mirrored=${profile.mirroredInput} standard_input=[1,20,225] " +
                        "standard_analysis_mirrored=false preview_mirrored=true"
                )
                runOnUiThread { bindCamera() }
            } catch (error: Throwable) {
                Log.e(TAG, "SCENARIO15_EXISTING_ASSETS_BLOCKED", error)
                running.set(false)
                blocked("STARTUP BLOCKED\n${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(applicationContext)
        future.addListener({
            try {
                val localProvider = future.get()
                provider = localProvider
                val cameraPreview = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                val localAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(192, 144))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                localAnalysis.setAnalyzer(analysisExecutor) { analyze(it) }
                analysis = localAnalysis
                localProvider.unbindAll()
                localProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, cameraPreview, localAnalysis)
                showNextTrial("Verified assets ready")
            } catch (error: Throwable) {
                Log.e(TAG, "SCENARIO15_CAMERA_BLOCKED", error)
                blocked("CAMERA BLOCKED\n${error.javaClass.simpleName}: ${error.message}")
            }
        }, uiExecutor)
    }

    private fun armCurrentTrial() {
        analysisExecutor.execute {
            if (!running.get() || trialIndex !in trials.indices || armedAtMs != 0L) return@execute
            phraseBuffer.clear()
            standardScaffold.reset()
            observedFrames = 0
            poseFrames = 0
            leftFrames = 0
            rightFrames = 0
            anyHandFrames = 0
            armedAtMs = SystemClock.elapsedRealtime()
            val trial = trials[trialIndex]
            Log.i(TAG, "SCENARIO15_TRIAL_ARMED index=$trialIndex lane=${trial.lane} expected=${trial.label} attempt=${trial.attempt}")
            runOnUiThread {
                armButton.isEnabled = false
                armButton.text = "SIGN ${trial.label} ONCE, THEN NEUTRAL"
                status.text = "${trial.label} attempt ${trial.attempt}/3\nSign once now, then return to neutral."
            }
        }
    }

    private fun analyze(image: androidx.camera.core.ImageProxy) {
        try {
            if (!running.get()) return
            val frame = extractor?.processFrame(image) ?: return
            if (armedAtMs == 0L || trialIndex !in trials.indices) return
            observedFrames += 1
            if (frame.hasPose) poseFrames += 1
            if (frame.hasLeftHand) leftFrames += 1
            if (frame.hasRightHand) rightFrames += 1
            if (frame.hasAnyHand) anyHandFrames += 1
            when (trials[trialIndex].lane) {
                Lane.PHRASE -> analyzePhrase(frame)
                Lane.STANDARD -> analyzeStandard(frame)
            }
        } catch (error: Throwable) {
            Log.e(TAG, "SCENARIO15_QUICK_GATE_ERROR", error)
            completeWithError(error)
        } finally {
            image.close()
        }
    }

    private fun analyzePhrase(frame: LandmarkFrame) {
        val profile = phraseProfile ?: return
        val builder = FullSign225FeatureBuilder(profile)
        if (!builder.hasRequiredLandmarks(frame)) {
            if (elapsedMs() >= TRIAL_TIMEOUT_MS) recordNoInference("TRACKING_TIMEOUT")
            return
        }
        val feature = builder.build(frame) ?: return
        phraseBuffer.add(feature, frame.hasAnyHand)
        val window = phraseBuffer.snapshot() ?: return
        val inference = phraseRuntime?.infer(window) ?: return
        val decision = PhraseGatePolicy.evaluate(inference)
        record(inference, decision.first, decision.second, window.size, inference.latencyMs)
    }

    private fun analyzeStandard(frame: LandmarkFrame) {
        standardScaffold.append(frame)
        val window = standardScaffold.snapshotForInference()
        if (window == null) {
            if (elapsedMs() >= TRIAL_TIMEOUT_MS) recordNoInference("BUFFER_TIMEOUT")
            return
        }
        val inference = standardRuntime?.infer(window) ?: return
        val update = standardScaffold.evaluate(inference, frame.timestampMs, frame.hasPose && frame.hasAnyHand)
        if (!update.decision.accepted && update.decision.reason == "TEMPORAL_STABILITY" && elapsedMs() < TRIAL_TIMEOUT_MS) return
        val exposed = inference.top1.label in STANDARD_EXPOSED_LABELS
        val accepted = update.decision.accepted && exposed
        val reason = when {
            update.decision.accepted && !exposed -> "UNSUPPORTED_STANDARD_LABEL"
            accepted -> "ACCEPTED"
            else -> update.decision.reason
        }
        record(
            Inference(
                inference.top1.label,
                inference.top1.probability,
                inference.margin,
                inference.top5.take(3).map { Ranked(it.label, it.probability) },
                inference.latencyMs
            ),
            accepted,
            reason,
            window.size,
            inference.latencyMs
        )
    }

    private fun record(
        inference: Inference,
        accepted: Boolean,
        reason: String,
        capturedFrames: Int,
        inferenceLatencyMs: Double
    ) {
        val trial = trials[trialIndex]
        val result = baseResult(trial)
            .put("raw_top1", inference.label)
            .put("raw_top1_probability", inference.confidence)
            .put("top1_top2_margin", inference.margin)
            .put("top3", JSONArray(inference.top3.map { JSONObject().put("label", it.label).put("probability", it.probability) }))
            .put("raw_correct", inference.label == trial.label)
            .put("accepted", accepted)
            .put("accepted_correct", accepted && inference.label == trial.label)
            .put("rejection_reason", if (accepted) JSONObject.NULL else reason)
            .put("captured_frames", capturedFrames)
            .put("inference_latency_ms", inferenceLatencyMs)
        persist(result)
        Log.i(
            TAG,
            "SCENARIO15_QUICK_RESULT lane=${trial.lane} expected=${trial.label} attempt=${trial.attempt} " +
                "raw_top1=${inference.label} confidence=${fmt(inference.confidence)} margin=${fmt(inference.margin)} " +
                "top3=${inference.top3.joinToString(prefix = "[", postfix = "]") { "${it.label}:${fmt(it.probability)}" }} " +
                "raw_correct=${inference.label == trial.label} accepted=$accepted reason=$reason " +
                "frames=$capturedFrames observed=$observedFrames pose_ratio=${fmt(ratio(poseFrames, observedFrames))} " +
                "left_ratio=${fmt(ratio(leftFrames, observedFrames))} right_ratio=${fmt(ratio(rightFrames, observedFrames))} " +
                "any_hand_ratio=${fmt(ratio(anyHandFrames, observedFrames))} latency_ms=${String.format(Locale.US, "%.3f", inferenceLatencyMs)}"
        )
        advance()
    }

    private fun recordNoInference(reason: String) {
        val trial = trials[trialIndex]
        val captured = if (trial.lane == Lane.PHRASE) phraseBuffer.size() else standardScaffold.bufferFrames
        persist(
            baseResult(trial)
                .put("raw_top1", JSONObject.NULL)
                .put("raw_correct", false)
                .put("accepted", false)
                .put("accepted_correct", false)
                .put("rejection_reason", reason)
                .put("captured_frames", captured)
        )
        Log.i(TAG, "SCENARIO15_QUICK_RESULT lane=${trial.lane} expected=${trial.label} attempt=${trial.attempt} raw_top1=NOT_RUN accepted=false reason=$reason")
        advance()
    }

    private fun baseResult(trial: Trial): JSONObject = JSONObject()
        .put("trial_index", trialIndex)
        .put("lane", trial.lane.name)
        .put("expected_label", trial.label)
        .put("attempt", trial.attempt)
        .put("observed_frames", observedFrames)
        .put("pose_presence_ratio", ratio(poseFrames, observedFrames))
        .put("left_hand_presence_ratio", ratio(leftFrames, observedFrames))
        .put("right_hand_presence_ratio", ratio(rightFrames, observedFrames))
        .put("any_hand_presence_ratio", ratio(anyHandFrames, observedFrames))
        .put("trial_duration_ms", elapsedMs())
        .put("preview_mirrored", true)
        .put("analysis_mirrored", trial.lane == Lane.PHRASE)
        .put("recorded_epoch_ms", System.currentTimeMillis())

    private fun advance() {
        val oldLane = trials[trialIndex].lane
        armedAtMs = 0L
        trialIndex += 1
        if (trialIndex >= trials.size) {
            runOnUiThread {
                armButton.isEnabled = false
                armButton.text = "QUICK GATE COMPLETE"
                status.text = "Quick gate complete (${trials.size}/${trials.size})\n${outputFile.absolutePath}"
            }
            Log.i(TAG, "SCENARIO15_QUICK_GATE_COMPLETE trials=${trials.size} file=${outputFile.absolutePath}")
            return
        }
        val next = trials[trialIndex]
        if (next.lane != oldLane) configureExtractor(next.lane)
        runOnUiThread { showNextTrial("Recorded ${trialIndex}/${trials.size}") }
    }

    private fun configureExtractor(lane: Lane) {
        runCatching { extractor?.close() }
        val mirrored = lane == Lane.PHRASE && phraseProfile?.mirroredInput == true
        extractor = MediaPipeLandmarkExtractor(applicationContext, mirrored)
        Log.i(TAG, "SCENARIO15_EXTRACTOR lane=$lane analysis_mirrored=$mirrored preview_mirrored=true")
    }

    private fun showNextTrial(prefix: String) {
        val trial = trials.getOrNull(trialIndex) ?: return
        status.text = "$prefix\nNext: ${trial.label} attempt ${trial.attempt}/3\nNeutral first. Tap ARM, sign once, then neutral."
        armButton.text = "ARM ${trial.label} ${trial.attempt}/3"
        armButton.isEnabled = true
    }

    private fun persist(result: JSONObject) {
        val root = if (outputFile.isFile) JSONObject(outputFile.readText(Charsets.UTF_8)) else JSONObject()
            .put("schema_version", 1)
            .put("session_id", sessionId)
            .put("profile", "SCENARIO15_EXISTING_ASSETS_QUICK_GATE")
            .put("phrase_profile", PHRASE_PROFILE)
            .put("phrase_input", JSONArray(listOf(1, PHRASE_FRAMES, FEATURE_SIZE)))
            .put("standard_profile", "STANDARD_FSL_FULLSIGN225")
            .put("standard_input", JSONArray(listOf(1, 20, FEATURE_SIZE)))
            .put("trials", JSONArray())
        root.getJSONArray("trials").put(result)
        root.put("updated_epoch_ms", System.currentTimeMillis())
        val temporary = File(outputFile.parentFile, outputFile.name + ".tmp")
        temporary.writeText(root.toString(2), Charsets.UTF_8)
        Files.move(
            temporary.toPath(),
            outputFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    }

    private fun completeWithError(error: Throwable) {
        armedAtMs = 0L
        runOnUiThread {
            armButton.isEnabled = true
            armButton.text = "RETRY CURRENT TRIAL"
            status.text = "Trial error: ${error.javaClass.simpleName}: ${error.message}"
        }
    }

    private fun blocked(message: String) = runOnUiThread {
        status.text = message
        armButton.isEnabled = false
    }

    private fun elapsedMs(): Long = (SystemClock.elapsedRealtime() - armedAtMs).coerceAtLeast(0L)
    private fun ratio(value: Int, total: Int): Float = if (total == 0) 0f else value.toFloat() / total
    private fun fmt(value: Float): String = String.format(Locale.US, "%.4f", value)

    private data class Trial(val label: String, val attempt: Int, val lane: Lane)
    private enum class Lane { PHRASE, STANDARD }

    private fun buildTrials(): List<Trial> = buildList {
        PHRASE_LABELS.forEach { label -> repeat(3) { add(Trial(label, it + 1, Lane.PHRASE)) } }
        STANDARD_EXPOSED_LABELS.forEach { label -> repeat(3) { add(Trial(label, it + 1, Lane.STANDARD)) } }
    }

    companion object {
        const val EXTRA_SESSION_ID = "com.voxgest.dryrun.scenario15.extra.SESSION_ID"
        private const val TAG = "VoxGestScenario15"
        private const val OUTPUT_DIRECTORY = "scenario15_quick_gate"
        private const val PHRASE_PROFILE = "fullsign225_phrase_v1"
        private const val PHRASE_FRAMES = 30
        private const val FEATURE_SIZE = 225
        private const val TRIAL_TIMEOUT_MS = 10_000L
        private val PHRASE_LABELS = listOf("WHAT", "YOUR", "NAME", "MY")
        private val STANDARD_EXPOSED_LABELS = listOf("MILK", "RICE")
    }
}

private data class Ranked(val label: String, val probability: Float)

private data class Inference(
    val label: String,
    val confidence: Float,
    val margin: Float,
    val top3: List<Ranked>,
    val latencyMs: Double
)

private object PhraseGatePolicy {
    private val allowed = setOf("WHAT", "YOUR", "NAME", "MY")

    fun evaluate(inference: Inference): Pair<Boolean, String> {
        if (inference.label == "NSAC") return false to "NSAC"
        if (inference.label !in allowed) return false to "UNSUPPORTED_LABEL"
        val confidence = if (inference.label == "NAME") 0.70f else 0.75f
        val margin = if (inference.label == "NAME") 0.10f else 0.12f
        if (inference.confidence < confidence) return false to "LOW_CONFIDENCE"
        if (inference.margin < margin) return false to "LOW_MARGIN"
        return true to "ACCEPTED"
    }
}

private class PhraseRuntime(context: Context, private val profile: RecognitionProfile) : Closeable {
    private val interpreter: Interpreter = TfliteModelLoader(context.applicationContext)
        .loadInterpreterWithOptions(Interpreter.Options().setNumThreads(2), profile.modelAsset)

    init {
        interpreter.allocateTensors()
        check(interpreter.getInputTensor(0).shape().contentEquals(profile.inputShape))
        check(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1, profile.labels.size)))
        check(interpreter.getInputTensor(0).dataType() == DataType.FLOAT32)
        check(interpreter.getOutputTensor(0).dataType() == DataType.FLOAT32)
    }

    fun infer(window: Array<FloatArray>): Inference {
        check(window.size == profile.sequenceLength && window.all { it.size == profile.featureSize })
        val output = Array(1) { FloatArray(profile.labels.size) }
        val started = SystemClock.elapsedRealtimeNanos()
        interpreter.run(arrayOf(window), output)
        val latency = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
        val probabilities = output[0]
        val ranked = probabilities.indices.sortedByDescending { probabilities[it] }
        val first = ranked[0]
        val second = ranked[1]
        return Inference(
            profile.labels[first],
            probabilities[first],
            probabilities[first] - probabilities[second],
            ranked.take(3).map { Ranked(profile.labels[it], probabilities[it]) },
            latency
        )
    }

    override fun close() = interpreter.close()
}
