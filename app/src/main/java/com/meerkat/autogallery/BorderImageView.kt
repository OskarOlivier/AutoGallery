// BorderImageView.kt - Custom ImageView with fade-out vignette effect
package com.meerkat.autogallery

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class BorderImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fadeWidth = 10f // 10px fade-out width from edges

    // Gradient shaders for each edge - created once and reused
    private var topGradient: LinearGradient? = null
    private var bottomGradient: LinearGradient? = null
    private var leftGradient: LinearGradient? = null
    private var rightGradient: LinearGradient? = null

    // Flag to track if gradients need to be recreated
    private var gradientsNeedUpdate = true

    init {
        // Use DST_IN to mask the image content - keeps only where mask is opaque
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradientsNeedUpdate = true
    }

    private fun createGradients(width: Int, height: Int) {
        if (!gradientsNeedUpdate || width <= 0 || height <= 0) return

        // Colors: fully opaque (0xFF000000) at inner edge to transparent (0x00000000) at outer edge
        val opaqueBlack = 0xFF000000.toInt()
        val transparent = 0x00000000

        // Top edge fade (vertical gradient from top edge inward)
        topGradient = LinearGradient(
            0f, 0f,
            0f, fadeWidth,
            transparent, opaqueBlack,
            Shader.TileMode.CLAMP
        )

        // Bottom edge fade (vertical gradient from bottom edge inward)
        bottomGradient = LinearGradient(
            0f, height.toFloat(),
            0f, height - fadeWidth,
            transparent, opaqueBlack,
            Shader.TileMode.CLAMP
        )

        // Left edge fade (horizontal gradient from left edge inward)
        leftGradient = LinearGradient(
            0f, 0f,
            fadeWidth, 0f,
            transparent, opaqueBlack,
            Shader.TileMode.CLAMP
        )

        // Right edge fade (horizontal gradient from right edge inward)
        rightGradient = LinearGradient(
            width.toFloat(), 0f,
            width - fadeWidth, 0f,
            transparent, opaqueBlack,
            Shader.TileMode.CLAMP
        )

        gradientsNeedUpdate = false
    }

    override fun onDraw(canvas: Canvas) {
        val width = width
        val height = height

        if (width <= 0 || height <= 0) {
            super.onDraw(canvas)
            return
        }

        createGradients(width, height)

        // Draw the image first on a separate layer
        val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        // Draw the image content
        super.onDraw(canvas)

        // Apply fade-out mask on all four edges
        drawFadeOutMask(canvas, width, height)

        // Restore canvas state
        canvas.restoreToCount(saveCount)
    }

    private fun drawFadeOutMask(canvas: Canvas, width: Int, height: Int) {
        // Draw fade mask rectangles that will make the edges transparent

        // Top edge fade
        topGradient?.let { gradient ->
            maskPaint.shader = gradient
            canvas.drawRect(0f, 0f, width.toFloat(), fadeWidth, maskPaint)
        }

        // Bottom edge fade
        bottomGradient?.let { gradient ->
            maskPaint.shader = gradient
            canvas.drawRect(0f, height - fadeWidth, width.toFloat(), height.toFloat(), maskPaint)
        }

        // Left edge fade
        leftGradient?.let { gradient ->
            maskPaint.shader = gradient
            canvas.drawRect(0f, 0f, fadeWidth, height.toFloat(), maskPaint)
        }

        // Right edge fade
        rightGradient?.let { gradient ->
            maskPaint.shader = gradient
            canvas.drawRect(width - fadeWidth, 0f, width.toFloat(), height.toFloat(), maskPaint)
        }
    }
}