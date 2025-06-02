// SlideshowGestureHandler.kt - Enhanced gesture handling with swipe-to-slide mapping
package com.meerkat.autogallery

import android.content.Context
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.view.GestureDetectorCompat
import kotlin.math.abs

class SlideshowGestureHandler(
    private val context: Context,
    private val uiManager: SlideshowUIManager,
    private val imageListManager: ImageListManager,
    private val onPauseToggle: () -> Unit,
    private val onNavigateToNext: (swipeDirection: SwipeDirection?) -> Unit,
    private val onNavigateToPrevious: (swipeDirection: SwipeDirection?) -> Unit,
    private val onExit: () -> Unit
) {

    private lateinit var gestureDetector: GestureDetectorCompat
    private var isPaused = false
    private var lastGestureTime = 0L
    private var screenWidth = 0
    private var screenHeight = 0

    enum class SwipeDirection {
        LEFT, RIGHT
    }

    companion object {
        private const val TAG = "SlideshowGestureHandler"
        private const val MIN_SWIPE_DISTANCE = 100 // pixels
        private const val MIN_SWIPE_VELOCITY = 800 // pixels per second
        private const val GESTURE_DEBOUNCE_TIME = 200L // ms
        private const val EDGE_THRESHOLD = 50 // pixels
    }

    init {
        val metrics = context.resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    fun setupGestureDetection(containerView: View) {
        val gestureListener = object : GestureDetector.SimpleOnGestureListener() {

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!shouldProcessGesture()) return false
                Log.d(TAG, "Single tap detected")
                togglePauseResume()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!shouldProcessGesture()) return false
                Log.d(TAG, "Double tap detected - exiting slideshow")
                uiManager.showExitFeedback()
                onExit()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (!shouldProcessGesture() || !isValidGesture(e1, e2)) return false

                e1?.let { startEvent ->
                    val deltaX = e2.x - startEvent.x
                    val deltaY = e2.y - startEvent.y

                    // Check if it's a horizontal swipe
                    if (abs(deltaX) > abs(deltaY) &&
                        abs(deltaX) > MIN_SWIPE_DISTANCE &&
                        abs(velocityX) > MIN_SWIPE_VELOCITY) {

                        if (deltaX > 0) {
                            // Swipe right - go to previous image with slide right animation
                            Log.d(TAG, "Swipe right - previous image with slide right")
                            onNavigateToPrevious(SwipeDirection.RIGHT)
                        } else {
                            // Swipe left - go to next image with slide left animation
                            Log.d(TAG, "Swipe left - next image with slide left")
                            onNavigateToNext(SwipeDirection.LEFT)
                        }
                        return true
                    }
                } ?: return false

                return false
            }
        }

        gestureDetector = GestureDetectorCompat(context, gestureListener)

        // Set touch listener on the main container
        containerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
    }

    private fun togglePauseResume() {
        isPaused = !isPaused
        Log.d(TAG, "Slideshow ${if (isPaused) "paused" else "resumed"}")

        if (isPaused) {
            uiManager.showPauseIndicator()
        } else {
            uiManager.hidePauseIndicator()
        }

        onPauseToggle()
    }

    private fun shouldProcessGesture(): Boolean {
        val currentTime = System.currentTimeMillis()
        return if (currentTime - lastGestureTime > GESTURE_DEBOUNCE_TIME) {
            lastGestureTime = currentTime
            true
        } else {
            false
        }
    }

    private fun isValidGesture(e1: MotionEvent?, e2: MotionEvent): Boolean {
        if (e1 == null) return false

        // Ignore touches near screen edges (accidental touches)
        if (e1.x < EDGE_THRESHOLD || e1.x > screenWidth - EDGE_THRESHOLD) return false

        // Ignore very short swipes (likely accidental)
        val deltaTime = e2.eventTime - e1.eventTime
        if (deltaTime < 100) return false

        return true
    }

    // Hide gesture hints when user interacts
    private fun hideGestureHints() {
        uiManager.hideGestureHints()
    }

    fun isPaused(): Boolean = isPaused

    fun setPaused(paused: Boolean) {
        if (isPaused != paused) {
            togglePauseResume()
        }
    }
}