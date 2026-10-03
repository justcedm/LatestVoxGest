package com.voxgest.dryrun

/** Samsung-confirmed Tasks boundary. Holistic training already supplies anatomical sides.
 * No image mirror, X flip, dominant-hand sorting, or second swap is allowed here.
 * Only Practical15 (including its isolated A/B adapter) opts into this boundary.
 */
object Practical15TasksAnatomy {
    const val ANALYSIS_MIRRORED = false
    const val POLICY = "TASKS_REPORTED_ANATOMICAL_V1"
    data class Detection(val label: String, val points: List<LandmarkPoint>, val score: Float? = null)

    fun frame(pose: List<LandmarkPoint>?, hands: List<Detection>, timestamp: Long,
        width: Int = 0, height: Int = 0, analysisMirrored: Boolean = false): LandmarkFrame {
        require(!analysisMirrored) { "Practical15 requires unmirrored analysis" }
        var left: List<LandmarkPoint>? = null
        var right: List<LandmarkPoint>? = null
        val observations = hands.map { hand ->
            val slot = when (hand.label.trim().lowercase()) {
                "left" -> { left = hand.points; "left" }
                "right" -> { right = hand.points; "right" }
                else -> "unassigned" // No image-X fallback.
            }
            HandObservation(slot, hand.label.lowercase(), hand.points.map { it.x }.average().toFloat(),
                "policy=$POLICY/analysis_mirrored=false", hand.score)
        }
        return LandmarkFrame(pose, left, right, timestamp, observations, width, height)
    }
}
