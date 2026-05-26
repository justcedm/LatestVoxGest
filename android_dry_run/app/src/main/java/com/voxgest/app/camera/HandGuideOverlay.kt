package com.voxgest.app.camera

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.max

class HandGuideOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    enum class GuideState {
        NO_HAND,
        HAND_FOUND,
        HAND_LOCKED,
        WRONG_ZONE
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private var state: GuideState = GuideState.NO_HAND
    private var normalizedBox: RectF? = null
    private var profileName: String = ""
    private var lockedHandedness: String? = null
    private var lockedWord: String? = null
    private var pulseAlpha: Float = 0.50f
    private var pulseAnimator: ValueAnimator? = null

    init {
        setWillNotDraw(false)
        contentDescription = null
    }

    fun setState(
        nextState: GuideState,
        handBox: RectF?,
        wordProfile: String,
        lockedHand: String? = null,
        wordLabel: String? = null
    ) {
        state = nextState
        normalizedBox = handBox?.let { RectF(it) }
        profileName = wordProfile
        lockedHandedness = lockedHand
        lockedWord = wordLabel
        invalidate()
    }

    fun isInIdealZone(handBox: RectF?): Boolean {
        val box = handBox ?: return false
        val ideal = idealZoneNormalized()
        return ideal.contains(box.centerX(), box.centerY())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startPulse()
    }

    override fun onDetachedFromWindow() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (state) {
            GuideState.NO_HAND -> drawNoHand(canvas)
            GuideState.HAND_FOUND -> drawTrackedHand(canvas, GREEN, false, "Tracking")
            GuideState.HAND_LOCKED -> drawTrackedHand(canvas, TEAL, true, lockedLabel())
            GuideState.WRONG_ZONE -> drawWrongZone(canvas)
        }
    }

    private fun drawNoHand(canvas: Canvas) {
        val rect = idealZonePixels()
        strokePaint.color = withAlpha(Color.WHITE, (pulseAlpha * 255f).toInt())
        strokePaint.strokeWidth = 2f.dp
        strokePaint.pathEffect = DashPathEffect(floatArrayOf(12f.dp, 8f.dp), 0f)
        canvas.drawRoundRect(rect, 18f.dp, 18f.dp, strokePaint)
        strokePaint.pathEffect = null

        textPaint.color = Color.WHITE
        textPaint.textSize = 12f.sp
        textPaint.setShadowLayer(4f.dp, 0f, 1f.dp, 0x99000000.toInt())
        canvas.drawText("Show hand here", rect.centerX(), rect.bottom + 22f.dp, textPaint)
        textPaint.clearShadowLayer()
    }

    private fun drawTrackedHand(canvas: Canvas, color: Int, locked: Boolean, label: String) {
        val rect = handBoxPixels() ?: return drawNoHand(canvas)
        if (locked) {
            fillPaint.color = withAlpha(color, 26)
            canvas.drawRoundRect(rect, 10f.dp, 10f.dp, fillPaint)
        }
        strokePaint.color = color
        strokePaint.strokeWidth = if (locked) 2.5f.dp else 2f.dp
        strokePaint.pathEffect = null
        canvas.drawRoundRect(rect, 10f.dp, 10f.dp, strokePaint)
        drawCornerBrackets(canvas, rect, color)

        textPaint.color = Color.WHITE
        textPaint.textSize = 11f.sp
        textPaint.setShadowLayer(4f.dp, 0f, 1f.dp, 0x99000000.toInt())
        canvas.drawText(label, rect.centerX(), rect.top + 18f.dp, textPaint)
        textPaint.clearShadowLayer()
    }

    private fun drawWrongZone(canvas: Canvas) {
        val rect = handBoxPixels() ?: return drawNoHand(canvas)
        strokePaint.color = AMBER
        strokePaint.strokeWidth = 2f.dp
        strokePaint.pathEffect = null
        canvas.drawRoundRect(rect, 10f.dp, 10f.dp, strokePaint)

        textPaint.color = AMBER
        textPaint.textSize = 11f.sp
        textPaint.typeface = Typeface.MONOSPACE
        canvas.drawText("Move to center", rect.centerX(), max(18f.dp, rect.top - 8f.dp), textPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun drawCornerBrackets(canvas: Canvas, rect: RectF, color: Int) {
        val length = 24f.dp
        strokePaint.color = color
        strokePaint.strokeWidth = 3f.dp
        strokePaint.pathEffect = null
        canvas.drawLine(rect.left, rect.top, rect.left + length, rect.top, strokePaint)
        canvas.drawLine(rect.left, rect.top, rect.left, rect.top + length, strokePaint)
        canvas.drawLine(rect.right, rect.top, rect.right - length, rect.top, strokePaint)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + length, strokePaint)
        canvas.drawLine(rect.left, rect.bottom, rect.left + length, rect.bottom, strokePaint)
        canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - length, strokePaint)
        canvas.drawLine(rect.right, rect.bottom, rect.right - length, rect.bottom, strokePaint)
        canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - length, strokePaint)
    }

    private fun handBoxPixels(): RectF? {
        val src = normalizedBox ?: return null
        val pad = 16f.dp
        return RectF(
            (src.left * width) - pad,
            (src.top * height) - pad,
            (src.right * width) + pad,
            (src.bottom * height) + pad
        ).apply {
            left = left.coerceIn(0f, width.toFloat())
            top = top.coerceIn(0f, height.toFloat())
            right = right.coerceIn(0f, width.toFloat())
            bottom = bottom.coerceIn(0f, height.toFloat())
        }
    }

    private fun idealZonePixels(): RectF {
        val normalized = idealZoneNormalized()
        return RectF(
            normalized.left * width,
            normalized.top * height,
            normalized.right * width,
            normalized.bottom * height
        )
    }

    private fun idealZoneNormalized(): RectF {
        return RectF(0.20f, 0.15f, 0.80f, 0.85f)
    }

    private fun lockedLabel(): String {
        val word = lockedWord?.takeIf { it.isNotBlank() } ?: "Signing..."
        return if (profileName.isBlank()) {
            "Locked - $word"
        } else {
            "Locked - $profileName"
        }.let { base ->
            if (lockedHandedness.isNullOrBlank()) base else "$base (${lockedHandedness})"
        }
    }

    private fun startPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0.35f, 0.85f).apply {
            duration = 1100L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                pulseAlpha = it.animatedValue as Float
                if (state == GuideState.NO_HAND) invalidate()
            }
            start()
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }

    private val Float.dp: Float get() = this * resources.displayMetrics.density
    private val Float.sp: Float get() = this * resources.displayMetrics.scaledDensity

    companion object {
        private const val GREEN = 0xFF00C853.toInt()
        private const val TEAL = 0xFF00897B.toInt()
        private const val AMBER = 0xFFFFB300.toInt()
    }
}
