from pathlib import Path
import re

root = Path(r"C:\BSIT 3RD YEAR\New VovGest\android_dry_run")
controller = root / "app/src/main/java/com/voxgest/dryrun/VoxGestCameraRecognitionController.kt"
ui = root / "app/src/main/java/com/voxgest/dryrun/ui/VoxGestPresentationApp.kt"

for path in [controller, ui]:
    backup = Path(str(path) + ".bak_camera_switch_safe")
    if not backup.exists():
        backup.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")

# =========================
# Controller patch
# =========================
text = controller.read_text(encoding="utf-8")

# Remove old hard-coded back camera constants if present.
text = "\n".join(
    line for line in text.splitlines()
    if "private const val USE_BACK_CAMERA_FOR_DEMO" not in line
    and "private const val BACK_CAMERA_FORCE_NO_PRE_MIRROR" not in line
) + "\n"

# Fix mirror policy.
text = re.sub(
    r'val rawMirrorCameraFrame = AndroidLandmarkInputPolicy\.shouldMirrorFrameBeforeLandmarkExtraction\(loadedOneHandProfile\)\s*\n\s*val mirrorCameraFrame = if \(.*?\) false else rawMirrorCameraFrame',
    'val mirrorCameraFrame = mirrorCameraFrameFor(loadedOneHandProfile)',
    text
)

text = text.replace(
    'val mirrorCameraFrame = AndroidLandmarkInputPolicy.shouldMirrorFrameBeforeLandmarkExtraction(loadedOneHandProfile)',
    'val mirrorCameraFrame = mirrorCameraFrameFor(loadedOneHandProfile)'
)

# Add camera state.
if "private var useBackCamera: Boolean" not in text:
    text = text.replace(
        '@Volatile private var statusToken: Long = 0L',
        '@Volatile private var statusToken: Long = 0L\n    @Volatile private var useBackCamera: Boolean = false'
    )

# Add helper functions before bindCamera.
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

# Add switchCamera function before bindCamera/helper area.
if "fun switchCamera(previewView: PreviewView)" not in text:
    marker = '    private fun currentCameraLabel(): String {'
    switch_func = '''    fun switchCamera(previewView: PreviewView) {
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
                bindCamera(previewView)
            }
        }
    }

'''
    if marker not in text:
        raise RuntimeError("Could not find insertion marker for switchCamera.")
    text = text.replace(marker, switch_func + marker, 1)

# Add cameraSelector variable after imageAnalysis assignment.
if "val cameraSelector = if (useBackCamera)" not in text:
    text = text.replace(
        '            imageAnalysis = analysis\n            provider.unbindAll()',
        '''            imageAnalysis = analysis
            val cameraSelector = if (useBackCamera) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            Log.i(TAG, "camera_selector=${currentCameraLabel().uppercase(Locale.US)}")
            provider.unbindAll()''',
        1
    )

# Replace only the bindToLifecycle front camera argument.
text = text.replace(
    '                CameraSelector.DEFAULT_FRONT_CAMERA,',
    '                cameraSelector,',
    1
)

controller.write_text(text, encoding="utf-8")

# =========================
# UI patch
# =========================
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
    insert = target + '''                Button(
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
    text = text.replace(target, insert, 1)

ui.write_text(text, encoding="utf-8")

print("Camera switch patch applied successfully.")
