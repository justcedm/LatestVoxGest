package com.voxgest.app.avatar

enum class AvatarAnimationKind {
    KNOWN_SIGN,
    FINGERSPELL,
    IDLE,
    NONE
}

data class AvatarAnimation(
    val word: String,
    val kind: AvatarAnimationKind,
    val durationMs: Long,
    val assetPath: String? = null
)

object AvatarAnimationLibrary {
    private val canvasAnimations = mapOf(
        "HELLO" to AvatarAnimation("HELLO", AvatarAnimationKind.KNOWN_SIGN, 620L, "avatar/signs/HELLO.json"),
        "THANKYOU" to AvatarAnimation("THANKYOU", AvatarAnimationKind.KNOWN_SIGN, 680L, "avatar/signs/THANKYOU.json"),
        "WATER" to AvatarAnimation("WATER", AvatarAnimationKind.KNOWN_SIGN, 640L, "avatar/signs/WATER.json"),
        "EAT" to AvatarAnimation("EAT", AvatarAnimationKind.KNOWN_SIGN, 640L, "avatar/signs/EAT.json")
    )

    fun resolve(word: String): AvatarAnimation {
        val normalized = AvatarMotion.normalizeWord(word)
        if (normalized.isBlank() || normalized == "NOTHING") {
            return AvatarAnimation(normalized, AvatarAnimationKind.NONE, 0L)
        }
        return canvasAnimations[normalized]
            ?: AvatarAnimation(normalized, AvatarAnimationKind.FINGERSPELL, normalized.length.coerceAtLeast(1) * 300L)
    }
}
