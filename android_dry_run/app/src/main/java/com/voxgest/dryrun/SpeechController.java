package com.voxgest.dryrun;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.Locale;

public final class SpeechController {
    public interface Listener {
        void onSpeechText(String text);

        default void onPartialSpeechText(String text) {
        }

        void onSpeechStatus(String status);
    }

    private final Context context;
    private final Listener listener;
    private TextToSpeech textToSpeech;
    private SpeechRecognizer recognizer;

    public SpeechController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        textToSpeech = new TextToSpeech(this.context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                notifyStatus("TextToSpeech ready");
            } else {
                notifyStatus("TextToSpeech unavailable");
            }
        });
    }

    public boolean speechRecognitionAvailable() {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    public void speak(String text) {
        if (textToSpeech == null || text == null || text.trim().length() == 0) {
            return;
        }
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voxgest-speak");
    }

    public void listenOnce() {
        if (!speechRecognitionAvailable()) {
            notifyStatus("Android SpeechRecognizer unavailable on this device.");
            return;
        }
        if (recognizer != null) {
            recognizer.destroy();
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                notifyStatus("Listening...");
            }

            @Override
            public void onBeginningOfSpeech() {
                notifyStatus("Speech started");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                notifyStatus("Processing speech...");
            }

            @Override
            public void onError(int error) {
                notifyStatus("Speech recognition error: " + error);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches == null || matches.isEmpty()) {
                    notifyStatus("No speech recognized");
                    return;
                }
                String text = matches.get(0);
                if (listener != null) {
                    listener.onSpeechText(text);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty() && listener != null) {
                    listener.onPartialSpeechText(matches.get(0));
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizer.startListening(intent);
    }

    public void shutdown() {
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        if (textToSpeech != null) {
            textToSpeech.shutdown();
            textToSpeech = null;
        }
    }

    private void notifyStatus(String status) {
        if (listener != null) {
            listener.onSpeechStatus(status);
        }
    }
}
