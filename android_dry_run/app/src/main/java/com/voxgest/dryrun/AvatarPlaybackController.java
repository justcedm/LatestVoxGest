package com.voxgest.dryrun;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AvatarPlaybackController {
    public interface Listener {
        void onAvatarMessage(String title, String detail);
    }

    private final Listener listener;

    public AvatarPlaybackController(Context context, Listener listener) {
        this.listener = listener;
    }

    public void handleTokenResult(TokenResult result) {
        if (result == null || !result.accepted) {
            return;
        }
        if ("delete".equals(result.action)) {
            show("Avatar idle", "Delete does not trigger animation.");
            return;
        }
        String playback = result.playbackText == null ? "" : result.playbackText.trim();
        if (playback.length() == 0) {
            return;
        }
        playText(playback);
    }

    public void playText(String text) {
        if (text == null) {
            return;
        }
        String cleaned = text.trim();
        if (cleaned.length() == 0) {
            return;
        }
        String upper = cleaned.toUpperCase(Locale.US);
        if ("NOTHING".equals(upper)) {
            return;
        }
        if (VoxGestContract.WORD_SET.contains(upper)) {
            playKnownWord(upper);
        } else {
            fingerspell(upper);
        }
    }

    public void playSpeechText(String text) {
        List<String> playback = new ArrayList<>();
        for (String token : text.split("[^A-Za-z]+")) {
            if (token.trim().length() == 0) {
                continue;
            }
            String upper = token.toUpperCase(Locale.US);
            playback.add(upper);
            playText(upper);
        }
        if (playback.isEmpty()) {
            show("Avatar idle", "No spoken words recognized.");
        }
    }

    private void playKnownWord(String word) {
        show("Playing sign: " + word, "Using built-in Canvas avatar clip.");
    }

    private void fingerspell(String word) {
        StringBuilder letters = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                if (letters.length() > 0) {
                    letters.append(' ');
                }
                letters.append(ch);
            }
        }
        if (letters.length() == 0) {
            show("Avatar idle", "No fingerspellable characters.");
        } else {
            show("Fingerspell: " + word, letters.toString());
        }
    }

    private void show(String title, String detail) {
        if (listener != null) {
            listener.onAvatarMessage(title, detail);
        }
    }
}
