package com.voxgest.dryrun

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class StandardFslArtifactGateTest {
    private val hash = "a".repeat(64)
    private val labels = List(105) { index -> "LABEL_$index" }

    @Test
    fun acceptsExactCanonicalTrainerBundleSchema() {
        val validated = StandardFslArtifactGate.validateContract(
            validManifest(),
            StandardFslLabelsMetadata("fullsign225_20f_v1", 105, labels),
            hash
        )

        assertEquals(labels, validated)
    }

    @Test
    fun rejectsWrongShapeOrientationHashLabelsAndDesktopParity() {
        expectRejected { validManifest().copy(inputShape = intArrayOf(1, 20, 162)) }
        expectRejected { validManifest().copy(modelInputOrientation = "mirrored") }
        expectRejected { validManifest().copy(modelSha256 = "b".repeat(64)) }
        expectRejected { validManifest().copy(desktopParityStatus = "FAIL") }
        expectRejected { validManifest().copy(classOrder = labels.reversed()) }
        try {
            StandardFslArtifactGate.validateContract(
                validManifest(),
                StandardFslLabelsMetadata("fullsign225_20f_v1", 105, labels.dropLast(1)),
                hash
            )
            fail("Expected labels rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun expectRejected(change: () -> StandardFslArtifactMetadata) {
        try {
            StandardFslArtifactGate.validateContract(
                change(),
                StandardFslLabelsMetadata("fullsign225_20f_v1", 105, labels),
                hash
            )
            fail("Expected contract rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun validManifest() = StandardFslArtifactMetadata(
        activeProfile = "STANDARD_FSL_FULLSIGN225",
        featureVersion = "fullsign225_20f_v1",
        inputShape = intArrayOf(1, 20, 225),
        outputShape = intArrayOf(1, 105),
        classCount = 105,
        inputDtype = "float32",
        outputDtype = "float32",
        modelInputOrientation = "unmirrored",
        handSlotPolicy = "pose99_then_anatomical_left63_then_anatomical_right63_never_swapped",
        hasNegativeOrNothingClass = false,
        rejectionRequired = true,
        androidDefaultChanged = false,
        poseRange = intArrayOf(0, 99),
        leftHandRange = intArrayOf(99, 162),
        rightHandRange = intArrayOf(162, 225),
        normalizationInputOrientation = "unmirrored_source_frame",
        modelFilename = "voxgest_fsl_fullsign225_105_float32.tflite",
        labelsFilename = "class_labels_fsl105_fullsign225_v1.json",
        modelSha256 = hash,
        desktopParityStatus = "PASS",
        classOrder = labels
    )
}
