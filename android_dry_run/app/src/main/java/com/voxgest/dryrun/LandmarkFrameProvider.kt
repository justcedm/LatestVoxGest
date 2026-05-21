package com.voxgest.dryrun

interface LandmarkFrameProvider {
    fun start(onFrame: (FloatArray) -> Unit)
    fun stop()
}

class MediaPipeLandmarkFrameProvider : LandmarkFrameProvider {
    override fun start(onFrame: (FloatArray) -> Unit) {
        // Real CameraX + MediaPipe Holistic hookup belongs here.
        // The provider must emit FullSign225 frames: pose 99 + left hand 63 + right hand 63.
    }

    override fun stop() {
    }
}
