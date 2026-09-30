# Avatar bilingual intent and phrase sequencer — offline v1

Status: **design only**. No production LISTEN, modern app, recognition, or Avatar Trial runtime integration is authorized by this document. `listen_ready=false`.

## Boundaries and contracts

1. Normalize spoken or typed input for case, Unicode normalization, whitespace, punctuation, and language identification without replacing words by animation names. A canonical alias lookup uses `AVATAR_BILINGUAL_ACTION_REGISTRY_V1.json`; ambiguous aliases remain unresolved rather than guessed.
2. Parse an application-level intent with slots. Both `Hi! My name is Anna.` and `Hi! Ako nga pala si Anna.` may yield `GREETING_SELF_INTRO(name="ANNA")`. The name is a slot, not a claim about FSL grammar or a playable action.
3. Resolve intent through a *versioned, FSL-reviewed* phrase plan. No playable plan for `GREETING_SELF_INTRO` exists yet. The conceptual `HELLO -> SELF_INTRO_NAME -> FINGERSPELL(name)` is a review candidate only; `fsl_order_status=PENDING_FSL_REVIEW`. English/Filipino word order must never be copied directly into FSL action order.
4. Validate every referenced runtime action, clip hash, linguistic approval, device approval, and spelling letter before making a playback queue. A plan with any unavailable/uncertified element returns `UNAVAILABLE` plus text/voice fallback; it must not silently omit words or substitute a historical ASL action.

Proposed interfaces (not implemented):

```text
NormalizedIntent { intent_id, slots, locale, source_utterance_id, confidence }
PhrasePlan { plan_id, version, fsl_review_id, fsl_order_status,
             steps: [Action(concept_id) | Fingerspell(slot_name) | Neutral(policy)] }
ResolvedQueue { plan_id, registry_version, asset_sha256, steps: [PlayableStep] }
PlayableStep { runtime_action, duration_ms_from_manifest, neutral_policy,
               min_hold_ms, max_wait_ms, source_step_id }
```

The planner is language-independent after intent extraction. English and Filipino aliases map to the same concept ID and therefore the same clip, never duplicate animations. `RICE` stays unresolved for `kanin`/`bigas` pending semantic review.

## Playback state machine

`IDLE -> RESOLVING -> QUEUED -> PLAYING -> TRANSITION -> PLAYING ... -> COMPLETE`, with `CANCELLED`, `UNAVAILABLE`, and `FAILED` exits. Exactly one active action has a monotonically increasing playback token. Completion/failure callbacks must match that token before the next step can start; stale callbacks after cancellation are ignored. The renderer may be neutralized only by an explicit plan transition policy, not after every letter or word by default. Per-action duration comes from the verified animation manifest; a bounded completion watchdog handles missing callbacks without overlap.

- Queue: immutable snapshot of the validated phrase plan, registry version, asset hash, slot values, and timing. No clip can start before the previous action's callback and required transition finish.
- Neutral transition: `KEEP`, `BLEND_TO_SHARED_NEUTRAL`, or `HOLD_THEN_NEUTRAL`, selected only after visual/FSL review. The current candidate clips show neutral-to-clip pose discontinuity, so a scheduler must not assume a snap-free blend. Do not change the GLB or renderer as part of this offline spec.
- Cancellation: invalidate token, clear pending steps, stop active clip, then return to a verified neutral pose; report cancellation to caller once.
- Replay: rebuild from the immutable validated queue snapshot, not from newly parsed speech or a mutable alias table. Revalidate asset hash and availability first.
- Duplicate suppression: dedupe repeated **source utterance IDs** within a short bounded window, not repeated concepts in an approved phrase or repeated letters such as `N N` in `ANNA`.
- Unknown input: retain original text and locale, surface `UNKNOWN_INTENT`/`UNSUPPORTED_SLOT`, and offer non-sign text/voice fallback. Fingerspell only when validated FSL letter assets exist and the context is a name, OOV/technical term, or explicit spelling request.
- Safety: no unapproved candidate or unvalidated spelling letter may enter a production playback queue even if the clip happens to exist in Avatar Trial.

## Offline acceptance tests before any integration

Normalize the two self-introduction examples to the same intent/slot, but assert `NO_PLAYABLE_FSL_PLAN` today. Test alias collision, unsupported Filipino alias, `RICE` semantic ambiguity, missing clip, missing letter, repeated `ANNA` letters, callback ordering, cancellation during a clip, stale callback, timeout, replay determinism, neutral transition, and duplicate utterance suppression. Require FSL-reviewed plan order and owner/device visual gates before implementation in production LISTEN.
