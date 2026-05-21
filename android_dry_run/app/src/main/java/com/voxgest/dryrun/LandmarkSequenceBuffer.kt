package com.voxgest.dryrun

class LandmarkSequenceBuffer(
    private val sequenceLength: Int = SEQUENCE_LENGTH,
    private val featureSize: Int = FULLSIGN225_FEATURE_SIZE
) {
    private val frames = ArrayDeque<FloatArray>(sequenceLength)

    fun clear() {
        frames.clear()
    }

    fun add(frame: FloatArray): Boolean {
        if (frame.size != featureSize) return false
        if (frames.size == sequenceLength) {
            frames.removeFirst()
        }
        frames.addLast(frame.copyOf())
        return true
    }

    fun isReady(): Boolean = frames.size == sequenceLength

    fun size(): Int = frames.size

    fun snapshot(): Array<FloatArray>? {
        if (!isReady()) return null
        return frames.map { it.copyOf() }.toTypedArray()
    }

    companion object {
        const val SEQUENCE_LENGTH = 30
        const val FULLSIGN225_FEATURE_SIZE = 225
    }
}
