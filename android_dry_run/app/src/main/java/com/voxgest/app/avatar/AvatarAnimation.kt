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
        "EAT" to AvatarAnimation("EAT", AvatarAnimationKind.KNOWN_SIGN, 640L, "avatar/signs/EAT.json"),
        "WHAT" to AvatarAnimation("WHAT", AvatarAnimationKind.KNOWN_SIGN, 720L),
        "YOUR" to AvatarAnimation("YOUR", AvatarAnimationKind.KNOWN_SIGN, 560L),
        "NAME" to AvatarAnimation("NAME", AvatarAnimationKind.KNOWN_SIGN, 720L),
        "MY" to AvatarAnimation("MY", AvatarAnimationKind.KNOWN_SIGN, 560L),
        "YOU" to AvatarAnimation("YOU", AvatarAnimationKind.KNOWN_SIGN, 560L),
        "OKAY" to AvatarAnimation("OKAY", AvatarAnimationKind.KNOWN_SIGN, 680L),
        "STUDENT" to AvatarAnimation("STUDENT", AvatarAnimationKind.KNOWN_SIGN, 760L),
        "WHERE" to AvatarAnimation("WHERE", AvatarAnimationKind.KNOWN_SIGN, 720L),
        "LIVE" to AvatarAnimation("LIVE", AvatarAnimationKind.KNOWN_SIGN, 720L)
    )

    val knownWords: Set<String> = canvasAnimations.keys

    fun resolve(word: String): AvatarAnimation {
        val normalized = AvatarMotion.normalizeWord(word)
        if (normalized.isBlank() || normalized == "NOTHING") {
            return AvatarAnimation(normalized, AvatarAnimationKind.NONE, 0L)
        }
        return canvasAnimations[normalized]
            ?: AvatarAnimation(normalized, AvatarAnimationKind.FINGERSPELL, normalized.length.coerceAtLeast(1) * 300L)
    }
}
