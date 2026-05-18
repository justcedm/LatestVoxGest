package com.voxgest.dryrun;

public final class TokenResult {
    public final String label;
    public final String action;
    public final boolean accepted;
    public final String sentence;
    public final String token;
    public final String playbackText;
    public final String reason;

    public TokenResult(
            String label,
            String action,
            boolean accepted,
            String sentence,
            String token,
            String playbackText,
            String reason
    ) {
        this.label = label;
        this.action = action;
        this.accepted = accepted;
        this.sentence = sentence;
        this.token = token;
        this.playbackText = playbackText;
        this.reason = reason;
    }
}
