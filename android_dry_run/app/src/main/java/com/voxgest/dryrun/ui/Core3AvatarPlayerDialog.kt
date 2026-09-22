package com.voxgest.dryrun.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.voxgest.app.avatar.Core3AvatarAssets
import com.voxgest.app.avatar.Core3AvatarRuntimeController
import com.voxgest.app.avatar.Core3AvatarState
import com.voxgest.app.avatar.Core3AvatarStatus
import com.voxgest.app.avatar.Core3FilamentHostView

/** Labels exposed during the staged Samsung rollout. Advance only after the preceding device gate. */
internal object Core3AvatarGuideAvailability {
    private val enabledLabels = setOf("HELLO", "MILK", "RICE")

    fun isAvailable(label: String): Boolean = label in enabledLabels
    fun runtimeAllowlist(): Set<String> = enabledLabels
}

@Composable
internal fun Core3AvatarPlayerDialog(
    canonicalLabel: String,
    reducedMotion: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val catalogResult = remember(context) { runCatching { Core3AvatarAssets.loadCatalog(context) } }
    val catalog = catalogResult.getOrNull()

    if (catalog == null) {
        Core3AvatarUnavailableDialog(
            reason = catalogResult.exceptionOrNull()?.message ?: "Verified Avatar manifest unavailable",
            onDismiss = onDismiss
        )
        return
    }

    val host = remember(context) { Core3FilamentHostView(context) }
    var playerState by remember { mutableStateOf(Core3AvatarState(currentSign = canonicalLabel)) }
    val controller = remember(catalog, host) {
        Core3AvatarRuntimeController(
            catalog = catalog,
            runtime = host,
            enabledLabels = Core3AvatarGuideAvailability.runtimeAllowlist(),
            onStateChanged = { playerState = it }
        )
    }

    LaunchedEffect(controller, canonicalLabel, reducedMotion) {
        controller.loadSign(canonicalLabel, autoPlay = !reducedMotion)
    }
    DisposableEffect(controller) {
        onDispose { controller.unload() }
    }
    val closePlayer = {
        // Release the heavy GLB while the SurfaceView is still attached. ModelViewer then owns
        // the remaining engine teardown when Compose removes the dialog surface.
        controller.unload()
        onDismiss()
    }

    Dialog(
        onDismissRequest = closePlayer,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
    ) {
        val viewportColor = MaterialTheme.colorScheme.surfaceVariant
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(14.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "3D Avatar",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            playerState.currentSign ?: canonicalLabel,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    TextButton(onClick = closePlayer) { Text("Close") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("HELLO", "MILK", "RICE").forEach { label ->
                        val selected = playerState.currentSign == label
                        OutlinedButton(
                            onClick = { controller.playSign(label) },
                            enabled = playerState.status == Core3AvatarStatus.READY ||
                                playerState.status == Core3AvatarStatus.PLAYING,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(viewportColor),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { host.apply { setViewportColor(viewportColor.toArgb()) } },
                        update = { it.setViewportColor(viewportColor.toArgb()) },
                        modifier = Modifier.fillMaxSize()
                    )
                    when (playerState.status) {
                        Core3AvatarStatus.UNLOADED,
                        Core3AvatarStatus.LOADING -> AvatarLoadingState(canonicalLabel)
                        Core3AvatarStatus.ERROR -> AvatarErrorState(playerState.error)
                        Core3AvatarStatus.READY,
                        Core3AvatarStatus.PLAYING -> Unit
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = when (playerState.status) {
                            Core3AvatarStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                            Core3AvatarStatus.PLAYING -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            playerState.detail,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            color = when (playerState.status) {
                                Core3AvatarStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                                Core3AvatarStatus.PLAYING -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    playerState.metrics?.let { metrics ->
                        Text(
                            "Loaded ${metrics.loadTimeMs} ms",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { controller.replay() },
                        enabled = playerState.status == Core3AvatarStatus.READY ||
                            playerState.status == Core3AvatarStatus.PLAYING,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(if (playerState.status == Core3AvatarStatus.PLAYING) "Replay" else "Play / Replay")
                    }
                    OutlinedButton(
                        onClick = { controller.resetNeutral() },
                        enabled = playerState.status == Core3AvatarStatus.READY ||
                            playerState.status == Core3AvatarStatus.PLAYING,
                        modifier = Modifier.weight(0.72f).height(50.dp),
                        shape = RoundedCornerShape(15.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text("Reset")
                    }
                }

                Text(
                    if (reducedMotion) {
                        "Reduced motion is on. Playback starts only when you press Play."
                    } else {
                        "Verified CORE3 motion • Non-looping • Returns to neutral"
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Technical animation verification only; not expert FSL linguistic validation.",
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AvatarLoadingState(label: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
            Text(
                "Loading $label Avatar…",
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun AvatarErrorState(reason: String?) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Avatar unavailable",
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold
            )
            if (!reason.isNullOrBlank()) {
                Text(
                    reason,
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                "VoxGest remains available.",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun Core3AvatarUnavailableDialog(reason: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Avatar unavailable", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    reason,
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Text("VoxGest remains available.", modifier = Modifier.padding(top = 8.dp), fontSize = 12.sp)
                Button(onClick = onDismiss, modifier = Modifier.padding(top = 16.dp)) { Text("Close") }
            }
        }
    }
}
