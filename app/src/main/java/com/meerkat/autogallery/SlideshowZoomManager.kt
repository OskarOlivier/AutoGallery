// SlideshowZoomManager.kt - Zoom animation management with duration cap
package com.meerkat.autogallery

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import kotlin.math.min

class SlideshowZoomManager {

    private var currentZoomAnimatorSet: AnimatorSet? = null
    private var isPaused = false

    companion object {
        private const val MAX_ZOOM_DURATION_MS = 15000L // 15 seconds maximum zoom duration
    }

    fun setInitialScale(imageView: ImageView, photoIndex: Int, settings: GallerySettings) {
        val zoomScale = 1.0f + (settings.zoomAmount / 100f)

        val initialScale = when (settings.zoomType) {
            ZoomType.SAWTOOTH -> 1.0f
            ZoomType.SINE_WAVE -> {
                if (photoIndex % 2 == 0) {
                    1.0f
                } else {
                    zoomScale
                }
            }
        }

        imageView.scaleX = initialScale
        imageView.scaleY = initialScale
    }

    fun startZoomOnView(imageView: ImageView, photoIndex: Int, settings: GallerySettings, isPreTransition: Boolean = false) {
        if (isPaused) {
            return
        }

        when (settings.zoomType) {
            ZoomType.SAWTOOTH -> {
                if (isPreTransition) {
                    return
                } else {
                    startSawtoothZoom(imageView, photoIndex, settings)
                }
            }
            ZoomType.SINE_WAVE -> {
                if (isPreTransition) {
                    startSinewaveZoom(imageView, photoIndex, settings)
                } else {
                    if (currentZoomAnimatorSet?.isRunning != true) {
                        startSinewaveZoom(imageView, photoIndex, settings)
                    }
                }
            }
        }
    }

    private fun startSawtoothZoom(imageView: ImageView, photoIndex: Int, settings: GallerySettings) {
        currentZoomAnimatorSet?.cancel()

        val startScale = 1.0f
        val endScale = 1.0f + (settings.zoomAmount / 100f)

        // Cap zoom duration at 15 seconds maximum
        val cappedDuration = min(settings.slideDuration.toLong(), MAX_ZOOM_DURATION_MS)
        startZoomAnimation(imageView, startScale, endScale, cappedDuration)
    }

    private fun startSinewaveZoom(imageView: ImageView, photoIndex: Int, settings: GallerySettings) {
        currentZoomAnimatorSet?.cancel()

        val baseScale = 1.0f
        val zoomScale = 1.0f + (settings.zoomAmount / 100f)

        val (startScale, endScale) = if (photoIndex % 2 == 0) {
            Pair(baseScale, zoomScale)
        } else {
            Pair(zoomScale, baseScale)
        }

        // Cap zoom duration at 15 seconds maximum
        val cappedDuration = min(settings.slideDuration.toLong(), MAX_ZOOM_DURATION_MS)
        startZoomAnimation(imageView, startScale, endScale, cappedDuration)
    }

    private fun startZoomAnimation(imageView: ImageView, startScale: Float, endScale: Float, duration: Long) {
        imageView.scaleX = startScale
        imageView.scaleY = startScale

        val scaleXAnimator = ObjectAnimator.ofFloat(imageView, "scaleX", startScale, endScale)
        val scaleYAnimator = ObjectAnimator.ofFloat(imageView, "scaleY", startScale, endScale)

        scaleXAnimator.duration = duration
        scaleYAnimator.duration = duration

        val interpolator = AccelerateDecelerateInterpolator()
        scaleXAnimator.interpolator = interpolator
        scaleYAnimator.interpolator = interpolator

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleXAnimator, scaleYAnimator)

        currentZoomAnimatorSet = animatorSet
        animatorSet.start()
    }

    fun pauseZoom() {
        isPaused = true
    }

    fun resumeZoom(imageView: ImageView, photoIndex: Int, settings: GallerySettings) {
        isPaused = false
    }

    fun cleanup() {
        currentZoomAnimatorSet?.cancel()
        currentZoomAnimatorSet = null
        isPaused = false
    }
}