# Published FSL source provenance and local inventory

Timestamp: 2026-09-20 13:42 +08:00

## Scope decision

Only the two published Filipino Sign Language sources authorized by the
offline-work prompt may contribute linguistic training truth:

1. De La Salle University FSL-105 v2.
2. Mapua Transactional Filipino Sign Language.

ASL, WLASL, WHAT/YOUR/NAME/MY phrase experiments, random internet media, and
unvalidated team recordings are quarantined. They remain in the repository
only as historical rollback material and are not selected by any new manifest.

## Mapua source

- Published dataset: Transactional Filipino Sign Language Dataset.
- Provenance family: Mendeley DOI 10.17632/jdyrr7cm4z.
- Original safe-C archive: present.
- Original archive bytes: 590,909,988.
- Original archive SHA-256:
  51333b36e8cca082bc5ecb1b53b0242e1c91d9d00393c2a75e18dce27ceb48de.
- Extracted MP4 inventory: 1,107 clips across 26 labels.
- Video contract: 640x480, 25 fps, 3.0 seconds for all 1,107 clips.
- Signer IDs: not recoverable from published/local metadata.
- Existing raw audit: 670 PASS, 408 REVIEW, 29 REJECT_TECHNICAL.
- Existing canonical cache: 670 PASS-only archives; every selected archive is
  independently hash-checked before a frozen manifest can use it.

The Mapua raw archive and extracted videos are outside Git. No raw media,
features, or absolute private paths are committed.

## FSL-105 source

- Published dataset: FSL-105: A dataset for recognizing 105 Filipino sign
  language videos.
- Contributor: Isaiah Jassen Tupal.
- Institution: De La Salle University, Manila.
- Official version: Mendeley v2, DOI 10.17632/48y2y99mb9.2.
- Official page: https://data.mendeley.com/datasets/48y2y99mb9/2
- Declared contents: 2,130 approximately four-second clips, 105 labels,
  640x360 compressed video, train/test CSVs, and labels CSV.
- Licence: CC BY 4.0.
- Safe-C raw status: NOT FOUND.

The safe-C search covered the named dataset, training, recovery, and Downloads
roots. It found historical labels/splits/features and Standard model assets,
but no official v2 raw MOV tree or archive.

## Acquisition result

Automated acquisition was attempted only against the official Mendeley source:

- official public metadata/API candidates returned HTTP 403;
- retry with a normal browser user agent also returned HTTP 403;
- the official dataset page presented a JavaScript/Cloudflare managed
  challenge to command-line access;
- the in-app official page route did not initialize within two bounded
  attempts and produced no downloadable artifact.

No mirror, scraped copy, ASL dataset, or third-party re-upload was substituted.
This is FSL105_PENDING_RAW, not an assertion that the source is unavailable to
a human browser.

Expected recovery artifact:

- official FSL-105 Mendeley v2 download for DOI 10.17632/48y2y99mb9.2;
- preserve the original download unchanged;
- hash and inventory it before extraction;
- keep it in a new safe-C dataset root outside Git.

## Active contingency

The authorized prompt requires continued work rather than waiting. The active
interim profile therefore uses published Mapua raw video only. The practical
pool contains the 13 mandated fallback concepts plus COIN and DISCOUNT, both
published Mapua retail concepts with materially stronger usable counts than the
weakest available anchors:

HELLO, THANK_YOU, YES, NO, PLEASE, HOW_MUCH, CASH, CARD, RECEIPT, WAIT,
HOW_MANY, AGAIN, PROBLEM, COIN, DISCOUNT.

This is a candidate pool, not yet the frozen vocabulary. Landmark quality,
complete-trajectory quality, leakage-safe pilot CV, and confusion evidence
must pass before the final vocabulary is frozen.
