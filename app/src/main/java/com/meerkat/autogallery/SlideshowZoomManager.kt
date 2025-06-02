// SlideshowZoomManager.kt - Updated to respect edge buffer requirements
package com.meerkat.autogallery

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import kotlin.math.max

class SlideshowZoomManager {

    private var currentZoomAnimatorSet: AnimatorSet? = null
    private var isPaused = false

    companion object {
        private const val TAG = "SlideshowZoomManager"
        private const val EDGE_BUFFER = 20 // 10px on each edge
    }

    /**
     * Calculate the minimum scale factor needed to maintain 10px buffer on touching edges
     */
    private fun calculateMinimumSafeScale(imageView: ImageView): Float {
        val drawable = imageView.drawable ?: return 1f

        val imageWidth = drawable.intrinsicWidth
        val imageHeight = drawable.intrinsicHeight

        if (imageWidth <= 0 || imageHeight <= 0) return 1f

        val metrics = imageView.context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        val imageAspectRatio = imageWidth.toFloat() / imageHeight.toFloat()
        val screenAspectRatio = screenWidth.toFloat() / screenHeight.toFloat()

        val minSafeScale = if (imageAspectRatio > screenAspectRatio) {
            // Image is wider - will touch top/bottom edges, needs buffer for height
            (screenHeight + EDGE_BUFFER).toFloat() / screenHeight.toFloat()
        } else {
            // Image is taller - will touch left/right edges, needs buffer for width
            (screenWidth + EDGE_BUFFER).toFloat() / screenWidth.toFloat()
        }

        Log.d(TAG, "Calculated minimum safe scale: $minSafeScale for image ${imageWidth}x${imageHeight}")
        return minSafeScale
    }

    fun setInitialScale(imageView: ImageView, photoIndex: Int, settings: GallerySettings) {
        val minSafeScale = calculateMinimumSafeScale(imageView)
        val zoomScale = minSafeScale + (settings.zoomAmount / 100f)

        val initialScale = when (settings.zoomType) {
            ZoomType.SAWTOOTH -> minSafeScale  // Start from minimum safe scale, not 1.0
            ZoomType.SINE_WAVE -> {
                if (photoIndex % 2 == 0) {
                    minSafeScale  // Start from minimum safe scale for even indices
                } else {
                    zoomScale  // Start from max zoom for odd indices
                }
            }
        }

        imageView.scaleX = initialScale
        imageView.scaleY = initialScale

        Log.d(TAG, "Set initial scale $initialScale (minSafe: $minSafeScale) for image at index $photoIndex")
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

        val minSafeScale = calculateMinimumSafeScale(imageView)
        val zoomScale = minSafeScale + (settings.zoomAmount / 100f)
        val startScale = minSafeScale  // Start from minimum safe scale
        val endScale = zoomScale

        Log.d(TAG, "Starting SAWTOOTH zoom: $startScale -> $endScale for photo index $photoIndex")

        startZoomAnimation(imageView, startScale, endScale, settings.slideDuration.toLong())
    }

    private fun startSinewaveZoom(imageView: ImageView, photoIndex: Int, settings: GallerySettings) {
        currentZoomAnimatorSet?.cancel()

        val minSafeScale = calculateMinimumSafeScale(imageView)
        val zoomScale = minSafeScale + (settings.zoomAmount / 100f)

        val (startScale, endScale) = if (photoIndex % 2 == 0) {
            Pair(minSafeScale, zoomScale)  // Even indices: zoom in from safe scale
        } else {
            Pair(zoomScale, minSafeScale)  // Odd indices: zoom out to safe scale
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