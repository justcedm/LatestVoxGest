"""Shared Filipino Sign Language dataset constants.

`FSL_LABELS` remains the original application-word collection list for backward
compatibility. Training labels are selected by the versioned dataset audit,
not by silently assuming every configured word has usable data.
"""

from voxgest_feature_builder import (
    EXPECTED_SEQUENCE_SHAPE,
    FEATURE_SIZE,
    FEATURE_VERSION,
    SEQUENCE_LENGTH,
)

FSL_LABELS = [
    "WHAT",
    "YOUR",
    "NAME",
    "MY",
    "NOTHING",
    "HELLO",
    "THANKYOU",
    "WATER",
    "EAT",
    "HELP",
    "STOP",
    "YES",
    "NO",
    "PLEASE",
    "SORRY",
    "DOCTOR",
    "SICK",
    "PAIN",
    "BATHROOM",
    "SLEEP",
    "WHO",
    "WHERE",
    "HOW",
    "WHEN",
    "UNDERSTAND",
]

FSL_ALPHABET_LABELS = list("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
FSL_RECORDING_LABELS = list(dict.fromkeys(FSL_LABELS + FSL_ALPHABET_LABELS))

FSL_SEQUENCE_LENGTH = SEQUENCE_LENGTH
FSL_FEATURE_SIZE = FEATURE_SIZE
FSL_FEATURE_SIZE_WITH_DELTA = 225
FSL_EXPECTED_SHAPE = EXPECTED_SEQUENCE_SHAPE
FSL_FEATURE_VERSION = FEATURE_VERSION
FSL_MINIMUMS = {label: 100 for label in FSL_LABELS}
FSL_PREFERRED = {label: 200 for label in FSL_LABELS}
FSL_MINIMUMS["NOTHING"] = 150
FSL_PREFERRED["NOTHING"] = 300
