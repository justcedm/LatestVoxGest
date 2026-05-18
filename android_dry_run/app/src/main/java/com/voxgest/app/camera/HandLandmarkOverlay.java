package com.voxgest.app.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.voxgest.dryrun.R;

public final class HandLandmarkOverlay extends View {
    private static final int[][] PALM_CONNECTIONS = {
            {0, 5}, {5, 9}, {9, 13}, {13, 17}, {0, 17}
    };
    private static final int[][] FINGER_CONNECTIONS = {
            {0, 1}, {1, 2}, {2, 3}, {3, 4},
            {5, 6}, {6, 7}, {7, 8},
            {9, 10}, {10, 11}, {11, 12},
            {13, 14}, {14, 15}, {15, 16},
            {17, 18}, {18, 19}, {19, 20}
    };
    private static final float[][] NORMALIZED_POINTS = {
            {0.50f, 0.70f},
            {0.38f, 0.62f}, {0.31f, 0.50f}, {0.25f, 0.39f}, {0.19f, 0.30f},
            {0.45f, 0.48f}, {0.42f, 0.34f}, {0.40f, 0.22f}, {0.39f, 0.11f},
            {0.54f, 0.46f}, {0.55f, 0.30f}, {0.56f, 0.17f}, {0.57f, 0.06f},
            {0.63f, 0.50f}, {0.67f, 0.36f}, {0.70f, 0.24f}, {0.73f, 0.14f},
            {0.72f, 0.58f}, {0.80f, 0.48f}, {0.86f, 0.39f}, {0.91f, 0.31f}
    };

    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private int signColor;
    private int palmColor;
    private int[] fingerColors;

    public HandLandmarkOverlay(Context context) {
        this(context, null);
    }

    public HandLandmarkOverlay(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HandLandmarkOverlay(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        loadColors();
        setWillNotDraw(false);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        dotPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) {
            return;
        }

        float scale = Math.min(width, height) * 0.52f;
        float offsetX = width * 0.50f - scale * 0.50f;
        float offsetY = height * 0.50f - scale * 0.43f;

        drawBounds(canvas, offsetX, offsetY, scale);
        drawCornerGuides(canvas, width, height);
        strokePaint.setStrokeWidth(dp(1.5f));
        strokePaint.setColor(palmColor);
        for (int[] connection : PALM_CONNECTIONS) {
            drawConnection(canvas, connection[0], connection[1], offsetX, offsetY, scale);
        }

        for (int[] connection : FINGER_CONNECTIONS) {
            strokePaint.setColor(colorForFinger(connection[1]));
            drawConnection(canvas, connection[0], connection[1], offsetX, offsetY, scale);
        }

        for (int i = 0; i < NORMALIZED_POINTS.length; i++) {
            float x = offsetX + NORMALIZED_POINTS[i][0] * scale;
            float y = offsetY + NORMALIZED_POINTS[i][1] * scale;
            dotPaint.setColor(i == 0 ? signColor : colorForFinger(i));
            float radius = i == 0 ? dp(7f) : (isFingertip(i) ? dp(5f) : dp(3f));
            canvas.drawCircle(x, y, radius, dotPaint);
        }
    }

    private void drawCornerGuides(Canvas canvas, float width, float height) {
        float pad = dp(24f);
        float len = dp(24f);
        strokePaint.setColor(signColor);
        strokePaint.setStrokeWidth(dp(2.5f));
        canvas.drawLine(pad, pad, pad + len, pad, strokePaint);
        canvas.drawLine(pad, pad, pad, pad + len, strokePaint);
        canvas.drawLine(width - pad, pad, width - pad - len, pad, strokePaint);
        canvas.drawLine(width - pad, pad, width - pad, pad + len, strokePaint);
        canvas.drawLine(pad, height - pad, pad + len, height - pad, strokePaint);
        canvas.drawLine(pad, height - pad, pad, height - pad - len, strokePaint);
        canvas.drawLine(width - pad, height - pad, width - pad - len, height - pad, strokePaint);
        canvas.drawLine(width - pad, height - pad, width - pad, height - pad - len, strokePaint);
    }

    private void drawBounds(Canvas canvas, float offsetX, float offsetY, float scale) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float maxY = Float.MIN_VALUE;
        for (float[] point : NORMALIZED_POINTS) {
            float x = offsetX + point[0] * scale;
            float y = offsetY + point[1] * scale;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        float pad = dp(14f);
        bounds.set(minX - pad, minY - pad, maxX + pad, maxY + pad);
        strokePaint.setColor(signColor);
        strokePaint.setStrokeWidth(dp(1.5f));
        canvas.drawRoundRect(bounds, dp(4f), dp(4f), strokePaint);
    }

    private void drawConnection(Canvas canvas, int start, int end, float offsetX, float offsetY, float scale) {
        canvas.drawLine(
                offsetX + NORMALIZED_POINTS[start][0] * scale,
                offsetY + NORMALIZED_POINTS[start][1] * scale,
                offsetX + NORMALIZED_POINTS[end][0] * scale,
                offsetY + NORMALIZED_POINTS[end][1] * scale,
                strokePaint
        );
    }

    private boolean isFingertip(int index) {
        return index == 4 || index == 8 || index == 12 || index == 16 || index == 20;
    }

    private int colorForFinger(int index) {
        if (index >= 1 && index <= 4) {
            return fingerColors[0];
        }
        if (index >= 5 && index <= 8) {
            return fingerColors[1];
        }
        if (index >= 9 && index <= 12) {
            return fingerColors[2];
        }
        if (index >= 13 && index <= 16) {
            return fingerColors[3];
        }
        if (index >= 17 && index <= 20) {
            return fingerColors[4];
        }
        return signColor;
    }

    private void loadColors() {
        signColor = getContext().getColor(R.color.tracking_green);
        palmColor = getContext().getColor(R.color.text_secondary);
        fingerColors = new int[]{
                getContext().getColor(R.color.finger_thumb),
                getContext().getColor(R.color.finger_index),
                getContext().getColor(R.color.finger_middle),
                getContext().getColor(R.color.finger_ring),
                getContext().getColor(R.color.finger_pinky)
        };
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
