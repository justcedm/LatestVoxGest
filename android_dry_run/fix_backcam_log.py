from pathlib import Path

path = Path(r"C:\BSIT 3RD YEAR\New VovGest\android_dry_run\app\src\main\java\com\voxgest\dryrun\VoxGestCameraRecognitionController.kt")
text = path.read_text(encoding="utf-8")

lines = text.splitlines()
fixed = []

for line in lines:
    if "camera_selector=" in line and "mirrorCameraFrame=" in line:
        indent = line[:len(line) - len(line.lstrip())]
        fixed.append(indent + 'Log.i(TAG, "camera_selector=${if (USE_BACK_CAMERA_FOR_DEMO) \\"BACK\\" else \\"FRONT\\"}")')
    else:
        fixed.append(line)

path.write_text("\n".join(fixed) + "\n", encoding="utf-8")
print("Fixed camera_selector log line.")
