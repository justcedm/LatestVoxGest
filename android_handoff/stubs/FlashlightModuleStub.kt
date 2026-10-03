package com.voxgest.handoff.pending

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager

/**
 * Handoff-only torch boundary. This is not wired into the VoxGest application.
 *
 * A future ambient-lux policy should decide *when* to request a change; this
 * class only performs the explicit CameraManager operation. The integrator is
 * responsible for checking flash availability and respecting camera lifecycle.
 */
class FlashlightModuleStub(
    private val cameraManager: CameraManager,
) {
    @Throws(CameraAccessException::class, IllegalArgumentException::class)
    fun setTorchEnabled(cameraId: String, enabled: Boolean) {
        require(cameraId.isNotBlank()) { "cameraId must not be blank" }
        cameraManager.setTorchMode(cameraId, enabled)
    }
}
