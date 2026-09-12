package com.voxgest.dryrun;

import java.util.Collections;
import java.util.List;

public final class RecognitionResult {
    public enum Source {
        LEGACY_DEMO,
        STANDARD_FSL105,
        MAPUA14_RESCUE_V1
    }

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
    public final Source source;

    public RecognitionResult(String label, float confidence, float margin, boolean accepted, String note) {
        this(label, confidence, margin, accepted, note, Collections.emptyList(), Source.LEGACY_DEMO);
    }

    public RecognitionResult(
            String label,
            float confidence,
            float margin,
            boolean accepted,
            String note,
            List<TopPrediction> top3
    ) {
        this(label, confidence, margin, accepted, note, top3, Source.LEGACY_DEMO);
    }

    public RecognitionResult(
            String label,
            float confidence,
            float margin,
            boolean accepted,
            String note,
            List<TopPrediction> top3,
            Source source
    ) {
        this.label = label;
        this.confidence = confidence;
        this.margin = margin;
        this.accepted = accepted;
        this.note = note;
        this.top3 = top3 == null ? Collections.emptyList() : Collections.unmodifiableList(top3);
        this.source = source == null ? Source.LEGACY_DEMO : source;
    }

    public static RecognitionResult inactive(String note) {
        return new RecognitionResult("", 0.0f, 0.0f, false, note);
    }
}
