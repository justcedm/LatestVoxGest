package com.voxgest.dryrun;

import android.content.Context;

import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;

public final class DynamicWordRecognizer {
    private Interpreter interpreter;
    private String[] labels = new String[0];
    private String status = "Dynamic recognizer not loaded";

    public void load(Context context) {
        try {
            TfliteModelLoader loader = new TfliteModelLoader(context);
            interpreter = loader.loadInterpreter(
                    "model/voxgest_tcn_fullsign225_manual5_team_v2.tflite",
                    "voxgest_tcn_fullsign225_manual5_team_v2.tflite",
                    "model/voxgest_tcn_v1.tflite",
                    "voxgest_tcn_v1.tflite",
                    "model/voxgest_lstm_v1.tflite",
                    "voxgest_lstm_v1.tflite"
            );
            labels = loadLabels(
                    context,
                    "model/class_labels_tcn_fullsign225_manual5_team_v2.json",
                    "class_labels_tcn_fullsign225_manual5_team_v2.json",
                    "model/class_labels_tcn_v1.json",
                    "class_labels_tcn_v1.json",
                    "model/class_labels_lstm_v1.json",
                    "class_labels_lstm_v1.json"
            );
            status = labels.length > 0
                    ? "Dynamic word recognizer loaded"
                    : "Dynamic model loaded without label asset";
        } catch (IOException exc) {
            status = "Dynamic recognizer unavailable: " + exc.getMessage();
        }
    }

    public RecognitionResult recognize(float[][] sequence) {
        if (interpreter == null || sequence == null || sequence.length != FeatureExtractor.SEQUENCE_LENGTH) {
            return RecognitionResult.inactive(status);
        }
        if (labels.length == 0) {
            return RecognitionResult.inactive(status);
        }
        int featureSize = sequence[0] == null ? 0 : sequence[0].length;
        if (featureSize != FeatureExtractor.DYNAMIC_FEATURE_SIZE
                && featureSize != FeatureExtractor.FULLSIGN225_FEATURE_SIZE) {
            return RecognitionResult.inactive("Dynamic sequence feature size mismatch");
        }

        float[][][] input = new float[1][FeatureExtractor.SEQUENCE_LENGTH][featureSize];
        for (int i = 0; i < FeatureExtractor.SEQUENCE_LENGTH; i++) {
            if (sequence[i] == null || sequence[i].length != featureSize) {
                return RecognitionResult.inactive("Dynamic sequence shape mismatch");
            }
            System.arraycopy(sequence[i], 0, input[0][i], 0, featureSize);
        }

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
        boolean accepted = confidence >= 0.68f && margin >= 0.18f;
        return new RecognitionResult(labels[best], confidence, margin, accepted, "dynamic_tflite");
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
