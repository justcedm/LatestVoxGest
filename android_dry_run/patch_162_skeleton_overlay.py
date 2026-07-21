from pathlib import Path
import re

root = Path(r"C:\BSIT 3RD YEAR\New VovGest\android_dry_run")
controller = root / "app/src/main/java/com/voxgest/dryrun/VoxGestCameraRecognitionController.kt"
ui = root / "app/src/main/java/com/voxgest/dryrun/ui/VoxGestPresentationApp.kt"

for path in [controller, ui]:
    backup = Path(str(path) + ".bak_skeleton_overlay")
    if not backup.exists():
        backup.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")

# ==========================================================
# Controller patch: expose latest LandmarkFrame to UI
# ==========================================================
text = controller.read_text(encoding="utf-8")

# Clean broken back-cam constants from previous failed patch if still present.
text = text.replace("USE_BACK_CAMERA_FOR_DEMO", "false")
text = text.replace("BACK_CAMERA_FORCE_NO_PRE_MIRROR", "true")

# Add constructor callback.
if "onSkeletonFrame: (LandmarkFrame?) -> Unit" not in text:
    text = text.replace(
        "private val onRecognitionFeedback: (RecognitionFeedback) -> Unit = {},\n    private val onAcceptedResult: (RecognitionResult) -> Unit",
        "private val onRecognitionFeedback: (RecognitionFeedback) -> Unit = {},\n    private val onSkeletonFrame: (LandmarkFrame?) -> Unit = {},\n    private val onAcceptedResult: (RecognitionResult) -> Unit"
    )

# Add helper to post skeleton frame to UI.
if "private fun postSkeletonFrame" not in text:
    text = text.replace(
        "    private fun postFeedback(feedback: RecognitionFeedback) {\n        mainExecutor.execute { onRecognitionFeedback(feedback) }\n    }\n",
        "    private fun postFeedback(feedback: RecognitionFeedback) {\n        mainExecutor.execute { onRecognitionFeedback(feedback) }\n    }\n\n    private fun postSkeletonFrame(frame: LandmarkFrame?) {\n        mainExecutor.execute { onSkeletonFrame(frame) }\n    }\n"
    )

# Send frame to overlay after extraction.
if "postSkeletonFrame(frame)" not in text:
    text = text.replace(
        "            val frame = extractor?.processFrame(imageProxy)\n            val decision = router.decide(frame)",
        "            val frame = extractor?.processFrame(imageProxy)\n            postSkeletonFrame(frame)\n            val decision = router.decide(frame)"
    )

# Clear overlay when stopped.
if "postSkeletonFrame(null)" not in text:
    text = text.replace(
        "        postFeedback(RecognitionFeedback.idle())\n        mainExecutor.execute {",
        "        postFeedback(RecognitionFeedback.idle())\n        postSkeletonFrame(null)\n        mainExecutor.execute {",
        1
    )

controller.write_text(text, encoding="utf-8")

# ==========================================================
# UI patch: add toggle and skeleton drawing overlay
# ==========================================================
text = ui.read_text(encoding="utf-8")

# Add LandmarkFrame import.
if "import com.voxgest.dryrun.LandmarkFrame" not in text:
    text = text.replace(
        "import com.voxgest.dryrun.DetectionStatus\n",
        "import com.voxgest.dryrun.DetectionStatus\nimport com.voxgest.dryrun.LandmarkFrame\n"
    )

# Add UI state in RecognitionAreaCard.
if "showSkeletonFeatureView" not in text:
    text = text.replace(
        "    var recognitionFeedback by remember { mutableStateOf(RecognitionFeedback.idle()) }\n    var recognizedToast by remember { mutableStateOf(\"\") }",
        "    var recognitionFeedback by remember { mutableStateOf(RecognitionFeedback.idle()) }\n    var recognizedToast by remember { mutableStateOf(\"\") }\n    var showSkeletonFeatureView by remember { mutableStateOf(false) }\n    var skeletonFrame by remember { mutableStateOf<LandmarkFrame?>(null) }"
    )

# Pass callback into controller.
if "onSkeletonFrame = { skeletonFrame = it }" not in text:
    text = text.replace(
        "            onStatus = onRecognitionStatus,\n            onRecognitionFeedback = { recognitionFeedback = it },\n            onAcceptedResult = onAcceptedRecognition",
        "            onStatus = onRecognitionStatus,\n            onRecognitionFeedback = { recognitionFeedback = it },\n            onSkeletonFrame = { skeletonFrame = it },\n            onAcceptedResult = onAcceptedRecognition"
    )

# Add skeleton overlay inside camera preview Box after AndroidView.
if "SkeletonFeatureOverlay(" not in text:
    text = text.replace(
        "            AndroidView(\n                factory = { previewView },\n                modifier = Modifier.fillMaxSize()\n            )",
        "            AndroidView(\n                factory = { previewView },\n                modifier = Modifier.fillMaxSize()\n            )\n            if (showSkeletonFeatureView) {\n                SkeletonFeatureOverlay(\n                    frame = skeletonFrame,\n                    modifier = Modifier.fillMaxSize()\n                )\n            }"
    )

