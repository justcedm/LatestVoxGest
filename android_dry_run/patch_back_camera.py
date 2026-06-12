from pathlib import Path

path = Path(r"C:\BSIT 3RD YEAR\New VovGest\android_dry_run\app\src\main\java\com\voxgest\dryrun\VoxGestCameraRecognitionController.kt")
text = path.read_text(encoding="utf-8")

backup = Path(str(path) + ".bak_backcam")
if not backup.exists():
    backup.write_text(text, encoding="utf-8")

# Add back camera flags.
if "USE_BACK_CAMERA_FOR_DEMO" not in text:
    text = text.replace(
        "private const val ANALYZE_INTERVAL_MS = 0L",
        "private const val USE_BACK_CAMERA_FOR_DEMO = true\n        private const val BACK_CAMERA_FORCE_NO_PRE_MIRROR = true\n        private const val ANALYZE_INTERVAL_MS = 0L"
    )

# Make mirror policy back-camera aware.
text = text.replace(
    "val mirrorCameraFrame = AndroidLandmarkInputPolicy.shouldMirrorFrameBeforeLandmarkExtraction(loadedOneHandProfile)",
    "val rawMirrorCameraFrame = AndroidLandmarkInputPolicy.shouldMirrorFrameBeforeLandmarkExtraction(loadedOneHandProfile)\n                val mirrorCameraFrame = if (USE_BACK_CAMERA_FOR_DEMO && BACK_CAMERA_FORCE_NO_PRE_MIRROR) false else rawMirrorCameraFrame"
)

# Add log so we can confirm active camera.
if "camera_selector=${if (USE_BACK_CAMERA_FOR_DEMO)" not in text:
    text = text.replace(
        "imageAnalysis = analysis\n            provider.unbindAll()",
        "imageAnalysis = analysis\n            Log.i(TAG, \"camera_selector=${if (USE_BACK_CAMERA_FOR_DEMO) \"BACK\" else \"FRONT\"} mirrorCameraFrame=${if (USE_BACK_CAMERA_FOR_DEMO && BACK_CAMERA_FORCE_NO_PRE_MIRROR) false else AndroidLandmarkInputPolicy.shouldMirrorFrameBeforeLandmarkExtraction(oneHandProfile ?: RecognitionProfile.active(appContext))}\")\n            provider.unbindAll()"
    )

# Replace front camera selector with back/front flag selector.
text = text.replace(
    "CameraSelector.DEFAULT_FRONT_CAMERA,",
    "if (USE_BACK_CAMERA_FOR_DEMO) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA,"
)

path.write_text(text, encoding="utf-8")
print("Back camera patch applied.")
