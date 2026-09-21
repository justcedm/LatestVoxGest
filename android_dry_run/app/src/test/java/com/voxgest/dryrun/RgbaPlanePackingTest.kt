package com.voxgest.dryrun

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class RgbaPlanePackingTest {
    @Test fun paddedRowsKeepBytesAndSourcePosition() {
        val source = ByteBuffer.wrap(byteArrayOf(1,2,3,4,5,6,7,8,99,99,99,99,9,10,11,12,13,14,15,16))
        source.position(3)
        val result = RgbaPlanePacking.pack(source, 2, 2, 12, 4)
        assertArrayEquals(ByteArray(16) { (it + 1).toByte() }, result.array())
        assertEquals(3, source.position())
    }
    @Test fun pixelPaddingIsRemoved() {
        val source = ByteBuffer.wrap(byteArrayOf(1,2,3,4,99,99,5,6,7,8))
        assertArrayEquals(byteArrayOf(1,2,3,4,5,6,7,8), RgbaPlanePacking.pack(source, 2, 1, 10, 6).array())
    }
    @Test(expected = IllegalArgumentException::class) fun truncatedPlaneFailsClosed() {
        RgbaPlanePacking.pack(ByteBuffer.allocate(7), 2, 1, 8, 4)
    }
}
