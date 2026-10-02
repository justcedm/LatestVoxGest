"""Deadline diagnostics must not ingest old/manual events or protected roots."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts_ml"))
from core5_deadline_replay import validated_private_root, validate_event  # noqa: E402


class DeadlineReplayGuardTest(unittest.TestCase):
    def test_rejects_manual_event_before_replay(self):
        event = {"event_id": "4a59077b-40f4-49f3-b79a-23c742696ee8",
                 "expected_test_label": "HELLO", "schema": "core5_event_v1",
                 "boundary_mode": "MANUAL"}
        with self.assertRaisesRegex(ValueError, "controlled-window"):
            validate_event(event, event["event_id"], "HELLO")

    def test_rejects_devset_and_historical_roots(self):
        for path in ("D:/VoxGest/evidence/core5_devset_v1",
                     "D:/VoxGest/evidence/fsl_core5_rebase_v1"):
            with self.assertRaisesRegex(ValueError, "must not overlap"):
                validated_private_root(Path(path))

    def test_allows_separate_deadline_diagnostic_root(self):
        path = Path("D:/VoxGest/evidence/core5_deadline_recovery_20261003")
        self.assertEqual(validated_private_root(path), path.resolve())


if __name__ == "__main__":
    unittest.main()
