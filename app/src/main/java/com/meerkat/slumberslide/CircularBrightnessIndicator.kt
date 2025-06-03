// CircularBrightnessIndicator.kt - Custom view for circular brightness progress indicator
package com.meerkat.slumberslide

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.widget.FrameLayout
import kotlin.math.min

class CircularBrightnessIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var brightnessProgress = 1.0f // 0.0f to 1.0f
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressRect = RectF()
    private val strokeWidth = 8f

    init {
        setWillNotDraw(false)

        // Progress arc paint (white)
        progressPaint.apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = this@CircularBrightnessIndicator.strokeWidth
            strokeCap = Paint.Cap.ROUND
        }

        // Background arc paint (semi-transparent white)
        backgroundPaint.apply {
            color = 0x40FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = this@CircularBrightnessIndicator.strokeWidth
            strokeCap = Paint.Cap.ROUND
        }
    }

    fun setBrightnessProgress(progress: Float) {
        brightnessProgress = progress.coerceIn(0.0f, 1.0f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(centerX, centerY) - strokeWidth / 2f - 4f // 4f padding

        // Set up the progress rectangle
        progressRect.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        // Draw background arc (full circle)
        canvas.drawArc(progressRect, -90f, 360f, false, backgroundPaint)

        // Draw progress arc
        val sweepAngle = 360f * brightnessProgress
        if (sweepAngle > 0f) {
            canvas.drawArc(progressRect, -90f, sweepAngle, false, progressPaint)
        }
    }
}