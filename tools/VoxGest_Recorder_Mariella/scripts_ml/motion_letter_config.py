"""Configuration for dynamic alphabet letters.

J and Z are motion-based fingerspelling letters, so they should not depend on
one static hand frame. Keep this model separate from the word model so letters
still compose as characters, not word tokens.
"""

import os


MOTION_LETTER_SEQ_LEN = int(os.environ.get("VOXGEST_MOTION_LETTER_SEQ_LEN", "30"))
MOTION_LETTER_LABELS = ["J", "Z", "NOTHING"]
MOTION_LETTER_OUTPUTS = {"J", "Z"}
MOTION_LETTER_NEGATIVES = {"NOTHING"}


def normalize_motion_letter(label):
    raw = str(label).strip()
    if len(raw) == 1 and raw.isalpha():
        return raw.upper()
    return raw.upper().replace(" ", "_").replace("-", "_")
