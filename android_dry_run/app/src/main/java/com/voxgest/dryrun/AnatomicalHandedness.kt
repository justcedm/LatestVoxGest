package com.voxgest.dryrun

/**
 * MediaPipe Hand Landmarker documents handedness as if the input were a
 * mirrored/selfie image. For an unmirrored model frame its reported Left/Right
 * category must therefore be swapped to recover the signer's anatomy.
 *
 * The legacy direct policy is retained for the existing mirrored OneHand162
 * path. Unknown handedness is intentionally not guessed in the standard path.
 */
enum class ReportedHandednessPolicy {
    DIRECT_REPORTED_SIDES,
    SWAP_REPORTED_SIDES_FOR_UNMIRRORED_INPUT
}

enum class AnatomicalHandSide {
    LEFT,
    RIGHT
}

object AnatomicalHandedness {
    fun resolve(
        mediaPipeCategory: String?,
        policy: ReportedHandednessPolicy
    ): AnatomicalHandSide? {
        val reported = when (mediaPipeCategory?.trim()?.lowercase()) {
            "left" -> AnatomicalHandSide.LEFT
            "right" -> AnatomicalHandSide.RIGHT
            else -> return null
        }
        return when (policy) {
            ReportedHandednessPolicy.DIRECT_REPORTED_SIDES -> reported
            ReportedHandednessPolicy.SWAP_REPORTED_SIDES_FOR_UNMIRRORED_INPUT -> when (reported) {
                AnatomicalHandSide.LEFT -> AnatomicalHandSide.RIGHT
                AnatomicalHandSide.RIGHT -> AnatomicalHandSide.LEFT
            }
        }
    }
}
