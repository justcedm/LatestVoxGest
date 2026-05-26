"""Audit merged OneHand162 phrase-v1 feature samples."""

from phrase_v1_dataset_tools import ONEHAND162_PHRASE_V1, audit_dataset


if __name__ == "__main__":
    audit_dataset(ONEHAND162_PHRASE_V1)
