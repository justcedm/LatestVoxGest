package com.voxgest.app.avatar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.cos
import kotlin.math.sin

class SignAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {
    private val currentPose = AvatarPose()
    private val pendingClips = mutableListOf<AvatarClip>()

    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null
    private var avatarAnimator: AvatarAnimator? = null
    private var drawing = false
    private var surfaceReady = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = BG_COLOR
    }
    private val skinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = SKIN
    }
    private val skinStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = SKIN
    }
    private val skinOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = SKIN_STROKE
    }
    private val torsoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = TORSO
    }
    private val torsoStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = TORSO_DARK
    }
    private val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val hairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = HAIR
    }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(22, 26, 28)
    }
    private val eyeHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val smilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.rgb(96, 57, 44)
    }

    private val torsoPath = Path()
    private val hairPath = Path()
    private val rect = RectF()
    private val rect2 = RectF()
    private val rect3 = RectF()

    private var figH = 0f
    private var figW = 0f
    private var centerX = 0f
    private var topY = 0f
    private var headRadius = 0f
    private var headCx = 0f
    private var headCy = 0f
    private var neckW = 0f
    private var neckH = 0f
    private var torsoTop = 0f
    private var torsoBottom = 0f
    private var torsoTopW = 0f
    private var torsoBottomW = 0f
    private var leftShoulderX = 0f
    private var rightShoulderX = 0f
    private var shoulderY = 0f
    private var upperArmLen = 0f
    private var forearmLen = 0f
    private var handLen = 0f
    private var armWidth = 0f
    private var forearmWidth = 0f
    private var palmW = 0f
    private var palmH = 0f
    private var fingerLen = 0f
    private var fingerW = 0f

    private var fpsStartNs = 0L
    private var fpsFrames = 0

    private val drawRunnable = object : Runnable {
        override fun run() {
            if (!drawing || !surfaceReady) return
            drawSurfaceFrame()
            renderHandler?.postDelayed(this, FRAME_DELAY_MS)
        }
    }

    init {
        holder.addCallback(this)
        isFocusable = false
        contentDescription = "VoxGest signing avatar"
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        startRenderThread()
        renderHandler?.post {
            drawing = true
            currentPose.resetToIdle()
            if (pendingClips.isNotEmpty()) {
                avatarAnimator?.playSequence(pendingClips.toList())
                pendingClips.clear()
            } else {
                avatarAnimator?.playIdle()
            }
            drawRunnable.run()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        renderHandler?.post { configureMetrics(width.toFloat(), height.toFloat()) }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        renderHandler?.post {
            drawing = false
            avatarAnimator?.stop()
        }
    }

    override fun onDetachedFromWindow() {
        releaseRenderer()
        super.onDetachedFromWindow()
    }

    fun playClip(clip: AvatarClip) {
        val handler = renderHandler
        if (handler == null) {
            pendingClips.clear()
            pendingClips += clip
            return
        }
        handler.post { avatarAnimator?.playClip(clip) { avatarAnimator?.playIdle() } }
    }

    fun playSequence(clips: List<AvatarClip>, gapMs: Long = 200L) {
        if (clips.isEmpty()) {
            playClip(AvatarClip.IDLE)
            return
        }
        val handler = renderHandler
        if (handler == null) {
            pendingClips.clear()
            pendingClips += clips
            return
        }
        handler.post { avatarAnimator?.playSequence(clips, gapMs) }
    }

    fun signText(text: String) {
        val clips = AvatarClip.sentenceToClips(text)
        playSequence(clips)
    }

    fun animateIdleBreathing() {
        renderHandler?.post { avatarAnimator?.playIdle() }
    }

    fun setListening(active: Boolean) {
        if (active) animateIdleBreathing()
    }

    fun setStatus(status: AvatarStatus) {
        if (status == AvatarStatus.STOPPED) {
            renderHandler?.post { avatarAnimator?.stop() }
        } else if (status == AvatarStatus.READY || status == AvatarStatus.LISTENING) {
            animateIdleBreathing()
        }
    }

    private fun startRenderThread() {
        if (renderThread != null) return
        val thread = HandlerThread("VoxGestSignAvatarRenderer").also { it.start() }
        val handler = Handler(thread.looper)
        renderThread = thread
        renderHandler = handler
        avatarAnimator = AvatarAnimator(handler) { pose ->
            currentPose.copyFrom(pose)
        }
        handler.post {
            configureMetrics(width.toFloat(), height.toFloat())
        }
    }

    private fun releaseRenderer() {
        val handler = renderHandler
        drawing = false
        surfaceReady = false
        if (handler != null) {
            handler.post {
                avatarAnimator?.release()
                avatarAnimator = null
                renderThread?.quitSafely()
                renderThread = null
                renderHandler = null
            }
        } else {
            renderThread?.quitSafely()
            renderThread = null
        }
    }

    private fun drawSurfaceFrame() {
        val canvas = try {
            holder.lockCanvas()
        } catch (_: Throwable) {
            null
        } ?: return
        try {
            render(canvas)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun configureMetrics(width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        figH = height * 0.85f
        figW = width * 0.62f
        centerX = width * 0.5f
        topY = (height - figH) * 0.48f
        headRadius = figH * 0.095f
        headCx = centerX
        headCy = topY + figH * 0.15f
        neckW = figW * 0.16f
        neckH = figH * 0.075f
        torsoTop = headCy + headRadius * 1.10f
        torsoBottom = topY + figH * 0.98f
        torsoTopW = figW * 0.62f
        torsoBottomW = figW * 0.42f
        shoulderY = torsoTop + figH * 0.035f
        leftShoulderX = centerX - torsoTopW * 0.58f
        rightShoulderX = centerX + torsoTopW * 0.58f
        upperArmLen = figH * 0.215f
        forearmLen = figH * 0.205f
        handLen = figH * 0.090f
        armWidth = figH * 0.063f
        forearmWidth = figH * 0.052f
        palmW = handLen * 0.78f
        palmH = handLen * 0.62f
        fingerLen = handLen * 0.54f
        fingerW = palmH * 0.21f
        skinStrokePaint.strokeWidth = armWidth
        torsoStrokePaint.strokeWidth = figH * 0.006f
        smilePaint.strokeWidth = figH * 0.006f
        fadePaint.shader = LinearGradient(
            0f,
            torsoBottom - figH * 0.16f,
            0f,
            torsoBottom + figH * 0.05f,
            TORSO,
            BG_COLOR,
            Shader.TileMode.CLAMP
        )
    }

    private fun render(canvas: Canvas) {
        if (figH <= 0f) configureMetrics(canvas.width.toFloat(), canvas.height.toFloat())
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), bgPaint)

        drawTorso(canvas)
        drawNeck(canvas)
        drawArm(
            canvas,
            leftShoulderX,
            shoulderY,
            currentPose.leftUpperArm,
            currentPose.leftForearm,
            currentPose.leftHand,
            currentPose.leftFingers,
            currentPose.leftHandOffsetX,
            currentPose.leftHandOffsetY
        )
        drawArm(
            canvas,
            rightShoulderX,
            shoulderY,
            currentPose.rightUpperArm,
            currentPose.rightForearm,
            currentPose.rightHand,
            currentPose.rightFingers,
            currentPose.rightHandOffsetX,
            currentPose.rightHandOffsetY
        )
        drawHead(canvas)
        trackFps()
    }

    private fun drawTorso(canvas: Canvas) {
        canvas.save()
        canvas.scale(1f, currentPose.chestBreath, centerX, torsoTop + figH * 0.18f)
        torsoPath.reset()
        torsoPath.moveTo(centerX - torsoTopW * 0.5f, torsoTop)
        torsoPath.quadTo(centerX - torsoTopW * 0.62f, torsoTop + figH * 0.09f, centerX - torsoBottomW * 0.5f, torsoBottom)
        torsoPath.lineTo(centerX + torsoBottomW * 0.5f, torsoBottom)
        torsoPath.quadTo(centerX + torsoTopW * 0.62f, torsoTop + figH * 0.09f, centerX + torsoTopW * 0.5f, torsoTop)
        torsoPath.quadTo(centerX, torsoTop - figH * 0.045f, centerX - torsoTopW * 0.5f, torsoTop)
        torsoPath.close()
        canvas.drawPath(torsoPath, torsoPaint)
        canvas.drawPath(torsoPath, torsoStrokePaint)
        rect.set(centerX - torsoBottomW * 0.52f, torsoBottom - figH * 0.14f, centerX + torsoBottomW * 0.52f, torsoBottom + figH * 0.04f)
        canvas.drawRect(rect, fadePaint)
        canvas.restore()
    }

    private fun drawNeck(canvas: Canvas) {
        rect.set(centerX - neckW * 0.5f, headCy + headRadius * 0.72f, centerX + neckW * 0.5f, torsoTop + neckH * 0.42f)
        canvas.drawRoundRect(rect, neckW * 0.26f, neckW * 0.26f, skinPaint)
        canvas.drawRoundRect(rect, neckW * 0.26f, neckW * 0.26f, skinOutlinePaint)
    }

    private fun drawHead(canvas: Canvas) {
        canvas.save()
        canvas.rotate(currentPose.headTilt, headCx, headCy)
        canvas.drawCircle(headCx, headCy, headRadius, skinPaint)
        canvas.drawCircle(headCx, headCy, headRadius, skinOutlinePaint)

        hairPath.reset()
        hairPath.moveTo(headCx - headRadius * 0.96f, headCy - headRadius * 0.08f)
        hairPath.cubicTo(
            headCx - headRadius * 0.92f,
            headCy - headRadius * 1.24f,
            headCx + headRadius * 0.92f,
            headCy - headRadius * 1.24f,
            headCx + headRadius * 0.96f,
            headCy - headRadius * 0.08f
        )
        hairPath.cubicTo(
            headCx + headRadius * 0.92f,
            headCy + headRadius * 0.28f,
            headCx + headRadius * 0.58f,
            headCy + headRadius * 0.36f,
            headCx + headRadius * 0.44f,
            headCy + headRadius * 0.06f
        )
        hairPath.lineTo(headCx - headRadius * 0.44f, headCy + headRadius * 0.06f)
        hairPath.cubicTo(
            headCx - headRadius * 0.58f,
            headCy + headRadius * 0.36f,
            headCx - headRadius * 0.92f,
            headCy + headRadius * 0.28f,
            headCx - headRadius * 0.96f,
            headCy - headRadius * 0.08f
        )
        hairPath.close()
        canvas.drawPath(hairPath, hairPaint)

        rect3.set(headCx - headRadius * 1.04f, headCy - headRadius * 0.10f, headCx - headRadius * 0.64f, headCy + headRadius * 0.90f)
        canvas.drawRoundRect(rect3, headRadius * 0.20f, headRadius * 0.20f, hairPaint)
        rect3.set(headCx + headRadius * 0.64f, headCy - headRadius * 0.10f, headCx + headRadius * 1.04f, headCy + headRadius * 0.90f)
        canvas.drawRoundRect(rect3, headRadius * 0.20f, headRadius * 0.20f, hairPaint)

        drawEye(canvas, headCx - headRadius * 0.34f, headCy - headRadius * 0.07f)
        drawEye(canvas, headCx + headRadius * 0.34f, headCy - headRadius * 0.07f)
        rect.set(headCx - headRadius * 0.28f, headCy + headRadius * 0.22f, headCx + headRadius * 0.28f, headCy + headRadius * 0.55f)
        canvas.drawArc(rect, 18f, 144f, false, smilePaint)
        canvas.restore()
    }

    private fun drawEye(canvas: Canvas, x: Float, y: Float) {
        rect.set(x - headRadius * 0.105f, y - headRadius * 0.065f, x + headRadius * 0.105f, y + headRadius * 0.065f)
        canvas.drawOval(rect, eyePaint)
        rect2.set(x + headRadius * 0.018f, y - headRadius * 0.044f, x + headRadius * 0.064f, y + headRadius * 0.002f)
        canvas.drawOval(rect2, eyeHighlightPaint)
    }

    private fun drawArm(
        canvas: Canvas,
        shoulderX: Float,
        shoulderY: Float,
        upper: Segment,
        forearm: Segment,
        hand: Segment,
        fingers: FingerPose,
        offsetXRatio: Float,
        offsetYRatio: Float
    ) {
        upper.length = upperArmLen
        forearm.length = forearmLen
        hand.length = handLen

        val upperRad = upper.angleDeg.toRadians()
        val elbowX = shoulderX + cos(upperRad) * upper.length
        val elbowY = shoulderY + sin(upperRad) * upper.length
        val foreRad = (upper.angleDeg + forearm.angleDeg).toRadians()
        val wristX = elbowX + cos(foreRad) * forearm.length + offsetXRatio * figW
        val wristY = elbowY + sin(foreRad) * forearm.length + offsetYRatio * figH

        skinStrokePaint.strokeWidth = armWidth
        canvas.drawLine(shoulderX, shoulderY, elbowX, elbowY, skinStrokePaint)
        skinStrokePaint.strokeWidth = forearmWidth
        canvas.drawLine(elbowX, elbowY, wristX, wristY, skinStrokePaint)
        drawHand(canvas, wristX, wristY, upper.angleDeg + forearm.angleDeg + hand.angleDeg, fingers)
    }

    private fun drawHand(canvas: Canvas, wristX: Float, wristY: Float, angleDeg: Float, fingers: FingerPose) {
        canvas.save()
        canvas.translate(wristX, wristY)
        canvas.rotate(angleDeg)
        rect.set(-palmW * 0.05f, -palmH * 0.50f, palmW, palmH * 0.50f)
        canvas.drawRoundRect(rect, palmH * 0.32f, palmH * 0.32f, skinPaint)
        canvas.drawRoundRect(rect, palmH * 0.32f, palmH * 0.32f, skinOutlinePaint)

        drawFinger(canvas, -0.40f - fingers.spread, fingers.indexCurl)
        drawFinger(canvas, -0.13f - fingers.spread * 0.25f, fingers.middleCurl)
        drawFinger(canvas, 0.13f + fingers.spread * 0.25f, fingers.ringCurl)
        drawFinger(canvas, 0.40f + fingers.spread, fingers.pinkyCurl)
        drawThumb(canvas, fingers)
        canvas.restore()
    }

    private fun drawFinger(canvas: Canvas, yRatio: Float, curl: Float) {
        val y = yRatio * palmH
        val visibleLen = fingerLen * (1f - curl.coerceIn(0f, 1f) * 0.72f)
        rect.set(palmW * 0.74f, y - fingerW * 0.48f, palmW * 0.74f + visibleLen, y + fingerW * 0.48f)
        canvas.drawRoundRect(rect, fingerW * 0.48f, fingerW * 0.48f, skinPaint)
        canvas.drawRoundRect(rect, fingerW * 0.48f, fingerW * 0.48f, skinOutlinePaint)
        if (curl > 0.35f) {
            rect2.set(palmW * 0.70f, y - fingerW * 0.32f, palmW * 0.70f + visibleLen * 0.48f, y + fingerW * 0.32f)
            canvas.drawRoundRect(rect2, fingerW * 0.32f, fingerW * 0.32f, skinPaint)
        }
    }

    private fun drawThumb(canvas: Canvas, fingers: FingerPose) {
        val thumbLen = fingerLen * (0.88f - fingers.thumbCurl.coerceIn(0f, 1f) * 0.48f)
        canvas.save()
        canvas.translate(palmW * 0.30f, palmH * 0.38f)
        canvas.rotate(48f + fingers.thumbAngle)
        skinStrokePaint.strokeWidth = fingerW * 1.18f
        canvas.drawLine(0f, 0f, thumbLen, 0f, skinStrokePaint)
        canvas.restore()
    }

    private fun trackFps() {
        val now = System.nanoTime()
        if (fpsStartNs == 0L) {
            fpsStartNs = now
            fpsFrames = 0
            return
        }
        fpsFrames += 1
        val elapsed = now - fpsStartNs
        if (elapsed >= FPS_LOG_WINDOW_NS) {
            val fps = fpsFrames * 1_000_000_000f / elapsed.toFloat()
            Log.i(TAG, "fps=${fps.toInt()}")
            fpsStartNs = now
            fpsFrames = 0
        }
    }

    private fun Float.toRadians(): Float = this * 0.017453292f

    companion object {
        private const val TAG = "VoxGestSignAvatar"
        private const val FRAME_DELAY_MS = 8L
        private const val FPS_LOG_WINDOW_NS = 2_000_000_000L
        private const val BG_COLOR = 0xFFE5F0F1.toInt()
        private const val SKIN = 0xFFE8C9A0.toInt()
        private const val SKIN_STROKE = 0xFFC4956A.toInt()
        private const val TORSO = 0xFF18686D.toInt()
        private const val TORSO_DARK = 0xFF0D4F55.toInt()
        private const val HAIR = 0xFF1A0E08.toInt()
    }
}
