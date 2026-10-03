package com.voxgest.dryrun

import kotlin.math.sqrt

enum class StandardFslEventState {
    IDLE,
    PRIMING,
    SIGN_ACTIVE,
    CANDIDATE,
    ACCEPTED,
    WAIT_FOR_RELEASE
}

data class StandardFslActivityMetrics(
    val currentFrameUsable: Boolean,
    val activityScore: Float,
    val wristDisplacement: Float,
    val fingertipDisplacement: Float,
    val jointShapeDisplacement: Float,
    val temporalVariance: Float,
    val recentValidFrameRatio: Float,
    val deliberateEntryObserved: Boolean
)

data class StandardFslEventUpdate(
    val state: StandardFslEventState,
    val reason: String,
    val activity: StandardFslActivityMetrics,
    val collectFrame: Boolean,
    val allowInference: Boolean,
    val flushWindow: Boolean
)

data class StandardFslEventConfig(
    val entryActivityThreshold: Float = 0.018f,
    val activeActivityThreshold: Float = 0.009f,
    val stableHoldActivityThreshold: Float = 0.008f,
    val entryMotionFrames: Int = 2,
    val primingFrames: Int = 3,
    val releaseFrames: Int = 3,
    val startupNeutralFrames: Int = 12,
    val neutralWristHipSlack: Float = 0.08f,
    val requiredStableHoldFrames: Int = 5,
    val activityHistorySize: Int = 8
) {
    init {
        require(entryActivityThreshold > 0f)
        require(activeActivityThreshold > 0f)
        require(stableHoldActivityThreshold in 0f..entryActivityThreshold)
        require(entryMotionFrames >= 1)
        require(primingFrames >= 1)
        require(releaseFrames >= 1)
        require(startupNeutralFrames >= releaseFrames)
        require(neutralWristHipSlack >= 0f)
        require(requiredStableHoldFrames >= 1)
        require(activityHistorySize >= 2)
    }
}

/**
 * Activity/event boundary in front of the Standard FSL-105 rolling window.
 *
 * A hand merely being visible is not an event. Collection starts only after a verified hands-down
 * neutral release followed by a new hand entry. Static/held signs remain supported because their
 * deliberate entry primes the window and the following held frames remain in that event.
 */
