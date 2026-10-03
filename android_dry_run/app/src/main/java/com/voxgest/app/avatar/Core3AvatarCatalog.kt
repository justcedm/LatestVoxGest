package com.voxgest.app.avatar

import android.content.Context
import org.json.JSONObject
import java.util.Locale

data class Core3AvatarClip(
    val canonicalLabel: String,
    val runtimeClipName: String,
    val durationSeconds: Float,
    val frames: Int,
    val fps: Int,
    val neutralTransitionFramesEachEnd: Int
)

data class Core3AvatarCatalog(
    val runtimeAssetSha256: String,
    val clips: List<Core3AvatarClip>
) {
    private val clipsByLabel = clips.associateBy { it.canonicalLabel }

    fun resolve(label: String): Core3AvatarClip? =
        clipsByLabel[label.trim().uppercase(Locale.ROOT)]

    val supportedLabels: Set<String>
        get() = clipsByLabel.keys
}

object Core3AvatarAssets {
    const val MODEL_ASSET_PATH = "avatar/core3/voxgest_avatar_B32_CORE3_RC2.glb"
    const val MANIFEST_ASSET_PATH = "avatar/core3/animation_manifest.json"
    const val EXPECTED_MODEL_BYTES = 28_123_308L
    const val EXPECTED_MODEL_SHA256 =
        "30F13FB65E7557992A8C3109460A790A69161E69F3BA9771A1E64A33F98294F8"

    fun loadCatalog(context: Context): Core3AvatarCatalog =
        context.assets.open(MANIFEST_ASSET_PATH).bufferedReader(Charsets.UTF_8).use { reader ->
            Core3AvatarManifestParser.parse(reader.readText())
        }
}

object Core3AvatarManifestParser {
    private val EXPECTED_CLIPS = mapOf(
        "HELLO" to ExpectedClip("FSL_HELLO", 98, 60, 18),
        "MILK" to ExpectedClip("FSL_MILK", 120, 60, 18),
        "RICE" to ExpectedClip("FSL_RICE", 98, 60, 18)
    )

    fun parse(json: String): Core3AvatarCatalog {
        val root = JSONObject(json)
        require(root.getInt("schema_version") == 1) { "Unsupported CORE3 manifest schema" }
        require(root.getString("status") == "VERIFIED_HANDOFF") {
            "CORE3 package is not a verified handoff"
        }
        require(root.getString("main_asset") == "runtime_avatar/voxgest_avatar_B32_CORE3_RC2.glb") {
            "Unexpected CORE3 runtime asset"
        }
        require(root.getString("runtime_format") == "GLB/glTF 2.0") {
            "Unexpected CORE3 runtime format"
        }
        require(root.getString("runtime_root") == "VoxGestAvatar") {
            "Unexpected CORE3 runtime root"
        }
        require(root.getString("skeleton") == "Waitress RIG") {
            "Unexpected CORE3 skeleton"
        }
        require(root.getString("sha256").uppercase(Locale.ROOT) == Core3AvatarAssets.EXPECTED_MODEL_SHA256) {
            "CORE3 manifest asset checksum does not match the frozen package"
        }
        require(!root.getBoolean("expert_fsl_validation")) {
            "CORE3 validation claim changed unexpectedly"
        }

        val clipsJson = root.getJSONArray("clips")
        require(root.getInt("clip_count") == clipsJson.length()) {
            "CORE3 clip count does not match its manifest"
        }
        val clips = List(clipsJson.length()) { index ->
            val clipJson = clipsJson.getJSONObject(index)
            val label = clipJson.getString("canonical_label").uppercase(Locale.ROOT)
            val expected = requireNotNull(EXPECTED_CLIPS[label]) {
                "Unexpected CORE3 clip label: $label"
            }
            require(clipJson.getString("runtime_clip_name") == expected.runtimeClipName) {
                "Unexpected runtime clip name for $label"
            }
            require(clipJson.getInt("frames") == expected.frames) {
                "Unexpected frame count for $label"
            }
            require(clipJson.getInt("fps") == expected.fps) {
                "Unexpected frame rate for $label"
            }
            require(clipJson.getInt("neutral_transition_frames_each_end") == expected.neutralFrames) {
                "Unexpected neutral transition for $label"
            }
            require(!clipJson.getBoolean("loop")) { "$label must remain non-looping" }
            require(clipJson.getBoolean("guide_enabled")) { "$label is not enabled for Guide" }
            require(clipJson.getString("fsl_validation_status") == "NOT_FSL_VALIDATED") {
                "Unexpected linguistic-validation claim for $label"
            }
            require(
                clipJson.getString("runtime_asset_sha256").uppercase(Locale.ROOT) ==
                    Core3AvatarAssets.EXPECTED_MODEL_SHA256
            ) { "$label points to a different runtime asset" }

            Core3AvatarClip(
                canonicalLabel = label,
                runtimeClipName = expected.runtimeClipName,
                durationSeconds = clipJson.getDouble("duration").toFloat(),
                frames = expected.frames,
                fps = expected.fps,
                neutralTransitionFramesEachEnd = expected.neutralFrames
            )
        }

        require(clips.map { it.canonicalLabel }.toSet() == EXPECTED_CLIPS.keys) {
            "CORE3 Guide allowlist must be exactly HELLO, MILK, and RICE"
        }
        require(clips.map { it.runtimeClipName }.distinct().size == clips.size) {
            "CORE3 runtime clip names must be unique"
        }
        return Core3AvatarCatalog(
            runtimeAssetSha256 = Core3AvatarAssets.EXPECTED_MODEL_SHA256,
            clips = clips
        )
    }

    private data class ExpectedClip(
        val runtimeClipName: String,
        val frames: Int,
        val fps: Int,
        val neutralFrames: Int
    )
}
