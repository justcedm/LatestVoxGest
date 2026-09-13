package com.voxgest.dryrun

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Mapua-14 temporal preparation copied from scripts_ml/94_prepare_mapua14_rescue.py.
 *
 * The contract is intentionally fixed: short internal hand gaps are interpolated in raw landmark
 * space, an adaptive hand-centre motion envelope receives a three-frame boundary, and the complete
 * envelope is linearly resampled to 48 frames. It never selects the first or latest 48 camera
 * frames.
 */
object Mapua14CompleteTrajectory48 {
    const val OUTPUT_LENGTH = 48
    const val FEATURE_SIZE = 225
    const val MOTION_BOUNDARY_FRAMES = 3
    const val MAXIMUM_INTERNAL_HAND_GAP_FRAMES = 3
    const val MINIMUM_MOTION_SPEED = 0.004f

    fun prepare(capturedFrames: List<LandmarkFrame>): Mapua14PreparedTrajectory {
        require(capturedFrames.isNotEmpty()) { "complete trajectory requires at least one frame" }
        require(capturedFrames.zipWithNext().all { (before, after) ->
            after.timestampMs > before.timestampMs
        }) { "captured landmark frames must be strictly chronological" }

        val sanitized = capturedFrames.map(::sanitize)
        val leftInterpolation = interpolateShortInternalGaps(
            sanitized.map { it.leftHandLandmarks },
            StandardFullSign225Contract.HAND_LANDMARK_COUNT
        )
        val rightInterpolation = interpolateShortInternalGaps(
            sanitized.map { it.rightHandLandmarks },
            StandardFullSign225Contract.HAND_LANDMARK_COUNT
        )
        val handCenters = sanitized.indices.map { index ->
            meanHandCenter(leftInterpolation.values[index], rightInterpolation.values[index])
        }
        val envelope = detectMotionEnvelope(handCenters, MOTION_BOUNDARY_FRAMES)
        val selectedSource = sanitized.subList(envelope.first, envelope.last + 1)
        // Count only raw, sanitized detections inside the selected envelope. The interpolation
        // below must not make tracking quality look better than the camera observation was.
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
        val modelInput = resampleComplete48(complete)
        check(modelInput.size == OUTPUT_LENGTH)
        check(modelInput.all { it.size == FEATURE_SIZE && it.all(Float::isFinite) })

        return Mapua14PreparedTrajectory(
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

    /** Exact 48-position linear policy used by NumPy resample_complete. */
    internal fun resampleComplete48(sequence: List<FloatArray>): Array<FloatArray> {
        require(sequence.isNotEmpty()) { "cannot resample an empty trajectory" }
        require(sequence.all { frame ->
            frame.size == FEATURE_SIZE && frame.all(Float::isFinite)
        }) { "trajectory frames must be finite FullSign225 vectors" }
        if (sequence.size == 1) {
            return Array(OUTPUT_LENGTH) { sequence[0].copyOf() }
        }

        return Array(OUTPUT_LENGTH) { outputIndex ->
            val position = outputIndex.toFloat() * (sequence.size - 1).toFloat() /
                (OUTPUT_LENGTH - 1).toFloat()
            val low = floor(position).toInt()
            val high = minOf(low + 1, sequence.lastIndex)
            val alpha = position - low.toFloat()
            FloatArray(FEATURE_SIZE) { featureIndex ->
                (1f - alpha) * sequence[low][featureIndex] +
                    alpha * sequence[high][featureIndex]
            }
        }
    }

    private fun sanitize(frame: LandmarkFrame): LandmarkFrame = frame.copy(
        poseLandmarks = validLandmarks(
            frame.poseLandmarks,
            StandardFullSign225Contract.POSE_LANDMARK_COUNT
        ),
        leftHandLandmarks = validLandmarks(
            frame.leftHandLandmarks,
            StandardFullSign225Contract.HAND_LANDMARK_COUNT
        ),
        rightHandLandmarks = validLandmarks(
            frame.rightHandLandmarks,
            StandardFullSign225Contract.HAND_LANDMARK_COUNT
        )
    )

    private fun validLandmarks(
        landmarks: List<LandmarkPoint>?,
        expectedCount: Int
    ): List<LandmarkPoint>? {
        if (landmarks?.size != expectedCount) return null
        if (landmarks.any { !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() }) return null
        return landmarks.map { it.copy() }
    }

    private fun interpolateShortInternalGaps(
        source: List<List<LandmarkPoint>?>,
        expectedCount: Int
    ): InterpolationResult {
        val result = source.map { points -> points?.map { it.copy() } }.toMutableList()
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
            if (start == 0 || index == result.size ||
                gap > MAXIMUM_INTERNAL_HAND_GAP_FRAMES
            ) {
                continue
            }
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
        boundary: Int
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
            MINIMUM_MOTION_SPEED,
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

data class Mapua14PreparedTrajectory(
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
        require(modelInput.size == Mapua14CompleteTrajectory48.OUTPUT_LENGTH)
        require(modelInput.all { it.size == Mapua14CompleteTrajectory48.FEATURE_SIZE })
        require(quality.frameCount == completeTrajectoryFrameCount)
        require((1 until sourceTimestampsMs.size).all { index ->
            sourceTimestampsMs[index] > sourceTimestampsMs[index - 1]
        })
    }

    fun copyModelInput(): Array<FloatArray> = Array(modelInput.size) { modelInput[it].copyOf() }
}
