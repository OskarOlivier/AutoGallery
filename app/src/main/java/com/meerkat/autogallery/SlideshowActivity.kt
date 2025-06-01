// Fixed SlideshowActivity.kt - Synchronizes view references after transitions
package com.meerkat.autogallery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class SlideshowActivity : AppCompatActivity() {

    // UI Components
    private lateinit var currentImageView: ImageView
    private lateinit var nextImageView: ImageView
    private lateinit var currentBackgroundImageView: ImageView
    private lateinit var nextBackgroundImageView: ImageView
    private lateinit var pauseIndicator: LinearLayout
    private lateinit var gestureHints: LinearLayout

    // Managers
    private lateinit var uiManager: SlideshowUIManager
    private lateinit var gestureHandler: SlideshowGestureHandler
    private lateinit var imageLoader: SlideshowImageLoader
    private lateinit var transitionManager: ImageTransitionManager
    private lateinit var zoomManager: SlideshowZoomManager
    private lateinit var photoListManager: PhotoListManager

    // Core properties
    private lateinit var preferencesManager: PreferencesManager
    private var isActivityActive = false
    private var hasStartedSlideshow = false
    private var startTime = 0L
    private var shouldIgnoreScreenOn = true
    private var isReceiverRegistered = false

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "SlideshowActivity"
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Screen state changed: ${intent?.action}")
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    val timeSinceStart = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Screen turned on after ${timeSinceStart}ms, shouldIgnoreScreenOn: $shouldIgnoreScreenOn")

                    if (shouldIgnoreScreenOn && timeSinceStart < 5000) {
                        Log.d(TAG, "Ignoring screen on event - activity just started")
                        handler.postDelayed({
                            shouldIgnoreScreenOn = false
                            Log.d(TAG, "Now listening for screen on events")
                        }, 10000 - timeSinceStart)
                        return
                    }

                    Log.d(TAG, "Screen turned on, finishing slideshow")
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startTime = System.currentTimeMillis()
        Log.d(TAG, "onCreate called at $startTime")

        try {
            setContentView(R.layout.activity_slideshow)
            initViews()
            initManagers()
            setupActivity()

            if (photoListManager.getCurrentPhotoList().isEmpty()) {
                Log.e(TAG, "No photos available for current orientation, finishing activity")
                finish()
                return
            }

            isActivityActive = true
            registerScreenStateReceiver()
            uiManager.showGestureHintsIfNeeded()

            handler.postDelayed({
                if (!isFinishing && isActivityActive) {
                    Log.d(TAG, "Starting slideshow after delay")
                    startSlideshow()
                }
            }, 500)

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            finish()
        }
    }

    private fun initViews() {
        currentImageView = findViewById(R.id.currentImageView)
        nextImageView = findViewById(R.id.nextImageView)
        currentBackgroundImageView = findViewById(R.id.currentBackgroundImageView)
        nextBackgroundImageView = findViewById(R.id.nextBackgroundImageView)
        pauseIndicator = findViewById(R.id.pauseIndicator)
        gestureHints = findViewById(R.id.gestureHints)

        // Initially hide the next image views and overlays
        nextImageView.alpha = 0f
        nextBackgroundImageView.alpha = 0f
        pauseIndicator.visibility = android.view.View.GONE
        gestureHints.visibility = android.view.View.GONE
    }

    private fun initManagers() {
        preferencesManager = PreferencesManager(this)

        // Initialize managers with dependencies
        uiManager = SlideshowUIManager(this, pauseIndicator, gestureHints)
        photoListManager = PhotoListManager(this, preferencesManager)
        imageLoader = SlideshowImageLoader(this)
        zoomManager = SlideshowZoomManager()

        transitionManager = ImageTransitionManager(
            this,
            currentImageView,
            nextImageView,
            currentBackgroundImageView,
            nextBackgroundImageView,
            zoomManager
        )

        gestureHandler = SlideshowGestureHandler(
            this,
            uiManager,
            photoListManager,
            ::onPauseToggle,
            ::onNavigateToNext,
            ::onNavigateToPrevious,
            ::onExit
        )
    }

    private fun setupActivity() {
        uiManager.setupFullscreen()
        gestureHandler.setupGestureDetection(findViewById(R.id.slideshowContainer))
        photoListManager.loadAndFilterPhotos()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "Configuration changed: orientation = ${newConfig.orientation}")

        val oldPhotoCount = photoListManager.getCurrentPhotoList().size
        photoListManager.handleOrientationChange()

        Log.d(TAG, "Orientation change: ${oldPhotoCount} -> ${photoListManager.getCurrentPhotoList().size} photos")

        if (photoListManager.getCurrentPhotoList().isEmpty()) {
            Log.w(TAG, "No photos available for new orientation, finishing slideshow")
            finish()
            return
        }

        if (hasStartedSlideshow) {
            showNextImage()
        }
    }

    private fun registerScreenStateReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenStateReceiver, filter)
            isReceiverRegistered = true
            Log.d(TAG, "Screen state receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering screen state receiver", e)
            isReceiverRegistered = false
        }
    }

    private fun startSlideshow() {
        if (photoListManager.getCurrentPhotoList().isNotEmpty() && isActivityActive && !hasStartedSlideshow) {
            hasStartedSlideshow = true
            Log.d(TAG, "Starting slideshow with ${photoListManager.getCurrentPhotoList().size} photos")
            showNextImage()
        }
    }

    private fun showNextImage(fastTransition: Boolean = false) {
        if (photoListManager.getCurrentPhotoList().isEmpty() || !isActivityActive) return

        val currentPhoto = photoListManager.getCurrentPhoto()
        Log.d(TAG, "Loading image ${photoListManager.getCurrentIndex() + 1}/${photoListManager.getCurrentPhotoList().size}: ${currentPhoto.orientation}")

        imageLoader.loadImage(
            photoInfo = currentPhoto,
            targetView = nextImageView,
            backgroundView = if (photoListManager.getSettings().enableBlurredBackground) nextBackgroundImageView else null,
            onImageReady = { drawable ->
                zoomManager.setInitialScale(nextImageView, photoListManager.getCurrentIndex(), photoListManager.getSettings())
                transitionManager.performTransition(
                    settings = photoListManager.getSettings(),
                    photoIndex = photoListManager.getCurrentIndex(),
                    fastTransition = fastTransition
                ) { updatedViewReferences ->
                    // CRITICAL FIX: Update our view references to match the transition manager
                    updateViewReferences(updatedViewReferences)

                    // Schedule next image only if not paused and not a manual fast transition
                    if (!fastTransition && !gestureHandler.isPaused()) {
                        scheduleNextImage()
                    }
                }
            },
            onError = {
                zoomManager.setInitialScale(nextImageView, photoListManager.getCurrentIndex(), photoListManager.getSettings())
                transitionManager.performTransition(
                    settings = photoListManager.getSettings(),
                    photoIndex = photoListManager.getCurrentIndex(),
                    fastTransition = fastTransition
                ) { updatedViewReferences ->
                    // CRITICAL FIX: Update our view references to match the transition manager
                    updateViewReferences(updatedViewReferences)

                    if (!fastTransition && !gestureHandler.isPaused()) {
                        scheduleNextImage()
                    }
                }
            }
        )
    }

    // NEW METHOD: Synchronize view references with transition manager
    private fun updateViewReferences(viewReferences: ImageTransitionManager.ViewReferences) {
        Log.d(TAG, "Updating view references to match transition manager")

        currentImageView = viewReferences.currentImageView
        nextImageView = viewReferences.nextImageView
        currentBackgroundImageView = viewReferences.currentBackgroundView
        nextBackgroundImageView = viewReferences.nextBackgroundView

        Log.d(TAG, "View references synchronized - " +
                "currentImageView: ${currentImageView.id}, " +
                "nextImageView: ${nextImageView.id}")
    }

    private fun scheduleNextImage() {
        if (!isActivityActive || gestureHandler.isPaused()) return

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (isActivityActive && !gestureHandler.isPaused()) {
                photoListManager.moveToNext()
                showNextImage()
            }
        }, photoListManager.getSettings().slideDuration.toLong())
    }

    // Gesture handler callbacks
    private fun onPauseToggle() {
        if (gestureHandler.isPaused()) {
            handler.removeCallbacksAndMessages(null)
            zoomManager.pauseZoom()
        } else {
            if (!transitionManager.isTransitioning()) {
                scheduleNextImage()
                zoomManager.resumeZoom(currentImageView, photoListManager.getCurrentIndex(), photoListManager.getSettings())
            }
        }
    }

    private fun onNavigateToNext() {
        if (transitionManager.isTransitioning()) return

        val wasRunning = !gestureHandler.isPaused()
        if (wasRunning) {
            handler.removeCallbacksAndMessages(null)
        }

        photoListManager.moveToNext()
        showNextImage(fastTransition = true)
    }

    private fun onNavigateToPrevious() {
        if (transitionManager.isTransitioning()) return

        val wasRunning = !gestureHandler.isPaused()
        if (wasRunning) {
            handler.removeCallbacksAndMessages(null)
        }

        photoListManager.moveToPrevious()
        showNextImage(fastTransition = true)
    }

    private fun onExit() {
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")

        isActivityActive = false
        shouldIgnoreScreenOn = false
        handler.removeCallbacksAndMessages(null)
        zoomManager.cleanup()

        if (isReceiverRegistered) {
            try {
                unregisterReceiver(screenStateReceiver)
                isReceiverRegistered = false
                Log.d(TAG, "Screen state receiver unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver", e)
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}