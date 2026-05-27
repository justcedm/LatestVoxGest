package com.voxgest.app.avatar

import com.google.android.filament.gltfio.FilamentAsset

object MixamoAvatarRig {
    val boneAliases: Map<String, List<String>> = mapOf(
        "hips" to listOf("mixamorig:Hips", "Hips"),
        "spine" to listOf("mixamorig:Spine", "Spine"),
        "chest" to listOf("mixamorig:Spine2", "mixamorig:Spine1", "mixamorig:Chest", "Spine2", "Spine1", "Chest"),
        "neck" to listOf("mixamorig:Neck", "Neck"),
        "head" to listOf("mixamorig:Head", "Head"),
        "leftShoulder" to listOf("mixamorig:LeftShoulder", "LeftShoulder"),
        "leftUpperArm" to listOf("mixamorig:LeftArm", "LeftArm"),
        "leftForearm" to listOf("mixamorig:LeftForeArm", "mixamorig:LeftForearm", "LeftForeArm", "LeftForearm"),
        "leftHand" to listOf("mixamorig:LeftHand", "LeftHand"),
        "rightShoulder" to listOf("mixamorig:RightShoulder", "RightShoulder"),
        "rightUpperArm" to listOf("mixamorig:RightArm", "RightArm"),
        "rightForearm" to listOf("mixamorig:RightForeArm", "mixamorig:RightForearm", "RightForeArm", "RightForearm"),
        "rightHand" to listOf("mixamorig:RightHand", "RightHand")
    )

    fun findBone(asset: FilamentAsset?, logicalName: String): Int {
        if (asset == null) return 0
        val aliases = boneAliases[logicalName].orEmpty()
        for (name in aliases) {
            val entity = asset.getFirstEntityByName(name)
            if (entity != 0) return entity
        }
        return 0
    }

    fun describeResolvedBones(asset: FilamentAsset?): String {
        if (asset == null) return "no_asset"
        return boneAliases.keys.joinToString(prefix = "resolved bones: ") { key ->
            val found = findBone(asset, key) != 0
            "$key=${if (found) "yes" else "no"}"
        }
    }
}
