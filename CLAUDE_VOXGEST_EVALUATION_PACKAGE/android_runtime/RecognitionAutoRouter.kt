package com.voxgest.dryrun

import kotlin.math.sqrt

enum class RecognitionRoute {
    STATIC,
    ONEHAND,
    FULLSIGN,
    NOTHING
}

data class RouterDecision(
    val route: RecognitionRoute,
    val statusText: String,
    val reason: String,
    val handPresence: Float,
    val motion: Float,
    val shapeMotion: Float,
    val stableFrames: Int,
    val movingFrames: Int,
    val bothHandsPresent: Boolean
)

class RecognitionAutoRouter {
    private var previousWrist: LandmarkPoint? = null
    private var previousHandShape: FloatArray? = null
    private val motionWindow = ArrayDeque<Float>()
    private val shapeWindow = ArrayDeque<Float>()
    private var stableFrames = 0
    private var movingFrames = 0

    fun decide(frame: LandmarkFrame?): RouterDecision {
        if (frame == null || !frame.hasAnyHand) {
            reset()
            return RouterDecision(
                route = RecognitionRoute.NOTHING,
                statusText = "Looking for hand",
                reason = "no_hand",
                handPresence = 0f,
                motion = 0f,
                shapeMotion = 1f,
                stableFrames = 0,
                movingFrames = 0,
                bothHandsPresent = false
            )
        }

        val selectedHand = frame.rightHandLandmarks ?: frame.leftHandLandmarks
        val wrist = selectedHand?.firstOrNull()
        val motion = previousWrist?.let { distance(it, wrist ?: it) } ?: 0f
        previousWrist = wrist
        push(motionWindow, motion)

        val shape = selectedHand?.let { normalizedShape(it) }
        val shapeMotion = if (shape != null && previousHandShape != null) {
            meanAbsoluteDifference(shape, previousHandShape!!)
        } else {
            1f
        }
        previousHandShape = shape
        push(shapeWindow, shapeMotion)

        val averageMotion = motionWindow.averageOrZero()
        val averageShapeMotion = shapeWindow.averageOrZero()
        val stable = averageMotion <= STATIC_MAX_WRIST_MOTION && averageShapeMotion <= STATIC_MAX_SHAPE_MOTION
        stableFrames = if (stable) stableFrames + 1 else 0
        movingFrames = if (averageMotion >= DYNAMIC_MIN_WRIST_MOTION) {
            movingFrames + 1
        } else {
            (movingFrames - 1).coerceAtLeast(0)
        }

        val bothHands = frame.hasLeftHand && frame.hasRightHand
        val route = when {
            !frame.hasPose && stableFrames < STATIC_ROUTE_FRAMES -> RecognitionRoute.NOTHING
            !bothHands && stableFrames >= STATIC_ROUTE_FRAMES -> RecognitionRoute.STATIC
            bothHands && frame.hasPose && movingFrames >= FULLSIGN_ROUTE_FRAMES -> RecognitionRoute.FULLSIGN
            bothHands && frame.hasPose && stableFrames < STATIC_ROUTE_FRAMES -> RecognitionRoute.FULLSIGN
            !bothHands && frame.hasPose && movingFrames >= ONEHAND_ROUTE_FRAMES -> RecognitionRoute.ONEHAND
            else -> RecognitionRoute.NOTHING
        }

        val status = when (route) {
            RecognitionRoute.STATIC -> "Hold steady"
            RecognitionRoute.ONEHAND, RecognitionRoute.FULLSIGN -> "Signing..."
            RecognitionRoute.NOTHING -> if (stableFrames > 0) "Hold steady" else "Looking for hand"
        }
        return RouterDecision(
            route = route,
            statusText = status,
            reason = "motion=${averageMotion.format3()} shape=${averageShapeMotion.format3()} stable=$stableFrames moving=$movingFrames",
            handPresence = 1f,
            motion = averageMotion,
            shapeMotion = averageShapeMotion,
            stableFrames = stableFrames,
            movingFrames = movingFrames,
            bothHandsPresent = bothHands
        )
    }

    fun reset() {
        previousWrist = null
        previousHandShape = null
        motionWindow.clear()
        shapeWindow.clear()
        stableFrames = 0
        movingFrames = 0
    }

    private fun normalizedShape(points: List<LandmarkPoint>): FloatArray? {
        if (points.size != HAND_LANDMARK_COUNT) return null
        val wrist = points[0]
        val middleMcp = points[9]
        val scale = distance(wrist, middleMcp)
        if (scale <= MIN_SCALE) return null
        val out = FloatArray(HAND_LANDMARK_COUNT * 3)
        for (index in points.indices) {
            val dest = index * 3
            out[dest] = (points[index].x - wrist.x) / scale
            out[dest + 1] = (points[index].y - wrist.y) / scale
            out[dest + 2] = (points[index].z - wrist.z) / scale
        }
        return out
    }

    private fun push(window: ArrayDeque<Float>, value: Float) {
        if (window.size == WINDOW_SIZE) window.removeFirst()
        window.addLast(value)
    }

    private fun ArrayDeque<Float>.averageOrZero(): Float {
        return if (isEmpty()) 0f else sum() / size.toFloat()
    }

    private fun distance(a: LandmarkPoint, b: LandmarkPoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dz = b.z - a.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun meanAbsoluteDifference(a: FloatArray, b: FloatArray): Float {
        val size = minOf(a.size, b.size)
        if (size == 0) return 1f
        var total = 0f
        for (index in 0 until size) total += kotlin.math.abs(a[index] - b[index])
        return total / size.toFloat()
    }

    private fun Float.format3(): String = String.format(java.util.Locale.US, "%.3f", this)

    companion object {
        private const val WINDOW_SIZE = 5
        private const val HAND_LANDMARK_COUNT = 21
        private const val MIN_SCALE = 1.0e-4f
        private const val STATIC_ROUTE_FRAMES = 5
        private const val ONEHAND_ROUTE_FRAMES = 3
        private const val FULLSIGN_ROUTE_FRAMES = 2
        private const val STATIC_MAX_WRIST_MOTION = 0.010f
        private const val STATIC_MAX_SHAPE_MOTION = 0.020f
        private const val DYNAMIC_MIN_WRIST_MOTION = 0.014f
    }
}
