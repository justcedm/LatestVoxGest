package com.voxgest.dryrun;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TokenComposer {
    private static final Set<String> NO_OUTPUT_LABELS = new HashSet<>();

    static {
        NO_OUTPUT_LABELS.add("NOTHING");
        NO_OUTPUT_LABELS.add("IDLE");
        NO_OUTPUT_LABELS.add("REST");
        NO_OUTPUT_LABELS.add("NO_WORD");
        NO_OUTPUT_LABELS.add("NONE");
    }

    private final Set<String> wordLabels;
    private final List<String> tokens = new ArrayList<>();
    private final List<String> letters = new ArrayList<>();

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
