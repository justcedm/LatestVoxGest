# Experimental next-word repair

These scripts reproduce the local source-fit review candidates; they are not the frozen B32 solver
and do not certify motion, source provenance, linguistic accuracy, export or device readiness.
Run from LOCAL_ROOT in reports/ASTRA_LIVE_HANDOFF.md, not the repository checkout.
They consume existing private working files under LOCAL_ROOT/work and write only versioned local
review outputs. Do not change input paths to the purchased source or commit private outputs.

Order:
1. Blender 3.2.0 background, factory startup, autoexec disabled: extract.py.
2. System Python with NumPy/SciPy: fit_continuous.py IM_FINE; fit_continuous.py UNDERSTAND.
3. System Python: fit_source_shape.py HOW_ARE_YOU.
4. Blender background: build_IM_FINE_v6.py, build_HOW_ARE_YOU_v6.py, build_UNDERSTAND_v6.py.
5. Blender background: fix_transitions.py, then package_v7.py.

Every Blender script needs --python-exit-code 1. No script reads historical drive paths embedded
in source metadata. Keep all inputs and outputs on approved C: storage.

The fitting stage uses a 3-frame grid, temporal regularization and trust-region bounds with
interpolation at original frame times. The bounds are engineering constraints, not validated
anatomical ranges. HOW ARE YOU uses a source-observed folded ring/pinky prior. Arm placement uses
visible projection plus fixed limb lengths and a median depth-direction cue. UNDERSTAND aligns
its index tip relative to source/model eyes. Native controls remain muted in the working copy.
The untracked entry/exit intervals use a fixed 24-frame neutral blend; full source matching there
remains open. No time compression is applied. The review playlist adds neutral gaps only between
unchanged-length master candidates. See the handoff for measured results and open gates.
