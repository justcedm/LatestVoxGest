package com.voxgest.dryrun;

import java.util.Collections;
import java.util.List;

public final class RecognitionResult {
    public static final class TopPrediction {
        public final String label;
        public final float confidence;

        public TopPrediction(String label, float confidence) {
            this.label = label;
            this.confidence = confidence;
        }
    }

    public final String label;
    public final float confidence;
    public final float margin;
    public final boolean accepted;
    public final String note;
    public final List<TopPrediction> top3;

    public RecognitionResult(String label, float confidence, float margin, boolean accepted, String note) {
        this(label, confidence, margin, accepted, note, Collections.emptyList());
    }

    public RecognitionResult(
            String label,
            float confidence,
            float margin,
            boolean accepted,
            String note,
            List<TopPrediction> top3
    ) {
        this.label = label;
        this.confidence = confidence;
        this.margin = margin;
        this.accepted = accepted;
        this.note = note;
        this.top3 = top3 == null ? Collections.emptyList() : Collections.unmodifiableList(top3);
    }

    public static RecognitionResult inactive(String note) {
        return new RecognitionResult("", 0.0f, 0.0f, false, note);
    }
}
