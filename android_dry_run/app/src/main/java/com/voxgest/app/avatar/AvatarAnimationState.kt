package com.voxgest.app.avatar

data class AvatarAnimationState(
    val word: String = "",
    val progress: Float = 0f,
    val motionName: String = "idle",
    val isFallback: Boolean = false
)
