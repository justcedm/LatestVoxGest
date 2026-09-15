package com.voxgest.dryrun

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/** Immutable temporal contract selected by the active runtime profile. */
data class CompleteSignTemporalProfile(
    val id: String,
    val outputLength: Int,
    val featureSize: Int,
    val motionBoundaryFrames: Int,
    val maximumInternalHandGapFrames: Int,
    val minimumMotionSpeed: Float,
    val rejectMalformedLandmarks: Boolean
) {
    init {
        require(id.isNotBlank())
        require(outputLength >= 2)
        require(featureSize == StandardFullSign225Contract.FEATURE_SIZE)
        require(motionBoundaryFrames >= 0)
        require(maximumInternalHandGapFrames >= 0)
        require(minimumMotionSpeed > 0f)
    }
}

/** Temporal policies; tensor dimensions still come from each verified runtime manifest. */
object CompleteSignTemporalProfiles {
    val MAPUA14_LIVE_SEGMENT_V1 = CompleteSignTemporalProfile(
        id = "MAPUA14_LIVE_SEGMENT_V1",
        outputLength = 48,
        featureSize = StandardFullSign225Contract.FEATURE_SIZE,
        motionBoundaryFrames = 3,
        maximumInternalHandGapFrames = 3,
        minimumMotionSpeed = 0.004f,
        rejectMalformedLandmarks = false
    )

    fun standardFsl105(sequenceLength: Int, featureSize: Int): CompleteSignTemporalProfile {
        return CompleteSignTemporalProfile(
            id = Fsl105LiveSegmentProfile.ID,
            outputLength = sequenceLength,
            featureSize = featureSize,
            motionBoundaryFrames = 3,
            // Standard specifies missing-hand zero fill; it does not authorize interpolation.
            maximumInternalHandGapFrames = 0,
            minimumMotionSpeed = 0.004f,
            rejectMalformedLandmarks = true
        )
    }
}

data class CompleteSignPreparedTrajectory(
    val temporalProfileId: String,
    val modelInput: Array<FloatArray>,
    val capturedFrameCount: Int,
    val motionStartCaptureIndex: Int,
    val motionEndCaptureIndex: Int,
    val completeTrajectoryFrameCount: Int,
    val interpolatedLeftFrames: Int,
    val interpolatedRightFrames: Int,
    val quality: StandardFullSign225WindowQuality,
    val sourceTimestampsMs: LongArray
) {
    init {
        require(temporalProfileId.isNotBlank())
        require(modelInput.isNotEmpty())
        require(modelInput.all { it.size == StandardFullSign225Contract.FEATURE_SIZE })
        require(quality.frameCount == completeTrajectoryFrameCount)
        require((1 until sourceTimestampsMs.size).all { index ->
            sourceTimestampsMs[index] > sourceTimestampsMs[index - 1]
        })
    }

    fun copyModelInput(): Array<FloatArray> =
        Array(modelInput.size) { modelInput[it].copyOf() }
}

