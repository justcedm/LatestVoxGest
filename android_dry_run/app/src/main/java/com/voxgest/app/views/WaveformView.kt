package com.voxgest.app.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.voxgest.dryrun.R
import kotlin.math.sin

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.primary_teal)
        alpha = 105
    }
    private var phase = 0f
    private var animator: ValueAnimator? = null

    fun setListening(listening: Boolean) {
        animator?.cancel()
        if (listening) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 600L
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    phase = it.animatedFraction
                    invalidate()
                }
                start()
            }
        } else {
            phase = 0f
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val bars = 7
        val gap = width / (bars * 2f)
        val centerY = height / 2f
        val barWidth = 3f.dp
        for (i in 0 until bars) {
            val wave = if (phase == 0f) 0f else sin((phase * 6.28f) + i * 0.8f)
            val barHeight = 4f.dp + (8f.dp + i % 3 * 4f.dp) * (0.45f + 0.55f * kotlin.math.abs(wave))
            val x = gap + i * gap * 2f
            canvas.drawRoundRect(RectF(x, centerY - barHeight / 2f, x + barWidth, centerY + barHeight / 2f), 2f.dp, 2f.dp, paint)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private val Float.dp: Float get() = this * resources.displayMetrics.density
}
