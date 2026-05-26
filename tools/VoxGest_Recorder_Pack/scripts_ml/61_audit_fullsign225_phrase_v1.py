"""Audit merged FullSign225 phrase-v1 feature samples."""

from phrase_v1_dataset_tools import FULLSIGN225_PHRASE_V1, audit_dataset


if __name__ == "__main__":
    audit_dataset(FULLSIGN225_PHRASE_V1)
