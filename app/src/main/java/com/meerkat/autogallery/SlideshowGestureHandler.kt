// SlideshowGestureHandler.kt - Enhanced gesture handling with swipe-to-slide mapping and brightness control
package com.meerkat.autogallery

import android.content.Context
import android.os.Handler
import android.os.Looper
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
    private val onBrightnessChange: (brightness: Float) -> Unit,
    private val onExit: () -> Unit
) {

    private lateinit var gestureDetector: GestureDetectorCompat
    private var isPaused = false
    private var lastGestureTime = 0L
    private var screenWidth = 0
    private var screenHeight = 0
    private var currentBrightness = 1.0f

    // State tracking for gesture conflicts
    private var isBrightnessControlActive = false
    private var isNavigationActive = false
    private var gestureStateResetRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    enum class SwipeDirection {
        LEFT, RIGHT
    }

    companion object {
        private const val TAG = "SlideshowGestureHandler"
        private const val MIN_SWIPE_DISTANCE = 40 // pixels - very gentle swipes
        private const val MIN_SWIPE_VELOCITY = 100 // pixels per second - very slow swipes work
        private const val GESTURE_DEBOUNCE_TIME = 50L // ms - very responsive
        private const val EDGE_THRESHOLD = 20 // pixels - almost entire screen usable
        private const val BRIGHTNESS_ADJUSTMENT_FACTOR = 0.01f // Brightness change per pixel
        private const val GESTURE_STATE_RESET_DELAY = 300L // ms - reset gesture state after inactivity
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
                // Skip debouncing for navigation - allow rapid swipes
                if (e1 == null || !isValidGesture(e1, e2)) return false

                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y

                val absDeltaX = abs(deltaX)
                val absDeltaY = abs(deltaY)

                Log.v(TAG, "Fling gesture: deltaX=$deltaX, deltaY=$deltaY, velX=$velocityX, velY=$velocityY")
                Log.v(TAG, "Gesture state: brightness=$isBrightnessControlActive, navigation=$isNavigationActive")

                // HORIZONTAL NAVIGATION - only if brightness control is not active
                if (!isBrightnessControlActive && (absDeltaX > MIN_SWIPE_DISTANCE || abs(velocityX) > MIN_SWIPE_VELOCITY)) {
                    // Accept gesture if EITHER distance OR velocity threshold is met
                    setNavigationActive()

                    if (deltaX > 0) {
                        Log.d(TAG, "Swipe right - previous image (deltaX: $deltaX, deltaY: $deltaY)")
                        onNavigateToPrevious(SwipeDirection.RIGHT)
                    } else {
                        Log.d(TAG, "Swipe left - next image (deltaX: $deltaX, deltaY: $deltaY)")
                        onNavigateToNext(SwipeDirection.LEFT)
                    }
                    return true
                }

                // VERTICAL BRIGHTNESS CONTROL - only if navigation is not active
                else if (!isNavigationActive && absDeltaY > MIN_SWIPE_DISTANCE && abs(velocityY) > MIN_SWIPE_VELOCITY && absDeltaY > absDeltaX * 2.0f) {
                    // Vertical swipe - brightness control (only if clearly more vertical than horizontal)
                    if (!shouldProcessGesture()) return false // Apply debouncing only to brightness

                    setBrightnessControlActive()

                    val brightnessChange = deltaY * BRIGHTNESS_ADJUSTMENT_FACTOR
                    val newBrightness = (currentBrightness - brightnessChange).coerceIn(0.0f, 1.0f)

                    Log.d(TAG, "Vertical swipe - brightness ${(currentBrightness * 100).toInt()}% -> ${(newBrightness * 100).toInt()}%")
                    setBrightness(newBrightness)
                    return true
                }

                else {
                    if (isBrightnessControlActive) {
                        Log.v(TAG, "Horizontal navigation ignored - brightness control active")
                    } else if (isNavigationActive) {
                        Log.v(TAG, "Brightness control ignored - navigation active")
                    } else {
                        Log.v(TAG, "Gesture ignored - insufficient movement (deltaX: $absDeltaX, deltaY: $absDeltaY)")
                    }
                    return false
                }
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (!shouldProcessGesture() || !isValidGesture(e1, e2)) return false

                e1?.let { startEvent ->
                    val totalDeltaX = e2.x - startEvent.x
                    val totalDeltaY = e2.y - startEvent.y

                    val absTotalDeltaX = abs(totalDeltaX)
                    val absTotalDeltaY = abs(totalDeltaY)

                    // BRIGHTNESS SCROLLING - only if navigation is not active
                    // Very restrictive criteria to avoid interfering with navigation
                    val isOverwhelminglyVertical = absTotalDeltaY > absTotalDeltaX * 3.0f && absTotalDeltaY > 60 && absTotalDeltaX < 30

                    if (!isNavigationActive && isOverwhelminglyVertical) {
                        setBrightnessControlActive()

                        val brightnessChange = distanceY * BRIGHTNESS_ADJUSTMENT_FACTOR
                        val newBrightness = (currentBrightness + brightnessChange).coerceIn(0.0f, 1.0f)

                        setBrightness(newBrightness)
                        return true
                    }
                }

                // Return false for ALL other cases to ensure fling detection works properly
                return false
            }
        }

        gestureDetector = GestureDetectorCompat(context, gestureListener)

        // Set touch listener on the main container
        containerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
    }

    fun setBrightness(brightness: Float) {
        currentBrightness = brightness.coerceIn(0.0f, 1.0f)
        uiManager.setBrightness(currentBrightness)
        uiManager.showBrightnessIndicator(currentBrightness)
        onBrightnessChange(currentBrightness)
        Log.d(TAG, "Brightness set to: ${(currentBrightness * 100).toInt()}%")
    }

    fun setInitialBrightness(brightness: Float) {
        currentBrightness = brightness.coerceIn(0.0f, 1.0f)
        uiManager.setBrightness(currentBrightness)
        Log.d(TAG, "Initial brightness set to: ${(currentBrightness * 100).toInt()}%")
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

        // Very minimal edge detection - allow almost entire screen
        if (e1.x < EDGE_THRESHOLD || e1.x > screenWidth - EDGE_THRESHOLD) return false

        // Accept almost any gesture timing
        val deltaTime = e2.eventTime - e1.eventTime
        if (deltaTime < 10) return false // Only reject extremely fast accidental touches

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

    fun getCurrentBrightness(): Float = currentBrightness

    private fun setNavigationActive() {
        isNavigationActive = true
        isBrightnessControlActive = false
        scheduleGestureStateReset()
        Log.v(TAG, "Navigation active - brightness control disabled")
    }

    private fun setBrightnessControlActive() {
        isBrightnessControlActive = true
        isNavigationActive = false
        scheduleGestureStateReset()
        Log.v(TAG, "Brightness control active - navigation disabled")
    }

    private fun scheduleGestureStateReset() {
        gestureStateResetRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
        }

        gestureStateResetRunnable = Runnable {
            resetGestureState()
        }.also { runnable ->
            handler.postDelayed(runnable, GESTURE_STATE_RESET_DELAY)
        }
    }

    private fun resetGestureState() {
        isBrightnessControlActive = false
        isNavigationActive = false
        gestureStateResetRunnable = null
        Log.v(TAG, "Gesture state reset - both controls available")
    }

    fun cleanup() {
        gestureStateResetRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
        }
        resetGestureState()
    }
}