package com.voxgest.dryrun

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Missing-frame statistics for one anatomical hand inside the current window. */
data class LandmarkSideDropoutReport(
    val dropoutFrameCount: Int,
    val longestDropoutStreak: Int,
    val reacquisitionCount: Int
)

/**
 * Read-only landmark-quality evidence for a bounded, rolling frame window.
 *
 * Geometry observations retain one nullable entry per frame, in window order.
 * They are diagnostic values only: this type defines no acceptance threshold and
 * does not alter, fill, mirror, swap, or otherwise transform model landmarks.
 */
data class LandmarkOcclusionReport(
    val windowCapacity: Int,
    val frameCount: Int,
    val selectedSide: AnatomicalHandSide,
    val posePresentFrames: Int,
    val leftHandPresentFrames: Int,
    val rightHandPresentFrames: Int,
    val bothHandsPresentFrames: Int,
    val posePresenceRatio: Float,
    val leftHandPresenceRatio: Float,
    val rightHandPresenceRatio: Float,
    val bothHandsPresenceRatio: Float,
    val selectedSidePresenceRatio: Float,
    val leftDropout: LandmarkSideDropoutReport,
    val rightDropout: LandmarkSideDropoutReport,
    val resolvedSlotCollisionCount: Int,
    val handednessCategoryFlipCount: Int,
    val handednessComparableCategoryCount: Int,
    val handBoundingBoxIouObservations: List<Float?>,
    val wristDistanceObservations: List<Float?>
) {
    val leftDropoutFrameCount: Int get() = leftDropout.dropoutFrameCount
    val leftLongestDropoutStreak: Int get() = leftDropout.longestDropoutStreak
    val leftReacquisitionCount: Int get() = leftDropout.reacquisitionCount
    val rightDropoutFrameCount: Int get() = rightDropout.dropoutFrameCount
    val rightLongestDropoutStreak: Int get() = rightDropout.longestDropoutStreak
    val rightReacquisitionCount: Int get() = rightDropout.reacquisitionCount
    val latestHandBoundingBoxIou: Float? get() = handBoundingBoxIouObservations.lastOrNull()
    val latestWristDistance: Float? get() = wristDistanceObservations.lastOrNull()
}

/**
 * Diagnostics-only observer. It stores derived scalar metadata, never the source
 * [LandmarkFrame] or its landmark collections, so recognition inputs remain untouched.
 *
 * A handedness-category flip is counted conservatively. Adjacent frames are compared
 * only when they contain the same non-zero number of known MediaPipe categories;
 * categories are paired by ascending raw average-X. This is observable evidence, not
 * a claim of persistent anatomical identity.
 */
