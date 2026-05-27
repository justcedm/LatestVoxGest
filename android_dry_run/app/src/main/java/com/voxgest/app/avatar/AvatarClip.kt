package com.voxgest.app.avatar

import java.util.Locale

data class Segment(var angleDeg: Float, var length: Float)

data class FingerPose(
    var thumbCurl: Float = 0f,
    var indexCurl: Float = 0f,
    var middleCurl: Float = 0f,
    var ringCurl: Float = 0f,
    var pinkyCurl: Float = 0f,
    var thumbAngle: Float = 0f,
    var spread: Float = 0f
) {
    fun setAll(curl: Float, thumb: Float = curl, spreadValue: Float = 0f) {
        thumbCurl = thumb
        indexCurl = curl
        middleCurl = curl
        ringCurl = curl
        pinkyCurl = curl
        thumbAngle = 0f
        spread = spreadValue
    }

    fun copyFrom(other: FingerPose) {
        thumbCurl = other.thumbCurl
        indexCurl = other.indexCurl
        middleCurl = other.middleCurl
        ringCurl = other.ringCurl
        pinkyCurl = other.pinkyCurl
        thumbAngle = other.thumbAngle
        spread = other.spread
    }
}

data class AvatarPose(
    val rightUpperArm: Segment = Segment(76f, 0f),
    val rightForearm: Segment = Segment(14f, 0f),
    val rightHand: Segment = Segment(0f, 0f),
    val leftUpperArm: Segment = Segment(104f, 0f),
    val leftForearm: Segment = Segment(-24f, 0f),
    val leftHand: Segment = Segment(0f, 0f),
    val rightFingers: FingerPose = FingerPose(),
    val leftFingers: FingerPose = FingerPose(),
    var headTilt: Float = 0f,
    var chestBreath: Float = 1f,
    var rightHandOffsetX: Float = 0f,
    var rightHandOffsetY: Float = 0f,
    var leftHandOffsetX: Float = 0f,
    var leftHandOffsetY: Float = 0f
) {
    fun copyFrom(other: AvatarPose) {
        rightUpperArm.angleDeg = other.rightUpperArm.angleDeg
        rightForearm.angleDeg = other.rightForearm.angleDeg
        rightHand.angleDeg = other.rightHand.angleDeg
        leftUpperArm.angleDeg = other.leftUpperArm.angleDeg
        leftForearm.angleDeg = other.leftForearm.angleDeg
        leftHand.angleDeg = other.leftHand.angleDeg
        rightFingers.copyFrom(other.rightFingers)
        leftFingers.copyFrom(other.leftFingers)
        headTilt = other.headTilt
        chestBreath = other.chestBreath
        rightHandOffsetX = other.rightHandOffsetX
        rightHandOffsetY = other.rightHandOffsetY
        leftHandOffsetX = other.leftHandOffsetX
        leftHandOffsetY = other.leftHandOffsetY
    }

    fun resetToIdle() {
        rightUpperArm.angleDeg = 76f
        rightForearm.angleDeg = 14f
        rightHand.angleDeg = 0f
        leftUpperArm.angleDeg = 104f
        leftForearm.angleDeg = -30f
        leftHand.angleDeg = 4f
        rightFingers.setAll(0f)
        leftFingers.setAll(0f)
        headTilt = 0f
        chestBreath = 1f
        rightHandOffsetX = 0f
        rightHandOffsetY = 0f
        leftHandOffsetX = 0f
        leftHandOffsetY = 0f
    }
}

sealed class AvatarClip(val label: String, val durationMs: Long) {
    data object IDLE : AvatarClip("IDLE", 2000L)
    data object HELLO : AvatarClip("HELLO", 1200L)
    data object THANKYOU : AvatarClip("THANKYOU", 1000L)
    data object WATER : AvatarClip("WATER", 1200L)
    data object EAT : AvatarClip("EAT", 1200L)
    data object MY : AvatarClip("MY", 800L)
    data object NAME : AvatarClip("NAME", 900L)
    data object YOUR : AvatarClip("YOUR", 800L)
    data object WHAT : AvatarClip("WHAT", 1000L)
    data object YOU : AvatarClip("YOU", 700L)
    data object OKAY : AvatarClip("OKAY", 800L)
    data class LETTER(val letter: Char) : AvatarClip("LETTER_${letter.uppercaseChar()}", 600L)

