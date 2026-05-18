package com.voxgest.app.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.voxgest.dryrun.R

class SigningPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        fill.shader = LinearGradient(0f, 0f, 0f, h, 0xFFB8C2C1.toInt(), 0xFF1B1F22.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        val cx = w * 0.56f
        val headY = h * 0.34f
        fill.color = 0xFF201814.toInt()
        canvas.drawOval(cx - 46f.dp, headY - 54f.dp, cx + 46f.dp, headY + 48f.dp, fill)
        fill.color = 0xFFF0C8A2.toInt()
        canvas.drawOval(cx - 35f.dp, headY - 36f.dp, cx + 35f.dp, headY + 42f.dp, fill)
        fill.color = 0xFF151515.toInt()
        canvas.drawRoundRect(RectF(cx - 78f.dp, h * 0.62f, cx + 78f.dp, h * 1.12f), 36f.dp, 36f.dp, fill)
        fill.color = 0xFF2B211D.toInt()
        canvas.drawOval(cx - 14f.dp, headY - 6f.dp, cx - 7f.dp, headY + 2f.dp, fill)
        canvas.drawOval(cx + 7f.dp, headY - 6f.dp, cx + 14f.dp, headY + 2f.dp, fill)
        stroke.color = 0xFF2B211D.toInt()
        stroke.strokeWidth = 1.8f.dp
        canvas.drawArc(RectF(cx - 14f.dp, headY + 8f.dp, cx + 14f.dp, headY + 22f.dp), 18f, 144f, false, stroke)
        drawRaisedHand(canvas, w * 0.26f, h * 0.58f)
    }

    private fun drawRaisedHand(canvas: Canvas, x: Float, y: Float) {
        stroke.color = 0xFFF0C8A2.toInt()
        stroke.strokeWidth = 15f.dp
        canvas.drawLine(x + 60f.dp, y + 90f.dp, x + 24f.dp, y + 22f.dp, stroke)
        fill.color = 0xFFF0C8A2.toInt()
        canvas.drawRoundRect(RectF(x - 16f.dp, y - 8f.dp, x + 16f.dp, y + 34f.dp), 12f.dp, 12f.dp, fill)
        stroke.strokeWidth = 9f.dp
        val fingerX = floatArrayOf(-16f, -5f, 6f, 17f, 27f)
        val fingerH = floatArrayOf(48f, 66f, 72f, 60f, 44f)
        for (i in fingerX.indices) {
            stroke.color = 0xFFF0C8A2.toInt()
            canvas.drawLine(x + fingerX[i].dp, y, x + fingerX[i].dp, y - fingerH[i].dp, stroke)
        }
        stroke.color = context.getColor(R.color.tracking_green)
        stroke.strokeWidth = 2f.dp
        canvas.drawRoundRect(RectF(x - 34f.dp, y - 78f.dp, x + 48f.dp, y + 42f.dp), 8f.dp, 8f.dp, stroke)
    }

    private val Float.dp: Float get() = this * resources.displayMetrics.density
}
