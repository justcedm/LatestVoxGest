package com.voxgest.app.avatar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.voxgest.dryrun.MotionSettings
import org.json.JSONObject
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sin

class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cacheLock = Any()
    private val knownWords = HashMap<String, String>()
    private val keyframeCache = HashMap<String, List<Keyframe>>()
    private val pendingFingerRunnables = ArrayList<Runnable>()

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PRIMARY_TEAL
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_SECONDARY
        textAlign = Paint.Align.CENTER
    }
    private val eyebrowPath = Path()

    private var backgroundShader: LinearGradient? = null
    private var currentState = idleState()
    private var assetsLoaded = false
    private var pendingWord: String? = null
    private var signAnimator: ValueAnimator? = null
    private var idleAnimator: ValueAnimator? = null
    private var listeningAnimator: ValueAnimator? = null
    private var fingerAnimator: ValueAnimator? = null
    private var activeFrames: List<Keyframe> = emptyList()
    private var activeDurationMs = 500L
    private var breathingPhase = 0f
    private var listeningPulse = 0f
    private var listeningActive = false
    private var isSigning = false
    private var fingerspellActive = false
    private var currentFingerText = ""
    private var currentFingerAlpha = 0f
    private var lastLandmarkCount = 0
    private var debugStateText = "Idle"
    private var viewAttached = false
    private var drawPosted = false
    private var lastIdleDrawMs = 0L

    init {
        contentDescription = "Avatar signing: Ready"
        textPaint.textSize = 28f.sp
        captionPaint.textSize = 12f.sp
        loadAnimationsInBackground()
    }

    fun signText(text: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { signText(text) }
            return
        }

        val cleaned = text.trim().uppercase(Locale.US)
        if (cleaned.isBlank() || cleaned == "NOTHING") {
            pendingWord = null
            stopSignAnimation()
            stopFingerspell()
            currentState = idleState()
            isSigning = false
            debugStateText = "Idle"
            contentDescription = "Avatar signing: Ready"
            requestDraw()
            return
        }

        val word = cleaned.split(Regex("[^A-Z]+")).firstOrNull().orEmpty().ifBlank { cleaned }
        val frames = synchronized(cacheLock) { keyframeCache[word] } ?: builtInFrames(word)
        if (frames != null && frames.isNotEmpty()) {
            pendingWord = null
            contentDescription = "Avatar signing: $word"
            startKeyframeAnimation(word, frames)
            return
        }

        if (!assetsLoaded) {
            pendingWord = word
            debugStateText = "Signing $word"
            contentDescription = "Avatar signing: $word"
            requestDraw()
            return
        }

        contentDescription = "Avatar signing: $word"
        startFingerspell(word)
    }

    fun animateIdleBreathing() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { animateIdleBreathing() }
            return
        }
        if (!viewAttached) return
        if (idleAnimator?.isStarted == true) return
        idleAnimator?.cancel()
        if (!MotionSettings.animationsEnabled(context)) {
            breathingPhase = 0f
            requestDraw()
            return
        }
        idleAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 3200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                breathingPhase = (it.animatedValue as Float) * TWO_PI
                val now = SystemClock.uptimeMillis()
                if (now - lastIdleDrawMs >= IDLE_FRAME_INTERVAL_MS) {
                    lastIdleDrawMs = now
                    requestDraw()
                }
            }
            start()
        }
    }

    fun setListening(active: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setListening(active) }
            return
        }
        listeningActive = active
        debugStateText = if (active) "Listening" else "Idle"
        listeningAnimator?.cancel()
        if (!active || !MotionSettings.animationsEnabled(context)) {
            listeningPulse = if (active) 1f else 0f
            requestDraw()
            return
        }
        listeningAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                listeningPulse = it.animatedValue as Float
                requestDraw()
            }
            start()
        }
    }

    fun setStatus(status: AvatarStatus) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setStatus(status) }
            return
        }
        when (status) {
            AvatarStatus.LISTENING -> setListening(true)
            AvatarStatus.SIGNING -> {
                listeningActive = false
                listeningAnimator?.cancel()
                debugStateText = "Signing"
                requestDraw()
            }
            AvatarStatus.FINGERSPELLING -> {
                debugStateText = "Fingerspelling"
                requestDraw()
            }
            AvatarStatus.ANALYZING -> {
                debugStateText = "Analyzing"
                requestDraw()
            }
            AvatarStatus.ASSET_MISSING -> {
                debugStateText = "Avatar asset missing"
                requestDraw()
            }
            AvatarStatus.STOPPED -> {
                signText("")
                debugStateText = "Stopped"
                requestDraw()
            }
            AvatarStatus.READY -> {
                listeningActive = false
                listeningAnimator?.cancel()
                debugStateText = "Idle"
                requestDraw()
            }
        }
    }

    fun setLandmarks(points: FloatArray) {
        if (lastLandmarkCount != points.size) {
            lastLandmarkCount = points.size
            requestDraw()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewAttached = true
        animateIdleBreathing()
        requestDraw()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        backgroundShader = LinearGradient(
            0f,
            0f,
            0f,
            h.toFloat(),
            AVATAR_BG,
            SURFACE_WHITE,
            Shader.TileMode.CLAMP
        )
        backgroundPaint.shader = backgroundShader
    }

    override fun onDetachedFromWindow() {
        viewAttached = false
        drawPosted = false
        signAnimator?.cancel()
        idleAnimator?.cancel()
        listeningAnimator?.cancel()
        fingerAnimator?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        for (runnable in pendingFingerRunnables) {
            mainHandler.removeCallbacks(runnable)
        }
        pendingFingerRunnables.clear()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawPosted = false
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        drawBackground(canvas, w, h)
        drawBodyShadow(canvas, w, h)
        drawBody(canvas, w, h)
        drawHead(canvas, w, h)
        drawRestingLeftArm(canvas, w, h)
        drawArm(canvas, w, h)
        drawHand(canvas, w, h)
        if (fingerspellActive) {
            drawFingerspell(canvas, w, h)
        }
        drawStateText(canvas, w, h)
    }

    private fun loadAnimationsInBackground() {
        val assets = context.applicationContext.assets
        Thread {
            val loadedWords = HashMap<String, String>()
            val loadedFrames = HashMap<String, List<Keyframe>>()
            try {
                val manifestText = assets.open("avatar/avatar_manifest.json").bufferedReader().use { it.readText() }
                val manifest = JSONObject(manifestText)
                val known = manifest.optJSONObject("known_words")
                if (known != null) {
                    val keys = known.keys()
                    while (keys.hasNext()) {
                        val word = keys.next().uppercase(Locale.US)
                        val relativePath = known.optString(word)
                        if (relativePath.isBlank()) continue
                        loadedWords[word] = relativePath
                        val frames = parseSignFile("avatar/$relativePath")
                        if (frames.isNotEmpty()) {
                            loadedFrames[word] = frames
                        }
                    }
                }
            } catch (_: Exception) {
                loadedWords.clear()
                loadedFrames.clear()
            }

            mainHandler.post {
                synchronized(cacheLock) {
                    knownWords.clear()
                    knownWords.putAll(loadedWords)
                    keyframeCache.clear()
                    keyframeCache.putAll(loadedFrames)
                    assetsLoaded = true
                }
                val replay = pendingWord
                pendingWord = null
                if (replay.isNullOrBlank()) {
                    requestDraw()
                } else {
                    signText(replay)
                }
            }
        }.start()
    }

    private fun parseSignFile(assetPath: String): List<Keyframe> {
        return try {
            val jsonText = context.applicationContext.assets.open(assetPath).bufferedReader().use { it.readText() }
            val root = JSONObject(jsonText)
            val frames = root.optJSONObject("animation")?.optJSONArray("keyframes") ?: return emptyList()
            val parsed = ArrayList<Keyframe>(frames.length())
            for (i in 0 until frames.length()) {
                val frame = frames.optJSONObject(i) ?: continue
                val hand = frame.optJSONObject("right_hand") ?: continue
                val elbow = frame.optJSONObject("right_elbow") ?: continue
                parsed.add(
                    Keyframe(
                        t = frame.optLong("t", (i * 160).toLong()),
                        handX = hand.floatValue("x", IDLE_HAND_X),
                        handY = hand.floatValue("y", IDLE_HAND_Y),
                        elbowX = elbow.floatValue("x", IDLE_ELBOW_X),
                        elbowY = elbow.floatValue("y", IDLE_ELBOW_Y),
                        shape = parseShape(hand.optString("shape", "open_palm")),
                        face = parseFace(frame.optString("face", "neutral"))
                    )
                )
            }
            parsed.sortedBy { it.t }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun builtInFrames(word: String): List<Keyframe> {
        return when (word) {
            "HELLO" -> listOf(
                kf(0, 0.70f, 0.30f, 0.62f, 0.48f, HandShape.OPEN_PALM, FaceExpression.FRIENDLY),
                kf(130, 0.78f, 0.24f, 0.63f, 0.46f, HandShape.OPEN_PALM, FaceExpression.FRIENDLY),
                kf(260, 0.67f, 0.27f, 0.61f, 0.48f, HandShape.OPEN_PALM, FaceExpression.FRIENDLY),
                kf(390, 0.78f, 0.24f, 0.63f, 0.46f, HandShape.OPEN_PALM, FaceExpression.FRIENDLY),
                kf(620, 0.70f, 0.30f, 0.62f, 0.48f, HandShape.OPEN_PALM, FaceExpression.CONFIRM)
            )
            "THANKYOU" -> listOf(
                kf(0, 0.55f, 0.32f, 0.64f, 0.49f, HandShape.FLAT_PALM, FaceExpression.WARM),
                kf(180, 0.58f, 0.27f, 0.63f, 0.46f, HandShape.FLAT_PALM, FaceExpression.WARM),
                kf(420, 0.82f, 0.28f, 0.70f, 0.45f, HandShape.FLAT_PALM, FaceExpression.CONFIRM),
                kf(680, 0.74f, 0.37f, 0.68f, 0.52f, HandShape.FLAT_PALM, FaceExpression.FRIENDLY)
            )
            "WATER" -> listOf(
                kf(0, 0.62f, 0.36f, 0.66f, 0.52f, HandShape.W_HAND, FaceExpression.FRIENDLY),
                kf(150, 0.58f, 0.30f, 0.64f, 0.48f, HandShape.W_HAND, FaceExpression.FRIENDLY),
                kf(280, 0.60f, 0.34f, 0.65f, 0.49f, HandShape.W_HAND, FaceExpression.FRIENDLY),
                kf(420, 0.58f, 0.30f, 0.64f, 0.48f, HandShape.W_HAND, FaceExpression.FRIENDLY),
                kf(640, 0.72f, 0.45f, 0.68f, 0.57f, HandShape.W_HAND, FaceExpression.CONFIRM)
            )
            "EAT" -> listOf(
                kf(0, 0.72f, 0.48f, 0.67f, 0.58f, HandShape.PINCH, FaceExpression.FRIENDLY),
                kf(160, 0.60f, 0.34f, 0.65f, 0.50f, HandShape.PINCH, FaceExpression.FRIENDLY),
                kf(300, 0.58f, 0.29f, 0.64f, 0.48f, HandShape.PINCH, FaceExpression.FRIENDLY),
                kf(460, 0.61f, 0.35f, 0.65f, 0.50f, HandShape.PINCH, FaceExpression.FRIENDLY),
                kf(640, 0.73f, 0.48f, 0.68f, 0.58f, HandShape.PINCH, FaceExpression.CONFIRM)
            )
            "WHAT" -> listOf(
                kf(0, 0.72f, 0.50f, 0.65f, 0.58f, HandShape.OPEN_PALM, FaceExpression.NEUTRAL),
                kf(180, 0.62f, 0.43f, 0.62f, 0.54f, HandShape.OPEN_PALM, FaceExpression.FIRM),
                kf(360, 0.78f, 0.43f, 0.68f, 0.54f, HandShape.OPEN_PALM, FaceExpression.FIRM),
                kf(540, 0.62f, 0.43f, 0.62f, 0.54f, HandShape.OPEN_PALM, FaceExpression.FIRM),
                kf(720, 0.74f, 0.50f, 0.66f, 0.58f, HandShape.OPEN_PALM, FaceExpression.NEUTRAL)
            )
            "YOUR", "YOU" -> listOf(
                kf(0, 0.70f, 0.48f, 0.65f, 0.57f, HandShape.TWO_FINGERS, FaceExpression.FRIENDLY),
                kf(180, 0.82f, 0.40f, 0.70f, 0.51f, HandShape.TWO_FINGERS, FaceExpression.CONFIRM),
                kf(360, 0.88f, 0.36f, 0.72f, 0.48f, HandShape.TWO_FINGERS, FaceExpression.CONFIRM),
                kf(560, 0.76f, 0.48f, 0.68f, 0.57f, HandShape.TWO_FINGERS, FaceExpression.FRIENDLY)
            )
            "NAME" -> listOf(
                kf(0, 0.74f, 0.48f, 0.67f, 0.58f, HandShape.TWO_FINGERS, FaceExpression.FRIENDLY),
                kf(180, 0.58f, 0.43f, 0.63f, 0.54f, HandShape.TWO_FINGERS, FaceExpression.FRIENDLY),
                kf(360, 0.66f, 0.42f, 0.65f, 0.53f, HandShape.TWO_FINGERS, FaceExpression.CONFIRM),
                kf(540, 0.58f, 0.43f, 0.63f, 0.54f, HandShape.TWO_FINGERS, FaceExpression.FRIENDLY),
                kf(720, 0.74f, 0.50f, 0.67f, 0.58f, HandShape.TWO_FINGERS, FaceExpression.FRIENDLY)
            )
            "MY" -> listOf(
                kf(0, 0.76f, 0.48f, 0.68f, 0.58f, HandShape.FLAT_PALM, FaceExpression.WARM),
                kf(220, 0.53f, 0.46f, 0.60f, 0.54f, HandShape.FLAT_PALM, FaceExpression.WARM),
                kf(420, 0.51f, 0.50f, 0.59f, 0.57f, HandShape.FLAT_PALM, FaceExpression.CONFIRM),
                kf(560, 0.64f, 0.53f, 0.64f, 0.60f, HandShape.FLAT_PALM, FaceExpression.FRIENDLY)
            )
            "OKAY" -> listOf(
                kf(0, 0.74f, 0.50f, 0.67f, 0.58f, HandShape.PINCH, FaceExpression.FRIENDLY),
                kf(200, 0.70f, 0.34f, 0.66f, 0.49f, HandShape.PINCH, FaceExpression.CONFIRM),
                kf(420, 0.80f, 0.30f, 0.70f, 0.47f, HandShape.PINCH, FaceExpression.CONFIRM),
                kf(680, 0.74f, 0.48f, 0.67f, 0.58f, HandShape.PINCH, FaceExpression.FRIENDLY)
            )
            "STUDENT" -> listOf(
                kf(0, 0.76f, 0.50f, 0.68f, 0.58f, HandShape.FLAT_PALM, FaceExpression.FRIENDLY),
                kf(180, 0.68f, 0.28f, 0.65f, 0.46f, HandShape.FLAT_PALM, FaceExpression.FRIENDLY),
                kf(380, 0.56f, 0.36f, 0.61f, 0.50f, HandShape.FLAT_PALM, FaceExpression.FRIENDLY),
                kf(560, 0.62f, 0.50f, 0.63f, 0.58f, HandShape.OPEN_PALM, FaceExpression.CONFIRM),
                kf(760, 0.76f, 0.52f, 0.68f, 0.60f, HandShape.OPEN_PALM, FaceExpression.FRIENDLY)
            )
            "WHERE" -> listOf(
                kf(0, 0.72f, 0.48f, 0.66f, 0.57f, HandShape.TWO_FINGERS, FaceExpression.FIRM),
                kf(150, 0.62f, 0.39f, 0.63f, 0.52f, HandShape.TWO_FINGERS, FaceExpression.FIRM),
                kf(300, 0.78f, 0.39f, 0.68f, 0.52f, HandShape.TWO_FINGERS, FaceExpression.FIRM),
                kf(450, 0.62f, 0.39f, 0.63f, 0.52f, HandShape.TWO_FINGERS, FaceExpression.FIRM),
                kf(720, 0.72f, 0.48f, 0.66f, 0.57f, HandShape.TWO_FINGERS, FaceExpression.NEUTRAL)
            )
            "LIVE" -> listOf(
                kf(0, 0.72f, 0.62f, 0.67f, 0.64f, HandShape.FLAT_PALM, FaceExpression.FRIENDLY),
                kf(180, 0.64f, 0.56f, 0.64f, 0.60f, HandShape.FLAT_PALM, FaceExpression.FRIENDLY),
                kf(400, 0.58f, 0.45f, 0.61f, 0.54f, HandShape.FLAT_PALM, FaceExpression.CONFIRM),
                kf(720, 0.72f, 0.52f, 0.67f, 0.59f, HandShape.FLAT_PALM, FaceExpression.FRIENDLY)
            )
            else -> emptyList()
        }
    }

    private fun kf(
        t: Long,
        handX: Float,
        handY: Float,
        elbowX: Float,
        elbowY: Float,
        shape: HandShape,
        face: FaceExpression
    ): Keyframe {
        return Keyframe(t, handX, handY, elbowX, elbowY, shape, face)
    }

    private fun startKeyframeAnimation(word: String, frames: List<Keyframe>) {
        stopFingerspell()
        signAnimator?.cancel()
        activeFrames = frames
        activeDurationMs = max(1L, frames.last().t)
        currentState = frameStateAt(0L)
        isSigning = true
        debugStateText = "Signing $word"
        if (!MotionSettings.animationsEnabled(context)) {
            currentState = frameStateAt(activeDurationMs)
            isSigning = false
            debugStateText = "Idle"
            requestDraw()
            return
        }
        signAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = activeDurationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val fraction = it.animatedValue as Float
                val currentTime = (fraction * activeDurationMs).toLong()
                currentState = frameStateAt(currentTime)
                requestDraw()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var canceled = false

                override fun onAnimationCancel(animation: Animator) {
                    canceled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!canceled) {
                        isSigning = false
                        currentState = idleState()
                        debugStateText = "Idle"
                        contentDescription = "Avatar signing: $word complete"
                        requestDraw()
                    }
                }
            })
            start()
        }
    }

    private fun frameStateAt(timeMs: Long): FrameState {
        if (activeFrames.isEmpty()) return idleState()
        if (timeMs <= activeFrames.first().t) return activeFrames.first().toState()
        if (timeMs >= activeFrames.last().t) return activeFrames.last().toState()

        var left = activeFrames.first()
        var right = activeFrames.last()
        for (i in 0 until activeFrames.lastIndex) {
            val a = activeFrames[i]
            val b = activeFrames[i + 1]
            if (timeMs >= a.t && timeMs <= b.t) {
                left = a
                right = b
                break
            }
        }
        val span = max(1L, right.t - left.t).toFloat()
        val local = ((timeMs - left.t) / span).coerceIn(0f, 1f)
        val eased = smoothStep(local)
        val face = if (eased < 0.5f) left.face else right.face
        val shape = if (eased < 0.5f) left.shape else right.shape
        return FrameState(
            handX = lerp(left.handX, right.handX, eased),
            handY = lerp(left.handY, right.handY, eased),
            elbowX = lerp(left.elbowX, right.elbowX, eased),
            elbowY = lerp(left.elbowY, right.elbowY, eased),
            shape = shape,
            face = face
        )
    }

    private fun startFingerspell(word: String) {
        stopSignAnimation()
        stopFingerspell()
        val letters = word.filter { it in 'A'..'Z' }.ifBlank { word.take(1) }
        if (letters.isBlank()) {
            currentState = idleState()
            requestDraw()
            return
        }
        isSigning = false
        fingerspellActive = true
        currentState = idleState()
        debugStateText = "Fingerspelling"
        contentDescription = "Avatar signing: Fingerspelling $word"
        if (!MotionSettings.animationsEnabled(context)) {
            currentFingerText = letters.first().toString()
            currentFingerAlpha = 1f
            requestDraw()
            return
        }
        letters.forEachIndexed { index, letter ->
            val runnable = Runnable { showFingerspellLetter(letter.toString()) }
            pendingFingerRunnables.add(runnable)
            mainHandler.postDelayed(runnable, index * FINGERSPELL_STEP_MS)
        }
        val finish = Runnable {
            fingerspellActive = false
            currentFingerText = ""
            currentFingerAlpha = 0f
            currentState = idleState()
            debugStateText = "Idle"
            requestDraw()
        }
        pendingFingerRunnables.add(finish)
        mainHandler.postDelayed(finish, letters.length * FINGERSPELL_STEP_MS + FINGERSPELL_STEP_MS)
    }

    private fun showFingerspellLetter(letter: String) {
        fingerAnimator?.cancel()
        currentFingerText = letter
        currentFingerAlpha = 0f
        if (!MotionSettings.animationsEnabled(context)) {
            currentFingerAlpha = 1f
            requestDraw()
            return
        }
        fingerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FINGERSPELL_STEP_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                currentFingerAlpha = it.animatedValue as Float
                requestDraw()
            }
            start()
        }
    }

    private fun stopSignAnimation() {
        signAnimator?.cancel()
        signAnimator = null
        activeFrames = emptyList()
        isSigning = false
    }

    private fun stopFingerspell() {
        fingerAnimator?.cancel()
        fingerAnimator = null
        for (runnable in pendingFingerRunnables) {
            mainHandler.removeCallbacks(runnable)
        }
        pendingFingerRunnables.clear()
        fingerspellActive = false
        currentFingerText = ""
        currentFingerAlpha = 0f
    }

    private fun drawBackground(canvas: Canvas, w: Float, h: Float) {
        if (backgroundPaint.shader == null) {
            backgroundPaint.color = AVATAR_BG
        }
        canvas.drawRect(0f, 0f, w, h, backgroundPaint)
    }

    private fun drawBodyShadow(canvas: Canvas, w: Float, h: Float) {
        val cx = w * 0.5f
        fillPaint.color = SHADOW_30
        canvas.drawOval(cx - 40f.dp, h * 0.80f, cx + 40f.dp, h * 0.80f + 16f.dp, fillPaint)
    }

    private fun drawBody(canvas: Canvas, w: Float, h: Float) {
        val cx = w * 0.5f
        val headR = w * 0.14f
        val headCy = h * 0.28f
        val neckW = w * 0.06f
        val neckH = h * 0.06f
        val neckTop = headCy + headR * 0.74f
        val torsoW = w * 0.30f
        val torsoH = h * 0.22f
        val torsoTop = neckTop + neckH - 4f.dp
        val torsoLeft = cx - torsoW * 0.5f
        val torsoRight = cx + torsoW * 0.5f
        val torsoBottom = torsoTop + torsoH

        fillPaint.color = SKIN
        strokePaint.color = SKIN_STROKE
        strokePaint.strokeWidth = 1.5f.dp
        canvas.drawRoundRect(cx - neckW * 0.5f, neckTop, cx + neckW * 0.5f, neckTop + neckH, 8f.dp, 8f.dp, fillPaint)
        canvas.drawRoundRect(cx - neckW * 0.5f, neckTop, cx + neckW * 0.5f, neckTop + neckH, 8f.dp, 8f.dp, strokePaint)

        fillPaint.color = SHIRT
        strokePaint.color = PRIMARY_TEAL
        strokePaint.strokeWidth = 2f.dp
        canvas.drawRoundRect(torsoLeft, torsoTop, torsoRight, torsoBottom, 18f.dp, 18f.dp, fillPaint)
        canvas.drawRoundRect(torsoLeft, torsoTop, torsoRight, torsoBottom, 18f.dp, 18f.dp, strokePaint)

        fillPaint.color = SHIRT_HIGHLIGHT
        canvas.drawRoundRect(
            torsoLeft + 10f.dp,
            torsoTop + 8f.dp,
            cx + torsoW * 0.05f,
            torsoTop + torsoH * 0.62f,
            14f.dp,
            14f.dp,
            fillPaint
        )

        fillPaint.color = withAlpha(Color.WHITE, 34)
        canvas.drawCircle(cx - torsoW * 0.18f, torsoTop + torsoH * 0.18f, 9f.dp, fillPaint)

        strokePaint.color = PRIMARY_DARK
        strokePaint.strokeWidth = 3f.dp
        canvas.drawLine(cx - w * 0.22f, torsoTop + 8f.dp, cx + w * 0.22f, torsoTop + 8f.dp, strokePaint)
    }

    private fun drawHead(canvas: Canvas, w: Float, h: Float) {
        val cx = w * 0.5f
        val cy = h * 0.28f
        val r = w * 0.14f
        val face = if (listeningActive && listeningPulse > 0.5f) FaceExpression.FRIENDLY else currentState.face

        if (listeningActive) {
            strokePaint.color = withAlpha(PRIMARY_TEAL, (70 + listeningPulse * 95f).toInt())
            strokePaint.strokeWidth = 3f.dp
            canvas.drawCircle(cx, cy, r + 8f.dp + listeningPulse * 4f.dp, strokePaint)
        }

        fillPaint.color = SKIN
        strokePaint.color = SKIN_STROKE
        strokePaint.strokeWidth = 2f.dp
        canvas.drawCircle(cx, cy, r, fillPaint)
        canvas.drawCircle(cx, cy, r, strokePaint)

        fillPaint.color = withAlpha(Color.WHITE, 74)
        canvas.drawCircle(cx - r * 0.30f, cy - r * 0.34f, r * 0.30f, fillPaint)

        fillPaint.color = withAlpha(SKIN_STROKE, 42)
        canvas.drawOval(cx - r * 0.56f, cy + r * 0.24f, cx + r * 0.56f, cy + r * 0.82f, fillPaint)

        fillPaint.color = HAIR
        canvas.drawArc(cx - r * 1.04f, cy - r * 1.10f, cx + r * 1.04f, cy + r * 0.32f, 190f, 160f, true, fillPaint)
        canvas.drawOval(cx - r * 1.10f, cy - r * 0.42f, cx - r * 0.62f, cy + r * 0.36f, fillPaint)
        canvas.drawOval(cx + r * 0.62f, cy - r * 0.42f, cx + r * 1.10f, cy + r * 0.36f, fillPaint)

        fillPaint.color = withAlpha(Color.WHITE, 38)
        canvas.drawOval(cx - r * 0.78f, cy - r * 0.86f, cx - r * 0.22f, cy - r * 0.50f, fillPaint)

        fillPaint.color = EYE
        val eyeY = cy - r * 0.08f
        val leftEyeX = cx - r * 0.38f
        val rightEyeX = cx + r * 0.38f
        canvas.drawCircle(leftEyeX, eyeY, 4f.dp, fillPaint)
        canvas.drawCircle(rightEyeX, eyeY, 4f.dp, fillPaint)

        drawEyebrows(canvas, leftEyeX, rightEyeX, eyeY - 10f.dp, face)
        drawMouth(canvas, cx, cy + r * 0.24f, r, face)
    }

    private fun drawRestingLeftArm(canvas: Canvas, w: Float, h: Float) {
        val cx = w * 0.5f
        val torsoTop = torsoTopY(w, h)
        val shoulderX = cx - w * 0.20f
        val shoulderY = torsoTop + h * 0.04f
        val elbowX = cx - w * 0.32f
        val elbowY = torsoTop + h * 0.18f
        val wristX = cx - w * 0.20f
        val wristY = torsoTop + h * 0.28f

        strokePaint.color = SKIN
        strokePaint.strokeWidth = 12f.dp
        canvas.drawLine(shoulderX, shoulderY, elbowX, elbowY, strokePaint)
        strokePaint.strokeWidth = 10f.dp
        canvas.drawLine(elbowX, elbowY, wristX, wristY, strokePaint)

        fillPaint.color = SKIN
        strokePaint.color = SKIN_STROKE
        strokePaint.strokeWidth = 1.5f.dp
        canvas.drawRoundRect(wristX - 11f.dp, wristY - 14f.dp, wristX + 11f.dp, wristY + 14f.dp, 8f.dp, 8f.dp, fillPaint)
        canvas.drawRoundRect(wristX - 11f.dp, wristY - 14f.dp, wristX + 11f.dp, wristY + 14f.dp, 8f.dp, 8f.dp, strokePaint)
    }

    private fun drawEyebrows(canvas: Canvas, leftX: Float, rightX: Float, y: Float, face: FaceExpression) {
        strokePaint.color = EYE
        strokePaint.strokeWidth = 2f.dp
        if (face == FaceExpression.FRIENDLY || face == FaceExpression.WARM) {
            eyebrowPath.reset()
            eyebrowPath.moveTo(leftX - 8f.dp, y + 2f.dp)
            eyebrowPath.quadTo(leftX, y - 3f.dp, leftX + 8f.dp, y + 2f.dp)
            canvas.drawPath(eyebrowPath, strokePaint)
            eyebrowPath.reset()
            eyebrowPath.moveTo(rightX - 8f.dp, y + 2f.dp)
            eyebrowPath.quadTo(rightX, y - 3f.dp, rightX + 8f.dp, y + 2f.dp)
            canvas.drawPath(eyebrowPath, strokePaint)
        } else {
            canvas.drawLine(leftX - 8f.dp, y, leftX + 8f.dp, y, strokePaint)
            canvas.drawLine(rightX - 8f.dp, y, rightX + 8f.dp, y, strokePaint)
        }
    }

    private fun drawMouth(canvas: Canvas, cx: Float, cy: Float, headR: Float, face: FaceExpression) {
        strokePaint.color = TEXT_PRIMARY
        strokePaint.strokeWidth = 1.5f.dp
        val mouthW = when (face) {
            FaceExpression.CONFIRM -> headR * 0.60f
            FaceExpression.FRIENDLY, FaceExpression.WARM -> headR * 0.52f
            else -> headR * 0.42f
        }
        when (face) {
            FaceExpression.FIRM -> canvas.drawLine(cx - mouthW * 0.5f, cy, cx + mouthW * 0.5f, cy, strokePaint)
            FaceExpression.CONFIRM -> canvas.drawArc(cx - mouthW * 0.5f, cy - 5f.dp, cx + mouthW * 0.5f, cy + 16f.dp, 10f, 160f, false, strokePaint)
            FaceExpression.NEUTRAL -> canvas.drawArc(cx - mouthW * 0.5f, cy - 3f.dp, cx + mouthW * 0.5f, cy + 12f.dp, 60f, 60f, false, strokePaint)
            else -> canvas.drawArc(cx - mouthW * 0.5f, cy - 5f.dp, cx + mouthW * 0.5f, cy + 16f.dp, 18f, 144f, false, strokePaint)
        }
    }

    private fun drawArm(canvas: Canvas, w: Float, h: Float) {
        val cx = w * 0.5f
        val torsoTop = torsoTopY(w, h)
        val shoulderX = cx + w * 0.20f
        val shoulderY = torsoTop + h * 0.04f
        val wristX = currentState.handX * w
        val wristY = handYWithIdleBreath(h)
        val elbowX = currentState.elbowX * w
        val elbowY = currentState.elbowY * h

        strokePaint.color = SKIN
        strokePaint.strokeWidth = 12f.dp
        canvas.drawLine(shoulderX, shoulderY, elbowX, elbowY, strokePaint)
        strokePaint.strokeWidth = 10f.dp
        canvas.drawLine(elbowX, elbowY, wristX, wristY, strokePaint)
    }

    private fun drawHand(canvas: Canvas, w: Float, h: Float) {
        val wristX = currentState.handX * w
        val wristY = handYWithIdleBreath(h)
        val elbowX = currentState.elbowX * w
        val elbowY = currentState.elbowY * h
        val angle = Math.toDegrees(atan2((wristY - elbowY).toDouble(), (wristX - elbowX).toDouble()) + PI / 2.0).toFloat()
        canvas.save()
        canvas.rotate(angle, wristX, wristY)
        when (currentState.shape) {
            HandShape.OPEN_PALM -> drawOpenPalm(canvas, wristX, wristY)
            HandShape.FIST -> drawFist(canvas, wristX, wristY)
            HandShape.FLAT_PALM -> drawFlatPalm(canvas, wristX, wristY)
            HandShape.PINCH -> drawPinch(canvas, wristX, wristY)
            HandShape.TWO_FINGERS -> drawTwoFingers(canvas, wristX, wristY)
            HandShape.W_HAND -> drawWHand(canvas, wristX, wristY)
        }
        canvas.restore()
    }

    private fun drawOpenPalm(canvas: Canvas, x: Float, y: Float) {
        drawPalm(canvas, x, y, 22f.dp, 28f.dp, 8f.dp)
        strokePaint.strokeWidth = 5f.dp
        val baseY = y - 12f.dp
        drawFinger(canvas, 0, x - 11f.dp, baseY + 3f.dp, x - 27f.dp, baseY - 15f.dp, true)
        drawFinger(canvas, 1, x - 7f.dp, baseY, x - 7f.dp, baseY - 30f.dp, true)
        drawFinger(canvas, 2, x, baseY - 1f.dp, x, baseY - 33f.dp, true)
        drawFinger(canvas, 3, x + 7f.dp, baseY, x + 7f.dp, baseY - 30f.dp, true)
        drawFinger(canvas, 4, x + 13f.dp, baseY + 2f.dp, x + 13f.dp, baseY - 20f.dp, true)
    }

    private fun drawFist(canvas: Canvas, x: Float, y: Float) {
        drawPalm(canvas, x, y, 20f.dp, 22f.dp, 7f.dp)
        fillPaint.color = SKIN
        strokePaint.color = SKIN_STROKE
        strokePaint.strokeWidth = 1.2f.dp
        val top = y - 11f.dp
        for (i in 0 until 4) {
            val cx = x - 7.5f.dp + i * 5f.dp
            canvas.drawCircle(cx, top, 5f.dp, fillPaint)
            canvas.drawCircle(cx, top, 5f.dp, strokePaint)
        }
        strokePaint.color = FINGER_THUMB
        strokePaint.strokeWidth = 4f.dp
        canvas.drawLine(x - 9f.dp, y + 4f.dp, x - 16f.dp, y - 7f.dp, strokePaint)
    }

    private fun drawFlatPalm(canvas: Canvas, x: Float, y: Float) {
        drawPalm(canvas, x, y, 28f.dp, 18f.dp, 7f.dp)
        strokePaint.strokeWidth = 4.5f.dp
        val startY = y - 9f.dp
        for (i in 0 until 4) {
            val fx = x - 9f.dp + i * 6f.dp
            drawFinger(canvas, i + 1, fx, startY, fx, startY - 16f.dp, false)
        }
    }

    private fun drawPinch(canvas: Canvas, x: Float, y: Float) {
        fillPaint.color = SKIN
        strokePaint.color = SKIN_STROKE
        strokePaint.strokeWidth = 1.5f.dp
        canvas.drawOval(x - 9f.dp, y - 10f.dp, x + 9f.dp, y + 10f.dp, fillPaint)
        canvas.drawOval(x - 9f.dp, y - 10f.dp, x + 9f.dp, y + 10f.dp, strokePaint)
        strokePaint.strokeWidth = 5f.dp
        drawFinger(canvas, 0, x - 6f.dp, y - 6f.dp, x - 2f.dp, y - 22f.dp, true)
        drawFinger(canvas, 1, x + 6f.dp, y - 6f.dp, x + 2f.dp, y - 22f.dp, true)
        drawFinger(canvas, 2, x + 4f.dp, y - 3f.dp, x + 12f.dp, y - 12f.dp, false)
        drawFinger(canvas, 3, x + 6f.dp, y + 2f.dp, x + 15f.dp, y - 5f.dp, false)
        drawFinger(canvas, 4, x + 6f.dp, y + 6f.dp, x + 14f.dp, y + 2f.dp, false)
    }

    private fun drawTwoFingers(canvas: Canvas, x: Float, y: Float) {
        drawPalm(canvas, x, y, 20f.dp, 22f.dp, 7f.dp)
        strokePaint.strokeWidth = 5f.dp
        val startY = y - 10f.dp
        drawFinger(canvas, 1, x - 5f.dp, startY, x - 7f.dp, startY - 30f.dp, true)
        drawFinger(canvas, 2, x + 4f.dp, startY, x + 5f.dp, startY - 30f.dp, true)
        drawFinger(canvas, 0, x - 9f.dp, y + 1f.dp, x - 17f.dp, y - 8f.dp, false)
        drawFinger(canvas, 3, x + 7f.dp, y - 1f.dp, x + 15f.dp, y - 9f.dp, false)
        drawFinger(canvas, 4, x + 8f.dp, y + 4f.dp, x + 15f.dp, y + 1f.dp, false)
    }

    private fun drawWHand(canvas: Canvas, x: Float, y: Float) {
        drawPalm(canvas, x, y, 22f.dp, 22f.dp, 7f.dp)
        strokePaint.strokeWidth = 5f.dp
        val startY = y - 11f.dp
        drawFinger(canvas, 1, x - 7f.dp, startY, x - 18f.dp, startY - 28f.dp, true)
        drawFinger(canvas, 2, x, startY, x, startY - 32f.dp, true)
        drawFinger(canvas, 3, x + 7f.dp, startY, x + 18f.dp, startY - 28f.dp, true)
        drawFinger(canvas, 0, x - 9f.dp, y + 2f.dp, x - 16f.dp, y - 5f.dp, false)
        drawFinger(canvas, 4, x + 9f.dp, y + 2f.dp, x + 16f.dp, y - 5f.dp, false)
    }

    private fun drawPalm(canvas: Canvas, x: Float, y: Float, palmW: Float, palmH: Float, radius: Float) {
        val left = x - palmW * 0.5f
        val top = y - palmH * 0.5f
        val right = x + palmW * 0.5f
        val bottom = y + palmH * 0.5f
        fillPaint.color = SKIN
        strokePaint.color = SKIN_STROKE
        strokePaint.strokeWidth = 1.5f.dp
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, fillPaint)
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, strokePaint)
    }

    private fun drawFinger(
        canvas: Canvas,
        index: Int,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        tip: Boolean
    ) {
        strokePaint.color = fingerColors[index.coerceIn(0, fingerColors.lastIndex)]
        canvas.drawLine(startX, startY, endX, endY, strokePaint)
        if (tip) {
            fillPaint.color = strokePaint.color
            canvas.drawCircle(endX, endY, 7f.dp, fillPaint)
        }
    }

    private fun drawFingerspell(canvas: Canvas, w: Float, h: Float) {
        val cx = currentState.handX * w
        val cy = handYWithIdleBreath(h)
        val alpha = (currentFingerAlpha.coerceIn(0f, 1f) * 255f).toInt()
        fillPaint.color = withAlpha(AVATAR_BG, alpha)
        canvas.drawCircle(cx, cy, 48f.dp, fillPaint)
        strokePaint.color = withAlpha(PRIMARY_TEAL, alpha)
        strokePaint.strokeWidth = 2f.dp
        canvas.drawCircle(cx, cy, 48f.dp, strokePaint)
        textPaint.color = withAlpha(PRIMARY_TEAL, alpha)
        val baseline = cy - (textPaint.ascent() + textPaint.descent()) * 0.5f
        canvas.drawText(currentFingerText, cx, baseline, textPaint)
    }

    private fun drawStateText(canvas: Canvas, w: Float, h: Float) {
        captionPaint.color = TEXT_SECONDARY
        canvas.drawText(debugStateText, w * 0.5f, h - 10f.dp, captionPaint)
    }

    private fun torsoTopY(w: Float, h: Float): Float {
        val headR = w * 0.14f
        val headCy = h * 0.28f
        return headCy + headR * 0.74f + h * 0.06f - 4f.dp
    }

    private fun handYWithIdleBreath(h: Float): Float {
        val base = currentState.handY * h
        if (isSigning) return base
        val normalizedOffset = 0.012f * sin(breathingPhase.toDouble()).toFloat() * h
        val pixelOffset = 8f.dp * sin(breathingPhase.toDouble()).toFloat()
        return base + normalizedOffset + pixelOffset
    }

    private fun requestDraw() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { requestDraw() }
            return
        }
        if (!viewAttached) return
        if (drawPosted) return
        drawPosted = true
        postInvalidateOnAnimation()
    }

    private fun idleState(): FrameState {
        return FrameState(
            handX = IDLE_HAND_X,
            handY = IDLE_HAND_Y,
            elbowX = IDLE_ELBOW_X,
            elbowY = IDLE_ELBOW_Y,
            shape = HandShape.OPEN_PALM,
            face = FaceExpression.FRIENDLY
        )
    }

    private fun Keyframe.toState(): FrameState {
        return FrameState(handX, handY, elbowX, elbowY, shape, face)
    }

    private fun smoothStep(t: Float): Float = t * t * (3f - 2f * t)

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun parseShape(value: String): HandShape {
        return when (value.lowercase(Locale.US)) {
            "fist" -> HandShape.FIST
            "flat_palm" -> HandShape.FLAT_PALM
            "pinch" -> HandShape.PINCH
            "two_fingers_open", "two_fingers" -> HandShape.TWO_FINGERS
            "w_hand" -> HandShape.W_HAND
            else -> HandShape.OPEN_PALM
        }
    }

    private fun parseFace(value: String): FaceExpression {
        return when (value.lowercase(Locale.US)) {
            "friendly" -> FaceExpression.FRIENDLY
            "confirm" -> FaceExpression.CONFIRM
            "firm" -> FaceExpression.FIRM
            "warm" -> FaceExpression.WARM
            else -> FaceExpression.NEUTRAL
        }
    }

    private fun JSONObject.floatValue(name: String, defaultValue: Float): Float {
        return optDouble(name, defaultValue.toDouble()).toFloat()
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }

    private val Float.dp: Float get() = this * resources.displayMetrics.density
    private val Float.sp: Float get() = this * resources.displayMetrics.scaledDensity

    companion object {
        private const val IDLE_HAND_X = 0.88f
        private const val IDLE_HAND_Y = 0.52f
        private const val IDLE_ELBOW_X = 0.66f
        private const val IDLE_ELBOW_Y = 0.58f
        private const val FINGERSPELL_STEP_MS = 300L
        private const val TWO_PI = (Math.PI * 2.0).toFloat()
        private const val IDLE_FRAME_INTERVAL_MS = 66L

        private const val AVATAR_BG = 0xFF1E1E26.toInt()
        private const val SURFACE_WHITE = 0xFF17171C.toInt()
        private const val PRIMARY_TEAL = 0xFFB8F060.toInt()
        private const val PRIMARY_DARK = 0xFF9ED84A.toInt()
        private const val TEXT_PRIMARY = 0xFFEDEDEA.toInt()
        private const val TEXT_SECONDARY = 0xFF989894.toInt()
        private const val SKIN = 0xFFF2C9A8.toInt()
        private const val SKIN_STROKE = 0xFFD9A47D.toInt()
        private const val HAND_STROKE = 0xFFD09266.toInt()
        private const val HAIR = 0xFF3A261D.toInt()
        private const val EYE = 0xFF2B1A13.toInt()
        private const val SHIRT = 0xFF0B3F43.toInt()
        private const val SHIRT_HIGHLIGHT = 0xFF18686D.toInt()
        private const val SHADOW_30 = 0x4D000000

        private const val FINGER_THUMB = 0xFFFFB300.toInt()
        private const val FINGER_INDEX = 0xFF00C853.toInt()
        private const val FINGER_MIDDLE = 0xFF2196F3.toInt()
        private const val FINGER_RING = 0xFF9C27B0.toInt()
        private const val FINGER_PINKY = 0xFFF44336.toInt()
    }

    private val fingerColors = intArrayOf(
        FINGER_THUMB,
        FINGER_INDEX,
        FINGER_MIDDLE,
        FINGER_RING,
        FINGER_PINKY
    )
}
