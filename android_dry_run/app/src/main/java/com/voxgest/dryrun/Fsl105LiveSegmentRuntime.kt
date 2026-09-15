package com.voxgest.dryrun

import kotlin.math.sqrt

object Fsl105LiveSegmentProfile {
    const val ID = "FSL105_LIVE_SEGMENT_V1"
}

data class Fsl105InferenceTrajectory(
    val prepared: CompleteSignPreparedTrajectory,
    val completion: Mapua14SegmentCompletion,
    val signStartTimestampMs: Long,
    val estimatedSignEndTimestampMs: Long,
    val completionDetectedTimestampMs: Long,
    val inferenceReadyTimestampMs: Long,
    val endToInferenceReadyMs: Long,
    val targetPostEndResultMs: Long,
    val p95PostEndResultGoalMs: Long,
    val retainedLeadingNeutralFrames: Int
)

/**
 * Complete-event segmentation for Standard FSL105. It deliberately mirrors the proven MAPUA14
 * state flow while finalizing through the manifest-sized Standard temporal profile.
 */
class Fsl105LiveSegmentStateMachine(
    private val temporalProfile: CompleteSignTemporalProfile,
    private val config: Mapua14LiveSegmentConfig = Mapua14LiveSegmentConfig()
) {
    private val leadingNeutral = ArrayDeque<LandmarkFrame>(temporalProfile.motionBoundaryFrames)
    private val captured = ArrayList<LandmarkFrame>()
    private var lastTimestampMs = Long.MIN_VALUE
    private var previousObservedFrame: LandmarkFrame? = null
    private var previousCaptureHandFrame: LandmarkFrame? = null
    private var neutralCount = 0
    private var releaseCount = 0
    private var signFrameCount = 0
    private var stableCount = 0
    private var stableRunStartedAtMs = 0L
    private var movementFrames = 0
    private var movementPath = 0f
    private var lastMotionTimestampMs = 0L
    private var signStartTimestampMs = 0L
    private var completionDetectedTimestampMs = 0L
    private var completion: Mapua14SegmentCompletion? = null
    private var retainedLeadingNeutralFrames = 0
    private var inferenceTrajectory: Fsl105InferenceTrajectory? = null

    var state: Mapua14LiveSegmentState = Mapua14LiveSegmentState.IDLE
        private set

    init {
        require(temporalProfile.id == Fsl105LiveSegmentProfile.ID)
        require(temporalProfile.outputLength == StandardFullSign225Contract.SEQUENCE_LENGTH)
        require(temporalProfile.featureSize == StandardFullSign225Contract.FEATURE_SIZE)
        require(config.dynamicEndStableFrames >= temporalProfile.motionBoundaryFrames)
        require(config.staticHoldFrames >= temporalProfile.motionBoundaryFrames)
    }

    fun onFrame(
        frame: LandmarkFrame,
        processTimestampMs: Long = frame.timestampMs
    ): Mapua14LiveSegmentUpdate {
        require(processTimestampMs >= 0L)
        if (frame.timestampMs <= lastTimestampMs) {
            return update("NON_CHRONOLOGICAL_FRAME_IGNORED")
        }
        lastTimestampMs = frame.timestampMs

        if (state == Mapua14LiveSegmentState.FINALIZING) {
            return update("FINALIZATION_PENDING", completion = completion)
        }
        if (state == Mapua14LiveSegmentState.INFERENCE) {
            return update("INFERENCE_PENDING", completion = completion)
        }
        if (state == Mapua14LiveSegmentState.WAIT_FOR_RELEASE) return observeRelease(frame)

        val neutral = isHandsDownNeutral(frame)
        val activity = activityBetween(previousObservedFrame, frame)
        previousObservedFrame = frame
        return when (state) {
            Mapua14LiveSegmentState.IDLE -> observeIdle(frame, neutral)
            Mapua14LiveSegmentState.ARMING ->
                observeArming(frame, neutral, activity, processTimestampMs)
            Mapua14LiveSegmentState.CAPTURING ->
                observeCapture(frame, neutral, activity, processTimestampMs)
            else -> error("state handled above: $state")
        }
    }

    fun finalizeForInference(inferenceReadyTimestampMs: Long): Fsl105InferenceTrajectory {
        check(state == Mapua14LiveSegmentState.FINALIZING) {
            "segment can only finalize from FINALIZING, was $state"
        }
        check(inferenceReadyTimestampMs >= completionDetectedTimestampMs)
        val finishedBy = requireNotNull(completion)
        val prepared = CompleteSignTrajectoryFinalizer.prepare(captured, temporalProfile)
        val estimatedEnd = when (finishedBy) {
            Mapua14SegmentCompletion.DYNAMIC_END,
            Mapua14SegmentCompletion.NEUTRAL_RETURN ->
                lastMotionTimestampMs.takeIf { it > 0L } ?: completionDetectedTimestampMs
            Mapua14SegmentCompletion.STATIC_HOLD ->
                stableRunStartedAtMs.takeIf { it > 0L } ?: completionDetectedTimestampMs
            Mapua14SegmentCompletion.CAPTURE_LIMIT ->
                lastMotionTimestampMs.takeIf { it > 0L } ?: completionDetectedTimestampMs
        }
        return Fsl105InferenceTrajectory(
            prepared = prepared,
            completion = finishedBy,
            signStartTimestampMs = signStartTimestampMs,
            estimatedSignEndTimestampMs = estimatedEnd,
            completionDetectedTimestampMs = completionDetectedTimestampMs,
            inferenceReadyTimestampMs = inferenceReadyTimestampMs,
            endToInferenceReadyMs = (inferenceReadyTimestampMs - estimatedEnd).coerceAtLeast(0L),
            targetPostEndResultMs = config.targetPostEndResultMs,
            p95PostEndResultGoalMs = config.p95PostEndResultGoalMs,
            retainedLeadingNeutralFrames = retainedLeadingNeutralFrames
        ).also {
            inferenceTrajectory = it
            state = Mapua14LiveSegmentState.INFERENCE
        }
    }

    fun markInferenceFinished(resultTimestampMs: Long): Mapua14PostEndResultTiming {
        check(state == Mapua14LiveSegmentState.INFERENCE)
        val trajectory = requireNotNull(inferenceTrajectory)
        check(resultTimestampMs >= trajectory.inferenceReadyTimestampMs)
        val delay = (resultTimestampMs - trajectory.estimatedSignEndTimestampMs).coerceAtLeast(0L)
        state = Mapua14LiveSegmentState.WAIT_FOR_RELEASE
        releaseCount = 0
        return Mapua14PostEndResultTiming(
            endToResultMs = delay,
            targetPostEndResultMs = config.targetPostEndResultMs,
            p95PostEndResultGoalMs = config.p95PostEndResultGoalMs,
            withinTarget = delay <= config.targetPostEndResultMs,
            withinP95Goal = delay <= config.p95PostEndResultGoalMs
        )
    }

    fun reset() {
        state = Mapua14LiveSegmentState.IDLE
        lastTimestampMs = Long.MIN_VALUE
        previousObservedFrame = null
        leadingNeutral.clear()
        clearEvent()
        neutralCount = 0
        releaseCount = 0
    }

    private fun observeIdle(frame: LandmarkFrame, neutral: Boolean): Mapua14LiveSegmentUpdate {
        if (!neutral) {
            neutralCount = 0
            leadingNeutral.clear()
            return update("WAITING_FOR_HANDS_DOWN_NEUTRAL")
        }
        rememberNeutral(frame)
        neutralCount += 1
        if (neutralCount >= config.armingNeutralFrames) {
            state = Mapua14LiveSegmentState.ARMING
            return update("NEUTRAL_CONFIRMED_ARMED")
        }
        return update("NEUTRAL_ARMING")
    }

    private fun observeArming(
        frame: LandmarkFrame,
        neutral: Boolean,
        activity: Float,
        processTimestampMs: Long
    ): Mapua14LiveSegmentUpdate {
        if (neutral) {
            rememberNeutral(frame)
            return update("ARMED_WAITING_FOR_SIGN", activity = activity)
        }
        val handEntered = frame.hasAnyHand && leadingNeutral.lastOrNull()?.hasAnyHand != true
        val deliberateEntry = frame.hasAnyHand &&
            (handEntered || activity >= config.motionActivityThreshold || !neutral)
        if (!deliberateEntry) return update("ARMED_WAITING_FOR_USABLE_HAND", activity = activity)
        beginCapture(frame, activity, processTimestampMs)
        return update(
            if (handEntered) "CAPTURE_STARTED_HAND_ENTRY" else "CAPTURE_STARTED_MOTION",
            activity = activity
        )
    }

    private fun observeCapture(
        frame: LandmarkFrame,
        neutral: Boolean,
        activity: Float,
        processTimestampMs: Long
    ): Mapua14LiveSegmentUpdate {
        captured += copyFrame(frame)
        signFrameCount += 1
        if (neutral) {
            releaseCount += 1
            stableCount = 0
        } else {
            releaseCount = 0
            observeActivity(frame, activity, processTimestampMs)
        }

        val enoughFrames = signFrameCount >= config.minimumSignFrames
        val movementConfirmed = movementFrames >= config.dynamicMotionFrames &&
            movementPath >= config.minimumDynamicPath
        val completedBy = when {
            enoughFrames && releaseCount >= temporalProfile.motionBoundaryFrames ->
                Mapua14SegmentCompletion.NEUTRAL_RETURN
            enoughFrames && movementConfirmed && stableCount >= config.dynamicEndStableFrames ->
                Mapua14SegmentCompletion.DYNAMIC_END
            enoughFrames && !movementConfirmed && stableCount >= config.staticHoldFrames ->
                Mapua14SegmentCompletion.STATIC_HOLD
            captured.size >= config.maximumCaptureFrames -> Mapua14SegmentCompletion.CAPTURE_LIMIT
            else -> null
        }
        if (completedBy != null) {
            state = Mapua14LiveSegmentState.FINALIZING
            completion = completedBy
            completionDetectedTimestampMs = processTimestampMs
            return update(
                "SEGMENT_COMPLETE_${completedBy.name}",
                completion = completedBy,
                activity = activity
            )
        }
        return update(
            when {
                neutral -> "CAPTURING_NEUTRAL_BOUNDARY"
                movementConfirmed -> "CAPTURING_DYNAMIC"
                else -> "CAPTURING_STATIC_OR_ENTRY"
            },
            activity = activity
        )
    }

    private fun beginCapture(
        frame: LandmarkFrame,
        activity: Float,
        processTimestampMs: Long
    ) {
        clearEvent()
        retainedLeadingNeutralFrames = leadingNeutral.size
        captured.addAll(leadingNeutral.map(::copyFrame))
        captured += copyFrame(frame)
        signStartTimestampMs = processTimestampMs
        signFrameCount = 1
        previousCaptureHandFrame = frame.takeIf { it.hasAnyHand }
        stableCount = if (frame.hasAnyHand) 1 else 0
        stableRunStartedAtMs = if (stableCount == 1) processTimestampMs else 0L
        if (activity >= config.motionActivityThreshold) {
            movementFrames = 1
            movementPath = activity
            lastMotionTimestampMs = processTimestampMs
            stableCount = 0
            stableRunStartedAtMs = 0L
        }
        state = Mapua14LiveSegmentState.CAPTURING
    }

    private fun observeActivity(
        frame: LandmarkFrame,
        activity: Float,
        processTimestampMs: Long
    ) {
        if (!frame.hasAnyHand) {
            previousCaptureHandFrame = null
            stableCount = 0
            stableRunStartedAtMs = 0L
            return
        }
        val effectiveActivity = maxOf(activity, activityBetween(previousCaptureHandFrame, frame))
        previousCaptureHandFrame = frame
        if (effectiveActivity >= config.motionActivityThreshold) {
            movementFrames += 1
            movementPath += effectiveActivity
            lastMotionTimestampMs = processTimestampMs
            stableCount = 0
            stableRunStartedAtMs = 0L
        } else if (effectiveActivity <= config.stableActivityThreshold) {
            if (stableCount == 0) stableRunStartedAtMs = processTimestampMs
            stableCount += 1
        } else {
            stableCount = 0
            stableRunStartedAtMs = 0L
        }
    }

    private fun observeRelease(frame: LandmarkFrame): Mapua14LiveSegmentUpdate {
        val neutral = isHandsDownNeutral(frame)
        releaseCount = if (neutral) releaseCount + 1 else 0
        if (neutral) rememberNeutral(frame) else leadingNeutral.clear()
        if (releaseCount >= config.releaseNeutralFrames) {
            state = Mapua14LiveSegmentState.IDLE
            previousObservedFrame = frame
            neutralCount = 0
            releaseCount = 0
            clearEvent()
            return update("RELEASE_CONFIRMED")
        }
        return update(if (neutral) "RELEASE_CONFIRMING" else "WAITING_FOR_RELEASE")
    }

    private fun rememberNeutral(frame: LandmarkFrame) {
        if (leadingNeutral.size == temporalProfile.motionBoundaryFrames) leadingNeutral.removeFirst()
        leadingNeutral.addLast(copyFrame(frame))
    }

    private fun clearEvent() {
        captured.clear()
        previousCaptureHandFrame = null
        signFrameCount = 0
        stableCount = 0
        stableRunStartedAtMs = 0L
        movementFrames = 0
        movementPath = 0f
        lastMotionTimestampMs = 0L
        signStartTimestampMs = 0L
        completionDetectedTimestampMs = 0L
        completion = null
        retainedLeadingNeutralFrames = 0
        inferenceTrajectory = null
    }

    private fun update(
        reason: String,
        completion: Mapua14SegmentCompletion? = null,
        activity: Float = 0f
    ) = Mapua14LiveSegmentUpdate(state, reason, captured.size, completion, activity)

    private fun isHandsDownNeutral(frame: LandmarkFrame): Boolean {
        val pose = frame.poseLandmarks?.takeIf { it.size == 33 } ?: return false
        return pose[15].y >= pose[23].y - config.neutralWristHipSlack &&
            pose[16].y >= pose[24].y - config.neutralWristHipSlack
    }

    private fun activityBetween(previous: LandmarkFrame?, current: LandmarkFrame): Float {
        if (previous == null) return 0f
        val pairs = listOf(
            previous.leftHandLandmarks to current.leftHandLandmarks,
            previous.rightHandLandmarks to current.rightHandLandmarks
        ).filter { (before, after) -> before?.size == 21 && after?.size == 21 }
        if (pairs.isEmpty()) return 0f
        val wrists = pairs.map { (before, after) -> distance(before!![0], after!![0]) }
        val tips = intArrayOf(4, 8, 12, 16, 20)
        val fingertip = pairs.flatMap { (before, after) ->
            tips.map { index -> distance(before!![index], after!![index]) }
        }
        val joints = intArrayOf(4, 5, 8, 9, 12, 13, 16, 17, 20)
        val shape = pairs.flatMap { (before, after) ->
            val beforeHand = requireNotNull(before)
            val afterHand = requireNotNull(after)
            joints.map { index ->
                relativeDistance(beforeHand[index], beforeHand[0], afterHand[index], afterHand[0])
            }
        }
        return maxOf(
            wrists.average().toFloat(),
            fingertip.average().toFloat(),
            shape.average().toFloat()
        )
    }

    private fun distance(left: LandmarkPoint, right: LandmarkPoint): Float {
        val dx = left.x - right.x
        val dy = left.y - right.y
        val dz = left.z - right.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun relativeDistance(
        before: LandmarkPoint,
        beforeWrist: LandmarkPoint,
        after: LandmarkPoint,
        afterWrist: LandmarkPoint
    ): Float {
        val dx = (before.x - beforeWrist.x) - (after.x - afterWrist.x)
        val dy = (before.y - beforeWrist.y) - (after.y - afterWrist.y)
        val dz = (before.z - beforeWrist.z) - (after.z - afterWrist.z)
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun copyFrame(frame: LandmarkFrame): LandmarkFrame = frame.copy(
        poseLandmarks = frame.poseLandmarks?.map { it.copy() },
        leftHandLandmarks = frame.leftHandLandmarks?.map { it.copy() },
        rightHandLandmarks = frame.rightHandLandmarks?.map { it.copy() },
        handObservations = frame.handObservations.toList()
    )
}

data class Fsl105SegmentGateEvaluation(
    val decision: StandardFslGateDecision,
    val timing: StandardFullSign225WindowTiming
)

/** One confidence/margin/tracking decision per completed Standard FSL105 event. */
object Fsl105SegmentGate {
    const val CONFIDENCE_THRESHOLD = 0.70f
    const val MARGIN_THRESHOLD = 0.20f
    const val MINIMUM_ANY_HAND_PRESENCE_RATIO = 0.65f
    const val MAXIMUM_TRAJECTORY_DURATION_MS = 8_000L
    const val MAXIMUM_MEDIAN_FRAME_GAP_MS = 350L
    const val MAXIMUM_FRAME_GAP_MS = 700L

    /** Returns a rejection when the completed event must not be sent to TFLite. */
    fun preflight(
        trajectory: Fsl105InferenceTrajectory,
        resultTimestampMs: Long,
        trackingFailureReason: String? = null
    ): Fsl105SegmentGateEvaluation? {
        val prepared = trajectory.prepared
        val timing = timing(
            prepared.sourceTimestampsMs,
            (resultTimestampMs - trajectory.signStartTimestampMs).coerceAtLeast(0L)
        )
        fun reject(reason: String) = Fsl105SegmentGateEvaluation(
            StandardFslGateDecision(
                false,
                reason,
                StandardFslRejectionGate.HOLD_SIGN_CLEARLY,
                0,
                false
            ),
            timing
        )
        if (trajectory.completion == Mapua14SegmentCompletion.CAPTURE_LIMIT) {
            return reject("CAPTURE_LIMIT_WITHOUT_SIGN_END")
        }
        if (trackingFailureReason != null) return reject(trackingFailureReason)
        if (prepared.temporalProfileId != Fsl105LiveSegmentProfile.ID ||
            prepared.modelInput.size != StandardFullSign225Contract.SEQUENCE_LENGTH ||
            prepared.modelInput.any { it.size != StandardFullSign225Contract.FEATURE_SIZE }
        ) return reject("RESAMPLED_SHAPE_INVALID")
        if (prepared.quality.frameCount < 4) return reject("TRAJECTORY_TOO_SHORT")
        if (prepared.quality.posePresentFrames != prepared.quality.frameCount) {
            return reject("MISSING_REQUIRED_POSE_REFERENCE")
        }
        if (prepared.quality.anyHandPresenceRatio < MINIMUM_ANY_HAND_PRESENCE_RATIO) {
            return reject("LOW_HAND_PRESENCE")
        }
        if (!timing.available || !timing.chronological) return reject("TRAJECTORY_TIMING_INVALID")
        if (timing.windowDurationMs > MAXIMUM_TRAJECTORY_DURATION_MS) {
            return reject("TRAJECTORY_DURATION_EXCEEDED")
        }
        if (timing.medianFrameGapMs > MAXIMUM_MEDIAN_FRAME_GAP_MS) {
            return reject("MEDIAN_FRAME_GAP_EXCEEDED")
        }
        if (timing.maxFrameGapMs > MAXIMUM_FRAME_GAP_MS) {
            return reject("MAX_FRAME_GAP_EXCEEDED")
        }
        return null
    }

    fun evaluate(
        inference: StandardFslInference,
        trajectory: Fsl105InferenceTrajectory,
        resultTimestampMs: Long,
        trackingFailureReason: String? = null
    ): Fsl105SegmentGateEvaluation {
        preflight(trajectory, resultTimestampMs, trackingFailureReason)?.let { return it }
        val timing = timing(
            trajectory.prepared.sourceTimestampsMs,
            (resultTimestampMs - trajectory.signStartTimestampMs).coerceAtLeast(0L)
        )
        fun reject(reason: String) = Fsl105SegmentGateEvaluation(
            StandardFslGateDecision(
                false,
                reason,
                StandardFslRejectionGate.HOLD_SIGN_CLEARLY,
                1,
                false
            ),
            timing
        )

        if (!inference.top1.probability.isFinite() || !inference.top2.probability.isFinite()) {
            return reject("NON_FINITE_PREDICTION")
        }
        if (inference.top1.probability < CONFIDENCE_THRESHOLD) return reject("LOW_CONFIDENCE")
        if (inference.margin < MARGIN_THRESHOLD) return reject("LOW_MARGIN")
        return Fsl105SegmentGateEvaluation(
            StandardFslGateDecision(true, "ACCEPTED", inference.top1.label, 1, false),
            timing
        )
    }

    private fun timing(
        timestamps: LongArray,
        oldestFrameAgeMs: Long
    ): StandardFullSign225WindowTiming {
        if (timestamps.size < 2 || timestamps.any { it <= 0L }) {
            return StandardFullSign225WindowTiming(false, false, 0L, 0L, 0L, 0L)
        }
        val gaps = List(timestamps.size - 1) { timestamps[it + 1] - timestamps[it] }
        if (gaps.any { it <= 0L }) {
            return StandardFullSign225WindowTiming(true, false, 0L, 0L, 0L, 0L)
        }
        val sorted = gaps.sorted()
        val middle = sorted.size / 2
        val median = if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2L
        } else {
            sorted[middle]
        }
        return StandardFullSign225WindowTiming(
            true,
            true,
            timestamps.last() - timestamps.first(),
            oldestFrameAgeMs,
            median,
            gaps.maxOrNull() ?: 0L
        )
    }
}
