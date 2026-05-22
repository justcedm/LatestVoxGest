package com.voxgest.app.avatar

enum class AvatarStatus {
    READY,
    LISTENING,
    ANALYZING,
    SIGNING,
    FINGERSPELLING,
    STOPPED,
    ASSET_MISSING
}

data class AvatarPlaybackState(
    val status: AvatarStatus = AvatarStatus.READY,
    val label: String = "Ready",
    val detail: String = "Avatar ready",
    val currentWord: String = "",
    val isPlaying: Boolean = false,
    val isListening: Boolean = false,
    val isFallback: Boolean = false,
    val rendererMode: String = "Canvas fallback"
)
