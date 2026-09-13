package com.voxgest.dryrun

import kotlin.math.min
import kotlin.math.sqrt

/** Raw Hand Landmarker output before an anatomical slot is chosen. */
data class TemporalHandDetection(
    val landmarks: List<LandmarkPoint>,
    val mediaPipeHandedness: String?,
    val handednessConfidence: Float = 1f
)

enum class TemporalHandAssignmentStatus {
    RELIABLE,
    PARTIAL,
    NO_HANDS,
    FAILED_CLOSED
}

/**
 * A slot change records a confident reported category that temporal/pose
 * evidence had to override. It does not use detection order or image X.
 */
data class TemporalHandSlotChange(
    val detectionIndex: Int,
    val reportedSide: AnatomicalHandSide,
    val stabilizedSide: AnatomicalHandSide,
    val reason: String
)

data class TemporalHandDropoutDuration(
    val currentFrames: Int,
    val currentDurationMs: Long,
    val lastReacquiredAfterFrames: Int?,
    val lastReacquiredAfterMs: Long?,
    val totalReacquisitions: Int
)

data class TemporalHandednessDiagnostic(
    val detectionIndex: Int,
    val validLandmarks: Boolean,
    val mediaPipeHandedness: String,
    val handednessConfidence: Float?,
    val reportedAnatomicalSide: AnatomicalHandSide?,
    val wrist: LandmarkPoint?,
    val assignedSide: AnatomicalHandSide?,
    val leftScore: Float?,
    val rightScore: Float?,
    val leftContinuityDistance: Float?,
    val rightContinuityDistance: Float?,
    val leftPoseAnchorDistance: Float?,
    val rightPoseAnchorDistance: Float?,
    val assignmentEvidence: List<String>
)

data class TemporalHandIdentityDiagnostics(
    val status: TemporalHandAssignmentStatus,
    val leftPresent: Boolean,
    val rightPresent: Boolean,
    val handedness: List<TemporalHandednessDiagnostic>,
    val leftWrist: LandmarkPoint?,
    val rightWrist: LandmarkPoint?,
    val poseLeftWristAnchor: LandmarkPoint?,
    val poseRightWristAnchor: LandmarkPoint?,
    val interHandDistance: Float?,
    val slotChanges: List<TemporalHandSlotChange>,
    val cumulativeSlotChangeCount: Int,
    val leftDropout: TemporalHandDropoutDuration,
    val rightDropout: TemporalHandDropoutDuration,
    val unassignedDetectionIndices: List<Int>,
    val failClosedReasons: List<String>
)

data class TemporalHandIdentityResult(
    val leftDetection: TemporalHandDetection?,
    val rightDetection: TemporalHandDetection?,
    val diagnostics: TemporalHandIdentityDiagnostics
) {
    val leftHandLandmarks: List<LandmarkPoint>? get() = leftDetection?.landmarks
    val rightHandLandmarks: List<LandmarkPoint>? get() = rightDetection?.landmarks
}

data class TemporalHandIdentityConfig(
    val maxWristContinuityDistance: Float = 0.28f,
    val maxPoseWristDistance: Float = 0.22f,
    val maxReacquisitionFrames: Int = 4,
    val maxReacquisitionDurationMs: Long = 400L,
    val maxVelocityPredictionMs: Long = 160L,
    val maxVelocityExtrapolationDistance: Float = 0.20f,
    val minimumHandednessConfidence: Float = 0.55f,
    val continuityWeight: Float = 0.55f,
    val poseAnchorWeight: Float = 0.30f,
    val handednessWeight: Float = 0.15f,
    val minimumAssignmentScore: Float = 0.10f,
    val minimumSingleHandMargin: Float = 0.08f,
    val minimumTwoHandMargin: Float = 0.12f
)

/**
 * Stateful anatomical slot resolver for an unmirrored FullSign225 lane.
 *
 * It uses reported anatomy, prior wrist trajectory, pose wrist anchors and a
 * bounded reacquisition interval. Ambiguous detections stay unassigned;
 * landmarks are never synthesized or carried forward into the result.
 */
