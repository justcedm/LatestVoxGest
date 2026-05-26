"""Shared configuration for endpoint-based phrase-intent recognition."""

import os

PHRASE_SEQ_LEN = int(os.environ.get("VOXGEST_PHRASE_SEQ_LEN", "60"))

PHRASE_OUTPUTS = {
    "ASK_NAME": "What is your name?",
}

# Negative labels are trained by the phrase model but never displayed as text.
PHRASE_NEGATIVE_LABELS = [
    "NOTHING",
    "PARTIAL_ASK_NAME",
]

PHRASE_INTENT_LABELS = list(PHRASE_OUTPUTS.keys())
PHRASE_TRAINING_LABELS = PHRASE_INTENT_LABELS + PHRASE_NEGATIVE_LABELS
PHRASE_RECORDABLE_LABELS = PHRASE_TRAINING_LABELS
PHRASE_REQUIRED_LABELS = [
    "ASK_NAME",
    "NOTHING",
]


def normalize_phrase_label(label):
    return label.strip().upper().replace(" ", "_").replace("-", "_")


def phrase_output_text(label):
    return PHRASE_OUTPUTS.get(label, label)


def is_phrase_intent(label):
    return label in PHRASE_OUTPUTS


def is_phrase_noop(label):
    return label in PHRASE_NEGATIVE_LABELS or label.lower() in {
        "idle",
        "rest",
        "no_word",
        "none",
    }
