"""Non-deployment scaffold for future FSL rejection/activity experiments.

This module deliberately defines no thresholds. The stable Android quality,
confidence, margin, consistency, and cooldown settings remain authoritative
until a dataset containing NSAC/background activity is evaluated.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass
from enum import Enum
from typing import Iterable


class RejectionExperiment(str, Enum):
    FSL_PLUS_NSAC = "fsl_plus_nsac"
    ACTIVITY_THEN_FSL = "activity_then_fsl"
    ACTIVITY_THEN_FSL_PLUS_REJECT = "activity_then_fsl_plus_reject"


@dataclass(frozen=True)
class RejectionEvidence:
    quality_pass: bool
    classifier_confidence: float
    classifier_margin: float
    temporal_consistency: float
    activity_probability: float | None = None


@dataclass(frozen=True)
class MeasuredThresholds:
    confidence: float
    margin: float
    temporal_consistency: float
    activity: float | None = None
    provenance_report: str = ""

    def __post_init__(self) -> None:
        if not self.provenance_report:
            raise ValueError("thresholds require a measured provenance report")
        for name, value in asdict(self).items():
            if name == "provenance_report" or value is None:
                continue
            if not 0.0 <= float(value) <= 1.0:
                raise ValueError(f"{name} threshold must be in [0,1]")


def accept_with_measured_thresholds(
    evidence: RejectionEvidence,
    thresholds: MeasuredThresholds,
    strategy: RejectionExperiment,
) -> bool:
    """Evaluate offline evidence; callers must supply measured thresholds."""
    if not evidence.quality_pass:
        return False
    classifier_accepts = (
        evidence.classifier_confidence >= thresholds.confidence
        and evidence.classifier_margin >= thresholds.margin
        and evidence.temporal_consistency >= thresholds.temporal_consistency
    )
    if strategy == RejectionExperiment.FSL_PLUS_NSAC:
        return classifier_accepts
    if thresholds.activity is None or evidence.activity_probability is None:
        raise ValueError("activity strategies require measured activity evidence/threshold")
    activity_accepts = evidence.activity_probability >= thresholds.activity
    if strategy == RejectionExperiment.ACTIVITY_THEN_FSL:
        return activity_accepts
    return activity_accepts and classifier_accepts


def supported_experiments() -> Iterable[str]:
    return (strategy.value for strategy in RejectionExperiment)
