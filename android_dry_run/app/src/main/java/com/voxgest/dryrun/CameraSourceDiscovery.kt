package com.voxgest.dryrun

import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider

enum class CameraSource {
    AUTO,
    FRONT,
    BACK,
    EXTERNAL
}

data class CameraSourceDescriptor(
    val cameraId: String,
    val source: CameraSource
)

data class ResolvedCameraSource internal constructor(
    val requested: CameraSource,
    val resolved: CameraSource,
    val cameraId: String,
    val selector: CameraSelector
) {
    val isFront: Boolean get() = resolved == CameraSource.FRONT
    val previewMirroredByDefault: Boolean get() = isFront
}

class CameraSourceUnavailableException(message: String) : IllegalStateException(message)

/** Pure selection policy, split out so device-independent tests cover fail-closed behavior. */
object CameraSourceSelectionPolicy {
    fun choose(requested: CameraSource, available: List<CameraSourceDescriptor>): CameraSourceDescriptor? {
        if (requested != CameraSource.AUTO) return available.firstOrNull { it.source == requested }
        return listOf(CameraSource.FRONT, CameraSource.BACK, CameraSource.EXTERNAL)
            .firstNotNullOfOrNull { source -> available.firstOrNull { it.source == source } }
    }
}

/**
 * CameraX discovery backed by Camera2 lens-facing characteristics.
 * Unknown-facing cameras are excluded, and EXTERNAL never falls back.
 */
@OptIn(ExperimentalCamera2Interop::class)
object AndroidCameraSourceDiscovery {
    private data class ObservedCamera(
        val info: CameraInfo,
        val descriptor: CameraSourceDescriptor
    )

    fun enumerate(provider: ProcessCameraProvider): List<CameraSourceDescriptor> {
        return observe(provider).map { it.descriptor }
    }

    fun resolve(provider: ProcessCameraProvider, requested: CameraSource): ResolvedCameraSource {
        val observed = observe(provider)
        val chosenDescriptor = CameraSourceSelectionPolicy.choose(
            requested,
            observed.map { it.descriptor }
        ) ?: throw CameraSourceUnavailableException(
            "Camera source $requested unavailable; discovered=${observed.map { it.descriptor }}"
        )
        val selected = observed.first { it.descriptor == chosenDescriptor }
        val selector = CameraSelector.Builder()
            .addCameraFilter { candidates -> candidates.filter { it == selected.info } }
            .build()
        return ResolvedCameraSource(
            requested = requested,
            resolved = chosenDescriptor.source,
            cameraId = chosenDescriptor.cameraId,
            selector = selector
        )
    }

    private fun observe(provider: ProcessCameraProvider): List<ObservedCamera> {
        return provider.availableCameraInfos.mapNotNull { info ->
            val camera2 = Camera2CameraInfo.from(info)
            val source = when (camera2.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> CameraSource.FRONT
                CameraCharacteristics.LENS_FACING_BACK -> CameraSource.BACK
                CameraCharacteristics.LENS_FACING_EXTERNAL -> CameraSource.EXTERNAL
                else -> null
            } ?: return@mapNotNull null
            ObservedCamera(info, CameraSourceDescriptor(camera2.cameraId, source))
        }.sortedWith(compareBy<ObservedCamera> { it.descriptor.source.ordinal }.thenBy { it.descriptor.cameraId })
    }
}

