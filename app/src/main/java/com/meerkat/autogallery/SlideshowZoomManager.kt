// SlideshowZoomManager.kt - Handles zoom animations with sawtooth vs sinewave behavior
package com.meerkat.autogallery

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView

class SlideshowZoomManager {

    private var currentZoomAnimator: ObjectAnimator? = null

    companion object {
        private const val TAG = "SlideshowZoomManager"
    }

    fun setInitialScale(imageView: ImageView, photoIndex: Int, settings: GallerySettings) {
        val zoomScale = 1f + (settings.zoomAmount / 100f)

        val initialScale = when (settings.zoomType) {
            ZoomType.SAWTOOTH -> 1f  // Always start from 1.0 for sawtooth
            ZoomType.SINE_WAVE -> {
                if (photoIndex % 2 == 0) {
                    1f  // Start from 1.0 for even indices
                } else {
                    zoomScale  // Start from max zoom for odd indices
                }
            }
        }

        imageView.scaleX = initialScale
        imageView.scaleY = initialScale

        Log.d(TAG, "Set initial scale $initialScale for image at index $photoIndex")
    }

    // For SINEWAVE: Start zoom immediately when image is loaded
    // For SAWTOOTH: Start zoom when transition begins
    fun startZoomOnView(imageView: ImageView, photoIndex: Int, settings: GallerySettings, isPreTransition: Boolean = false) {
        when (settings.zoomType) {
            ZoomType.SAWTOOTH -> {
                if (isPreTransition) {
                    // For SAWTOOTH, don't start zoom before transition - wait for transition start
                    Log.d(TAG, "SAWTOOTH mode: deferring zoom until transition starts")
                    return
                } else {
                    startSawtoothZoom(imageView, photoIndex, settings)
                }
            }
            ZoomType.SINE_WAVE -> {
                if (isPreTransition) {
                    // For SINEWAVE, start zoom immediately when image loads
                    startSinewaveZoom(imageView, photoIndex, settings)
                } else {
                    // If zoom isn't already running, start it
                    if (currentZoomAnimator?.isRunning != true) {
                        startSinewaveZoom(imageView, photoIndex, settings)
                    }
                }
            }
        }
    }

    private fun startSawtoothZoom(imageView: ImageView, photoIndex: Int, settings: GallerySettings) {
        // Cancel any existing zoom animation
        currentZoomAnimator?.cancel()

        val zoomScale = 1f + (settings.zoomAmount / 100f)
        // SAWTOOTH always zooms from 1.0 to max zoom
        val startScale = 1f
        val endScale = zoomScale

        Log.d(TAG, "Starting SAWTOOTH zoom: $startScale -> $endScale for photo index $photoIndex")

        startZoomAnimation(imageView, startScale, endScale, settings.slideDuration.toLong())
    }

    private fun startSinewaveZoom(imageView: ImageView, photoIndex: Int, settings: GallerySettings) {
        // Cancel any existing zoom animation
        currentZoomAnimator?.cancel()

        val zoomScale = 1f + (settings.zoomAmount / 100f)

        val (startScale, endScale) = if (photoIndex % 2 == 0) {
            Pair(1f, zoomScale)  // Even indices: zoom in
        } else {
            Pair(zoomScale, 1f)  // Odd indices: zoom out
        }

        Log.d(TAG, "Starting SINEWAVE zoom: $startScale -> $endScale for photo index $photoIndex")

        startZoomAnimation(imageView, startScale, endScale, settings.slideDuration.toLong())
    }

    private fun startZoomAnimation(imageView: ImageView, startScale: Float, endScale: Float, duration: Long) {
        // Set initial scale
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

        // Update reference to track the current animation
        currentZoomAnimator = scaleXAnimator

        animatorSet.start()
    }

    fun pauseZoom() {
        currentZoomAnimator?.cancel()
        Log.d(TAG, "Zoom animation paused")
    }

    fun resumeZoom(imageView: ImageView, photoIndex: Int, settings: GallerySettings) {
        // Resume zoom from current scale
        startZoomOnView(imageView, photoIndex, settings, isPreTransition = false)
        Log.d(TAG, "Zoom animation resumed")
    }

    fun cleanup() {
        currentZoomAnimator?.cancel()
        currentZoomAnimator = null
        Log.d(TAG, "Zoom manager cleaned up")
    }
}