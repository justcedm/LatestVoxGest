package com.voxgest.dryrun;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TokenComposer {
    private static final Set<String> NO_OUTPUT_LABELS = new HashSet<>();

    static {
        NO_OUTPUT_LABELS.add("NSAC");
        NO_OUTPUT_LABELS.add("IDLE");
        NO_OUTPUT_LABELS.add("REST");
        NO_OUTPUT_LABELS.add("NO_WORD");
        NO_OUTPUT_LABELS.add("NONE");
    }

    private final Set<String> wordLabels;
    private final List<String> tokens = new ArrayList<>();
    private final List<String> letters = new ArrayList<>();

    /**
     * Immutable point-in-time view of composer state.
     *
     * Sentence suggestion code accepts this type instead of raw recognition labels. That keeps
     * suggestions downstream of composition and prevents them from mutating recognition history.
     */
    public static final class Snapshot {
        private final List<String> tokens;
        private final List<String> pendingLetters;
        private final String sentence;

        private Snapshot(List<String> tokens, List<String> pendingLetters, String sentence) {
            this.tokens = Collections.unmodifiableList(new ArrayList<>(tokens));
            this.pendingLetters = Collections.unmodifiableList(new ArrayList<>(pendingLetters));
            this.sentence = sentence;
        }

        public List<String> getTokens() {
            return tokens;
        }

        public List<String> getPendingLetters() {
            return pendingLetters;
        }

        public String getSentence() {
            return sentence;
        }
    }

    public TokenComposer(Set<String> wordLabels) {
        this.wordLabels = new HashSet<>();
        for (String label : wordLabels) {
            this.wordLabels.add(label.toUpperCase(Locale.US));
        }
    }

    public void clear() {
        tokens.clear();
        letters.clear();
    }

    public String sentence() {
        List<String> parts = new ArrayList<>(tokens);
        if (!letters.isEmpty()) {
            parts.add(joinLetters());
        }
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.length() == 0) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(part);
        }
        return out.toString().trim();
    }

    public Snapshot snapshot() {
        return new Snapshot(tokens, letters, sentence());
    }

    public String strip(int maxChars) {
        String text = sentence();
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(text.length() - maxChars);
    }

    public TokenResult accept(String label) {
        String raw = label == null ? "" : label.trim();
        if (raw.length() == 0) {
            return result(raw, "ignore", false, "", "", "empty");
        }

        String upper = raw.toUpperCase(Locale.US);
        String lower = raw.toLowerCase(Locale.US);

        if (NO_OUTPUT_LABELS.contains(upper)) {
            return result(raw, "noop", false, "", "", "no_output");
        }

        if (upper.length() == 1 && upper.charAt(0) >= 'A' && upper.charAt(0) <= 'Z') {
            letters.add(upper);
            return result(upper, "append_letter", true, upper, upper, "");
        }

        if ("space".equals(lower)) {
            String flushed = flushLetters();
            return result(raw, "space", true, " ", flushed, "");
        }

        if ("del".equals(lower)) {
            String removed = "";
            if (!letters.isEmpty()) {
                removed = letters.remove(letters.size() - 1);
            } else if (!tokens.isEmpty()) {
                removed = tokens.remove(tokens.size() - 1);
            }
            return result(raw, "delete", true, removed, "", "");
        }

        if (wordLabels.contains(upper)) {
            flushLetters();
            tokens.add(upper);
            return result(upper, "append_word", true, upper, upper, "");
        }

        return result(raw, "ignore", false, "", "", "unknown_label");
    }

    /** Accepts an exact label emitted by the parity-gated STANDARD_FSL105 runtime. */
    public TokenResult acceptVerifiedWord(String label) {
        String raw = label == null ? "" : label.trim();
        if (raw.length() == 0) {
            return result(raw, "ignore", false, "", "", "empty");
        }
        String upper = raw.toUpperCase(Locale.US);
        if (NO_OUTPUT_LABELS.contains(upper)) {
            return result(raw, "noop", false, "", "", "no_output");
        }
        if ("DEL".equals(upper) || "SPACE".equals(upper) ||
                "CLEAR".equals(upper) || "SPEAK".equals(upper)) {
            return result(raw, "ignore", false, "", "", "ui_control");
        }
        String flushed = flushLetters();
        tokens.add(raw);
        return result(raw, "append_verified_word", true, raw, flushed, "");
    }

    private TokenResult result(
            String label,
            String action,
            boolean accepted,
            String token,
            String playbackText,
            String reason
    ) {
        return new TokenResult(label, action, accepted, sentence(), token, playbackText, reason);
    }

    private String flushLetters() {
        String word = joinLetters();
        if (word.length() > 0) {
            tokens.add(word);
            letters.clear();
        }
        return word;
    }

    private String joinLetters() {
        StringBuilder out = new StringBuilder();
        for (String letter : letters) {
            out.append(letter);
        }
        return out.toString();
    }
}
