package com.voxgest.dryrun;

import android.content.Context;

import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;

public final class StaticLetterRecognizer {
    private Interpreter interpreter;
    private String[] labels = new String[0];
    private String status = "Static recognizer not loaded";

    public void load(Context context) {
        try {
            interpreter = new TfliteModelLoader(context).loadInterpreter(
                    "model/voxgest_v3.tflite",
                    "voxgest_v3.tflite"
            );
            labels = loadLabels(context, "model/class_labels_v3.json", "class_labels_v3.json");
            status = labels.length > 0
                    ? "Static letter recognizer loaded"
                    : "Static model loaded without label asset";
        } catch (IOException exc) {
            status = "Static recognizer unavailable: " + exc.getMessage();
        }
    }

    public RecognitionResult recognize(float[] features63) {
        if (interpreter == null || features63 == null || features63.length != FeatureExtractor.STATIC_FEATURE_SIZE) {
            return RecognitionResult.inactive(status);
        }
        if (labels.length == 0) {
            return RecognitionResult.inactive(status);
        }

        float[][] input = new float[1][FeatureExtractor.STATIC_FEATURE_SIZE];
        System.arraycopy(features63, 0, input[0], 0, FeatureExtractor.STATIC_FEATURE_SIZE);
        float[][] output = new float[1][labels.length];
        interpreter.run(input, output);
        int best = 0;
        int second = 0;
        for (int i = 1; i < output[0].length; i++) {
            if (output[0][i] > output[0][best]) {
                second = best;
                best = i;
            } else if (i != best && output[0][i] > output[0][second]) {
                second = i;
            }
        }
        float confidence = output[0][best];
        float margin = confidence - output[0][second];
        return new RecognitionResult(labels[best], confidence, margin, confidence >= 0.80f, "static_tflite");
    }

    public String status() {
        return status;
    }

    private String[] loadLabels(Context context, String... candidates) throws IOException {
        IOException last = null;
        for (String assetPath : candidates) {
            try {
                String text = readAsset(context, assetPath);
                JSONObject object = new JSONObject(text);
                String[] out = new String[object.length()];
                Iterator<String> keys = object.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    int index = object.getInt(key);
                    if (index >= 0 && index < out.length) {
                        out[index] = key;
                    }
                }
                for (int i = 0; i < out.length; i++) {
                    if (out[i] == null) {
                        out[i] = "";
                    }
                }
                return out;
            } catch (Exception exc) {
                last = exc instanceof IOException ? (IOException) exc : new IOException(exc);
            }
        }
        throw last == null ? new IOException("No label asset candidates supplied") : last;
    }

    private String readAsset(Context context, String assetPath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(assetPath)))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
            }
            return out.toString();
        }
    }
}
