package com.voxgest.dryrun;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class FeatureExtractor {
    public static final int STATIC_FEATURE_SIZE = 63;
    public static final int SEQUENCE_LENGTH = 30;
    public static final int DYNAMIC_FEATURE_SIZE = 162;

    private final ArrayDeque<float[]> dynamicFrames = new ArrayDeque<>();

    public void reset() {
        dynamicFrames.clear();
    }

    public void addDynamicFrame(float[] frame) {
        if (frame == null || frame.length != DYNAMIC_FEATURE_SIZE) {
            return;
        }
        if (dynamicFrames.size() == SEQUENCE_LENGTH) {
            dynamicFrames.removeFirst();
        }
        dynamicFrames.addLast(frame);
    }

    public boolean hasDynamicSequence() {
        return dynamicFrames.size() == SEQUENCE_LENGTH;
    }

    public float[][] currentDynamicSequence() {
        float[][] out = new float[SEQUENCE_LENGTH][DYNAMIC_FEATURE_SIZE];
        int index = 0;
        for (float[] frame : dynamicFrames) {
            System.arraycopy(frame, 0, out[index], 0, DYNAMIC_FEATURE_SIZE);
            index++;
        }
        return out;
    }

    public List<float[]> extractFromMediaPipeFrame(Object mediaPipeResult) {
        // Android camera adapters should pass MediaPipe landmark results through
        // the same Python contract: static letters use 63 hand floats, dynamic
        // words use 30 frames of 162 pose+hand floats, and the dominant-hand
        // policy must match the model runtime manifest.
        return new ArrayList<>();
    }
}
