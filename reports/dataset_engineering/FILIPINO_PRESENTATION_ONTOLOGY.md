# Filipino Presentation Ontology

`FILIPINO_PRESENTATION_ONTOLOGY.csv` is a review-gated presentation
specification, not a deployed Android change. It contains 117 distinct canonical
concepts: the 105 current FSL-105 source labels plus 12 distinct Mapúa-only
concepts. WELCOME and YOURE WELCOME deliberately remain separate.

The contract is:

`model/source label -> stable CANONICAL_ID -> English/Filipino display`

- `FSL_SOURCE_LABEL` preserves the deployed 105-label text, including existing
  source spelling. It is not rewritten by the display layer.
- `ENGLISH_DISPLAY` and `FILIPINO_DISPLAY` are human-facing glosses. They do not
  relabel training examples or assert word-for-word equivalence with an FSL sign.
- Every row is marked
  `REQUIRES_FILIPINO_LANGUAGE_AND_FSL_PRESENTATION_REVIEW`; none is approved for
  automatic dataset merging or production localization by this audit.
- Multi-token output requires a reviewed composer/grammar policy. Concatenating
  Filipino words does not model FSL grammar, and Filipino grammar must not be
  claimed as FSL grammar.

The requested seed examples appear as HELLO → Hello/Kumusta, MILK → Milk/Gatas,
YES → Yes/Oo, and NO → No/Hindi. These examples remain review-gated with the
rest of the table. No Listen, Avatar, UI, model, runtime label, or Android source
was changed.
