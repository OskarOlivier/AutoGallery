// SlideshowUIManager.kt - Handles UI state and fullscreen functionality
package com.meerkat.autogallery

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout

class SlideshowUIManager(
    private val activity: Activity,
    private val pauseIndicator: LinearLayout,
    private val gestureHints: LinearLayout,
    private val brightnessIndicator: CircularBrightnessIndicator
) {

    companion object {
        private const val TAG = "SlideshowUIManager"
        private const val BRIGHTNESS_INDICATOR_DURATION = 1250L // 1.25 seconds
    }

    private var brightnessIndicatorRunnable: Runnable? = null

    fun setupFullscreen() {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                activity.window.setDecorFitsSystemWindows(false)
            }
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
            activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }

        setupUIVisibilityListener()
        Log.d(TAG, "Fullscreen setup completed for Android ${Build.VERSION.SDK_INT}")
    }

    fun setBrightness(brightness: Float) {
        val clampedBrightness = brightness.coerceIn(0.0f, 1.0f)
        val layoutParams = activity.window.attributes
        layoutParams.screenBrightness = clampedBrightness
        activity.window.attributes = layoutParams
        Log.d(TAG, "Window brightness set to: $clampedBrightness")
    }

    fun showBrightnessIndicator(brightness: Float) {
        val clampedBrightness = brightness.coerceIn(0.0f, 1.0f)

        // Always update the circular progress
        brightnessIndicator.setBrightnessProgress(clampedBrightness)

        // Check if indicator is already visible to avoid flickering
        val wasVisible = brightnessIndicator.visibility == View.VISIBLE && brightnessIndicator.alpha > 0.1f

        if (!wasVisible) {
            // Show the indicator with fade-in animation only if it wasn't already visible
            brightnessIndicator.visibility = View.VISIBLE
            brightnessIndicator.alpha = 0f
            brightnessIndicator.animate()
                .alpha(0.9f)
                .setDuration(200)
                .start()

            Log.d(TAG, "Brightness indicator shown with fade-in: ${(clampedBrightness * 100).toInt()}%")
        } else {
            // Just ensure it's at full opacity if it was already visible
            brightnessIndicator.alpha = 0.9f
            Log.v(TAG, "Brightness indicator updated: ${(clampedBrightness * 100).toInt()}%")
        }

        // Always cancel any existing hide runnable and reschedule
        brightnessIndicatorRunnable?.let {
            brightnessIndicator.removeCallbacks(it)
        }

        // Schedule hiding the indicator
        brightnessIndicatorRunnable = Runnable {
            hideBrightnessIndicator()
        }.also { runnable ->
            brightnessIndicator.postDelayed(runnable, BRIGHTNESS_INDICATOR_DURATION)
        }
    }

    fun hideBrightnessIndicator() {
        brightnessIndicatorRunnable?.let {
            brightnessIndicator.removeCallbacks(it)
            brightnessIndicatorRunnable = null
        }

        if (brightnessIndicator.visibility == View.VISIBLE) {
            brightnessIndicator.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    brightnessIndicator.visibility = View.GONE
                }
                .start()
        }
    }

    private fun setupUIVisibilityListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.decorView.setOnApplyWindowInsetsListener { _, insets ->
                val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
                if (systemBars.top > 0 || systemBars.bottom > 0) {
                    activity.window.insetsController?.hide(WindowInsets.Type.systemBars())
                }
                insets
            }
        } else {
            activity.window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
                if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                    @Suppress("DEPRECATION")
                    activity.window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            )
                }
            }
        }
    }

    fun showPauseIndicator() {
        val pauseIcon = pauseIndicator.findViewById<ImageView>(R.id.pauseIcon)
        pauseIcon.setImageResource(R.drawable.ic_pause)

        pauseIndicator.visibility = View.VISIBLE
        pauseIndicator.alpha = 0f
        pauseIndicator.animate()
            .alpha(0.9f)
            .setDuration(200)
            .start()
    }

    fun hidePauseIndicator() {
        pauseIndicator.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                pauseIndicator.visibility = View.GONE
            }
            .start()
    }

    fun showGestureHintsIfNeeded() {
        val prefs = activity.getSharedPreferences("gesture_hints", Context.MODE_PRIVATE)
        val hintCount = prefs.getInt("hint_count", 0)

        if (hintCount < 3) {
            gestureHints.apply {
                visibility = View.VISIBLE
                alpha = 0f
                animate()
                    .alpha(1f)
                    .setDuration(500)
                    .setStartDelay(2000) // Show after 2 seconds
                    .withEndAction {
                        animate()
                            .alpha(0f)
                            .setDuration(500)
                            .setStartDelay(4000) // Hide after 4 seconds
                            .withEndAction { visibility = View.GONE }
                            .start()
                    }
                    .start()
            }

            prefs.edit().putInt("hint_count", hintCount + 1).apply()
        }
    }

    fun hideGestureHints() {
        if (gestureHints.visibility == View.VISIBLE) {
            gestureHints.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction { gestureHints.visibility = View.GONE }
                .start()
        }
    }

    fun showExitFeedback() {
        // Brief visual feedback before exiting
        val overlay = View(activity).apply {
            setBackgroundColor(0x40FFFFFF)
        }

        val container = activity.findViewById<View>(R.id.slideshowContainer) as android.widget.FrameLayout
        container.addView(overlay, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))

        overlay.alpha = 0f
        overlay.animate()
            .alpha(1f)
            .setDuration(150)
            .withEndAction {
                overlay.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    fun cleanup() {
        brightnessIndicatorRunnable?.let {
            brightnessIndicator.removeCallbacks(it)
            brightnessIndicatorRunnable = null
        }
    }
}