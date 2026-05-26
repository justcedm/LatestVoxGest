# Word-Token Phrase Builder

## Why ASK_NAME Was Rejected

`ASK_NAME` is a shortcut phrase label. It asks the model to recognize an entire sentence as one output class.

That is not the right direction for VoxGest now. It makes the model brittle, hides which words were recognized, and does not scale well when phrases share words.

The current design uses actual word tokens instead:

`WHAT + YOUR + NAME` -> `What is your name?`

This is easier to debug, easier to extend, and safer for a defense/demo because the output comes from confirmed component words.

## Recognition Flow

The intended runtime flow is:

`model prediction -> gates -> token composer -> phrase_builder -> output`

Raw model predictions must never update sentence text. Only accepted/gated predictions may enter the token composer and phrase builder.

`NOTHING` remains a no-output class. It is ignored by the phrase builder and must never appear in sentence output.

## Shared Across FullSign225 And OneHand162

The phrase builder is feature-profile agnostic. FullSign225 and OneHand162 can share the same token logic because the builder only sees confirmed labels such as `WHAT`, `IS`, `YOUR`, or static letters.

Supported word profiles:

- `fullsign225_phrase_v1`
- `onehand162_phrase_v1`

Both phrase-v1 profiles intentionally avoid English filler labels such as
`IS`, `ARE`, `A`, and `DO`. The phrase builder adds those words in the final
English sentence after the meaningful accepted sign tokens are complete.

## Static A-Z Name Spelling

Names are handled with the existing static alphabet recognizer after:

`MY + NAME`

Example:

`MY + NAME + J + H + O + N` -> `My name is JHON.`

Because name length is unknown, this phrase is finalized only when the user confirms/speaks. Timeout keeps the partial phrase visible but does not falsely finalize it.

## Current Phrase Targets

1. `WHAT + YOUR + NAME` -> `What is your name?`
2. `YOUR + NAME + WHAT` -> `What is your name?`
3. `MY + NAME + A-Z letters` -> `My name is <spelled name>.`
4. `YOU + STUDENT` or `STUDENT + YOU` -> `Are you a student?`
5. `YOU + OKAY` or `OKAY + YOU` -> `Are you okay?`
6. `WHERE + YOU + LIVE` or `YOU + LIVE + WHERE` -> `Where do you live?`

## Current Limitations

- The phrase builder does not train or classify signs.
- It assumes each token was already accepted by the recognition gates.
- It does not replace the legacy phrase-intent scripts; those remain future/experimental only.
- The new phrase words still require recognition data and live validation before they are demo-safe.
- Partial phrases are shown as partial text, but they are not spoken/finalized until a known pattern is complete or the user confirms.
