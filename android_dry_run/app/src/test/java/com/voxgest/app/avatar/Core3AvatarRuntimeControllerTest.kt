package com.voxgest.app.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class Core3AvatarRuntimeControllerTest {
    @Test
    fun `verified manifest maps exactly HELLO MILK and RICE`() {
        val manifest = locateAsset("avatar/core3/animation_manifest.json")
        val catalog = Core3AvatarManifestParser.parse(manifest.readText(Charsets.UTF_8))

        assertEquals(setOf("HELLO", "MILK", "RICE"), catalog.supportedLabels)
        assertEquals("FSL_HELLO", catalog.resolve("hello")?.runtimeClipName)
        assertEquals("FSL_MILK", catalog.resolve(" MILK ")?.runtimeClipName)
        assertEquals("FSL_RICE", catalog.resolve("rice")?.runtimeClipName)
        assertEquals(null, catalog.resolve("WATER"))
        assertEquals(Core3AvatarAssets.EXPECTED_MODEL_SHA256, catalog.runtimeAssetSha256)
    }

    @Test
    fun `Android GLB is byte exact immutable Astra asset`() {
        val model = locateAsset("avatar/core3/voxgest_avatar_B32_CORE3_RC2.glb")
        val digest = MessageDigest.getInstance("SHA-256")
        val sha256 = model.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        }

        assertEquals(Core3AvatarAssets.EXPECTED_MODEL_BYTES, model.length())
        assertEquals(Core3AvatarAssets.EXPECTED_MODEL_SHA256, sha256)
    }

    @Test
    fun `controller remains unloaded until explicit user request`() {
        val runtime = FakeRuntime()
        val controller = controller(runtime)

        assertEquals(Core3AvatarStatus.UNLOADED, controller.state.status)
        assertTrue(runtime.events.isEmpty())

        assertTrue(controller.loadSign("HELLO", autoPlay = false))
        assertEquals(Core3AvatarStatus.LOADING, controller.state.status)
        assertEquals(listOf("load"), runtime.events)

        runtime.finishLoad()
        assertEquals(Core3AvatarStatus.READY, controller.state.status)
        assertEquals("HELLO", controller.state.currentSign)
    }

    @Test
    fun `play command resets neutral and completes without looping`() {
        val runtime = FakeRuntime()
        val controller = controller(runtime)

        controller.playSign("HELLO")
        runtime.finishLoad()

        assertEquals(listOf("load", "reset", "play:FSL_HELLO"), runtime.events)
        assertEquals(Core3AvatarStatus.PLAYING, controller.state.status)

        runtime.finishPlayback("FSL_HELLO")
        assertEquals(Core3AvatarStatus.READY, controller.state.status)
        assertEquals("Neutral pose", controller.state.detail)
    }

    @Test
    fun `rapid sign switch ignores prior completion and preserves isolation`() {
        val runtime = FakeRuntime()
        val controller = controller(runtime)
        controller.playSign("HELLO")
        runtime.finishLoad()

        controller.playSign("RICE")
        runtime.finishPlayback("FSL_HELLO")

        assertEquals(Core3AvatarStatus.PLAYING, controller.state.status)
        assertEquals("RICE", controller.state.currentSign)
        assertEquals(
            listOf("load", "reset", "play:FSL_HELLO", "reset", "play:FSL_RICE"),
            runtime.events
        )

        runtime.finishPlayback("FSL_RICE")
        assertEquals(Core3AvatarStatus.READY, controller.state.status)
    }

    @Test
    fun `unsupported label never allocates renderer`() {
        val runtime = FakeRuntime()
        val controller = controller(runtime)

        assertFalse(controller.playSign("WATER"))
        assertTrue(runtime.events.isEmpty())
        assertEquals(Core3AvatarStatus.ERROR, controller.state.status)
        assertEquals("Avatar unavailable", controller.state.detail)
    }

    @Test
    fun `renderer exception is isolated as error state`() {
        val runtime = FakeRuntime(throwOnLoad = true)
        val controller = controller(runtime)

        assertTrue(controller.playSign("MILK"))

        assertEquals(Core3AvatarStatus.ERROR, controller.state.status)
        assertEquals("Avatar unavailable", controller.state.detail)
        assertNotNull(controller.state.error)
    }

    @Test
    fun `reset and unload are explicit state commands`() {
        val runtime = FakeRuntime()
        val controller = controller(runtime)
        controller.loadSign("RICE")
        runtime.finishLoad()

        assertTrue(controller.resetNeutral())
        assertEquals(Core3AvatarStatus.READY, controller.state.status)
        controller.unload()

        assertEquals(Core3AvatarStatus.UNLOADED, controller.state.status)
        assertEquals(listOf("load", "reset", "unload"), runtime.events)
    }

    private fun controller(runtime: FakeRuntime): Core3AvatarRuntimeController {
        val catalog = Core3AvatarManifestParser.parse(
            locateAsset("avatar/core3/animation_manifest.json").readText(Charsets.UTF_8)
        )
        return Core3AvatarRuntimeController(catalog, runtime)
    }

    private fun locateAsset(relativePath: String): File {
        val relative = File("src/main/assets", relativePath)
        val candidates = listOf(
            relative,
            File("app", relative.path),
            File("android_dry_run/app", relative.path)
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("CORE3 asset not found from ${File(".").absolutePath}: $relativePath")
    }

    private class FakeRuntime(private val throwOnLoad: Boolean = false) : Core3AvatarRuntime {
        val events = mutableListOf<String>()
        private var readyCallback: ((Core3AvatarLoadMetrics) -> Unit)? = null
        private val completionCallbacks = mutableMapOf<String, () -> Unit>()

        override fun load(
            onReady: (Core3AvatarLoadMetrics) -> Unit,
            onFailure: (Throwable) -> Unit
        ) {
            events += "load"
            if (throwOnLoad) error("Synthetic renderer failure")
            readyCallback = onReady
        }

        override fun play(
            clip: Core3AvatarClip,
            onComplete: () -> Unit,
            onFailure: (Throwable) -> Unit
        ) {
            events += "play:${clip.runtimeClipName}"
            completionCallbacks[clip.runtimeClipName] = onComplete
        }

        override fun resetNeutral() {
            events += "reset"
        }

        override fun unload() {
            events += "unload"
        }

        fun finishLoad() {
            requireNotNull(readyCallback).invoke(Core3AvatarLoadMetrics(420L, 380L))
            readyCallback = null
        }

        fun finishPlayback(runtimeClipName: String) {
            completionCallbacks.remove(runtimeClipName)?.invoke()
        }
    }
}