class StandardFslEventStateMachine(
    private val config: StandardFslEventConfig = StandardFslEventConfig()
) {
    private val activityHistory = ArrayDeque<Float>(config.activityHistorySize)
    private val validHistory = ArrayDeque<Boolean>(config.activityHistorySize)
    private var previousUsableFrame: LandmarkFrame? = null
    private var primingCount = 0
    private var entryMotionCount = 0
    private var unusableCount = 0
    private var stableHoldCount = 0
    private var hasSeenUsableFrame = false
    private var releasedSinceIdle = false

    var state: StandardFslEventState = StandardFslEventState.IDLE
        private set

    fun onFrame(frame: LandmarkFrame): StandardFslEventUpdate {
        if (state == StandardFslEventState.ACCEPTED) {
            state = StandardFslEventState.WAIT_FOR_RELEASE
        }

        val usable = frame.hasPose && frame.hasAnyHand
        val displacement = if (usable) compare(previousUsableFrame, frame) else Displacement.ZERO
        val score = maxOf(
            displacement.wrist,
            displacement.fingertip,
            displacement.jointShape
        )
        pushHistory(score, usable)

        if (!usable) {
            val neutralReleaseFrame = isHandsDownNeutral(frame)
            unusableCount = if (neutralReleaseFrame) unusableCount + 1 else 0
            previousUsableFrame = null
            val startupNeutral = state == StandardFslEventState.IDLE &&
                !hasSeenUsableFrame &&
                !releasedSinceIdle
            val requiredReleaseFrames = if (startupNeutral) {
                config.startupNeutralFrames
            } else {
                config.releaseFrames
            }
            val releaseTransition = neutralReleaseFrame &&
                unusableCount == requiredReleaseFrames &&
                (startupNeutral || state != StandardFslEventState.IDLE || hasSeenUsableFrame)
            if (releaseTransition) {
                state = StandardFslEventState.IDLE
                primingCount = 0
                entryMotionCount = 0
                stableHoldCount = 0
                releasedSinceIdle = true
            }
            return update(
                reason = when {
                    releaseTransition && startupNeutral -> "STARTUP_NEUTRAL_READY"
                    releaseTransition -> "LANDMARK_RELEASE"
                    startupNeutral && neutralReleaseFrame -> "STARTUP_NEUTRAL_ARMING"
                    state == StandardFslEventState.IDLE -> "IDLE_NO_LANDMARKS"
                    else -> "CURRENT_FRAME_UNUSABLE"
                },
                score = score,
                displacement = displacement,
                usable = false,
                collect = false,
                infer = false,
                flush = releaseTransition
            )
        }

        val handEnteredAfterRelease = releasedSinceIdle && previousUsableFrame == null
        previousUsableFrame = frame
        hasSeenUsableFrame = true
        unusableCount = 0

        return when (state) {
            StandardFslEventState.IDLE -> {
                val eventArmed = releasedSinceIdle
                entryMotionCount = if (eventArmed && score >= config.entryActivityThreshold) {
                    entryMotionCount + 1
                } else {
                    0
                }
                val deliberateEntry = eventArmed &&
                    (handEnteredAfterRelease || entryMotionCount >= config.entryMotionFrames)
                if (deliberateEntry) {
                    state = StandardFslEventState.PRIMING
                    primingCount = 1
                    entryMotionCount = 0
                    stableHoldCount = 0
                    releasedSinceIdle = false
                    update(
                        reason = if (handEnteredAfterRelease) "HAND_ENTRY_AFTER_RELEASE" else "MOTION_ENTRY",
                        score = score,
                        displacement = displacement,
                        usable = true,
                        collect = true,
                        infer = false,
                        flush = true,
                        deliberateEntry = true
                    )
                } else {
                    update(
                        reason = if (eventArmed) {
                            "WAITING_FOR_DELIBERATE_ENTRY"
                        } else {
                            "WAITING_FOR_NEUTRAL_RELEASE"
                        },
                        score = score,
                        displacement = displacement,
                        usable = true,
                        collect = false,
                        infer = false,
                        flush = false
                    )
                }
            }

            StandardFslEventState.PRIMING -> {
                primingCount += 1
                stableHoldCount = if (score <= config.stableHoldActivityThreshold) {
                    stableHoldCount + 1
                } else {
                    0
                }
                if (primingCount >= config.primingFrames) {
                    state = StandardFslEventState.SIGN_ACTIVE
                }
                update(
                    reason = if (state == StandardFslEventState.SIGN_ACTIVE) "SIGN_EVENT_ACTIVE" else "PRIMING_EVENT",
                    score = score,
                    displacement = displacement,
                    usable = true,
                    collect = true,
                    infer = state == StandardFslEventState.SIGN_ACTIVE &&
                        stableHoldCount >= config.requiredStableHoldFrames,
                    flush = false,
                    deliberateEntry = true
                )
            }

            StandardFslEventState.SIGN_ACTIVE,
            StandardFslEventState.CANDIDATE -> {
                stableHoldCount = if (score <= config.stableHoldActivityThreshold) {
                    stableHoldCount + 1
                } else {
                    0
                }
                if (state == StandardFslEventState.CANDIDATE && stableHoldCount == 0) {
                    state = StandardFslEventState.SIGN_ACTIVE
                }
                val settled = stableHoldCount >= config.requiredStableHoldFrames
                update(
                    reason = when {
                        settled -> "STABLE_SIGN_HOLD"
                        score >= config.activeActivityThreshold -> "SIGN_MOVING"
                        else -> "SIGN_SETTLING"
                    },
                    score = score,
                    displacement = displacement,
                    usable = true,
                    collect = true,
                    infer = settled,
                    flush = false,
                    deliberateEntry = true
                )
            }

            StandardFslEventState.WAIT_FOR_RELEASE -> update(
                reason = "WAITING_FOR_LANDMARK_RELEASE",
                score = score,
                displacement = displacement,
                usable = true,
                collect = false,
                infer = false,
                flush = false,
                deliberateEntry = true
            )

            StandardFslEventState.ACCEPTED -> error("ACCEPTED is converted to WAIT_FOR_RELEASE above")
        }
    }

    fun markCandidate() {
        if (state == StandardFslEventState.SIGN_ACTIVE) state = StandardFslEventState.CANDIDATE
    }

    fun clearCandidate() {
        if (state == StandardFslEventState.CANDIDATE) state = StandardFslEventState.SIGN_ACTIVE
    }

    fun markAccepted() {
        state = StandardFslEventState.ACCEPTED
        stableHoldCount = 0
    }

    fun reset() {
        state = StandardFslEventState.IDLE
        previousUsableFrame = null
        primingCount = 0
        entryMotionCount = 0
        unusableCount = 0
        stableHoldCount = 0
        hasSeenUsableFrame = false
        releasedSinceIdle = false
        activityHistory.clear()
        validHistory.clear()
    }

    private fun update(
        reason: String,
        score: Float,
        displacement: Displacement,
        usable: Boolean,
        collect: Boolean,
        infer: Boolean,
        flush: Boolean,
        deliberateEntry: Boolean = false
    ): StandardFslEventUpdate {
        val mean = if (activityHistory.isEmpty()) 0f else activityHistory.average().toFloat()
        val variance = if (activityHistory.isEmpty()) 0f else activityHistory
            .map { value -> val delta = value - mean; delta * delta }
            .average()
            .toFloat()
        val validRatio = if (validHistory.isEmpty()) 0f else {
            validHistory.count { it }.toFloat() / validHistory.size
        }
        return StandardFslEventUpdate(
            state = state,
            reason = reason,
            activity = StandardFslActivityMetrics(
                currentFrameUsable = usable,
                activityScore = score,
                wristDisplacement = displacement.wrist,
                fingertipDisplacement = displacement.fingertip,
                jointShapeDisplacement = displacement.jointShape,
                temporalVariance = variance,
                recentValidFrameRatio = validRatio,
                deliberateEntryObserved = deliberateEntry || state !in setOf(
                    StandardFslEventState.IDLE,
                    StandardFslEventState.WAIT_FOR_RELEASE
                )
            ),
            collectFrame = collect,
            allowInference = infer,
            flushWindow = flush
        )
    }

    private fun pushHistory(activity: Float, usable: Boolean) {
        if (activityHistory.size == config.activityHistorySize) activityHistory.removeFirst()
        if (validHistory.size == config.activityHistorySize) validHistory.removeFirst()
        activityHistory.addLast(activity)
        validHistory.addLast(usable)
    }

    private fun isHandsDownNeutral(frame: LandmarkFrame): Boolean {
        if (!frame.hasPose || frame.hasAnyHand) return false
        val pose = frame.poseLandmarks ?: return false
        if (pose.size < 25) return false
        val leftWrist = pose[15]
        val rightWrist = pose[16]
        val leftHip = pose[23]
        val rightHip = pose[24]
        return leftWrist.y >= leftHip.y - config.neutralWristHipSlack &&
            rightWrist.y >= rightHip.y - config.neutralWristHipSlack
    }

    private fun compare(previous: LandmarkFrame?, current: LandmarkFrame): Displacement {
        if (previous == null) return Displacement.ZERO
        val sidePairs = listOf(
            previous.leftHandLandmarks to current.leftHandLandmarks,
            previous.rightHandLandmarks to current.rightHandLandmarks
        ).filter { (before, after) -> before?.size == 21 && after?.size == 21 }
        if (sidePairs.isEmpty()) return Displacement.ZERO

        val wrist = sidePairs.map { (before, after) -> distance(before!![0], after!![0]) }.average().toFloat()
        val tips = intArrayOf(4, 8, 12, 16, 20)
        val fingertip = sidePairs.flatMap { (before, after) ->
            tips.map { index -> distance(before!![index], after!![index]) }
        }.average().toFloat()
        val joints = intArrayOf(4, 5, 8, 9, 12, 13, 16, 17, 20)
        val jointShape = sidePairs.flatMap { (before, after) ->
            val beforeHand = requireNotNull(before)
            val afterHand = requireNotNull(after)
            val beforeWrist = beforeHand[0]
            val afterWrist = afterHand[0]
            joints.map { index ->
                distanceRelativeToWrist(beforeHand[index], beforeWrist, afterHand[index], afterWrist)
            }
        }.average().toFloat()
        return Displacement(wrist, fingertip, jointShape)
    }

    private fun distance(a: LandmarkPoint, b: LandmarkPoint): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun distanceRelativeToWrist(
        before: LandmarkPoint,
        beforeWrist: LandmarkPoint,
        after: LandmarkPoint,
        afterWrist: LandmarkPoint
    ): Float {
        val dx = (before.x - beforeWrist.x) - (after.x - afterWrist.x)
        val dy = (before.y - beforeWrist.y) - (after.y - afterWrist.y)
        return sqrt(dx * dx + dy * dy)
    }

    private data class Displacement(
        val wrist: Float,
        val fingertip: Float,
        val jointShape: Float
    ) {
        companion object {
            val ZERO = Displacement(0f, 0f, 0f)
        }
    }
}