/** Finalizes a chronological event. It never selects a first/latest rolling window. */
object CompleteSignTrajectoryFinalizer {
    fun prepare(
        capturedFrames: List<LandmarkFrame>,
        profile: CompleteSignTemporalProfile
    ): CompleteSignPreparedTrajectory {
        require(capturedFrames.isNotEmpty()) { "complete trajectory requires at least one frame" }
        require(capturedFrames.zipWithNext().all { (before, after) ->
            after.timestampMs > before.timestampMs
        }) { "captured landmark frames must be strictly chronological" }

        val sanitized = capturedFrames.map { sanitize(it, profile.rejectMalformedLandmarks) }
        val leftInterpolation = interpolateShortInternalGaps(
            sanitized.map { it.leftHandLandmarks },
            StandardFullSign225Contract.HAND_LANDMARK_COUNT,
            profile.maximumInternalHandGapFrames
        )
        val rightInterpolation = interpolateShortInternalGaps(
            sanitized.map { it.rightHandLandmarks },
            StandardFullSign225Contract.HAND_LANDMARK_COUNT,
            profile.maximumInternalHandGapFrames
        )
        val handCenters = sanitized.indices.map { index ->
            meanHandCenter(leftInterpolation.values[index], rightInterpolation.values[index])
        }
        val envelope = detectMotionEnvelope(
            handCenters,
            profile.motionBoundaryFrames,
            profile.minimumMotionSpeed
        )
        val selectedSource = sanitized.subList(envelope.first, envelope.last + 1)
        // Raw observations, never interpolated landmarks, determine presence quality.
        val quality = StandardFullSign225WindowQuality(
            frameCount = selectedSource.size,
            posePresentFrames = selectedSource.count { it.poseLandmarks != null },
            leftHandPresentFrames = selectedSource.count { it.leftHandLandmarks != null },
            rightHandPresentFrames = selectedSource.count { it.rightHandLandmarks != null },
            anyHandPresentFrames = selectedSource.count {
                it.leftHandLandmarks != null || it.rightHandLandmarks != null
            },
            bothHandsPresentFrames = selectedSource.count {
                it.leftHandLandmarks != null && it.rightHandLandmarks != null
            }
        )
        val complete = (envelope.first..envelope.last).map { index ->
            val source = sanitized[index]
            StandardFullSign225FeatureBuilder.build(
                source.copy(
                    leftHandLandmarks = leftInterpolation.values[index],
                    rightHandLandmarks = rightInterpolation.values[index]
                ),
                inputMirrored = false
            ).vector
        }
        val modelInput = resampleComplete(
            sequence = complete,
            outputLength = profile.outputLength,
            featureSize = profile.featureSize
        )
        check(modelInput.size == profile.outputLength)
        check(modelInput.all { it.size == profile.featureSize && it.all(Float::isFinite) })

        return CompleteSignPreparedTrajectory(
            temporalProfileId = profile.id,
            modelInput = modelInput,
            capturedFrameCount = capturedFrames.size,
            motionStartCaptureIndex = envelope.first,
            motionEndCaptureIndex = envelope.last,
            completeTrajectoryFrameCount = complete.size,
            interpolatedLeftFrames = leftInterpolation.filledCount,
            interpolatedRightFrames = rightInterpolation.filledCount,
            quality = quality,
            sourceTimestampsMs = capturedFrames
                .subList(envelope.first, envelope.last + 1)
                .map { it.timestampMs }
                .toLongArray()
        )
    }

    /** Exact evenly spaced linear resampling over the complete event. */
    internal fun resampleComplete(
        sequence: List<FloatArray>,
        outputLength: Int,
        featureSize: Int = StandardFullSign225Contract.FEATURE_SIZE
    ): Array<FloatArray> {
        require(sequence.isNotEmpty()) { "cannot resample an empty trajectory" }
        require(outputLength >= 2)
        require(featureSize == StandardFullSign225Contract.FEATURE_SIZE)
        require(sequence.all { frame ->
            frame.size == featureSize && frame.all(Float::isFinite)
        }) { "trajectory frames must be finite FullSign225 vectors" }
        if (sequence.size == 1) return Array(outputLength) { sequence[0].copyOf() }

        return Array(outputLength) { outputIndex ->
            val position = outputIndex.toFloat() * (sequence.size - 1).toFloat() /
                (outputLength - 1).toFloat()
            val low = floor(position).toInt()
            val high = minOf(low + 1, sequence.lastIndex)
            val alpha = position - low.toFloat()
            FloatArray(featureSize) { featureIndex ->
                (1f - alpha) * sequence[low][featureIndex] +
                    alpha * sequence[high][featureIndex]
            }
        }
    }

    private fun sanitize(frame: LandmarkFrame, rejectMalformed: Boolean): LandmarkFrame =
        frame.copy(
            poseLandmarks = validLandmarks(
                frame.poseLandmarks,
                StandardFullSign225Contract.POSE_LANDMARK_COUNT,
                "pose",
                rejectMalformed
            ),
            leftHandLandmarks = validLandmarks(
                frame.leftHandLandmarks,
                StandardFullSign225Contract.HAND_LANDMARK_COUNT,
                "left_hand",
                rejectMalformed
            ),
            rightHandLandmarks = validLandmarks(
                frame.rightHandLandmarks,
                StandardFullSign225Contract.HAND_LANDMARK_COUNT,
                "right_hand",
                rejectMalformed
            )
        )

