package com.voxgest.dryrun;

public final class RecognitionResult {
    public final String label;
    public final float confidence;
    public final float margin;
    public final boolean accepted;
    public final String note;

    public RecognitionResult(String label, float confidence, float margin, boolean accepted, String note) {
        this.label = label;
        this.confidence = confidence;
        this.margin = margin;
        this.accepted = accepted;
        this.note = note;
    }

    public static RecognitionResult inactive(String note) {
        return new RecognitionResult("", 0.0f, 0.0f, false, note);
    }
}
