// BorderImageView.kt - Custom ImageView with optional edge fade effect
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

    private val fadeWidth = 100f
    private val edgeOverlap = 4f
    var featheringEnabled = true // Can be set programmatically

    override fun onDraw(canvas: Canvas) {
        val viewWidth = width
        val viewHeight = height

        if (viewWidth <= 0 || viewHeight <= 0 || drawable == null) {
            super.onDraw(canvas)
            return
        }

        if (!featheringEnabled) {
            super.onDraw(canvas)
            return
        }

        val imageBounds = calculateImageBounds(viewWidth, viewHeight)

        val layerPaint = Paint()
        val saveCount = canvas.saveLayer(imageBounds.left, imageBounds.top, imageBounds.right, imageBounds.bottom, layerPaint)

        super.onDraw(canvas)
        applyTransparencyMask(canvas, imageBounds, viewWidth, viewHeight)

        canvas.restoreToCount(saveCount)
    }

    private fun calculateImageBounds(viewWidth: Int, viewHeight: Int): RectF {
        val drawable = drawable ?: return RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())

        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()

        if (imageWidth <= 0 || imageHeight <= 0) {
            return RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        }

        when (scaleType) {
            ScaleType.CENTER_INSIDE -> {
                val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
                val scaledWidth = imageWidth * scale
                val scaledHeight = imageHeight * scale
                val left = (viewWidth - scaledWidth) / 2f
                val top = (viewHeight - scaledHeight) / 2f
                return RectF(left, top, left + scaledWidth, top + scaledHeight)
            }

            ScaleType.CENTER_CROP -> {
                val scale = maxOf(viewWidth / imageWidth, viewHeight / imageHeight)
                val scaledWidth = imageWidth * scale
                val scaledHeight = imageHeight * scale
                val left = (viewWidth - scaledWidth) / 2f
                val top = (viewHeight - scaledHeight) / 2f
                return RectF(left, top, left + scaledWidth, top + scaledHeight)
            }

            ScaleType.MATRIX -> {
                val matrix = imageMatrix
                val values = FloatArray(9)
                matrix.getValues(values)
                val scaleX = values[Matrix.MSCALE_X]
                val scaleY = values[Matrix.MSCALE_Y]
                val transX = values[Matrix.MTRANS_X]
                val transY = values[Matrix.MTRANS_Y]

                val scaledWidth = imageWidth * scaleX
                val scaledHeight = imageHeight * scaleY
                return RectF(transX, transY, transX + scaledWidth, transY + scaledHeight)
            }

            else -> {
                return RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
            }
        }
    }

    private fun applyTransparencyMask(canvas: Canvas, imageBounds: RectF, viewWidth: Int, viewHeight: Int) {
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)

        val effectiveFadeWidth = fadeWidth - edgeOverlap

        // Top edge fade
        if (imageBounds.top > 0) {
            val fadeStart = imageBounds.top
            val fadeEnd = minOf(imageBounds.top + effectiveFadeWidth, imageBounds.bottom)
            if (fadeEnd > fadeStart) {
                val shader = LinearGradient(
                    0f, fadeStart, 0f, fadeEnd,
                    0x00FFFFFF, 0xFFFFFFFF.toInt(),
                    Shader.TileMode.CLAMP
                )
                maskPaint.shader = shader
                canvas.drawRect(imageBounds.left - edgeOverlap, fadeStart - edgeOverlap, imageBounds.right + edgeOverlap, fadeEnd, maskPaint)
            }
        }

        // Bottom edge fade
        if (imageBounds.bottom < viewHeight) {
            val fadeStart = imageBounds.bottom
            val fadeEnd = maxOf(imageBounds.bottom - effectiveFadeWidth, imageBounds.top)
            if (fadeStart > fadeEnd) {
                val shader = LinearGradient(
                    0f, fadeStart, 0f, fadeEnd,
                    0x00FFFFFF, 0xFFFFFFFF.toInt(),
                    Shader.TileMode.CLAMP
                )
                maskPaint.shader = shader
                canvas.drawRect(imageBounds.left - edgeOverlap, fadeEnd, imageBounds.right + edgeOverlap, fadeStart + edgeOverlap, maskPaint)
            }
        }

        // Left edge fade
        if (imageBounds.left > 0) {
            val fadeStart = imageBounds.left
            val fadeEnd = minOf(imageBounds.left + effectiveFadeWidth, imageBounds.right)
            if (fadeEnd > fadeStart) {
                val shader = LinearGradient(
                    fadeStart, 0f, fadeEnd, 0f,
                    0x00FFFFFF, 0xFFFFFFFF.toInt(),
                    Shader.TileMode.CLAMP
                )
                maskPaint.shader = shader
                canvas.drawRect(fadeStart - edgeOverlap, imageBounds.top - edgeOverlap, fadeEnd, imageBounds.bottom + edgeOverlap, maskPaint)
            }
        }

        // Right edge fade
        if (imageBounds.right < viewWidth) {
            val fadeStart = imageBounds.right
            val fadeEnd = maxOf(imageBounds.right - effectiveFadeWidth, imageBounds.left)
            if (fadeStart > fadeEnd) {
                val shader = LinearGradient(
                    fadeStart, 0f, fadeEnd, 0f,
                    0x00FFFFFF, 0xFFFFFFFF.toInt(),
                    Shader.TileMode.CLAMP
                )
                maskPaint.shader = shader
                canvas.drawRect(fadeEnd, imageBounds.top - edgeOverlap, fadeStart + edgeOverlap, imageBounds.bottom + edgeOverlap, maskPaint)
            }
        }
    }
}