from pathlib import Path
import re

root = Path(r"C:\BSIT 3RD YEAR\New VovGest\android_dry_run")
controller = root / "app/src/main/java/com/voxgest/dryrun/VoxGestCameraRecognitionController.kt"
ui = root / "app/src/main/java/com/voxgest/dryrun/ui/VoxGestPresentationApp.kt"

# Backup first
for path in [controller, ui]:
    backup = Path(str(path) + ".bak_camera_switch")
    if not backup.exists():
        backup.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")

# ==========================================================
# Patch controller
# ==========================================================
text = controller.read_text(encoding="utf-8")

# Remove old hard-coded back-cam constants from previous attempt.
text = "\n".join(
    line for line in text.splitlines()
    if "private const val USE_BACK_CAMERA_FOR_DEMO" not in line
    and "private const val BACK_CAMERA_FORCE_NO_PRE_MIRROR" not in line
) + "\n"

# Replace old/broken mirror policy lines.
text = re.sub(
    r'val rawMirrorCameraFrame = AndroidLandmarkInputPolicy\.shouldMirrorFrameBeforeLandmarkExtraction\(loadedOneHandProfile\)\s*\n\s*val mirrorCameraFrame = if \(USE_BACK_CAMERA_FOR_DEMO && BACK_CAMERA_FORCE_NO_PRE_MIRROR\) false else rawMirrorCameraFrame',
    'val mirrorCameraFrame = mirrorCameraFrameFor(loadedOneHandProfile)',
    text
)

text = text.replace(
    'val mirrorCameraFrame = AndroidLandmarkInputPolicy.shouldMirrorFrameBeforeLandmarkExtraction(loadedOneHandProfile)',
    'val mirrorCameraFrame = mirrorCameraFrameFor(loadedOneHandProfile)'
)

# Add camera state field.
if "private var useBackCamera: Boolean" not in text:
    text = text.replace(
        '@Volatile private var statusToken: Long = 0L',
        '@Volatile private var statusToken: Long = 0L\n    @Volatile private var useBackCamera: Boolean = false'
    )

# Add switchCamera function after cancelCalibration().
if "fun switchCamera(previewView: PreviewView)" not in text:
    marker = '''    fun cancelCalibration() {
        analyzerExecutor.execute {
            calibrationLabel = ""
            calibrationBuffer?.clear()
            calibrationMissingPoseCount = 0
            calibrationMissingHandCount = 0
            postStatus("Looking for hand")
            Log.i(TAG, "calibration_cancelled")
        }
    }
'''
    insert = marker + '''

    fun switchCamera(previewView: PreviewView) {
        analyzerExecutor.execute {
            useBackCamera = !useBackCamera
            resetTemporalState()
            postFeedback(RecognitionFeedback.idle())
            oneHandProfile?.let { profile ->
                val mirrorCameraFrame = mirrorCameraFrameFor(profile)
                extractor?.close()
                extractor = MediaPipeLandmarkExtractor(appContext, mirrorCameraFrame)
                Log.i(TAG, "camera_switch camera=${currentCameraLabel()} mirrorCameraFrame=$mirrorCameraFrame")
            }
            postStatus("Switching to ${currentCameraLabel()} camera")
            mainExecutor.execute {
                cameraProvider?.unbindAll()
                bindCamera(previewView)
            }
        }
    }
'''
    if marker not in text:
        raise RuntimeError("Could not find cancelCalibration block for switchCamera insertion.")
    text = text.replace(marker, insert, 1)

# Add helper functions before bindCamera().
if "private fun currentCameraLabel()" not in text:
    marker = '    private fun bindCamera(previewView: PreviewView) {'
    helper = '''    private fun currentCameraLabel(): String {
        return if (useBackCamera) "Back" else "Front"
    }

    private fun mirrorCameraFrameFor(profile: RecognitionProfile): Boolean {
        return if (useBackCamera) {
            false
        } else {
            AndroidLandmarkInputPolicy.shouldMirrorFrameBeforeLandmarkExtraction(profile)
        }
    }

'''
    if marker not in text:
        raise RuntimeError("Could not find bindCamera marker.")
    text = text.replace(marker, helper + marker, 1)

# Replace bindToLifecycle camera selector block cleanly.
pattern = re.compile(
    r'''imageAnalysis = analysis\s*
\s*(?:val cameraSelector[\s\S]*?\n)?\s*
(?:Log\.i\(TAG, "camera_selector[\s\S]*?\n)?\s*
provider\.unbindAll\(\)\s*
provider\.bindToLifecycle\(\s*
lifecycleOwner,\s*
[\s\S]*?,
\s*preview,\s*
analysis\s*
\)''',
    re.MULTILINE
)

replacement = '''imageAnalysis = analysis
            val cameraSelector = if (useBackCamera) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            Log.i(TAG, "camera_selector=${currentCameraLabel().uppercase(Locale.US)}")
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                analysis
            )'''

text, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise RuntimeError("Could not replace camera bind block cleanly.")

controller.write_text(text, encoding="utf-8")

# ==========================================================
# Patch UI: add Switch Cam button beside Start/Stop
# ==========================================================
text = ui.read_text(encoding="utf-8")

if "Switch Cam" not in text:
    target = '''                Button(
                    onClick = {
                        controllerRef.value?.stop()
                        onStopRecognition()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CardWhite, contentColor = Primary),
                    border = BorderStroke(1.dp, Border)
                ) {
                    VoxIcon(R.drawable.ic_cancel, "Stop Recognition", Primary, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Stop Recognition", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
'''
    replacement = target + '''                Button(
                    onClick = {
                        controllerRef.value?.switchCamera(previewView)
                            ?: Toast.makeText(context, "Start recognition first", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CardWhite, contentColor = Primary),
                    border = BorderStroke(1.dp, Border)
                ) {
                    VoxIcon(R.drawable.ic_camera_off, "Switch Camera", Primary, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Switch Cam", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
'''
    if target not in text:
        raise RuntimeError("Could not find Stop Recognition button block.")
    text = text.replace(target, replacement, 1)

ui.write_text(text, encoding="utf-8")

print("Front/back camera switch patch applied successfully.")
