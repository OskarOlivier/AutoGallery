// SlideshowZoomManager.kt - Handles zoom animations
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

    fun startZoomOnView(imageView: ImageView, photoIndex: Int, settings: GallerySettings) {
        // Cancel any existing zoom animation
        currentZoomAnimator?.cancel()

        val zoomScale = 1f + (settings.zoomAmount / 100f)

        val (_, endScale) = when (settings.zoomType) {
            ZoomType.SAWTOOTH -> Pair(1f, zoomScale)
            ZoomType.SINE_WAVE -> {
                if (photoIndex % 2 == 0) {
                    Pair(1f, zoomScale)
                } else {
                    Pair(zoomScale, 1f)
                }
            }
        }

        Log.d(TAG, "Starting zoom effect: ${imageView.scaleX} -> $endScale for photo index $photoIndex")

        val scaleXAnimator = ObjectAnimator.ofFloat(imageView, "scaleX", imageView.scaleX, endScale)
        val scaleYAnimator = ObjectAnimator.ofFloat(imageView, "scaleY", imageView.scaleY, endScale)

        val duration = settings.slideDuration.toLong()
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
        startZoomOnView(imageView, photoIndex, settings)
        Log.d(TAG, "Zoom animation resumed")
    }

    fun cleanup() {
        currentZoomAnimator?.cancel()
        currentZoomAnimator = null
        Log.d(TAG, "Zoom manager cleaned up")
    }
}