    private fun validLandmarks(
        landmarks: List<LandmarkPoint>?,
        expectedCount: Int,
        name: String,
        rejectMalformed: Boolean
    ): List<LandmarkPoint>? {
        if (landmarks == null) return null
        val valid = landmarks.size == expectedCount &&
            landmarks.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }
        if (!valid && rejectMalformed) {
            throw IllegalArgumentException("$name landmarks are malformed")
        }
        return landmarks.takeIf { valid }?.map { it.copy() }
    }

    private fun interpolateShortInternalGaps(
        source: List<List<LandmarkPoint>?>,
        expectedCount: Int,
        maximumGapFrames: Int
    ): InterpolationResult {
        val result = source.map { points -> points?.map { it.copy() } }.toMutableList()
        if (maximumGapFrames == 0) return InterpolationResult(result, 0)
        var filled = 0
        var index = 0
        while (index < result.size) {
            if (result[index] != null) {
                index += 1
                continue
            }
            val start = index
            while (index < result.size && result[index] == null) index += 1
            val gap = index - start
            if (start == 0 || index == result.size || gap > maximumGapFrames) continue
            val before = result[start - 1] ?: continue
            val after = result[index] ?: continue
            check(before.size == expectedCount && after.size == expectedCount)
            repeat(gap) { offset ->
                val weight = (offset + 1).toFloat() / (gap + 1).toFloat()
                result[start + offset] = List(expectedCount) { pointIndex ->
                    val left = before[pointIndex]
                    val right = after[pointIndex]
                    LandmarkPoint(
                        x = (1f - weight) * left.x + weight * right.x,
                        y = (1f - weight) * left.y + weight * right.y,
                        z = (1f - weight) * left.z + weight * right.z
                    )
                }
                filled += 1
            }
        }
        return InterpolationResult(result, filled)
    }

    private fun meanHandCenter(
        left: List<LandmarkPoint>?,
        right: List<LandmarkPoint>?
    ): LandmarkPoint? {
        val hands = listOfNotNull(left, right)
        if (hands.isEmpty()) return null
        val centers = hands.map { hand ->
            LandmarkPoint(
                hand.sumOf { it.x.toDouble() }.toFloat() / hand.size,
                hand.sumOf { it.y.toDouble() }.toFloat() / hand.size,
                hand.sumOf { it.z.toDouble() }.toFloat() / hand.size
            )
        }
        return LandmarkPoint(
            centers.sumOf { it.x.toDouble() }.toFloat() / centers.size,
            centers.sumOf { it.y.toDouble() }.toFloat() / centers.size,
            centers.sumOf { it.z.toDouble() }.toFloat() / centers.size
        )
    }

    private fun detectMotionEnvelope(
        centers: List<LandmarkPoint?>,
        boundary: Int,
        minimumMotionSpeed: Float
    ): IntRange {
        val valid = centers.mapIndexedNotNull { index, point -> point?.let { index to it } }
        if (valid.isEmpty()) return 0..centers.lastIndex
        if (valid.size == 1) {
            val index = valid[0].first
            return maxOf(0, index - boundary)..minOf(centers.lastIndex, index + boundary)
        }

        val motion = valid.zipWithNext { previous, current ->
            val frameGap = maxOf(1, current.first - previous.first)
            current.first to distance(previous.second, current.second) / frameGap.toFloat()
        }
        val speeds = motion.map { it.second }
        val baseline = percentile(speeds, 0.5f)
        val deviations = speeds.map { kotlin.math.abs(it - baseline) }
        val medianAbsoluteDeviation = percentile(deviations, 0.5f)
        val threshold = maxOf(
            minimumMotionSpeed,
            baseline + 1.5f * medianAbsoluteDeviation,
            percentile(speeds, 0.55f) * 0.75f
        )
        val active = motion.filter { it.second >= threshold }.map { it.first }.ifEmpty {
            listOf(motion[speeds.indices.maxBy { speeds[it] }].first)
        }
        return maxOf(0, active.min() - boundary)..
            minOf(centers.lastIndex, active.max() + boundary)
    }

    private fun percentile(values: List<Float>, fraction: Float): Float {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val position = (sorted.size - 1).toFloat() * fraction
        val low = floor(position).toInt()
        val high = ceil(position).toInt()
        val alpha = position - low.toFloat()
        return (1f - alpha) * sorted[low] + alpha * sorted[high]
    }

    private fun distance(left: LandmarkPoint, right: LandmarkPoint): Float {
        val dx = left.x - right.x
        val dy = left.y - right.y
        val dz = left.z - right.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private data class InterpolationResult(
        val values: List<List<LandmarkPoint>?>,
        val filledCount: Int
    )
}
