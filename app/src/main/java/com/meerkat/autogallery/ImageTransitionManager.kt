// ImageTransitionManager.kt - Enhanced with gesture-driven transitions
package com.meerkat.autogallery

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView

class ImageTransitionManager(
    private val context: Context,
    private var currentImageView: ImageView,
    private var nextImageView: ImageView,
    private var currentBackgroundImageView: ImageView,
    private var nextBackgroundImageView: ImageView,
    private val zoomManager: SlideshowZoomManager
) {

    private var isTransitioning = false
    private var screenWidth = 0
    private var screenHeight = 0

    companion object {
        private const val TAG = "ImageTransitionManager"
    }

    data class ViewReferences(
        val currentImageView: ImageView,
        val nextImageView: ImageView,
        val currentBackgroundView: ImageView,
        val nextBackgroundView: ImageView
    )

    private enum class SlideDirection {
        LEFT, RIGHT, UP, DOWN
    }

    init {
        val metrics = context.resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    fun performTransition(
        settings: GallerySettings,
        photoIndex: Int,
        fastTransition: Boolean = false,
        swipeDirection: SlideshowGestureHandler.SwipeDirection? = null,
        onComplete: (ViewReferences) -> Unit
    ) {
        if (isTransitioning) {
            Log.w(TAG, "Transition already in progress, ignoring")
            return
        }

        isTransitioning = true
        val duration = if (fastTransition) 250L else 500L

        // Special case: if this is the first image (no current image), just show it directly
        if (currentImageView.drawable == null) {
            handleFirstImage(settings, photoIndex, onComplete)
            return
        }

        // Determine transition type - use swipe direction if available, otherwise use settings
        val transitionType = when (swipeDirection) {
            SlideshowGestureHandler.SwipeDirection.LEFT -> TransitionType.SLIDE_LEFT
            SlideshowGestureHandler.SwipeDirection.RIGHT -> TransitionType.SLIDE_RIGHT
            null -> settings.transitionType
        }

        Log.d(TAG, "Using transition type: $transitionType (swipe: $swipeDirection)")

        val animator = when (transitionType) {
            TransitionType.FADE -> createFadeTransition(settings, photoIndex, duration)
            TransitionType.SLIDE_LEFT -> {
                resetViewStates()
                createSlideTransition(SlideDirection.LEFT, settings, photoIndex, duration)
            }
            TransitionType.SLIDE_RIGHT -> {
                resetViewStates()
                createSlideTransition(SlideDirection.RIGHT, settings, photoIndex, duration)
            }
            TransitionType.SLIDE_UP -> {
                resetViewStates()
                createSlideTransition(SlideDirection.UP, settings, photoIndex, duration)
            }
            TransitionType.SLIDE_DOWN -> {
                resetViewStates()
                createSlideTransition(SlideDirection.DOWN, settings, photoIndex, duration)
            }
        }

        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                super.onAnimationEnd(animation)
                isTransitioning = false
                onComplete(getCurrentViewReferences())
            }
        })

        animator.start()
    }

    fun getCurrentViewReferences(): ViewReferences {
        return ViewReferences(
            currentImageView = currentImageView,
            nextImageView = nextImageView,
            currentBackgroundView = currentBackgroundImageView,
            nextBackgroundView = nextBackgroundImageView
        )
    }

    private fun handleFirstImage(
        settings: GallerySettings,
        photoIndex: Int,
        onComplete: (ViewReferences) -> Unit
    ) {
        Log.d(TAG, "Showing first image - no transition needed")
        swapImageViews()
        if (settings.enableBlurredBackground) {
            swapBackgroundViews()
        }

        // For first image, always start zoom (regardless of zoom type)
        zoomManager.startZoomOnView(currentImageView, photoIndex, settings, isPreTransition = false)
        isTransitioning = false
        onComplete(getCurrentViewReferences())
    }

    private fun createFadeTransition(settings: GallerySettings, photoIndex: Int, duration: Long): AnimatorSet {
        val imageFadeOut = ObjectAnimator.ofFloat(currentImageView, "alpha", 1f, 0f)
        val imageFadeIn = ObjectAnimator.ofFloat(nextImageView, "alpha", 0f, 1f)
        val backgroundFadeOut = ObjectAnimator.ofFloat(currentBackgroundImageView, "alpha", 1f, 0f)
        val backgroundFadeIn = ObjectAnimator.ofFloat(nextBackgroundImageView, "alpha", 0f, 1f)

        imageFadeOut.duration = duration
        imageFadeIn.duration = duration
        backgroundFadeOut.duration = duration
        backgroundFadeIn.duration = duration

        val animatorSet = AnimatorSet()

        if (settings.enableBlurredBackground) {
            animatorSet.playTogether(imageFadeOut, imageFadeIn, backgroundFadeOut, backgroundFadeIn)
        } else {
            animatorSet.playTogether(imageFadeOut, imageFadeIn)
        }

        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: android.animation.Animator) {
                startZoomIfNeeded(settings, photoIndex)
            }

            override fun onAnimationEnd(animation: android.animation.Animator) {
                swapImageViews()
                if (settings.enableBlurredBackground) {
                    swapBackgroundViews()
                }
            }
        })

        return animatorSet
    }

    private fun createSlideTransition(
        direction: SlideDirection,
        settings: GallerySettings,
        photoIndex: Int,
        duration: Long
    ): AnimatorSet {
        Log.d(TAG, "Creating slide transition: $direction, duration: $duration")

        val slideDistance = when (direction) {
            SlideDirection.LEFT, SlideDirection.RIGHT -> screenWidth.toFloat()
            SlideDirection.UP, SlideDirection.DOWN -> screenHeight.toFloat()
        }

        val (nextStartPos, currentEndPos) = when (direction) {
            SlideDirection.LEFT -> Pair(slideDistance, -slideDistance)
            SlideDirection.RIGHT -> Pair(-slideDistance, slideDistance)
            SlideDirection.UP -> Pair(slideDistance, -slideDistance)
            SlideDirection.DOWN -> Pair(-slideDistance, slideDistance)
        }

        resetPositionsForSlide()
        stageNextViewOffScreen(direction, nextStartPos)
        if (settings.enableBlurredBackground) {
            stageBackgroundViewOffScreen(direction, nextStartPos)
        }

        // Make views visible
        nextImageView.alpha = 1f
        currentImageView.alpha = 1f
        if (settings.enableBlurredBackground) {
            nextBackgroundImageView.alpha = 1f
            currentBackgroundImageView.alpha = 1f
        }

        val currentOut = createViewExitAnimation(currentImageView, direction, currentEndPos, duration)
        val nextIn = createViewEnterAnimation(nextImageView, direction, duration)

        val animatorSet = AnimatorSet()

        if (settings.enableBlurredBackground) {
            val currentBgOut = createViewExitAnimation(currentBackgroundImageView, direction, currentEndPos, duration)
            val nextBgIn = createViewEnterAnimation(nextBackgroundImageView, direction, duration)
            animatorSet.playTogether(currentOut, nextIn, currentBgOut, nextBgIn)
        } else {
            animatorSet.playTogether(currentOut, nextIn)
        }

        animatorSet.interpolator = AccelerateDecelerateInterpolator()

        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: android.animation.Animator) {
                startZoomIfNeeded(settings, photoIndex)
            }

            override fun onAnimationEnd(animation: android.animation.Animator) {
                completeSlideTransition(settings)
            }
        })

        return animatorSet
    }

    private fun startZoomIfNeeded(settings: GallerySettings, photoIndex: Int) {
        when (settings.zoomType) {
            ZoomType.SAWTOOTH -> {
                Log.d(TAG, "Starting SAWTOOTH zoom at transition start")
                zoomManager.startZoomOnView(nextImageView, photoIndex, settings, isPreTransition = false)
            }
            ZoomType.SINE_WAVE -> {
                Log.d(TAG, "SINEWAVE zoom already running from pre-transition")
            }
        }
    }

    private fun resetViewStates() {
        listOf(currentImageView, nextImageView, currentBackgroundImageView, nextBackgroundImageView).forEach { view ->
            view.translationX = 0f
            view.translationY = 0f
            view.rotation = 0f
        }

        currentImageView.alpha = 1f
        nextImageView.alpha = 0f
        currentBackgroundImageView.alpha = 1f
        nextBackgroundImageView.alpha = 0f
    }

    private fun resetPositionsForSlide() {
        Log.d(TAG, "Resetting positions for slide (preserving zoom)")
        listOf(currentImageView, nextImageView, currentBackgroundImageView, nextBackgroundImageView).forEach { view ->
            view.translationX = 0f
            view.translationY = 0f
            view.rotation = 0f
        }

        currentImageView.alpha = 1f
        nextImageView.alpha = 0f
        currentBackgroundImageView.alpha = 1f
        nextBackgroundImageView.alpha = 0f
    }

    private fun stageNextViewOffScreen(direction: SlideDirection, startPosition: Float) {
        when (direction) {
            SlideDirection.LEFT, SlideDirection.RIGHT -> {
                nextImageView.translationX = startPosition
                nextImageView.translationY = 0f
            }
            SlideDirection.UP, SlideDirection.DOWN -> {
                nextImageView.translationX = 0f
                nextImageView.translationY = startPosition
            }
        }
        Log.d(TAG, "Staged next view off-screen at position: $startPosition for direction: $direction")
    }

    private fun stageBackgroundViewOffScreen(direction: SlideDirection, startPosition: Float) {
        when (direction) {
            SlideDirection.LEFT, SlideDirection.RIGHT -> {
                nextBackgroundImageView.translationX = startPosition
                nextBackgroundImageView.translationY = 0f
            }
            SlideDirection.UP, SlideDirection.DOWN -> {
                nextBackgroundImageView.translationX = 0f
                nextBackgroundImageView.translationY = startPosition
            }
        }
    }

    private fun createViewExitAnimation(
        view: View,
        direction: SlideDirection,
        endPosition: Float,
        duration: Long
    ): ObjectAnimator {
        val property = when (direction) {
            SlideDirection.LEFT, SlideDirection.RIGHT -> "translationX"
            SlideDirection.UP, SlideDirection.DOWN -> "translationY"
        }

        return ObjectAnimator.ofFloat(view, property, 0f, endPosition).apply {
            this.duration = duration
        }
    }

    private fun createViewEnterAnimation(
        view: View,
        direction: SlideDirection,
        duration: Long
    ): ObjectAnimator {
        val property = when (direction) {
            SlideDirection.LEFT, SlideDirection.RIGHT -> "translationX"
            SlideDirection.UP, SlideDirection.DOWN -> "translationY"
        }

        return ObjectAnimator.ofFloat(view, property, view.translationX, 0f).apply {
            this.duration = duration
        }
    }

    private fun completeSlideTransition(settings: GallerySettings) {
        Log.d(TAG, "Completing slide transition")

        swapImageViews()
        if (settings.enableBlurredBackground) {
            swapBackgroundViews()
        }

        resetPositionsAfterSlide()
        Log.d(TAG, "Slide transition completed")
    }

    private fun resetPositionsAfterSlide() {
        currentImageView.apply {
            translationX = 0f
            translationY = 0f
            alpha = 1f
        }

        nextImageView.apply {
            translationX = 0f
            translationY = 0f
            alpha = 0f
        }

        currentBackgroundImageView.apply {
            translationX = 0f
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
        }

        nextBackgroundImageView.apply {
            translationX = 0f
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
            alpha = 0f
        }
    }

    private fun swapImageViews() {
        val temp = currentImageView
        currentImageView = nextImageView
        nextImageView = temp

        currentImageView.alpha = 1f
        nextImageView.alpha = 0f

        nextImageView.translationX = 0f
        nextImageView.translationY = 0f
    }

    private fun swapBackgroundViews() {
        val temp = currentBackgroundImageView
        currentBackgroundImageView = nextBackgroundImageView
        nextBackgroundImageView = temp

        currentBackgroundImageView.alpha = 1f
        nextBackgroundImageView.alpha = 0f

        nextBackgroundImageView.translationX = 0f
        nextBackgroundImageView.translationY = 0f
    }

    fun isTransitioning(): Boolean = isTransitioning
}