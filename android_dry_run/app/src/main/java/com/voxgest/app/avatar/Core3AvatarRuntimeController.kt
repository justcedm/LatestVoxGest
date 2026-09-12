package com.voxgest.app.avatar

import java.util.Locale

enum class Core3AvatarStatus {
    UNLOADED,
    LOADING,
    READY,
    PLAYING,
    ERROR
}

data class Core3AvatarLoadMetrics(
    val loadTimeMs: Long,
    val firstFrameTimeMs: Long
)

data class Core3AvatarState(
    val status: Core3AvatarStatus = Core3AvatarStatus.UNLOADED,
    val currentSign: String? = null,
    val detail: String = "Avatar not loaded",
    val error: String? = null,
    val metrics: Core3AvatarLoadMetrics? = null
)

interface Core3AvatarRuntime {
    fun load(
        onReady: (Core3AvatarLoadMetrics) -> Unit,
        onFailure: (Throwable) -> Unit
    )

    fun play(
        clip: Core3AvatarClip,
        onComplete: () -> Unit,
        onFailure: (Throwable) -> Unit
    )

    fun resetNeutral()
    fun unload()
}

/**
 * Renderer-independent state owner for the frozen CORE3 allowlist.
 *
 * Calls are expected on Android's main thread. Every renderer call is guarded so a GL/native
 * integration failure becomes ERROR state instead of propagating through the rest of VoxGest.
 */
class Core3AvatarRuntimeController(
    private val catalog: Core3AvatarCatalog,
    private val runtime: Core3AvatarRuntime,
    enabledLabels: Set<String> = catalog.supportedLabels,
    private val onStateChanged: (Core3AvatarState) -> Unit = {}
) {
    private val enabledLabels = enabledLabels.map { it.trim().uppercase(Locale.ROOT) }.toSet()
    private var pendingClip: Core3AvatarClip? = null
    private var pendingAutoPlay = false
    private var generation = 0L

    var state: Core3AvatarState = Core3AvatarState()
        private set

    init {
        require(this.enabledLabels.all { it in catalog.supportedLabels }) {
            "Runtime allowlist contains a clip that is not in the verified manifest"
        }
    }

    fun loadSign(label: String, autoPlay: Boolean = false): Boolean {
        val clip = resolveEnabled(label) ?: return unavailable(label)
        pendingClip = clip
        pendingAutoPlay = autoPlay

        return when (state.status) {
            Core3AvatarStatus.UNLOADED, Core3AvatarStatus.ERROR -> {
                beginLoad(clip)
                true
            }
            Core3AvatarStatus.LOADING -> true
            Core3AvatarStatus.READY, Core3AvatarStatus.PLAYING -> {
                if (autoPlay) playResolved(clip) else {
                    resetNeutral()
                    publish(
                        state.copy(
                            status = Core3AvatarStatus.READY,
                            currentSign = clip.canonicalLabel,
                            detail = "Avatar ready",
                            error = null
                        )
                    )
                }
                true
            }
        }
    }

    fun playSign(label: String): Boolean = loadSign(label, autoPlay = true)

    fun replay(): Boolean {
        val clip = state.currentSign?.let(catalog::resolve) ?: pendingClip ?: return false
        if (clip.canonicalLabel !in enabledLabels) return unavailable(clip.canonicalLabel)
        return when (state.status) {
            Core3AvatarStatus.UNLOADED, Core3AvatarStatus.ERROR -> loadSign(clip.canonicalLabel, autoPlay = true)
            Core3AvatarStatus.LOADING -> {
                pendingClip = clip
                pendingAutoPlay = true
                true
            }
            Core3AvatarStatus.READY, Core3AvatarStatus.PLAYING -> {
                playResolved(clip)
                true
            }
        }
    }

    fun resetNeutral(): Boolean = try {
        generation += 1
        runtime.resetNeutral()
        val sign = state.currentSign ?: pendingClip?.canonicalLabel
        publish(
            state.copy(
                status = Core3AvatarStatus.READY,
                currentSign = sign,
                detail = "Neutral pose",
                error = null
            )
        )
        true
    } catch (error: Throwable) {
        fail(error)
        false
    }

    fun unload() {
        generation += 1
        pendingClip = null
        pendingAutoPlay = false
        try {
            runtime.unload()
            publish(Core3AvatarState())
        } catch (error: Throwable) {
            fail(error)
        }
    }

    private fun beginLoad(clip: Core3AvatarClip) {
        val loadGeneration = ++generation
        publish(
            Core3AvatarState(
                status = Core3AvatarStatus.LOADING,
                currentSign = clip.canonicalLabel,
                detail = "Loading verified 3D avatar"
            )
        )
        try {
            runtime.load(
                onReady = { metrics ->
                    if (generation != loadGeneration) return@load
                    val readyClip = pendingClip ?: clip
                    publish(
                        state.copy(
                            status = Core3AvatarStatus.READY,
                            currentSign = readyClip.canonicalLabel,
                            detail = "Avatar ready",
                            error = null,
                            metrics = metrics
                        )
                    )
                    if (pendingAutoPlay) playResolved(readyClip)
                },
                onFailure = { error ->
                    if (generation == loadGeneration) fail(error)
                }
            )
        } catch (error: Throwable) {
            if (generation == loadGeneration) fail(error)
        }
    }

    private fun playResolved(clip: Core3AvatarClip) {
        val playbackGeneration = ++generation
        try {
            // Astra's contract requires an explicit neutral baseline and no animation layering.
            runtime.resetNeutral()
            publish(
                state.copy(
                    status = Core3AvatarStatus.PLAYING,
                    currentSign = clip.canonicalLabel,
                    detail = "Playing ${clip.canonicalLabel}",
                    error = null
                )
            )
            runtime.play(
                clip = clip,
                onComplete = {
                    if (generation == playbackGeneration) {
                        publish(
                            state.copy(
                                status = Core3AvatarStatus.READY,
                                currentSign = clip.canonicalLabel,
                                detail = "Neutral pose"
                            )
                        )
                    }
                },
                onFailure = { error ->
                    if (generation == playbackGeneration) fail(error)
                }
            )
        } catch (error: Throwable) {
            if (generation == playbackGeneration) fail(error)
        }
    }

    private fun resolveEnabled(label: String): Core3AvatarClip? =
        catalog.resolve(label)?.takeIf { it.canonicalLabel in enabledLabels }

    private fun unavailable(label: String): Boolean {
        val normalized = label.trim().uppercase(Locale.ROOT).ifBlank { "THIS SIGN" }
        publish(
            Core3AvatarState(
                status = Core3AvatarStatus.ERROR,
                currentSign = normalized,
                detail = "Avatar unavailable",
                error = "$normalized is not in the verified Avatar allowlist"
            )
        )
        return false
    }

    private fun fail(error: Throwable) {
        publish(
            state.copy(
                status = Core3AvatarStatus.ERROR,
                detail = "Avatar unavailable",
                error = error.message ?: error.javaClass.simpleName
            )
        )
    }

    private fun publish(next: Core3AvatarState) {
        state = next
        onStateChanged(next)
    }
}
