from pathlib import Path

path = Path(r"C:\BSIT 3RD YEAR\New VovGest\android_dry_run\app\src\main\java\com\voxgest\dryrun\VoxGestCameraRecognitionController.kt")
text = path.read_text(encoding="utf-8")

# Add constants safely.
if "USE_BACK_CAMERA_FOR_DEMO" not in text:
    text = text.replace(
        'private const val TAG = "VoxGestRecognition"',
        'private const val TAG = "VoxGestRecognition"\n'
        '        private const val USE_BACK_CAMERA_FOR_DEMO = true\n'
        '        private const val BACK_CAMERA_FORCE_NO_PRE_MIRROR = true'
    )

# Make MediaPipe mirror policy safe for back cam.
text = text.replace(
    'val mirrorCameraFrame = AndroidLandmarkInputPolicy.shouldMirrorFrameBeforeLandmarkExtraction(loadedOneHandProfile)',
    'val rawMirrorCameraFrame = AndroidLandmarkInputPolicy.shouldMirrorFrameBeforeLandmarkExtraction(loadedOneHandProfile)\n'
    '                val mirrorCameraFrame = if (USE_BACK_CAMERA_FOR_DEMO && BACK_CAMERA_FORCE_NO_PRE_MIRROR) false else rawMirrorCameraFrame'
)

old_block = '''            imageAnalysis = analysis
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis
            )
'''

new_block = '''            imageAnalysis = analysis
            val cameraSelector = if (USE_BACK_CAMERA_FOR_DEMO) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            Log.i(TAG, "camera_selector=" + (if (USE_BACK_CAMERA_FOR_DEMO) "BACK" else "FRONT"))
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                analysis
            )
'''

if old_block not in text:
    raise RuntimeError("Expected front-camera bind block not found. Backup may not have restored correctly.")

text = text.replace(old_block, new_block, 1)

path.write_text(text, encoding="utf-8")
print("Clean back camera patch applied.")
