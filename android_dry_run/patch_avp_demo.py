from pathlib import Path
import re

root = Path(r"C:\BSIT 3RD YEAR\New VovGest\android_dry_run")

ui = root / "app/src/main/java/com/voxgest/dryrun/ui/VoxGestPresentationApp.kt"
name_detector = root / "app/src/main/java/com/voxgest/dryrun/NamePhraseDetector.kt"
buffer_file = root / "app/src/main/java/com/voxgest/dryrun/LandmarkSequenceBuffer.kt"
controller = root / "app/src/main/java/com/voxgest/dryrun/VoxGestCameraRecognitionController.kt"

for path in [ui, name_detector, buffer_file, controller]:
    backup = Path(str(path) + ".bak_avp")
    if not backup.exists():
        backup.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")

# ------------------------------------------------------------
# Patch VoxGestPresentationApp.kt
# ------------------------------------------------------------
text = ui.read_text(encoding="utf-8")

# Show spelling assist panel.
text = text.replace(
    "private const val SHOW_DEBUG_TOOLS = false",
    "private const val SHOW_DEBUG_TOOLS = true"
)

# Force a clean token list with words + A-Z + controls.
tokens = ["WHAT", "YOUR", "NAME", "MY"] + [chr(i) for i in range(65, 91)] + ["DEL", "CLEAR", "SPEAK"]
token_block = "private val DemoTokenWords = listOf(\n" + ",\n".join(f'    "{t}"' for t in tokens) + "\n)\n\nprivate val AccuracyTestTargets"

text = re.sub(
    r"private val DemoTokenWords = listOf\([\s\S]*?\)\s*\n\s*private val AccuracyTestTargets",
    token_block,
    text,
    count=1
)

# Make the panel look intentional for AVP/demo.
text = text.replace('Label("DEMO TOKEN AREA")', 'Label("NAME SPELLING ASSIST")')
text = text.replace('label = "Phrase Demo"', 'label = "Manual Assist"')
text = text.replace('Tap tokens to build a phrase.', 'Tap letters to spell a name.')

# Do not auto-finalize MY + NAME before A-Z spelling.
text = re.sub(
    r'if \(letters\.isNotBlank\(\)\) return\s+"[^"]*\$letters"',
    'if (letters.isNotBlank()) return "My name is $letters"',
    text
)

text = re.sub(
    r'clean\.endsWithTokens\("MY", "NAME"\)\s*->\s*"[^"]*"',
    'clean.endsWithTokens("MY", "NAME") -> null',
    text
)

text = re.sub(
    r'clean\.endsWithTokens\("MY", "NAME", "IS"\)\s*->\s*"[^"]*"',
    'clean.endsWithTokens("MY", "NAME", "IS") -> null',
    text
)

ui.write_text(text, encoding="utf-8")

# ------------------------------------------------------------
# Patch NamePhraseDetector.kt
# ------------------------------------------------------------
text = name_detector.read_text(encoding="utf-8")
text = text.replace(
    'const val NAME_HINT = "Now fingerspell your name"',
    'const val NAME_HINT = "Tap letters below to spell your name"'
)
text = text.replace(
    'private const val NAME_PREFIX = "MY NAME IS"',
    'private const val NAME_PREFIX = "My name is"'
)
name_detector.write_text(text, encoding="utf-8")

# ------------------------------------------------------------
# Patch LandmarkSequenceBuffer.kt
# Add padded snapshot support for fast demo inference.
# ------------------------------------------------------------
text = buffer_file.read_text(encoding="utf-8")
if "snapshotPaddedToSequence" not in text:
    old = '''    fun snapshot(): Array<FloatArray>? {
        if (!isReady()) return null
        return frames.map { it.copyOf() }.toTypedArray()
    }
'''
    new = '''    fun snapshot(): Array<FloatArray>? {
        if (!isReady()) return null
        return frames.map { it.copyOf() }.toTypedArray()
    }

    fun snapshotPaddedToSequence(): Array<FloatArray>? {
        if (frames.isEmpty()) return null
        val out = frames.map { it.copyOf() }.toMutableList()
        val last = out.last().copyOf()
        while (out.size < sequenceLength) {
            out.add(last.copyOf())
        }
        return out.take(sequenceLength).toTypedArray()
    }
'''
    if old not in text:
        raise RuntimeError("Could not patch LandmarkSequenceBuffer.kt snapshot block.")
    text = text.replace(old, new)
buffer_file.write_text(text, encoding="utf-8")

# ------------------------------------------------------------
# Patch VoxGestCameraRecognitionController.kt
# Fast demo mode: infer once 18 frames are collected, padded to 30.
# ------------------------------------------------------------
text = controller.read_text(encoding="utf-8")

if "FAST_DEMO_MODE" not in text:
    text = text.replace(
        "private const val ANALYZE_INTERVAL_MS = 0L",
        "private const val ANALYZE_INTERVAL_MS = 0L\n        private const val FAST_DEMO_MODE = true\n        private const val FAST_DEMO_MIN_FRAMES = 18"
    )

old = '''        if (!buffer.isReady()) {
            postCollectionProgress(profile, framesCollected)
            return
        }

        val snapshot = buffer.snapshot()
'''
new = '''        val fastDemoReady = FAST_DEMO_MODE &&
            profile.id == OneHandCalibrationConfig.CALIBRATED_PROFILE_ID &&
            buffer.size() >= FAST_DEMO_MIN_FRAMES

        if (!buffer.isReady() && !fastDemoReady) {
            postCollectionProgress(profile, framesCollected)
            return
        }

        val snapshot = if (fastDemoReady && !buffer.isReady()) {
            Log.i(TAG, "fast_demo_inference frames=${buffer.size()}/${profile.sequenceLength} padded_to=${profile.sequenceLength}")
            buffer.snapshotPaddedToSequence()
        } else {
            buffer.snapshot()
        }
'''
if old not in text:
    print("WARNING: Fast demo block was not patched because the exact block was not found.")
else:
    text = text.replace(old, new, 1)

controller.write_text(text, encoding="utf-8")

print("AVP patch applied successfully.")
