package com.voxgest.dryrun

enum class GradingProfileActivationState {
    READY,
    BLOCKED
}

data class GradingProfileActivationDecision(
    val requestedProfile: GradingProfileId,
    val activeProfile: GradingProfileId?,
    val state: GradingProfileActivationState,
    val evidence: String
) {
    val canActivate: Boolean get() = state == GradingProfileActivationState.READY
}

/**
 * Explicit selection boundary. A blocked Standard request never silently loads
 * the OneHand model under the Standard profile; fallback is a separate request.
 */
object GradingProfileActivationGate {
    fun standard(
        artifactState: StandardFslArtifactState,
        featureParityPassed: Boolean,
        tfliteParityPassed: Boolean
    ): GradingProfileActivationDecision {
        val ready = artifactState == StandardFslArtifactState.READY_FOR_NUMERIC_PARITY &&
            featureParityPassed && tfliteParityPassed
        return GradingProfileActivationDecision(
            requestedProfile = GradingProfileId.STANDARD_FSL_FULLSIGN225,
            activeProfile = if (ready) GradingProfileId.STANDARD_FSL_FULLSIGN225 else null,
            state = if (ready) GradingProfileActivationState.READY else GradingProfileActivationState.BLOCKED,
            evidence = "artifacts=$artifactState feature_parity=$featureParityPassed tflite_parity=$tfliteParityPassed"
        )
    }

    fun accessibleOneHand(existingParityPassed: Boolean): GradingProfileActivationDecision {
        return GradingProfileActivationDecision(
            requestedProfile = GradingProfileId.ACCESSIBLE_ONEHAND162,
            activeProfile = if (existingParityPassed) GradingProfileId.ACCESSIBLE_ONEHAND162 else null,
            state = if (existingParityPassed) {
                GradingProfileActivationState.READY
            } else {
                GradingProfileActivationState.BLOCKED
            },
            evidence = "existing_onehand64_numeric_parity=$existingParityPassed"
        )
    }
}
