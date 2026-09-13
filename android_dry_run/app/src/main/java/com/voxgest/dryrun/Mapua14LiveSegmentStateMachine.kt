package com.voxgest.dryrun

import kotlin.math.sqrt

enum class Mapua14LiveSegmentState {
    IDLE,
    ARMING,
    CAPTURING,
    FINALIZING,
    INFERENCE,
    WAIT_FOR_RELEASE
}

enum class Mapua14SegmentCompletion {
    DYNAMIC_END,
    STATIC_HOLD,
    NEUTRAL_RETURN,
    CAPTURE_LIMIT
}

data class Mapua14LiveSegmentConfig(
    val armingNeutralFrames: Int = 3,
    val releaseNeutralFrames: Int = 3,
    val neutralWristHipSlack: Float = 0.08f,
    val motionActivityThreshold: Float = 0.004f,
    val stableActivityThreshold: Float = 0.003f,
    val dynamicMotionFrames: Int = 2,
    val minimumDynamicPath: Float = 0.012f,
    val dynamicEndStableFrames: Int = 5,
    val staticHoldFrames: Int = 12,
    val minimumSignFrames: Int = 6,
    val maximumCaptureFrames: Int = 240,
    val targetPostEndResultMs: Long = 2_000L,
    val p95PostEndResultGoalMs: Long = 3_000L
) {
    init {
        require(armingNeutralFrames >= 1)
        require(releaseNeutralFrames >= 1)
        require(neutralWristHipSlack >= 0f)
        require(motionActivityThreshold > 0f)
        require(stableActivityThreshold in 0f..motionActivityThreshold)
        require(dynamicMotionFrames >= 1)
        require(minimumDynamicPath >= 0f)
        require(dynamicEndStableFrames >= Mapua14CompleteTrajectory48.MOTION_BOUNDARY_FRAMES)
        require(staticHoldFrames >= Mapua14CompleteTrajectory48.MOTION_BOUNDARY_FRAMES)
        require(minimumSignFrames >= 1)
        require(maximumCaptureFrames >= minimumSignFrames)
        require(targetPostEndResultMs > 0L)
        require(p95PostEndResultGoalMs >= targetPostEndResultMs)
    }
}

data class Mapua14LiveSegmentUpdate(
    val state: Mapua14LiveSegmentState,
    val reason: String,
    val capturedFrameCount: Int,
    val completion: Mapua14SegmentCompletion? = null,
    val activity: Float = 0f
)

