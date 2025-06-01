// SlideshowZoomManager.kt - Let zoom animations complete naturally when paused
package com.meerkat.autogallery

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView

class SlideshowZoomManager {

    private var currentZoomAnimatorSet: AnimatorSet? = null
    private var isPaused = false

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

    fun startZoomOnView(imageView: ImageView, photoIndex: Int, settings: GallerySettings, isPreTransition: Boolean = false) {
        // Don't start NEW zoom animations if currently paused (let existing ones complete)
        if (isPaused) {
            Log.d(TAG, "Zoom manager is paused, not starting new zoom animation")
            return
        }

        when (settings.zoomType) {
            ZoomType.SAWTOOTH -> {
                if (isPreTransition) {
                    Log.d(TAG, "SAWTOOTH mode: deferring zoom until transition starts")
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

        val zoomScale = 1f + (settings.zoomAmount / 100f)
        val startScale = 1f
        val endScale = zoomScale

        Log.d(TAG, "Starting SAWTOOTH zoom: $startScale -> $endScale for photo index $photoIndex")

        startZoomAnimation(imageView, startScale, endScale, settings.slideDuration.toLong())
    }

    private fun startSinewaveZoom(imageView: ImageView, photoIndex: Int, settings: GallerySettings) {
        currentZoomAnimatorSet?.cancel()

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

        // Store reference to the AnimatorSet (not just one animator)
        currentZoomAnimatorSet = animatorSet

        animatorSet.start()
    }

    fun pauseZoom() {
        Log.d(TAG, "Slideshow paused - letting current zoom animation complete naturally")
        isPaused = true
        // Don't cancel the animation - let it complete naturally
        // This ensures both X and Y animations finish together
    }

    fun resumeZoom(imageView: ImageView, photoIndex: Int, settings: GallerySettings) {
        Log.d(TAG, "Slideshow resumed - ready for new zoom animations")
        isPaused = false
        // Don't restart zoom for current image - new images will start fresh zoom animations
    }

    fun cleanup() {
        currentZoomAnimatorSet?.cancel()
        currentZoomAnimatorSet = null
        isPaused = false
        Log.d(TAG, "Zoom manager cleaned up")
    }
}