class TemporalAnatomicalHandIdentityStabilizer(
    private val reportedHandednessPolicy: ReportedHandednessPolicy,
    private val config: TemporalHandIdentityConfig = TemporalHandIdentityConfig()
) {
    private var frameOrdinal = 0L
    private var lastTimestampMs: Long? = null
    private var cumulativeSlotChanges = 0
    private val tracks = mutableMapOf<AnatomicalHandSide, Track>()
    private val dropouts = mutableMapOf(
        AnatomicalHandSide.LEFT to DropoutState(),
        AnatomicalHandSide.RIGHT to DropoutState()
    )

    init {
        require(config.maxWristContinuityDistance > 0f)
        require(config.maxPoseWristDistance > 0f)
        require(config.maxReacquisitionFrames >= 0)
        require(config.maxReacquisitionDurationMs >= 0L)
        require(config.maxVelocityPredictionMs >= 0L)
        require(config.maxVelocityExtrapolationDistance >= 0f)
        require(config.minimumHandednessConfidence in 0f..1f)
        require(config.continuityWeight >= 0f)
        require(config.poseAnchorWeight >= 0f)
        require(config.handednessWeight >= 0f)
        require(config.minimumAssignmentScore >= 0f)
        require(config.minimumSingleHandMargin >= 0f)
        require(config.minimumTwoHandMargin >= 0f)
    }

    @Synchronized
    fun stabilize(
        detections: List<TemporalHandDetection>,
        poseLandmarks: List<LandmarkPoint>?,
        timestampMs: Long
    ): TemporalHandIdentityResult {
        val previousTimestamp = lastTimestampMs
        if (previousTimestamp != null && timestampMs <= previousTimestamp) {
            return failedWithoutStateAdvance(
                detections,
                poseLandmarks,
                previousTimestamp,
                NON_MONOTONIC_TIMESTAMP
            )
        }

        lastTimestampMs = timestampMs
        frameOrdinal += 1L
        val pose = poseAnchors(poseLandmarks)
        val candidates = detections.mapIndexed { index, detection ->
            candidate(index, detection, pose, timestampMs)
        }
        val valid = candidates.filter { it.valid }
        val reasons = pose.issues.toMutableList()
        candidates.filterNot { it.valid }
            .forEach { reasons.add(MALFORMED_HAND_DETECTION_ + it.index) }

        val decision = when {
            valid.size > MAX_HAND_DETECTIONS ->
                AssignmentDecision(emptyMap(), listOf(TOO_MANY_HAND_DETECTIONS))
            valid.isEmpty() -> AssignmentDecision(emptyMap(), emptyList())
            valid.size == 1 -> assignOne(valid.single())
            else -> assignTwo(valid[0], valid[1])
        }
        reasons.addAll(decision.reasons)
        val assignedBySide = decision.assignments.values.associateBy { it.side }
        val left = assignedBySide[AnatomicalHandSide.LEFT]?.candidate
        val right = assignedBySide[AnatomicalHandSide.RIGHT]?.candidate

        updateTrack(AnatomicalHandSide.LEFT, left, timestampMs)
        updateTrack(AnatomicalHandSide.RIGHT, right, timestampMs)
        val leftDropout = updateDropout(AnatomicalHandSide.LEFT, left != null, timestampMs)
        val rightDropout = updateDropout(AnatomicalHandSide.RIGHT, right != null, timestampMs)

        val slotChanges = decision.assignments.values.mapNotNull { assigned ->
            val reported = assigned.candidate.reportedSide ?: return@mapNotNull null
            if (reported == assigned.side) return@mapNotNull null
            TemporalHandSlotChange(
                assigned.candidate.index,
                reported,
                assigned.side,
                overrideReason(assigned.edge)
            )
        }
        cumulativeSlotChanges += slotChanges.size

        val assignedByIndex = decision.assignments.values.associateBy { it.candidate.index }
        val handedness = candidates.map { item ->
            val assignment = assignedByIndex[item.index]
            TemporalHandednessDiagnostic(
                detectionIndex = item.index,
                validLandmarks = item.valid,
                mediaPipeHandedness = item.rawHandedness,
                handednessConfidence = item.confidence,
                reportedAnatomicalSide = item.reportedSide,
                wrist = item.wrist,
                assignedSide = assignment?.side,
                leftScore = item.leftEdge?.score,
                rightScore = item.rightEdge?.score,
                leftContinuityDistance = item.leftEdge?.continuityDistance,
                rightContinuityDistance = item.rightEdge?.continuityDistance,
                leftPoseAnchorDistance = item.leftEdge?.poseAnchorDistance,
                rightPoseAnchorDistance = item.rightEdge?.poseAnchorDistance,
                assignmentEvidence = assignment?.edge?.evidence.orEmpty()
            )
        }
        val status = when {
            detections.isEmpty() -> TemporalHandAssignmentStatus.NO_HANDS
            decision.assignments.isEmpty() -> TemporalHandAssignmentStatus.FAILED_CLOSED
            decision.assignments.size < detections.size -> TemporalHandAssignmentStatus.PARTIAL
            else -> TemporalHandAssignmentStatus.RELIABLE
        }

        return TemporalHandIdentityResult(
            leftDetection = left?.detection,
            rightDetection = right?.detection,
            diagnostics = TemporalHandIdentityDiagnostics(
                status = status,
                leftPresent = left != null,
                rightPresent = right != null,
                handedness = handedness,
                leftWrist = left?.wrist,
                rightWrist = right?.wrist,
                poseLeftWristAnchor = pose.left,
                poseRightWristAnchor = pose.right,
                interHandDistance = distance3d(left?.wrist, right?.wrist),
                slotChanges = slotChanges,
                cumulativeSlotChangeCount = cumulativeSlotChanges,
                leftDropout = leftDropout,
                rightDropout = rightDropout,
                unassignedDetectionIndices = candidates
                    .filterNot { assignedByIndex.containsKey(it.index) }
                    .map { it.index },
                failClosedReasons = reasons.distinct()
            )
        )
    }

    @Synchronized
    fun reset() {
        frameOrdinal = 0L
        lastTimestampMs = null
        cumulativeSlotChanges = 0
        tracks.clear()
        dropouts.values.forEach { it.reset() }
    }

    private fun candidate(
        index: Int,
        detection: TemporalHandDetection,
        pose: PoseAnchors,
        timestampMs: Long
    ): Candidate {
        val valid = detection.landmarks.size == HAND_LANDMARK_COUNT &&
            detection.landmarks.all { it.isFinite() }
        val wrist = detection.landmarks.firstOrNull()?.takeIf { valid }
        val confidence = detection.handednessConfidence
            .takeIf { it.isFinite() && it in 0f..1f }
        val reportedSide = confidence
            ?.takeIf { it >= config.minimumHandednessConfidence }
            ?.let {
                AnatomicalHandedness.resolve(
                    detection.mediaPipeHandedness,
                    reportedHandednessPolicy
                )
            }
        val raw = detection.mediaPipeHandedness?.trim()?.ifBlank { unknown } ?: unknown
        if (!valid || wrist == null) {
            return Candidate(
                index,
                detection,
                false,
                null,
                raw,
                confidence,
                reportedSide,
                null,
                null
            )
        }
        return Candidate(
            index = index,
            detection = detection,
            valid = true,
            wrist = wrist,
            rawHandedness = raw,
            confidence = confidence,
            reportedSide = reportedSide,
            leftEdge = edge(
                wrist,
                AnatomicalHandSide.LEFT,
                reportedSide,
                confidence,
                pose.left,
                timestampMs
            ),
            rightEdge = edge(
                wrist,
                AnatomicalHandSide.RIGHT,
                reportedSide,
                confidence,
                pose.right,
                timestampMs
            )
        )
    }

    private fun edge(
        wrist: LandmarkPoint,
        side: AnatomicalHandSide,
        reportedSide: AnatomicalHandSide?,
        handednessConfidence: Float?,
        poseAnchor: LandmarkPoint?,
        timestampMs: Long
    ): Edge {
        val evidence = mutableListOf<String>()
        var score = 0f
        val predicted = predictedWrist(side, timestampMs)
        val continuityDistance = predicted?.let { distance2d(wrist, it) }
        if (continuityDistance != null &&
            continuityDistance < config.maxWristContinuityDistance
        ) {
            score += config.continuityWeight *
                (1f - continuityDistance / config.maxWristContinuityDistance)
            evidence.add(
                if (isReacquisition(side)) {
                    SHORT_REACQUISITION
                } else {
                    PRIOR_WRIST_CONTINUITY
                }
            )
        }

        val poseDistance = poseAnchor?.let { distance2d(wrist, it) }
        if (poseDistance != null && poseDistance < config.maxPoseWristDistance) {
            score += config.poseAnchorWeight *
                (1f - poseDistance / config.maxPoseWristDistance)
            evidence.add(POSE_WRIST_ANCHOR)
        }

        if (reportedSide != null && handednessConfidence != null) {
            val handednessScore = config.handednessWeight * handednessConfidence
            if (reportedSide == side) {
                score += handednessScore
                evidence.add(REPORTED_HANDEDNESS)
            } else {
                score -= handednessScore
            }
        }
        return Edge(
            side = side,
            score = score,
            reliable = evidence.isNotEmpty() && score >= config.minimumAssignmentScore,
            continuityDistance = continuityDistance,
            poseAnchorDistance = poseDistance,
            evidence = evidence
        )
    }

    private fun assignOne(candidate: Candidate): AssignmentDecision {
        val assigned = reliableSingle(candidate)
            ?: return AssignmentDecision(
                emptyMap(),
                listOf(AMBIGUOUS_SINGLE_HAND_ASSIGNMENT)
            )
        return AssignmentDecision(mapOf(candidate.index to assigned), emptyList())
    }

    private fun assignTwo(first: Candidate, second: Candidate): AssignmentDecision {
        val direct = mapping(
            first,
            AnatomicalHandSide.LEFT,
            second,
            AnatomicalHandSide.RIGHT
        )
        val crossed = mapping(
            first,
            AnatomicalHandSide.RIGHT,
            second,
            AnatomicalHandSide.LEFT
        )
        val reliableMappings = listOf(direct, crossed).filter { it.reliable }
        if (reliableMappings.size == 2) {
            val best = reliableMappings.maxBy { it.score }
            val alternate = reliableMappings.minBy { it.score }
            if (best.score - alternate.score < config.minimumTwoHandMargin) {
                return AssignmentDecision(
                    emptyMap(),
                    listOf(AMBIGUOUS_TWO_HAND_ASSIGNMENT)
                )
            }
            return AssignmentDecision(best.assignments, emptyList())
        }
        if (reliableMappings.size == 1) {
            return AssignmentDecision(reliableMappings.single().assignments, emptyList())
        }

        val firstSingle = reliableSingle(first)
        val secondSingle = reliableSingle(second)
        if (firstSingle == null && secondSingle == null) {
            return AssignmentDecision(
                emptyMap(),
                listOf(UNRELIABLE_TWO_HAND_ASSIGNMENT)
            )
        }
        if (firstSingle != null &&
            secondSingle != null &&
            firstSingle.side != secondSingle.side
        ) {
            return AssignmentDecision(
                mapOf(first.index to firstSingle, second.index to secondSingle),
                listOf(PARTIAL_EVIDENCE_ASSIGNMENT)
            )
        }
        if (firstSingle != null && secondSingle != null) {
            val winner = if (firstSingle.edge.score >= secondSingle.edge.score) {
                firstSingle
            } else {
                secondSingle
            }
            val loser = if (winner === firstSingle) secondSingle else firstSingle
            if (winner.edge.score - loser.edge.score < config.minimumTwoHandMargin) {
                return AssignmentDecision(
                    emptyMap(),
                    listOf(SLOT_COLLISION_FAILED_CLOSED)
                )
            }
            return AssignmentDecision(
                mapOf(winner.candidate.index to winner),
                listOf(SLOT_COLLISION_PARTIAL)
            )
        }
        val only = firstSingle ?: checkNotNull(secondSingle)
        return AssignmentDecision(
            mapOf(only.candidate.index to only),
            listOf(UNASSIGNED_UNRELIABLE_DETECTION)
        )
    }

    private fun mapping(
        first: Candidate,
        firstSide: AnatomicalHandSide,
        second: Candidate,
        secondSide: AnatomicalHandSide
    ): Mapping {
        val firstEdge = first.edge(firstSide)
        val secondEdge = second.edge(secondSide)
        val assignments = mapOf(
            first.index to Assigned(first, firstSide, firstEdge),
            second.index to Assigned(second, secondSide, secondEdge)
        )
        return Mapping(
            assignments,
            firstEdge.score + secondEdge.score,
            firstEdge.reliable && secondEdge.reliable
        )
    }

    private fun reliableSingle(candidate: Candidate): Assigned? {
        val left = candidate.leftEdge ?: return null
        val right = candidate.rightEdge ?: return null
        val best = if (left.score >= right.score) left else right
        val alternate = if (best === left) right else left
        if (!best.reliable ||
            best.score - alternate.score < config.minimumSingleHandMargin
        ) {
            return null
        }
        return Assigned(candidate, best.side, best)
    }

    private fun predictedWrist(
        side: AnatomicalHandSide,
        timestampMs: Long
    ): LandmarkPoint? {
        val track = tracks[side] ?: return null
        val missedFrames =
            (frameOrdinal - track.lastSeenFrame - 1L).coerceAtLeast(0L)
        val elapsedMs = timestampMs - track.lastSeenTimestampMs
        if (missedFrames > config.maxReacquisitionFrames ||
            elapsedMs < 0L ||
            elapsedMs > config.maxReacquisitionDurationMs
        ) {
            return null
        }

        val horizonMs = min(elapsedMs, config.maxVelocityPredictionMs).toFloat()
        var dx = track.velocityXPerMs * horizonMs
        var dy = track.velocityYPerMs * horizonMs
        var dz = track.velocityZPerMs * horizonMs
        val displacement = sqrt(dx * dx + dy * dy + dz * dz)
        if (displacement > config.maxVelocityExtrapolationDistance &&
            displacement > 0f
        ) {
            val scale = config.maxVelocityExtrapolationDistance / displacement
            dx *= scale
            dy *= scale
            dz *= scale
        }
        return LandmarkPoint(
            track.wrist.x + dx,
            track.wrist.y + dy,
            track.wrist.z + dz
        )
    }

    private fun isReacquisition(side: AnatomicalHandSide): Boolean {
        val track = tracks[side] ?: return false
        return frameOrdinal - track.lastSeenFrame > 1L
    }

    private fun updateTrack(
        side: AnatomicalHandSide,
        candidate: Candidate?,
        timestampMs: Long
    ) {
        val wrist = candidate?.wrist ?: return
        val existing = tracks[side]
        if (existing == null) {
            tracks[side] = Track(wrist, frameOrdinal, timestampMs)
            return
        }

        val frameGap = frameOrdinal - existing.lastSeenFrame
        val elapsedMs = timestampMs - existing.lastSeenTimestampMs
        if (frameGap == 1L && elapsedMs > 0L) {
            val inverseElapsed = 1f / elapsedMs.toFloat()
            existing.velocityXPerMs = (wrist.x - existing.wrist.x) * inverseElapsed
            existing.velocityYPerMs = (wrist.y - existing.wrist.y) * inverseElapsed
            existing.velocityZPerMs = (wrist.z - existing.wrist.z) * inverseElapsed
        } else {
            existing.velocityXPerMs = 0f
            existing.velocityYPerMs = 0f
            existing.velocityZPerMs = 0f
        }
        existing.wrist = wrist
        existing.lastSeenFrame = frameOrdinal
        existing.lastSeenTimestampMs = timestampMs
    }

    private fun updateDropout(
        side: AnatomicalHandSide,
        present: Boolean,
        timestampMs: Long
    ): TemporalHandDropoutDuration {
        val state = checkNotNull(dropouts[side])
        if (present) {
            if (state.currentFrames > 0 && state.everPresent) {
                state.totalReacquisitions += 1
                state.lastReacquiredAfterFrames = state.currentFrames
                state.lastReacquiredAfterMs =
                    (timestampMs - (state.dropoutStartedAtMs ?: timestampMs))
                        .coerceAtLeast(0L)
            }
            state.currentFrames = 0
            state.dropoutStartedAtMs = null
            state.everPresent = true
            state.lastPresentTimestampMs = timestampMs
        } else {
            if (state.currentFrames == 0) {
                state.dropoutStartedAtMs =
                    state.lastPresentTimestampMs ?: timestampMs
            }
            state.currentFrames += 1
        }
        return state.snapshot(timestampMs)
    }

    private fun poseAnchors(points: List<LandmarkPoint>?): PoseAnchors {
        if (points == null) return PoseAnchors(null, null, emptyList())
        if (points.size != POSE_LANDMARK_COUNT) {
            return PoseAnchors(
                null,
                null,
                listOf(MALFORMED_POSE_LANDMARKS)
            )
        }
        val issues = mutableListOf<String>()
        val left = points[LEFT_POSE_WRIST_INDEX].takeIf { it.isFinite() }
            ?: run {
                issues.add(INVALID_LEFT_POSE_WRIST_ANCHOR)
                null
            }
        val right = points[RIGHT_POSE_WRIST_INDEX].takeIf { it.isFinite() }
            ?: run {
                issues.add(INVALID_RIGHT_POSE_WRIST_ANCHOR)
                null
            }
        return PoseAnchors(left, right, issues)
    }

    private fun failedWithoutStateAdvance(
        detections: List<TemporalHandDetection>,
        poseLandmarks: List<LandmarkPoint>?,
        timestampMs: Long,
        reason: String
    ): TemporalHandIdentityResult {
        val pose = poseAnchors(poseLandmarks)
        val handedness = detections.mapIndexed { index, detection ->
            val valid = detection.landmarks.size == HAND_LANDMARK_COUNT &&
                detection.landmarks.all { it.isFinite() }
            val confidence = detection.handednessConfidence
                .takeIf { it.isFinite() && it in 0f..1f }
            val reported = confidence
                ?.takeIf { it >= config.minimumHandednessConfidence }
                ?.let {
                    AnatomicalHandedness.resolve(
                        detection.mediaPipeHandedness,
                        reportedHandednessPolicy
                    )
                }
            TemporalHandednessDiagnostic(
                detectionIndex = index,
                validLandmarks = valid,
                mediaPipeHandedness =
                    detection.mediaPipeHandedness?.trim()?.ifBlank { unknown } ?: unknown,
                handednessConfidence = confidence,
                reportedAnatomicalSide = reported,
                wrist = detection.landmarks.firstOrNull()?.takeIf { valid },
                assignedSide = null,
                leftScore = null,
                rightScore = null,
                leftContinuityDistance = null,
                rightContinuityDistance = null,
                leftPoseAnchorDistance = null,
                rightPoseAnchorDistance = null,
                assignmentEvidence = emptyList()
            )
        }
        return TemporalHandIdentityResult(
            leftDetection = null,
            rightDetection = null,
            diagnostics = TemporalHandIdentityDiagnostics(
                status = if (detections.isEmpty()) {
                    TemporalHandAssignmentStatus.NO_HANDS
                } else {
                    TemporalHandAssignmentStatus.FAILED_CLOSED
                },
                leftPresent = false,
                rightPresent = false,
                handedness = handedness,
                leftWrist = null,
                rightWrist = null,
                poseLeftWristAnchor = pose.left,
                poseRightWristAnchor = pose.right,
                interHandDistance = null,
                slotChanges = emptyList(),
                cumulativeSlotChangeCount = cumulativeSlotChanges,
                leftDropout = checkNotNull(dropouts[AnatomicalHandSide.LEFT]).snapshot(timestampMs),
                rightDropout = checkNotNull(dropouts[AnatomicalHandSide.RIGHT]).snapshot(timestampMs),
                unassignedDetectionIndices = detections.indices.toList(),
                failClosedReasons = (pose.issues + reason).distinct()
            )
        )
    }

    private fun overrideReason(edge: Edge): String {
        return edge.evidence.joinToString(
            separator = "+",
            prefix = "OVERRIDE_REPORTED_SIDE_BY_"
        ).ifBlank { "OVERRIDE_REPORTED_SIDE_BY_COMBINED_EVIDENCE" }
    }

    private fun Candidate.edge(side: AnatomicalHandSide): Edge {
        return when (side) {
            AnatomicalHandSide.LEFT -> checkNotNull(leftEdge)
            AnatomicalHandSide.RIGHT -> checkNotNull(rightEdge)
        }
    }

    private fun LandmarkPoint.isFinite(): Boolean =
        x.isFinite() && y.isFinite() && z.isFinite()

    private fun distance2d(first: LandmarkPoint, second: LandmarkPoint): Float {
        val dx = first.x - second.x
        val dy = first.y - second.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun distance3d(first: LandmarkPoint?, second: LandmarkPoint?): Float? {
        if (first == null || second == null) return null
        val dx = first.x - second.x
        val dy = first.y - second.y
        val dz = first.z - second.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private data class PoseAnchors(
        val left: LandmarkPoint?,
        val right: LandmarkPoint?,
        val issues: List<String>
    )

    private data class Edge(
        val side: AnatomicalHandSide,
        val score: Float,
        val reliable: Boolean,
        val continuityDistance: Float?,
        val poseAnchorDistance: Float?,
        val evidence: List<String>
    )

    private data class Candidate(
        val index: Int,
        val detection: TemporalHandDetection,
        val valid: Boolean,
        val wrist: LandmarkPoint?,
        val rawHandedness: String,
        val confidence: Float?,
        val reportedSide: AnatomicalHandSide?,
        val leftEdge: Edge?,
        val rightEdge: Edge?
    )

    private data class Assigned(
        val candidate: Candidate,
        val side: AnatomicalHandSide,
        val edge: Edge
    )

    private data class Mapping(
        val assignments: Map<Int, Assigned>,
        val score: Float,
        val reliable: Boolean
    )

    private data class AssignmentDecision(
        val assignments: Map<Int, Assigned>,
        val reasons: List<String>
    )

    private data class Track(
        var wrist: LandmarkPoint,
        var lastSeenFrame: Long,
        var lastSeenTimestampMs: Long,
        var velocityXPerMs: Float = 0f,
        var velocityYPerMs: Float = 0f,
        var velocityZPerMs: Float = 0f
    )

    private data class DropoutState(
        var currentFrames: Int = 0,
        var dropoutStartedAtMs: Long? = null,
        var lastPresentTimestampMs: Long? = null,
        var lastReacquiredAfterFrames: Int? = null,
        var lastReacquiredAfterMs: Long? = null,
        var totalReacquisitions: Int = 0,
        var everPresent: Boolean = false
    ) {
        fun snapshot(timestampMs: Long): TemporalHandDropoutDuration {
            val durationMs = if (currentFrames == 0) {
                0L
            } else {
                (timestampMs - (dropoutStartedAtMs ?: timestampMs)).coerceAtLeast(0L)
            }
            return TemporalHandDropoutDuration(
                currentFrames = currentFrames,
                currentDurationMs = durationMs,
                lastReacquiredAfterFrames = lastReacquiredAfterFrames,
                lastReacquiredAfterMs = lastReacquiredAfterMs,
                totalReacquisitions = totalReacquisitions
            )
        }

        fun reset() {
            currentFrames = 0
            dropoutStartedAtMs = null
            lastPresentTimestampMs = null
            lastReacquiredAfterFrames = null
            lastReacquiredAfterMs = null
            totalReacquisitions = 0
            everPresent = false
        }
    }

    companion object {
        private const val HAND_LANDMARK_COUNT = 21
        private const val POSE_LANDMARK_COUNT = 33
        private const val LEFT_POSE_WRIST_INDEX = 15
        private const val RIGHT_POSE_WRIST_INDEX = 16
        private const val MAX_HAND_DETECTIONS = 2
        private const val unknown = "unknown"

        private const val NON_MONOTONIC_TIMESTAMP = "NON_MONOTONIC_TIMESTAMP"
        private const val MALFORMED_HAND_DETECTION_ = "MALFORMED_HAND_DETECTION_"
        private const val TOO_MANY_HAND_DETECTIONS = "TOO_MANY_HAND_DETECTIONS"
        private const val AMBIGUOUS_SINGLE_HAND_ASSIGNMENT = "AMBIGUOUS_SINGLE_HAND_ASSIGNMENT"
        private const val AMBIGUOUS_TWO_HAND_ASSIGNMENT = "AMBIGUOUS_TWO_HAND_ASSIGNMENT"
        private const val UNRELIABLE_TWO_HAND_ASSIGNMENT = "UNRELIABLE_TWO_HAND_ASSIGNMENT"
        private const val PARTIAL_EVIDENCE_ASSIGNMENT = "PARTIAL_EVIDENCE_ASSIGNMENT"
        private const val SLOT_COLLISION_FAILED_CLOSED = "SLOT_COLLISION_FAILED_CLOSED"
        private const val SLOT_COLLISION_PARTIAL = "SLOT_COLLISION_PARTIAL"
        private const val UNASSIGNED_UNRELIABLE_DETECTION = "UNASSIGNED_UNRELIABLE_DETECTION"
        private const val SHORT_REACQUISITION = "SHORT_REACQUISITION"
        private const val PRIOR_WRIST_CONTINUITY = "PRIOR_WRIST_CONTINUITY"
        private const val POSE_WRIST_ANCHOR = "POSE_WRIST_ANCHOR"
        private const val REPORTED_HANDEDNESS = "REPORTED_HANDEDNESS"
        private const val MALFORMED_POSE_LANDMARKS = "MALFORMED_POSE_LANDMARKS"
        private const val INVALID_LEFT_POSE_WRIST_ANCHOR = "INVALID_LEFT_POSE_WRIST_ANCHOR"
        private const val INVALID_RIGHT_POSE_WRIST_ANCHOR = "INVALID_RIGHT_POSE_WRIST_ANCHOR"
    }
}
