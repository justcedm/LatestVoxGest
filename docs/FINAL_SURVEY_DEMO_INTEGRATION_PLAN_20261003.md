# VoxGest Final Survey Demo Integration Plan — 2026-10-03

## Objective

Prepare a single survey-ready VoxGest APK for tomorrow's FSL expert/participant evaluation using only already-engineered capabilities.

The demo must behave like a real product while clearly limiting scope to supported isolated signs and candidate Avatar actions.

## Product Demo Scope

### SIGN tab
Supported recognition vocabulary:
- HELLO
- THANK YOU
- YES
- NO
- UNDERSTAND

Use the qualified CONTROLLED_WINDOW path:
READY -> GET READY -> SIGN NOW -> RECOGNIZING -> RESULT / SIGN AGAIN.

Do not depend on legacy EVENT_TIMEOUT boundary completion.
Do not add new recognition labels or retrain from owner-device captures.

### LISTEN / AVATAR tab
Runtime Action catalog:
Known-good Core3 controls:
- HELLO
- MILK
- RICE

Candidate actions for expert evaluation:
- THANK YOU
- YES
- NO
- IM FINE
- HOW ARE YOU
- UNDERSTAND
- GOOD EVENING
- KNOW
- WRONG

Production view should use upper-body framing. Full-body framing remains diagnostic only.

The candidate actions must remain internally marked as pending FSL/expert validation. The survey may explicitly ask experts to rate/correct them.

## LISTEN interaction

Preferred survey-safe runtime:
Microphone / typed text
-> offline speech recognition
-> strict English/Filipino alias normalization
-> canonical concept ID
-> Avatar action
-> replay / neutral

Only supported exact concepts should trigger an action.

Unsupported speech/text:
- do not guess;
- show "Not supported yet";
- offer Board fallback.

Do not implement unrestricted sentence-to-FSL translation.

## Supported alias strategy

Aliases are semantic input routing only; they are not claims about FSL grammar.

Use conservative mappings, for example:
- HELLO: hello, hi, kumusta only if product owner accepts the semantic difference for demo; otherwise keep hello/hi
- THANK_YOU: thank you, thanks, salamat
- YES: yes, oo
- NO: no, hindi
- IM_FINE: I'm fine, I am fine, ayos lang ako, mabuti naman ako
- HOW_ARE_YOU: how are you, kumusta ka
- UNDERSTAND: understand, I understand, naiintindihan ko
- GOOD_EVENING: good evening, magandang gabi
- KNOW: know, I know, alam ko
- WRONG: wrong, incorrect, mali
- MILK / RICE: keep English-only or already-reviewed aliases unless semantic meaning is confirmed

Avoid broad fuzzy matching that could trigger the wrong sign.

## Survey Mode

Add a lightweight survey/demo indicator or internal configuration, not a developer console.

Recommended expert evaluation flow:
1. Participant tests SIGN recognition.
2. Participant hears/sees output.
3. Participant tests LISTEN/Avatar using supported words.
4. FSL expert rates Avatar motion for candidate actions.
5. Unsupported communication can use BOARD.

Do not hide candidate status from the research team; however the participant UI should remain clean and product-like.

## Final acceptance priorities

P0 — must work:
- app launches;
- SIGN camera opens;
- controlled capture completes;
- five-label recognition path works;
- result can be spoken;
- LISTEN/Avatar loads;
- upper-body Avatar visible;
- all 12 runtime actions enumerate/play;
- Replay/Neutral work;
- BOARD works;
- no crash.

P1 — should work:
- microphone STT for supported concepts;
- English/Filipino exact aliases;
- friendly unsupported-input message;
- expert-rating-friendly action selection.

P2 — defer if risky:
- continuous sign recognition;
- sentence translation;
- A-Z fingerspelling;
- new OOD architecture;
- dynamic camera zoom;
- cosmetic redesign.

## Safety / rollback

Before production modification:
- record current modern APK hash and commit;
- keep Avatar Trial and Recognition Lab installable;
- do not overwrite frozen model binaries;
- do not overwrite the candidate Avatar GLB;
- keep rollback APK.

If integration breaks unrelated tabs, revert rather than redesign.

## Final deliverables

- final survey APK + SHA256
- exact Git commit
- selected recognition model/hash
- Avatar asset hash
- supported recognition list
- supported Avatar list
- survey limitations
- final device smoke-test matrix
- rollback instructions