class LandmarkOcclusionDiagnostics(
    val selectedSide: AnatomicalHandSide,
    val windowCapacity: Int = DEFAULT_WINDOW_CAPACITY
) {
    private val frames = ArrayDeque<FrameObservation>(windowCapacity)

    init {
        require(windowCapacity > 0) { "windowCapacity must be positive" }
    }

    @Synchronized
    fun observe(frame: LandmarkFrame): LandmarkOcclusionReport {
        if (frames.size == windowCapacity) frames.removeFirst()
        frames.addLast(frame.toObservation())
        return snapshot()
    }

    @Synchronized
    fun observeAll(source: Iterable<LandmarkFrame>): LandmarkOcclusionReport {
        source.forEach { frame ->
            if (frames.size == windowCapacity) frames.removeFirst()
            frames.addLast(frame.toObservation())
        }
        return snapshot()
    }

    @Synchronized
    fun snapshot(): LandmarkOcclusionReport {
        val current = frames.toList()
        val count = current.size
        val poseFrames = current.count { it.posePresent }
        val leftFrames = current.count { it.leftPresent }
        val rightFrames = current.count { it.rightPresent }
        val bothFrames = current.count { it.leftPresent && it.rightPresent }
        val leftDropout = dropoutReport(current.map { it.leftPresent })
        val rightDropout = dropoutReport(current.map { it.rightPresent })
        val categoryChanges = categoryChanges(current)

        return LandmarkOcclusionReport(
            windowCapacity = windowCapacity,
            frameCount = count,
            selectedSide = selectedSide,
            posePresentFrames = poseFrames,
            leftHandPresentFrames = leftFrames,
            rightHandPresentFrames = rightFrames,
            bothHandsPresentFrames = bothFrames,
            posePresenceRatio = ratio(poseFrames, count),
            leftHandPresenceRatio = ratio(leftFrames, count),
            rightHandPresenceRatio = ratio(rightFrames, count),
            bothHandsPresenceRatio = ratio(bothFrames, count),
            selectedSidePresenceRatio = ratio(
                when (selectedSide) {
                    AnatomicalHandSide.LEFT -> leftFrames
                    AnatomicalHandSide.RIGHT -> rightFrames
                },
                count
            ),
            leftDropout = leftDropout,
            rightDropout = rightDropout,
            resolvedSlotCollisionCount = current.sumOf { it.resolvedSlotCollisionCount },
            handednessCategoryFlipCount = categoryChanges.flipCount,
            handednessComparableCategoryCount = categoryChanges.comparableCount,
            handBoundingBoxIouObservations = current.map { it.handBoundingBoxIou },
            wristDistanceObservations = current.map { it.wristDistance }
        )
    }

    @Synchronized
    fun reset() {
        frames.clear()
    }

    private fun LandmarkFrame.toObservation(): FrameObservation {
        return FrameObservation(
            posePresent = hasPose,
            leftPresent = hasLeftHand,
            rightPresent = hasRightHand,
            resolvedSlotCollisionCount = resolvedSlotCollisionCount(handObservations),
            spatialHandednessCategories = spatialHandednessCategories(handObservations),
            handBoundingBoxIou = boundingBoxIou(leftHandLandmarks, rightHandLandmarks),
            wristDistance = wristDistance(leftHandLandmarks, rightHandLandmarks)
        )
    }

    private fun dropoutReport(presence: List<Boolean>): LandmarkSideDropoutReport {
        var longest = 0
        var streak = 0
        var reacquisitions = 0
        presence.forEachIndexed { index, present ->
            if (present) {
                if (index > 0 && !presence[index - 1]) reacquisitions += 1
                streak = 0
            } else {
                streak += 1
                longest = max(longest, streak)
            }
        }
        return LandmarkSideDropoutReport(
            dropoutFrameCount = presence.count { !it },
            longestDropoutStreak = longest,
            reacquisitionCount = reacquisitions
        )
    }

    private fun categoryChanges(source: List<FrameObservation>): CategoryChanges {
        var flips = 0
        var comparable = 0
        source.zipWithNext().forEach { (previous, current) ->
            val before = previous.spatialHandednessCategories
            val after = current.spatialHandednessCategories
            if (before.isEmpty() || before.size != after.size) return@forEach
            comparable += before.size
            before.indices.forEach { index ->
                if (before[index] != after[index]) flips += 1
            }
        }
        return CategoryChanges(flips, comparable)
    }

    private fun resolvedSlotCollisionCount(observations: List<HandObservation>): Int {
        val counts = mutableMapOf<String, Int>()
        observations.forEach { observation ->
            val slot = observation.slot.trim().lowercase()
            if (slot == LEFT || slot == RIGHT) counts[slot] = (counts[slot] ?: 0) + 1
        }
        return counts.values.sumOf { count -> (count - 1).coerceAtLeast(0) }
    }

    private fun spatialHandednessCategories(observations: List<HandObservation>): List<String> {
        return observations.mapNotNull { observation ->
            val category = observation.mediaPipeHandedness.trim().lowercase()
            if ((category == LEFT || category == RIGHT) && observation.averageX.isFinite()) {
                SpatialCategory(observation.averageX, category, observation.slot)
            } else {
                null
            }
        }.sortedWith(
            compareBy<SpatialCategory> { it.averageX }
                .thenBy { it.category }
                .thenBy { it.slot }
        ).map { it.category }
    }

    private fun boundingBoxIou(
        left: List<LandmarkPoint>?,
        right: List<LandmarkPoint>?
    ): Float? {
        val leftBox = boundingBox(left) ?: return null
        val rightBox = boundingBox(right) ?: return null
        val intersectionWidth = max(0f, min(leftBox.maxX, rightBox.maxX) - max(leftBox.minX, rightBox.minX))
        val intersectionHeight = max(0f, min(leftBox.maxY, rightBox.maxY) - max(leftBox.minY, rightBox.minY))
        val intersectionArea = intersectionWidth * intersectionHeight
        val unionArea = leftBox.area + rightBox.area - intersectionArea
        return if (unionArea > 0f) (intersectionArea / unionArea).coerceIn(0f, 1f) else null
    }

    private fun boundingBox(points: List<LandmarkPoint>?): BoundingBox? {
        if (points?.size != HAND_LANDMARK_COUNT || points.any { !it.x.isFinite() || !it.y.isFinite() }) {
            return null
        }
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        return BoundingBox(minX, minY, maxX, maxY)
    }

    private fun wristDistance(
        left: List<LandmarkPoint>?,
        right: List<LandmarkPoint>?
    ): Float? {
        if (left?.size != HAND_LANDMARK_COUNT || right?.size != HAND_LANDMARK_COUNT) return null
        val leftWrist = left.first()
        val rightWrist = right.first()
        if (!leftWrist.isFinite() || !rightWrist.isFinite()) return null
        val dx = leftWrist.x - rightWrist.x
        val dy = leftWrist.y - rightWrist.y
        val dz = leftWrist.z - rightWrist.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun LandmarkPoint.isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

    private fun ratio(present: Int, total: Int): Float {
        return if (total == 0) 0f else present.toFloat() / total.toFloat()
    }

    private data class FrameObservation(
        val posePresent: Boolean,
        val leftPresent: Boolean,
        val rightPresent: Boolean,
        val resolvedSlotCollisionCount: Int,
        val spatialHandednessCategories: List<String>,
        val handBoundingBoxIou: Float?,
        val wristDistance: Float?
    )

    private data class SpatialCategory(
        val averageX: Float,
        val category: String,
        val slot: String
    )

    private data class BoundingBox(
        val minX: Float,
        val minY: Float,
        val maxX: Float,
        val maxY: Float
    ) {
        val area: Float get() = max(0f, maxX - minX) * max(0f, maxY - minY)
    }

    private data class CategoryChanges(
        val flipCount: Int,
        val comparableCount: Int
    )

    companion object {
        const val DEFAULT_WINDOW_CAPACITY = 20
        private const val HAND_LANDMARK_COUNT = 21
        private const val LEFT = "left"
        private const val RIGHT = "right"
    }
}