# Add toggle button in camera preview Box after StatusChip.
if "162 Feature View" not in text:
    text = text.replace(
        "            StatusChip(\n                label = recognitionStatus,\n                dotColor = if (recognitionRunning) AccentGreen else Amber,\n                containerColor = if (recognitionRunning) Color(0xFFE8F7EE) else Color(0xFFFFF7ED),\n                contentColor = if (recognitionRunning) Green else Amber,\n                modifier = Modifier\n                    .align(Alignment.TopStart)\n                    .padding(14.dp)\n            )",
        "            StatusChip(\n                label = recognitionStatus,\n                dotColor = if (recognitionRunning) AccentGreen else Amber,\n                containerColor = if (recognitionRunning) Color(0xFFE8F7EE) else Color(0xFFFFF7ED),\n                contentColor = if (recognitionRunning) Green else Amber,\n                modifier = Modifier\n                    .align(Alignment.TopStart)\n                    .padding(14.dp)\n            )\n            Surface(\n                modifier = Modifier\n                    .align(Alignment.TopEnd)\n                    .padding(14.dp)\n                    .clickable { showSkeletonFeatureView = !showSkeletonFeatureView },\n                shape = RoundedCornerShape(999.dp),\n                color = if (showSkeletonFeatureView) Primary.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.45f),\n                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f))\n            ) {\n                Text(\n                    if (showSkeletonFeatureView) \"Simple Camera\" else \"162 Feature View\",\n                    color = Color.White,\n                    fontSize = 11.sp,\n                    fontWeight = FontWeight.Bold,\n                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)\n                )\n            }"
    )

# Add composable functions before CurrentWordCard.
if "private fun SkeletonFeatureOverlay(" not in text:
    insert = r'''
@Composable
private fun SkeletonFeatureOverlay(
    frame: LandmarkFrame?,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.18f))
    ) {
        val pose = frame?.poseLandmarks.orEmpty()
        val left = frame?.leftHandLandmarks.orEmpty()
        val right = frame?.rightHandLandmarks.orEmpty()

        fun pointOf(x: Float, y: Float): Offset {
            return Offset(
                x = x.coerceIn(0f, 1f) * size.width,
                y = y.coerceIn(0f, 1f) * size.height
            )
        }

        fun drawPoint(x: Float, y: Float, color: Color, radius: Float = 4.2f) {
            drawCircle(
                color = color,
                radius = radius.dp.toPx(),
                center = pointOf(x, y)
            )
        }

        fun drawSegment(aX: Float, aY: Float, bX: Float, bY: Float, color: Color, stroke: Float = 2.2f) {
            drawLine(
                color = color,
                start = pointOf(aX, aY),
                end = pointOf(bX, bY),
                strokeWidth = stroke.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        fun drawPoseSegment(a: Int, b: Int, color: Color) {
            if (pose.size > maxOf(a, b)) {
                val pa = pose[a]
                val pb = pose[b]
                drawSegment(pa.x, pa.y, pb.x, pb.y, color, 2.6f)
            }
        }

        fun drawPosePoint(index: Int, color: Color, radius: Float = 4.4f) {
            if (pose.size > index) {
                val p = pose[index]
                drawPoint(p.x, p.y, color, radius)
            }
        }

        fun drawHandSkeletonRaw(points: List<com.voxgest.dryrun.LandmarkPoint>, color: Color) {
            if (points.size != 21) return
            HAND_CONNECTIONS.forEach { connection ->
                val a = points[connection.first]
                val b = points[connection.second]
                drawSegment(a.x, a.y, b.x, b.y, color.copy(alpha = 0.70f), 2.0f)
            }
            points.forEach { p ->
                drawPoint(p.x, p.y, color, 3.5f)
            }
        }

        val mouthColor = Color(0xFFFFD54F)
        val poseColor = Color(0xFF40C4FF)
        val armColor = Color(0xFF00E676)
        val leftHandColor = Color(0xFFFF80AB)
        val rightHandColor = Color(0xFFB8F060)

        // Mouth / face reference points: nose and mouth corners.
        listOf(0, 9, 10).forEach { drawPosePoint(it, mouthColor, 4.6f) }
        if (pose.size > 10) {
            drawPoseSegment(9, 10, mouthColor.copy(alpha = 0.70f))
        }

        // Shoulders and arms.
        drawPoseSegment(11, 12, poseColor)
        drawPoseSegment(11, 13, armColor)
        drawPoseSegment(13, 15, armColor)
        drawPoseSegment(12, 14, armColor)
        drawPoseSegment(14, 16, armColor)

        // Important pose/body points used for body-relative context.
        listOf(11, 12, 13, 14, 15, 16).forEach { drawPosePoint(it, poseColor, 4.4f) }

        // Hands.
        drawHandSkeletonRaw(left, leftHandColor)
        drawHandSkeletonRaw(right, rightHandColor)

        // Label.
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.WHITE
                textSize = 32f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            drawText("162 FEATURE VIEW: mouth + shoulders + arms + hands", 24f, size.height - 28f, paint)
        }
    }
}

'''
    text = text.replace("@Composable\nprivate fun CurrentWordCard", insert + "\n@Composable\nprivate fun CurrentWordCard", 1)

ui.write_text(text, encoding="utf-8")

print("162 skeletal feature overlay patch applied.")
