package com.voxgest.dryrun

import org.junit.Assert.*
import org.junit.Test

class DomainCCaptureTest {
    @Test fun cameraMetadataCannotAlterCanonicalFeatures() {
        val raw = LandmarkFrame(List(33) { LandmarkPoint(it * .01f, .2f, -.3f) },
            List(21) { LandmarkPoint(it * .02f, .4f, .1f) }, null, 42)
        val withDisplay = raw.copy(cameraMetadata = CameraFrameMetadata(42_000_000L, 270, 640, 480,
            null, "FRONT", true))
        assertArrayEquals(StandardFullSign225FeatureBuilder.build(raw).vector,
            StandardFullSign225FeatureBuilder.build(withDisplay).vector, 0f)
    }
    @Test fun eventHashAndContributionFlagsMatchExactCandidate() {
        val raw = LandmarkFrame(List(33) { LandmarkPoint(.1f, .2f, .3f) }, null, null, 1)
        val window = Array(48) { FloatArray(225) { .123f } }
        val candidate = FslPractical15Candidate(1, window, FslPractical15EventQuality(8, 8, 8, 0, 8, 1, 8, .03f))
        val json = DomainCCapture.encode(listOf(raw, raw.copy(timestampMs = 9)), "COMPLETE", candidate)
        assertEquals(DomainCCapture.tensorHash(window), json.getString("canonical_sha256_le_f32"))
        assertTrue(json.getJSONArray("frames").getJSONObject(0).getBoolean("included_in_canonical"))
        assertFalse(json.getJSONArray("frames").getJSONObject(1).getBoolean("included_in_canonical"))
    }
    @Test fun rawLayoutAndMissingPresence() {
        val frame = LandmarkFrame(List(33) { LandmarkPoint(.1f, .2f, -.3f) }, null, null, 42)
        val json = DomainCCapture.encode(listOf(frame), "TEST", null)
        assertFalse(json.getBoolean("pixels_stored"))
        val raw = json.getJSONArray("frames").getJSONObject(0)
        assertEquals(33, raw.getJSONArray("pose").length())
        assertEquals(21, raw.getJSONArray("left").length())
        assertEquals(21, raw.getJSONArray("right").length())
        assertEquals(.1, raw.getJSONArray("pose").getJSONArray(0).getDouble(0), .00001)
        assertFalse(raw.getBoolean("left_present"))
        assertTrue(json.isNull("canonical_sha256_le_f32"))
    }
    @Test fun littleEndianHashIsDeterministicAndDoesNotMutate() {
        val window = Array(48) { FloatArray(225) }
        val before = window.map { it.copyOf() }
        val hash = DomainCCapture.tensorHash(window)
        assertEquals(64, hash.length)
        assertEquals(hash, DomainCCapture.tensorHash(Array(48) { FloatArray(225) }))
        window.indices.forEach { assertArrayEquals(before[it], window[it], 0f) }
        window[0][0] = 1f
        assertNotEquals(hash, DomainCCapture.tensorHash(window))
    }
}
