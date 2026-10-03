package com.voxgest.dryrun

import java.util.Locale

data class StandardFslDiagnostics(
    val posePresent: Boolean,
    val leftHandPresent: Boolean,
    val rightHandPresent: Boolean,
    val bufferFrames: Int,
    val activeProfile: GradingProfileId,
    val top1Label: String?,
    val top1Confidence: Float?,
    val top2Label: String?,
    val top2Margin: Float?,
    val accepted: Boolean,
    val rejectionReason: String?,
    val inferenceLatencyMs: Double?,
    val eventState: StandardFslEventState = StandardFslEventState.IDLE,
    val eventReason: String? = null,
    val activityScore: Float? = null,
    val wristDisplacement: Float? = null,
    val fingertipDisplacement: Float? = null,
    val jointShapeDisplacement: Float? = null,
    val activityTemporalVariance: Float? = null,
    val recentValidFrameRatio: Float? = null,
    val windowDurationMs: Long? = null,
    val oldestFrameAgeMs: Long? = null,
    val medianFrameGapMs: Long? = null,
    val maxFrameGapMs: Long? = null
) {
    init {
        require(bufferFrames in 0..StandardFullSign225Contract.SEQUENCE_LENGTH)
    }

    fun toLogLine(): String = buildString {
        append("pose_present=$posePresent")
        append(" left_hand_present=$leftHandPresent")
        append(" right_hand_present=$rightHandPresent")
        append(" buffer=$bufferFrames/${StandardFullSign225Contract.SEQUENCE_LENGTH}")
        append(" active_profile=${activeProfile.name}")
        append(" top1=${top1Label ?: "<pending>"}")
        append(" confidence=${top1Confidence?.format4() ?: "<pending>"}")
        append(" top2=${top2Label ?: "<pending>"}")
        append(" margin=${top2Margin?.format4() ?: "<pending>"}")
        append(" accepted=$accepted")
        append(" rejection=${rejectionReason ?: "NONE"}")
        append(" latency_ms=${inferenceLatencyMs?.let { String.format(Locale.US, "%.3f", it) } ?: "<pending>"}")
        append(" event_state=${eventState.name}")
        append(" event_reason=${eventReason ?: "NONE"}")
        append(" activity_score=${activityScore?.format4() ?: "<pending>"}")
        append(" wrist_displacement=${wristDisplacement?.format4() ?: "<pending>"}")
        append(" fingertip_displacement=${fingertipDisplacement?.format4() ?: "<pending>"}")
        append(" joint_shape_displacement=${jointShapeDisplacement?.format4() ?: "<pending>"}")
        append(" activity_variance=${activityTemporalVariance?.format4() ?: "<pending>"}")
        append(" recent_valid_ratio=${recentValidFrameRatio?.format4() ?: "<pending>"}")
        append(" WINDOW_DURATION_MS=${windowDurationMs ?: "<pending>"}")
        append(" OLDEST_FRAME_AGE_MS=${oldestFrameAgeMs ?: "<pending>"}")
        append(" MEDIAN_FRAME_GAP_MS=${medianFrameGapMs ?: "<pending>"}")
        append(" MAX_FRAME_GAP_MS=${maxFrameGapMs ?: "<pending>"}")
    }

    private fun Float.format4(): String = String.format(Locale.US, "%.4f", this)
}
