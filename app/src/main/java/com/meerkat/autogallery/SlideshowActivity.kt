// SlideshowActivity.kt - Updated with feathering support and new battery management
package com.meerkat.autogallery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SlideshowActivity : AppCompatActivity() {

    // UI Components
    private lateinit var currentImageView: BorderImageView
    private lateinit var nextImageView: BorderImageView
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
    private var isBatteryReceiverRegistered = false

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "SlideshowActivity"
        private const val MIN_BATTERY_LEVEL = 20 // Same as service
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

    // Battery monitoring receiver with new battery management logic
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val batteryLevel = getBatteryLevel()
                Log.d(TAG, "Battery level: $batteryLevel%")

                // Check if slideshow should continue based on new battery management
                if (!canContinueBasedOnBattery()) {
                    Log.d(TAG, "Battery conditions no longer met, exiting slideshow")
                    showBatteryWarningAndExit()
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

            // Check battery conditions before starting with new battery management
            if (!canContinueBasedOnBattery()) {
                Log.w(TAG, "Battery conditions not met to start slideshow")
                showBatteryWarningAndExit()
                return
            }

            if (photoListManager.getCurrentPhotoList().isEmpty()) {
                Log.e(TAG, "No photos available for current orientation, finishing activity")
                finish()
                return
            }

            isActivityActive = true
            registerScreenStateReceiver()
            registerBatteryReceiver()
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

        nextImageView.alpha = 0f
        nextBackgroundImageView.alpha = 0f
        pauseIndicator.visibility = android.view.View.GONE
        gestureHints.visibility = android.view.View.GONE
    }

    private fun initManagers() {
        preferencesManager = PreferencesManager(this)

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

        // Apply feathering setting to BorderImageViews
        val settings = photoListManager.getSettings()
        currentImageView.featheringEnabled = settings.enableFeathering
        nextImageView.featheringEnabled = settings.enableFeathering
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

    private fun registerBatteryReceiver() {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            registerReceiver(batteryReceiver, filter)
            isBatteryReceiverRegistered = true
            Log.d(TAG, "Battery receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering battery receiver", e)
            isBatteryReceiverRegistered = false
        }
    }

    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery level", e)
            100 // Assume full battery if we can't get the level
        }
    }

    private fun isDeviceCharging(): Boolean {
        return try {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.isCharging
        } catch (e: Exception) {
            Log.e(TAG, "Error checking charging status", e)
            false
        }
    }

    private fun canContinueBasedOnBattery(): Boolean {
        val settings = photoListManager.getSettings()
        val batteryLevel = getBatteryLevel()
        val isCharging = isDeviceCharging()

        return when (settings.batteryManagementMode) {
            BatteryManagementMode.CHARGING_ONLY -> {
                Log.d(TAG, "Battery mode: CHARGING_ONLY - charging: $isCharging")
                isCharging
            }
            BatteryManagementMode.BATTERY_LEVEL_ONLY -> {
                Log.d(TAG, "Battery mode: BATTERY_LEVEL_ONLY - level: $batteryLevel% (required: >$MIN_BATTERY_LEVEL%)")
                batteryLevel > MIN_BATTERY_LEVEL
            }
        }
    }

    private fun showBatteryWarningAndExit() {
        val settings = photoListManager.getSettings()
        val batteryLevel = getBatteryLevel()
        val isCharging = isDeviceCharging()

        val message = when (settings.batteryManagementMode) {
            BatteryManagementMode.CHARGING_ONLY -> {
                if (isCharging) {
                    "Battery issue detected. Slideshow stopped."
                } else {
                    "Device unplugged. Slideshow stopped."
                }
            }
            BatteryManagementMode.BATTERY_LEVEL_ONLY -> {
                "Battery too low ($batteryLevel%). Slideshow stopped to preserve battery."
            }
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        handler.postDelayed({
            finish()
        }, 2000) // Give time to read the message
    }

    private fun startSlideshow() {
        if (photoListManager.getCurrentPhotoList().isNotEmpty() && isActivityActive && !hasStartedSlideshow) {
            hasStartedSlideshow = true
            Log.d(TAG, "Starting slideshow with ${photoListManager.getCurrentPhotoList().size} photos")
            showNextImage()
        }
    }

    private fun showNextImage(
        fastTransition: Boolean = false,
        swipeDirection: SlideshowGestureHandler.SwipeDirection? = null,
        resumeAutomaticProgression: Boolean = false
    ) {
        if (photoListManager.getCurrentPhotoList().isEmpty() || !isActivityActive) return

        // Additional battery check before loading next image
        if (!canContinueBasedOnBattery()) {
            Log.w(TAG, "Battery conditions no longer met during slideshow, exiting")
            showBatteryWarningAndExit()
            return
        }

        val currentPhoto = photoListManager.getCurrentPhoto()
        Log.d(TAG, "Loading image ${photoListManager.getCurrentIndex() + 1}/${photoListManager.getCurrentPhotoList().size}: ${currentPhoto.orientation}")

        imageLoader.loadImage(
            photoInfo = currentPhoto,
            targetView = nextImageView,
            backgroundView = if (photoListManager.getSettings().enableBlurredBackground) nextBackgroundImageView else null,
            onImageReady = { drawable ->
                val settings = photoListManager.getSettings()
                val photoIndex = photoListManager.getCurrentIndex()

                // Ensure feathering setting is applied to the image view that will become current
                nextImageView.featheringEnabled = settings.enableFeathering

                zoomManager.setInitialScale(nextImageView, photoIndex, settings)
                zoomManager.startZoomOnView(nextImageView, photoIndex, settings, isPreTransition = true)

                transitionManager.performTransition(
                    settings = settings,
                    photoIndex = photoIndex,
                    fastTransition = fastTransition,
                    swipeDirection = swipeDirection
                ) { updatedViewReferences ->
                    updateViewReferences(updatedViewReferences)

                    // Resume automatic progression if:
                    // 1. Normal transition (!fastTransition) OR fast transition that should resume (resumeAutomaticProgression)
                    // 2. AND slideshow is not paused
                    if ((!fastTransition || resumeAutomaticProgression) && !gestureHandler.isPaused()) {
                        scheduleNextImage()
                    }
                }
            },
            onError = {
                val settings = photoListManager.getSettings()
                val photoIndex = photoListManager.getCurrentIndex()

                // Ensure feathering setting is applied even on error
                nextImageView.featheringEnabled = settings.enableFeathering

                zoomManager.setInitialScale(nextImageView, photoIndex, settings)
                zoomManager.startZoomOnView(nextImageView, photoIndex, settings, isPreTransition = true)

                transitionManager.performTransition(
                    settings = settings,
                    photoIndex = photoIndex,
                    fastTransition = fastTransition,
                    swipeDirection = swipeDirection
                ) { updatedViewReferences ->
                    updateViewReferences(updatedViewReferences)

                    if ((!fastTransition || resumeAutomaticProgression) && !gestureHandler.isPaused()) {
                        scheduleNextImage()
                    }
                }
            }
        )
    }

    private fun updateViewReferences(viewReferences: ImageTransitionManager.ViewReferences) {
        Log.d(TAG, "Updating view references to match transition manager")

        // Update view references and ensure feathering settings are maintained
        val featheringEnabled = photoListManager.getSettings().enableFeathering

        if (viewReferences.currentImageView is BorderImageView) {
            currentImageView = viewReferences.currentImageView
            currentImageView.featheringEnabled = featheringEnabled
        }

        if (viewReferences.nextImageView is BorderImageView) {
            nextImageView = viewReferences.nextImageView
            nextImageView.featheringEnabled = featheringEnabled
        }

        currentBackgroundImageView = viewReferences.currentBackgroundView
        nextBackgroundImageView = viewReferences.nextBackgroundView

        Log.d(TAG, "View references synchronized with feathering enabled: $featheringEnabled")
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

    // Updated gesture handler callbacks with swipe direction support
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

    private fun onNavigateToNext(swipeDirection: SlideshowGestureHandler.SwipeDirection?) {
        if (transitionManager.isTransitioning()) return

        val wasRunning = !gestureHandler.isPaused()
        if (wasRunning) {
            handler.removeCallbacksAndMessages(null)
        }

        photoListManager.moveToNext()
        showNextImage(
            fastTransition = true,
            swipeDirection = swipeDirection,
            resumeAutomaticProgression = wasRunning
        )
    }

    private fun onNavigateToPrevious(swipeDirection: SlideshowGestureHandler.SwipeDirection?) {
        if (transitionManager.isTransitioning()) return

        val wasRunning = !gestureHandler.isPaused()
        if (wasRunning) {
            handler.removeCallbacksAndMessages(null)
        }

        photoListManager.moveToPrevious()
        showNextImage(
            fastTransition = true,
            swipeDirection = swipeDirection,
            resumeAutomaticProgression = wasRunning
        )
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
                Log.w(TAG, "Error unregistering screen receiver", e)
            }
        }

        if (isBatteryReceiverRegistered) {
            try {
                unregisterReceiver(batteryReceiver)
                isBatteryReceiverRegistered = false
                Log.d(TAG, "Battery receiver unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering battery receiver", e)
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}