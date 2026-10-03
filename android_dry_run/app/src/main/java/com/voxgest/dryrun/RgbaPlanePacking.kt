package com.voxgest.dryrun

import java.nio.ByteBuffer

/** Preserves RGBA byte order while removing device-specific row/pixel padding. */
object RgbaPlanePacking {
    fun pack(source: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int): ByteBuffer {
        require(width > 0 && height > 0 && pixelStride >= 4)
        require(rowStride.toLong() >= (width - 1L) * pixelStride + 4)
        require(source.limit().toLong() >= (height - 1L) * rowStride + (width - 1L) * pixelStride + 4)
        val packed = ByteBuffer.allocate(Math.multiplyExact(Math.multiplyExact(width, height), 4))
        for (row in 0 until height) for (col in 0 until width) {
            val offset = row * rowStride + col * pixelStride
            repeat(4) { packed.put(source.get(offset + it)) }
        }
        packed.rewind()
        return packed
    }
}