data class Mapua14InferenceTrajectory(
    val prepared: Mapua14PreparedTrajectory,
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

data class Mapua14PostEndResultTiming(
    val endToResultMs: Long,
    val targetPostEndResultMs: Long,
    val p95PostEndResultGoalMs: Long,
    val withinTarget: Boolean,
    val withinP95Goal: Boolean
)

/**
 * Isolated event capture for the experimental Mapua-14 lane.
 *
 * The caller owns inference. Once [onFrame] reports FINALIZING, call
 * [finalizeForInference], run the existing RD-TCN48 model with the returned input, then call
 * [markInferenceFinished]. A new event cannot arm until a hands-down neutral release is observed.
 */
class Mapua14LiveSegmentStateMachine(
    private val config: Mapua14LiveSegmentConfig = Mapua14LiveSegmentConfig()
) {
    private val leadingNeutral = ArrayDeque<LandmarkFrame>(
        Mapua14CompleteTrajectory48.MOTION_BOUNDARY_FRAMES
    )
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
    private var inferenceTrajectory: Mapua14InferenceTrajectory? = null

    var state: Mapua14LiveSegmentState = Mapua14LiveSegmentState.IDLE
        private set

    fun onFrame(frame: LandmarkFrame): Mapua14LiveSegmentUpdate {
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
        if (state == Mapua14LiveSegmentState.WAIT_FOR_RELEASE) {
            return observeRelease(frame)
        }

        val neutral = isHandsDownNeutral(frame)
        val activity = activityBetween(previousObservedFrame, frame)
        previousObservedFrame = frame
        return when (state) {
            Mapua14LiveSegmentState.IDLE -> observeIdle(frame, neutral)
            Mapua14LiveSegmentState.ARMING -> observeArming(frame, neutral, activity)
            Mapua14LiveSegmentState.CAPTURING -> observeCapture(frame, neutral, activity)
            else -> error("state handled above: $state")
        }
    }

    fun finalizeForInference(inferenceReadyTimestampMs: Long): Mapua14InferenceTrajectory {
        check(state == Mapua14LiveSegmentState.FINALIZING) {
            "segment can only finalize from FINALIZING, was $state"
        }
        check(inferenceReadyTimestampMs >= completionDetectedTimestampMs) {
            "inference-ready timestamp precedes completion detection"
        }
        val finishedBy = requireNotNull(completion)
        val prepared = Mapua14CompleteTrajectory48.prepare(captured)
        val estimatedEnd = when (finishedBy) {
            Mapua14SegmentCompletion.DYNAMIC_END,
            Mapua14SegmentCompletion.NEUTRAL_RETURN ->
                lastMotionTimestampMs.takeIf { it > 0L } ?: completionDetectedTimestampMs
            Mapua14SegmentCompletion.STATIC_HOLD ->
                stableRunStartedAtMs.takeIf { it > 0L } ?: completionDetectedTimestampMs
            Mapua14SegmentCompletion.CAPTURE_LIMIT ->
                lastMotionTimestampMs.takeIf { it > 0L } ?: completionDetectedTimestampMs
        }
        val trajectory = Mapua14InferenceTrajectory(
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
        )
        inferenceTrajectory = trajectory
        state = Mapua14LiveSegmentState.INFERENCE
        return trajectory
    }

    fun markInferenceFinished(resultTimestampMs: Long): Mapua14PostEndResultTiming {
        check(state == Mapua14LiveSegmentState.INFERENCE) {
            "inference can only finish from INFERENCE, was $state"
        }
        val trajectory = requireNotNull(inferenceTrajectory)
        check(resultTimestampMs >= trajectory.inferenceReadyTimestampMs) {
            "result timestamp precedes inference-ready timestamp"
        }
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

    private fun observeIdle(
        frame: LandmarkFrame,
        neutral: Boolean
    ): Mapua14LiveSegmentUpdate {
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
        activity: Float
    ): Mapua14LiveSegmentUpdate {
        if (neutral) {
            rememberNeutral(frame)
            return update("ARMED_WAITING_FOR_SIGN", activity = activity)
        }
        val handEntered = frame.hasAnyHand && previousObservedFrameBefore(frame)?.hasAnyHand != true
        val deliberateEntry = frame.hasAnyHand &&
            (handEntered || activity >= config.motionActivityThreshold || !neutral)
        if (!deliberateEntry) {
            return update("ARMED_WAITING_FOR_USABLE_HAND", activity = activity)
        }
        beginCapture(frame, activity)
        return update(
            reason = if (handEntered) "CAPTURE_STARTED_HAND_ENTRY" else "CAPTURE_STARTED_MOTION",
            activity = activity
        )
    }

    private fun observeCapture(
        frame: LandmarkFrame,
        neutral: Boolean,
        activity: Float
    ): Mapua14LiveSegmentUpdate {
        captured += copyFrame(frame)
        signFrameCount += 1

        if (neutral) {
            releaseCount += 1
            stableCount = 0
        } else {
            releaseCount = 0
            observeActivity(frame, activity)
        }

        val enoughFrames = signFrameCount >= config.minimumSignFrames
        val movementConfirmed = movementFrames >= config.dynamicMotionFrames &&
            movementPath >= config.minimumDynamicPath
        val completedBy = when {
            enoughFrames && releaseCount >= Mapua14CompleteTrajectory48.MOTION_BOUNDARY_FRAMES ->
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
            completionDetectedTimestampMs = frame.timestampMs
            return update(
                reason = "SEGMENT_COMPLETE_${completedBy.name}",
                completion = completedBy,
                activity = activity
            )
        }
        return update(
            reason = when {
                neutral -> "CAPTURING_NEUTRAL_BOUNDARY"
                movementConfirmed -> "CAPTURING_DYNAMIC"
                else -> "CAPTURING_STATIC_OR_ENTRY"
            },
            activity = activity
        )
    }

    private fun beginCapture(frame: LandmarkFrame, activity: Float) {
        clearEvent()
        retainedLeadingNeutralFrames = leadingNeutral.size
        captured.addAll(leadingNeutral.map(::copyFrame))
        captured += copyFrame(frame)
        signStartTimestampMs = frame.timestampMs
        signFrameCount = 1
        previousCaptureHandFrame = frame.takeIf { it.hasAnyHand }
        stableCount = if (frame.hasAnyHand) 1 else 0
        stableRunStartedAtMs = if (stableCount == 1) frame.timestampMs else 0L
        if (activity >= config.motionActivityThreshold) {
            movementFrames = 1
            movementPath = activity
            lastMotionTimestampMs = frame.timestampMs
            stableCount = 0
            stableRunStartedAtMs = 0L
        }
        state = Mapua14LiveSegmentState.CAPTURING
    }

    private fun observeActivity(frame: LandmarkFrame, activity: Float) {
        if (!frame.hasAnyHand) {
            previousCaptureHandFrame = null
            stableCount = 0
            stableRunStartedAtMs = 0L
            return
        }
        val captureActivity = activityBetween(previousCaptureHandFrame, frame)
        previousCaptureHandFrame = frame
        val effectiveActivity = maxOf(activity, captureActivity)
        if (effectiveActivity >= config.motionActivityThreshold) {
            movementFrames += 1
            movementPath += effectiveActivity
            lastMotionTimestampMs = frame.timestampMs
            stableCount = 0
            stableRunStartedAtMs = 0L
        } else if (effectiveActivity <= config.stableActivityThreshold) {
            if (stableCount == 0) stableRunStartedAtMs = frame.timestampMs
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
        if (leadingNeutral.size == Mapua14CompleteTrajectory48.MOTION_BOUNDARY_FRAMES) {
            leadingNeutral.removeFirst()
        }
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
    ): Mapua14LiveSegmentUpdate = Mapua14LiveSegmentUpdate(
        state = state,
        reason = reason,
        capturedFrameCount = captured.size,
        completion = completion,
        activity = activity
    )

    private fun previousObservedFrameBefore(frame: LandmarkFrame): LandmarkFrame? {
        // onFrame has already advanced previousObservedFrame to [frame]. The leading boundary is
        // the reliable source for detecting a no-hand to hand-entry transition.
        return leadingNeutral.lastOrNull()
    }

    private fun isHandsDownNeutral(frame: LandmarkFrame): Boolean {
        val pose = frame.poseLandmarks?.takeIf { it.size == 33 } ?: return false
        val leftWrist = pose[15]
        val rightWrist = pose[16]
        val leftHip = pose[23]
        val rightHip = pose[24]
        return leftWrist.y >= leftHip.y - config.neutralWristHipSlack &&
            rightWrist.y >= rightHip.y - config.neutralWristHipSlack
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
                relativeDistance(
                    beforeHand[index], beforeHand[0], afterHand[index], afterHand[0]
                )
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