    companion object {
        fun clipForLabel(label: String): AvatarClip? {
            val normalized = label.trim()
                .uppercase(Locale.US)
                .replace("THANK YOU", "THANKYOU")
                .replace(Regex("[^A-Z0-9]+"), "")
            if (normalized.isBlank() || normalized == "NOTHING") return null
            if (normalized.length == 1 && normalized[0] in 'A'..'Z') return LETTER(normalized[0])
            if (normalized.startsWith("LETTER") && normalized.lastOrNull() in 'A'..'Z') {
                return LETTER(normalized.last())
            }
            return when (normalized) {
                "HELLO" -> HELLO
                "THANKYOU" -> THANKYOU
                "WATER" -> WATER
                "FOOD", "EAT" -> EAT
                "MY" -> MY
                "NAME" -> NAME
                "YOUR" -> YOUR
                "WHAT" -> WHAT
                "YOU" -> YOU
                "OK", "OKAY" -> OKAY
                else -> null
            }
        }

        fun sentenceToClips(sentence: String): List<AvatarClip> {
            val clips = mutableListOf<AvatarClip>()
            sentence
                .uppercase(Locale.US)
                .replace("THANK YOU", "THANKYOU")
                .replace(Regex("[^A-Z0-9 ]+"), " ")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() && it != "NOTHING" }
                .forEach { token ->
                    val clip = clipForLabel(token)
                    if (clip != null) {
                        clips += clip
                    } else {
                        token.forEach { char ->
                            if (char in 'A'..'Z') clips += LETTER(char)
                        }
                    }
                }
            return clips
        }

        fun configureLetter(letter: Char, out: FingerPose) {
            out.setAll(0f)
            when (letter.uppercaseChar()) {
                'A' -> out.setAll(0.9f, thumb = 0.15f)
                'B' -> out.setAll(0f, thumb = 0.8f)
                'C' -> out.setAll(0.4f, thumb = 0.4f)
                'D' -> {
                    out.setAll(0.7f, thumb = 0.45f)
                    out.indexCurl = 0f
                }
                'E' -> out.setAll(0.8f, thumb = 0.9f)
                'F' -> {
                    out.setAll(0f, thumb = 0.3f)
                    out.indexCurl = 0.3f
                }
                'G' -> {
                    out.setAll(0.9f, thumb = 0.05f)
                    out.indexCurl = 0f
                    out.thumbAngle = -20f
                }
                'H' -> {
                    out.setAll(0.9f, thumb = 0.65f)
                    out.indexCurl = 0f
                    out.middleCurl = 0f
                    out.spread = 0.08f
                }
                'I' -> {
                    out.setAll(0.9f, thumb = 0.8f)
                    out.pinkyCurl = 0f
                }
                'J' -> {
                    out.setAll(0.9f, thumb = 0.8f)
                    out.pinkyCurl = 0f
                }
                'K' -> {
                    out.setAll(0.9f, thumb = 0f)
                    out.indexCurl = 0f
                    out.middleCurl = 0.3f
                    out.spread = 0.18f
                }
                'L' -> {
                    out.setAll(0.9f, thumb = 0f)
                    out.indexCurl = 0f
                    out.thumbAngle = -65f
                }
                'M' -> out.setAll(0.6f, thumb = 0.95f)
                'N' -> {
                    out.setAll(0.9f, thumb = 0.95f)
                    out.indexCurl = 0.6f
                    out.middleCurl = 0.6f
                }
                'O' -> out.setAll(0.5f, thumb = 0.5f)
                'P' -> {
                    out.setAll(0.9f, thumb = 0f)
                    out.indexCurl = 0f
                    out.middleCurl = 0.3f
                    out.spread = 0.18f
                }
                'Q' -> {
                    out.setAll(0.9f, thumb = 0.05f)
                    out.indexCurl = 0f
                    out.thumbAngle = -20f
                }
                'R' -> {
                    out.setAll(0.9f, thumb = 0.75f)
                    out.indexCurl = 0.1f
                    out.middleCurl = 0.1f
                    out.spread = -0.10f
                }
                'S' -> out.setAll(0.9f, thumb = 0.4f)
                'T' -> {
                    out.setAll(0.9f, thumb = 0.2f)
                    out.thumbAngle = -10f
                }
                'U' -> {
                    out.setAll(0.9f, thumb = 0.75f)
                    out.indexCurl = 0f
                    out.middleCurl = 0f
                    out.spread = -0.04f
                }
                'V' -> {
                    out.setAll(0.9f, thumb = 0.75f)
                    out.indexCurl = 0f
                    out.middleCurl = 0f
                    out.spread = 0.22f
                }
                'W' -> {
                    out.setAll(0.8f, thumb = 0.8f)
                    out.indexCurl = 0f
                    out.middleCurl = 0f
                    out.ringCurl = 0f
                    out.spread = 0.24f
                }
                'X' -> {
                    out.setAll(0.9f, thumb = 0.7f)
                    out.indexCurl = 0.5f
                }
                'Y' -> {
                    out.setAll(0.9f, thumb = 0f)
                    out.pinkyCurl = 0f
                    out.thumbAngle = -60f
                }
                'Z' -> {
                    out.setAll(0.9f, thumb = 0.7f)
                    out.indexCurl = 0f
                }
            }
        }
    }
}
