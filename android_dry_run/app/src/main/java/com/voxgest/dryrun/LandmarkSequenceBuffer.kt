package com.voxgest.dryrun

class LandmarkSequenceBuffer(
    private val sequenceLength: Int = SEQUENCE_LENGTH,
    private val featureSize: Int = FULLSIGN225_FEATURE_SIZE
) {
    private val frames = ArrayDeque<FloatArray>(sequenceLength)
    private val handPresence = ArrayDeque<Boolean>(sequenceLength)
    private var stableFrameCount = 0

    fun clear() {
        frames.clear()
        handPresence.clear()
        stableFrameCount = 0
    }

    fun resetStability() {
        stableFrameCount = 0
        frames.clear()
        handPresence.clear()
    }

    fun add(frame: FloatArray): Boolean {
        return add(frame, true)
    }

    fun add(frame: FloatArray, handPresent: Boolean): Boolean {
        if (frame.size != featureSize) return false
        if (frames.size == sequenceLength) {
            frames.removeFirst()
            handPresence.removeFirst()
        }
        frames.addLast(frame.copyOf())
        handPresence.addLast(handPresent)
        return true
    }

    fun markStableFrame(handPresent: Boolean, minStableFrames: Int = STABLE_FRAME_WARMUP): Boolean {
        stableFrameCount = if (handPresent) stableFrameCount + 1 else 0
        if (!handPresent) {
            frames.clear()
            handPresence.clear()
        }
        return stableFrameCount >= minStableFrames
    }

    fun isReady(): Boolean = frames.size == sequenceLength

    fun size(): Int = frames.size

    fun handPresenceRatio(): Float {
        if (handPresence.isEmpty()) return 0f
        return handPresence.count { it }.toFloat() / handPresence.size.toFloat()
    }

    fun snapshot(): Array<FloatArray>? {
        if (!isReady()) return null
        return frames.map { it.copyOf() }.toTypedArray()
    }

    fun snapshotPaddedToSequence(): Array<FloatArray>? {
        if (frames.isEmpty()) return null
        val out = frames.map { it.copyOf() }.toMutableList()
        val last = out.last().copyOf()
        while (out.size < sequenceLength) {
            out.add(last.copyOf())
        }
        return out.take(sequenceLength).toTypedArray()
    }

    companion object {
        const val SEQUENCE_LENGTH = 20
        const val ONEHAND162_FEATURE_SIZE = 162
        const val FULLSIGN225_FEATURE_SIZE = 225
        const val FULLSIGN_WITH_DELTA_FEATURE_SIZE = 225
        const val STABLE_FRAME_WARMUP = 4
    }
}
