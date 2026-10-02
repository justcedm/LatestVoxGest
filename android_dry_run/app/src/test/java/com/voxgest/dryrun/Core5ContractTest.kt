package com.voxgest.dryrun
import org.junit.Assert.*
import org.junit.Test
class Core5ContractTest {
    private fun pose()=List(33) { LandmarkPoint(.4f,.4f,0f) }
    private fun hand()=List(21) { LandmarkPoint(.2f+it*.01f,.3f,0f) }
    @Test fun irregularTimestamps() {
        val x=listOf(FloatArray(225),FloatArray(225),FloatArray(225)); x[1][10]=10f; x[2][10]=100f
        assertEquals(50f,Core5Contract.resample(x,listOf(0,10,100),3)[1][10],1e-5f)
    }
    @Test(expected=IllegalArgumentException::class) fun duplicateTimestampRejected() {
        Core5Contract.resample(listOf(FloatArray(225),FloatArray(225)),listOf(1,1))
    }
    @Test fun duplicateHandUnassigned() {
        val f=LandmarkFrame(pose(),hand(),null,1,List(2) { HandObservation("left","left",.3f,"reported",.9f) })
        assertNull(Core5Contract.sanitize(f).leftHandLandmarks)
    }
    @Test fun envelopePreservesFinalHold() {
        val f=(0..20).map { i -> LandmarkFrame(pose(),if(i in 4..15) hand() else null,null,i*100L) }
        assertEquals(3..16,Core5Contract.envelope(f))
    }
    @Test fun noHandsNoEnvelope() { assertNull(Core5Contract.envelope((0..20).map { LandmarkFrame(pose(),null,null,it*100L) })) }
    @Test fun confidentStaticNotAccepted() {
        val f=(0..9).map { LandmarkFrame(pose(),hand(),null,it*100L) }
        assertEquals("LOW_TRAJECTORY_MOTION",Core5Contract.gate(floatArrayOf(.99f,.0025f,.0025f,.0025f,.0025f),
            f,f.map { StandardFullSign225FeatureBuilder.build(it).vector },"MANUAL_END"))
    }
    @Test fun timeoutRejected() {
        assertEquals("EVENT_TIMEOUT",Core5Contract.gate(floatArrayOf(1f,0f,0f,0f,0f),emptyList(),emptyList(),"EVENT_TIMEOUT"))
    }
    @Test fun controlledWindowEndIsNotAnAutomaticTimeout() {
        val frames=(0..9).map { i -> LandmarkFrame(pose(),hand().map { it.copy(x=it.x+i*.015f) },null,i*400L) }
        val vectors=frames.map { StandardFullSign225FeatureBuilder.build(it).vector }
        val result=Core5Contract.gate(floatArrayOf(.99f,.0025f,.0025f,.0025f,.0025f),frames,vectors,"CONTROLLED_WINDOW_END")
        assertNotEquals("EVENT_TIMEOUT",result)
    }
}
