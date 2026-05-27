package com.voxgest.dryrun;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AvatarPlaybackController {
    public interface Listener {
        void onAvatarMessage(String title, String detail);
    }

    private final AssetManager assets;
    private final Listener listener;

    public AvatarPlaybackController(Context context, Listener listener) {
        this.assets = context.getAssets();
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
        String json = readFirstAvailable(
                "avatar/signs/" + word + ".json",
                "signs/" + word + ".json"
        );
        if (json == null || json.contains("\"keyframes\": []")) {
            show("Playing sign: " + word, "Placeholder animation card. Add avatar/signs/" + word + ".json keyframes for final playback.");
        } else {
            show("Playing sign: " + word, "Loaded animation JSON for " + word + ".");
        }
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

    private String readFirstAvailable(String... paths) {
        for (String path : paths) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(assets.open(path)))) {
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
                return out.toString();
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private void show(String title, String detail) {
        if (listener != null) {
            listener.onAvatarMessage(title, detail);
        }
    }
}
