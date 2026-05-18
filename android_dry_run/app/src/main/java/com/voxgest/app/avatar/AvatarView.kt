package com.voxgest.app.avatar

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.voxgest.dryrun.MotionSettings
import com.voxgest.dryrun.R
import java.util.Locale
import kotlin.math.sin

class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f.sp
        color = color(R.color.text_secondary)
    }
    private val mouth = Path()
    private var breathe = 0f
    private var signProgress = 0f
    private var activeWord = "HELLO"
    private var overlayText = ""
    private var animator: ValueAnimator? = null
    private var idleAnimator: ValueAnimator? = null

    init {
        contentDescription = "Avatar signing: HELLO"
        startIdle()
    }

    fun setLandmarks(points: FloatArray) {
        invalidate()
    }

    fun setListening(active: Boolean) {
        overlayText = if (active) "Listening" else ""
        invalidate()
    }

    fun signText(text: String) {
        val cleaned = text.trim().uppercase(Locale.US)
        if (cleaned.isBlank() || cleaned == "NOTHING") {
            activeWord = "READY"
            overlayText = ""
            contentDescription = "Avatar signing: Ready"
            invalidate()
            return
        }
        activeWord = cleaned.split(Regex("[^A-Z]+")).firstOrNull().orEmpty().ifBlank { cleaned }
        overlayText = if (activeWord.length > 8) "Fingerspelling: $activeWord" else ""
        contentDescription = "Avatar signing: $activeWord"
        animator?.cancel()
        if (!MotionSettings.animationsEnabled(context)) {
            signProgress = 1f
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                signProgress = smoothStep(it.animatedValue as Float)
                invalidate()
            }
            start()
        }
    }

    fun animateIdleBreathing() = startIdle()

    private fun startIdle() {
        idleAnimator?.cancel()
        if (!MotionSettings.animationsEnabled(context)) return
        idleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                breathe = sin((it.animatedFraction * Math.PI).toFloat()) * 0.02f
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        idleAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        fill.shader = LinearGradient(0f, 0f, 0f, h, color(R.color.avatar_bg), color(R.color.surface_white), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, fill)
        fill.shader = null

        val cx = w * 0.5f
        val baseY = h * 0.63f
        canvas.save()
        canvas.scale(1f, 1f + breathe, cx, baseY)
        drawShadow(canvas, cx, h)
        drawBody(canvas, cx, baseY)
        drawHead(canvas, cx, h * 0.34f)
        drawArms(canvas, cx, baseY)
        canvas.restore()

        if (overlayText.isNotBlank()) {
            canvas.drawText(overlayText, cx, h - 14f.dp, textPaint)
        }
    }

    private fun drawShadow(canvas: Canvas, cx: Float, h: Float) {
        fill.color = color(R.color.shadow_light)
        canvas.drawOval(cx - 58f.dp, h * 0.86f, cx + 58f.dp, h * 0.92f, fill)
    }

    private fun drawBody(canvas: Canvas, cx: Float, baseY: Float) {
        val torso = RectF(cx - 42f.dp, baseY - 38f.dp, cx + 42f.dp, baseY + 62f.dp)
        fill.color = color(R.color.primary_teal)
        stroke.color = color(R.color.primary_dark)
        stroke.strokeWidth = 2f.dp
        canvas.drawRoundRect(torso, 28f.dp, 28f.dp, fill)
        canvas.drawRoundRect(torso, 28f.dp, 28f.dp, stroke)
        fill.color = color(R.color.primary_dark)
        canvas.drawOval(cx - 40f.dp, baseY - 46f.dp, cx + 40f.dp, baseY - 8f.dp, fill)
    }

    private fun drawHead(canvas: Canvas, cx: Float, cy: Float) {
        fill.color = 0xFF3A261D.toInt()
        canvas.drawOval(cx - 43f.dp, cy - 48f.dp, cx + 43f.dp, cy + 40f.dp, fill)
        fill.color = 0xFFF2C9A8.toInt()
        stroke.color = 0xFFD9A47D.toInt()
        stroke.strokeWidth = 1.5f.dp
        canvas.drawOval(cx - 34f.dp, cy - 34f.dp, cx + 34f.dp, cy + 38f.dp, fill)
        canvas.drawOval(cx - 34f.dp, cy - 34f.dp, cx + 34f.dp, cy + 38f.dp, stroke)
        fill.color = 0xFF2B1A13.toInt()
        canvas.drawOval(cx - 15f.dp, cy - 7f.dp, cx - 7f.dp, cy + 3f.dp, fill)
        canvas.drawOval(cx + 7f.dp, cy - 7f.dp, cx + 15f.dp, cy + 3f.dp, fill)
        stroke.color = color(R.color.text_primary)
        stroke.strokeWidth = 1.8f.dp
        mouth.reset()
        mouth.addArc(RectF(cx - 14f.dp, cy + 7f.dp, cx + 14f.dp, cy + 23f.dp), 18f, 144f)
        canvas.drawPath(mouth, stroke)
        fill.color = 0xFF3A261D.toInt()
        canvas.drawArc(RectF(cx - 38f.dp, cy - 42f.dp, cx + 38f.dp, cy + 8f.dp), 185f, 170f, true, fill)
    }

    private fun drawArms(canvas: Canvas, cx: Float, baseY: Float) {
        val leftHandX = cx - 70f.dp + signProgress * 20f.dp
        val leftHandY = baseY - 8f.dp - signProgress * 45f.dp
        val rightHandX = when (activeWord) {
            "THANKYOU" -> cx + 8f.dp + signProgress * 52f.dp
            "WATER" -> cx + 34f.dp
            else -> cx + 72f.dp - signProgress * 12f.dp
        }
        val rightHandY = when (activeWord) {
            "YES" -> baseY - 20f.dp + sin(signProgress * Math.PI.toFloat()) * 16f.dp
            "WATER" -> baseY - 80f.dp
            else -> baseY - 12f.dp - signProgress * 58f.dp
        }
        stroke.color = color(R.color.primary_dark)
        stroke.strokeWidth = 13f.dp
        canvas.drawLine(cx - 35f.dp, baseY - 20f.dp, leftHandX, leftHandY, stroke)
        canvas.drawLine(cx + 35f.dp, baseY - 20f.dp, rightHandX, rightHandY, stroke)
        drawHand(canvas, leftHandX, leftHandY, -15f)
        drawHand(canvas, rightHandX, rightHandY, 10f)
    }

    private fun drawHand(canvas: Canvas, x: Float, y: Float, angle: Float) {
        canvas.save()
        canvas.rotate(angle, x, y)
        fill.color = 0xFFF2C9A8.toInt()
        stroke.color = 0xFFD09266.toInt()
        stroke.strokeWidth = 1.5f.dp
        canvas.drawRoundRect(RectF(x - 11f.dp, y - 12f.dp, x + 11f.dp, y + 15f.dp), 8f.dp, 8f.dp, fill)
        canvas.drawRoundRect(RectF(x - 11f.dp, y - 12f.dp, x + 11f.dp, y + 15f.dp), 8f.dp, 8f.dp, stroke)
        stroke.strokeWidth = 4f.dp
        val colors = intArrayOf(0xFFFFB300.toInt(), 0xFF00C853.toInt(), 0xFF2196F3.toInt(), 0xFF9C27B0.toInt(), 0xFFF44336.toInt())
        for (i in 0 until 5) {
            stroke.color = colors[i]
            val fx = x - 10f.dp + i * 5f.dp
            canvas.drawLine(fx, y - 10f.dp, fx - 2f.dp, y - 26f.dp, stroke)
        }
        canvas.restore()
    }

    private fun smoothStep(t: Float): Float = t * t * (3f - 2f * t)
    private fun color(id: Int): Int = context.getColor(id)
    private val Float.dp: Float get() = this * resources.displayMetrics.density
    private val Float.sp: Float get() = this * resources.displayMetrics.scaledDensity
}
