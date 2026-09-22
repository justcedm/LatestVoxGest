package com.voxgest.dryrun

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object ImageProxyBitmapConverter {
    fun toUprightBitmap(imageProxy: ImageProxy, mirrorHorizontally: Boolean): Bitmap {
        val bitmap = if (imageProxy.format == PixelFormat.RGBA_8888 && imageProxy.planes.size == 1) {
            rgba8888ToBitmap(imageProxy)
        } else {
            val nv21 = yuv420888ToNv21(imageProxy)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val jpeg = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 78, jpeg)
            val raw = jpeg.toByteArray()
            BitmapFactory.decodeByteArray(raw, 0, raw.size)
        }
        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            if (mirrorHorizontally) {
                postScale(-1f, 1f)
            }
        }
        val transformed = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            false
        )
        if (transformed !== bitmap) bitmap.recycle()
        return transformed
    }

    private fun rgba8888ToBitmap(imageProxy: ImageProxy): Bitmap {
        val bitmap = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
        val buffer = imageProxy.planes[0].buffer.duplicate()
        buffer.rewind()
        val plane = imageProxy.planes[0]
        if (plane.pixelStride == 4 && plane.rowStride == imageProxy.width * 4) {
            bitmap.copyPixelsFromBuffer(buffer)
        } else {
            // CameraX permits row padding. Copy RGBA bytes without assuming packed rows.
            val packed = RgbaPlanePacking.pack(buffer, imageProxy.width, imageProxy.height, plane.rowStride, plane.pixelStride)
            bitmap.copyPixelsFromBuffer(packed)
        }
        return bitmap
    }

    private fun yuv420888ToNv21(imageProxy: ImageProxy): ByteArray {
        val width = imageProxy.width
        val height = imageProxy.height
        val ySize = width * height
        val nv21 = ByteArray(ySize + width * height / 2)

        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]
        val yBuffer = yPlane.buffer.duplicate()
        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()

        var outputOffset = 0
        for (row in 0 until height) {
            val rowStart = row * yPlane.rowStride
            for (col in 0 until width) {
                nv21[outputOffset++] = yBuffer.get(rowStart + col * yPlane.pixelStride)
            }
        }

        val chromaHeight = height / 2
        val chromaWidth = width / 2
        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val vuOffset = ySize + row * width + col * 2
                nv21[vuOffset] = vBuffer.get(row * vPlane.rowStride + col * vPlane.pixelStride)
                nv21[vuOffset + 1] = uBuffer.get(row * uPlane.rowStride + col * uPlane.pixelStride)
            }
        }

        return nv21
    }
}
