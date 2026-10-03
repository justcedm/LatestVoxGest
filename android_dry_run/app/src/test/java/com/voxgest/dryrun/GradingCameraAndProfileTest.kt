package com.voxgest.dryrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradingCameraAndProfileTest {
    @Test
    fun frontCameraIsDefaultWhileStandardPreviewAndAnalysisMirroringStaySeparate() {
        assertEquals(GradingCameraLens.FRONT, GradingCameraPolicy.DEFAULT_LENS)
        val policy = GradingCameraPolicy.framePolicy(
            GradingRecognitionProfiles.STANDARD_FSL_FULLSIGN225
        )

        assertTrue(policy.previewMirrorAllowed)
        assertFalse(policy.analysisMirrorHorizontally)
        assertEquals(
            ReportedHandednessPolicy.SWAP_REPORTED_SIDES_FOR_UNMIRRORED_INPUT,
            policy.handednessPolicy
        )
    }

    @Test
    fun unmirroredMediaPipeCategoryIsCorrectedExactlyOnceToAnatomicalSide() {
        val policy = ReportedHandednessPolicy.SWAP_REPORTED_SIDES_FOR_UNMIRRORED_INPUT
        assertEquals(AnatomicalHandSide.RIGHT, AnatomicalHandedness.resolve("Left", policy))
        assertEquals(AnatomicalHandSide.LEFT, AnatomicalHandedness.resolve("Right", policy))
        assertNull(AnatomicalHandedness.resolve("unknown", policy))
        assertNull(AnatomicalHandedness.resolve(null, policy))
    }

    @Test
    fun oneHand16264PreviewAndAnalysisOrientationAreIndependent() {
        val policy = GradingCameraPolicy.framePolicy(
            GradingRecognitionProfiles.ACCESSIBLE_ONEHAND162
        )
        assertTrue(policy.previewMirrorAllowed)
        assertFalse(policy.analysisMirrorHorizontally)
        assertEquals(
            ModelInputOrientation.UNMIRRORED,
            GradingRecognitionProfiles.ACCESSIBLE_ONEHAND162.modelInputOrientation
        )
        assertEquals(
            ReportedHandednessPolicy.SWAP_REPORTED_SIDES_FOR_UNMIRRORED_INPUT,
            policy.handednessPolicy
        )
        assertEquals(
            AnatomicalHandSide.RIGHT,
            AnatomicalHandedness.resolve("Left", policy.handednessPolicy)
        )
    }

    @Test
    fun both64ClassAndFullSign225ModelInputsStayUnmirrored() {
        val profiles = listOf(
            GradingRecognitionProfiles.ACCESSIBLE_ONEHAND162,
            GradingRecognitionProfiles.STANDARD_FSL_FULLSIGN225
        )
        profiles.forEach { profile ->
            val policy = GradingCameraPolicy.framePolicy(profile, GradingCameraLens.FRONT)
            assertEquals(ModelInputOrientation.UNMIRRORED, profile.modelInputOrientation)
            assertTrue(policy.previewMirrorAllowed)
            assertFalse(policy.analysisMirrorHorizontally)
            assertEquals(
                ReportedHandednessPolicy.SWAP_REPORTED_SIDES_FOR_UNMIRRORED_INPUT,
                policy.handednessPolicy
            )
        }
    }

    @Test
    fun previewOverlayMirrorsXOnlyWhenDisplayAndAnalysisOrientationsDiffer() {
        assertEquals(0.8f, PreviewOverlayMapper.xForPreview(0.2f, false, true), 0f)
        assertEquals(0.2f, PreviewOverlayMapper.xForPreview(0.2f, true, true), 0f)
        assertEquals(0.2f, PreviewOverlayMapper.xForPreview(0.2f, false, false), 0f)
    }

    @Test
    fun profilesHaveDistinctAssetsContractsAndExactShapes() {
        val standard = GradingRecognitionProfiles.STANDARD_FSL_FULLSIGN225
        val accessible = GradingRecognitionProfiles.ACCESSIBLE_ONEHAND162

        assertEquals("STANDARD_FSL_FULLSIGN225", standard.id.name)
        assertEquals("ACCESSIBLE_ONEHAND162", accessible.id.name)
        assertTrue(standard.inputShape.contentEquals(intArrayOf(1, 20, 225)))
        assertTrue(standard.outputShape.contentEquals(intArrayOf(1, 105)))
        assertTrue(accessible.inputShape.contentEquals(intArrayOf(1, 20, 162)))
        assertTrue(accessible.outputShape.contentEquals(intArrayOf(1, 64)))
        assertTrue(standard.modelAsset.contains("fsl_fullsign225_20f_105_v1"))
        assertTrue(accessible.modelAsset.contains("fsl_onehand162_20f_rdtcn_v2"))
        assertFalse(standard.modelAsset == accessible.modelAsset)
        assertFalse(standard.modelAsset.contains("fullsign225_phrase_v1"))
    }

    @Test
    fun blockedStandardSelectionNeverSilentlyBecomesOneHand() {
        val blocked = GradingProfileActivationGate.standard(
            StandardFslArtifactState.BLOCKED,
            featureParityPassed = true,
            tfliteParityPassed = false
        )
        assertFalse(blocked.canActivate)
        assertNull(blocked.activeProfile)

        val fallback = GradingProfileActivationGate.accessibleOneHand(existingParityPassed = true)
        assertTrue(fallback.canActivate)
        assertEquals(GradingProfileId.ACCESSIBLE_ONEHAND162, fallback.activeProfile)
    }
}
