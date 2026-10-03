"""Shared Filipino Sign Language dataset constants for future VoxGest retraining."""

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

FSL_SEQUENCE_LENGTH = 20
FSL_FEATURE_SIZE = 162
FSL_FEATURE_SIZE_WITH_DELTA = 225
FSL_EXPECTED_SHAPE = (FSL_SEQUENCE_LENGTH, FSL_FEATURE_SIZE)
FSL_MINIMUMS = {label: 100 for label in FSL_LABELS}
FSL_PREFERRED = {label: 200 for label in FSL_LABELS}
FSL_MINIMUMS["NOTHING"] = 150
FSL_PREFERRED["NOTHING"] = 300
