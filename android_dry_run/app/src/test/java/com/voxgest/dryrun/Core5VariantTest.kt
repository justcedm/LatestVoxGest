package com.voxgest.dryrun

import org.junit.Assert.*
import org.junit.Test

class Core5VariantTest {
    @Test fun baselineRemainsDefault() {
        assertEquals(Core5Variant.BASELINE, Core5Variant.fromIntent(null))
        assertEquals("model/fsl_core5_rebase_v1", Core5Variant.fromIntent(null).assetRoot)
    }
    @Test fun candidateHasIndependentProfileAndDirectory() {
        val candidate=Core5Variant.fromIntent("sparse10fps_candidate")
        assertNotEquals(Core5Variant.BASELINE.profileId,candidate.profileId)
        assertNotEquals(Core5Variant.BASELINE.assetRoot,candidate.assetRoot)
        assertEquals("FSL_CORE5_SIM10FPS_V1",candidate.profileId)
    }
    @Test(expected=IllegalStateException::class) fun unknownVariantCannotSilentlySelectBaseline() {
        Core5Variant.fromIntent("typo")
    }
}
