package com.voxgest.dryrun

/** Explicit debug-only routing. Baseline is always the default. */
enum class Core5Variant(val intentValue: String, val profileId: String, val assetRoot: String) {
    BASELINE("baseline", "FSL_CORE5_REBASE_V1", "model/fsl_core5_rebase_v1"),
    SPARSE10FPS_CANDIDATE(
        "sparse10fps_candidate",
        "FSL_CORE5_SIM10FPS_V1",
        "model/fsl_core5_sparse10fps_candidate_v1"
    );

    companion object {
        fun fromIntent(value: String?): Core5Variant =
            entries.singleOrNull { it.intentValue == (value ?: BASELINE.intentValue) }
                ?: error("Unknown Core5 variant: $value")
    }
}
