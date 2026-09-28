# VoxGest Modern UI Recovery + Next Recognition Integration — 2026-09-28

## Status

The previously verified modern VoxGest UI has been restored on the Samsung device using the preserved rollback APK:

```text
D:\VoxGest\evidence\fsl_core5_rebase_v1\rollback_ui_v2_6354700532f8.apk
```

The restored user-facing design is the preferred UI baseline for the next integration phase.

Expected modern navigation:

```text
SIGN | CONVERSATION | BOARD | LISTEN | GUIDE
```

The restored UI must now be treated as the visual/product baseline. Do not intentionally regress to the older four-tab UI.

## Critical recognition note

The restored modern UI APK and the previously validated Core5/SIM10 diagnostic APK are not assumed to be the same binary.

Therefore:

- successful prior Core5 diagnostic evidence remains valid as diagnostic evidence;
- the restored modern UI must not automatically be claimed to contain the same validated recognition runtime/profile;
- any recognition attempt made in the restored UI is a smoke test until the embedded model/profile/runtime identity is verified.

Previous preserved recognition findings include:

- HELLO: five timely manually-ended Samsung events were raw/accepted HELLO;
- four additional HELLO timeout events were still raw top-1 HELLO;
- all nine saved HELLO tensors replayed as HELLO in Baseline, Native48 and SIM10;
- UNDERSTAND: three automatic trials auto-released and were raw/accepted correct in the unchanged-boundary battery;
- NO: saved tensors were classifier-correct in Native48/SIM10 but automatic trials timed out;
- YES: same-tensor Samsung comparison showed SIM10 weaker than Baseline/Native48 on the saved YES sample;
- non-sign rejection remains unsafe under the unchanged gate;
- model selection alone does not solve rejection or automatic event ending.

## Immediate smoke-test policy

It is safe to try the restored modern UI on the physical Samsung as a smoke test.

Recommended order:

1. UNDERSTAND
2. HELLO
3. NO
4. THANK YOU
5. YES

For each attempt record only what the visible app does:

```text
WORD=
CAMERA_STARTED=YES/NO
LANDMARKS_VISIBLE=YES/NO/UNKNOWN
RAW_PREDICTION_VISIBLE=YES/NO/NOT_EXPOSED
ACCEPTED_OUTPUT=
TIMEOUT_OR_NO_OUTPUT=YES/NO
MESSAGE/TTS=
```

Do not interpret a failure in the restored UI as proof that the previously tested classifier failed. The restored UI binary may contain a different recognition route.

## Next Codex/Astra integration task

Create one controlled survey candidate containing:

```text
MODERN FIVE-TAB UI
+
CURRENT QUALIFIED CORE5 RECOGNITION PATH
+
BOARD
+
LISTEN/AVATAR FALLBACK
```

Preferred integration rule:

- start from the source state that produced the restored modern UI;
- identify exact package/model/profile identities before merging;
- bring in only the required Core5 runtime/integration pieces;
- preserve the modern UI and Board;
- do not retrain during integration;
- verify on Samsung;
- record APK SHA256, model/profile hashes, branch, HEAD, tests, and rollback path.

## UI baseline requirements

The integrated build must preserve:

- modern VoxGest visual language;
- SIGN page;
- CONVERSATION page;
- BOARD page;
- LISTEN page;
- GUIDE page;
- Board drawing functionality;
- existing appearance work;
- no duplicate Start controls;
- responsive layout work when added.

## Recognition priority

Core5 remains the survey priority:

```text
HELLO
THANK YOU
YES
NO
UNDERSTAND
```

Do not expand to 10–20 production-qualified words until:

- the five-word user-facing route is verified;
- automatic event ending is reliable enough for survey use;
- negative/OOD rejection is acceptable;
- the final survey APK is frozen and documented.

## GitHub documentation rule

Every new milestone must record:

- branch/worktree;
- HEAD;
- APK SHA256;
- model/profile identity;
- files changed;
- tests;
- physical Samsung result;
- evidence paths;
- rollback;
- known limitations;
- exact next action.

Private Samsung captures/evidence should remain outside Git; reports may reference local evidence paths.

## Next exact action

Run a short smoke test in the restored modern UI using UNDERSTAND then HELLO. Preserve the visible behavior. When Codex/Astra resumes, first verify the restored APK/runtime identity before integrating the qualified Core5 path into this modern UI baseline.
