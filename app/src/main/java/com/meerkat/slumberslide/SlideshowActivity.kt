// SlideshowActivity.kt - Updated with idle-based triggering and improved battery management
package com.meerkat.slumberslide

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
    private lateinit var brightnessIndicator: CircularBrightnessIndicator

    // Managers
    private lateinit var uiManager: SlideshowUIManager
    private lateinit var gestureHandler: SlideshowGestureHandler
    private lateinit var imageLoader: SlideshowImageLoader
    private lateinit var transitionManager: ImageTransitionManager
    private lateinit var zoomManager: SlideshowZoomManager
    private lateinit var imageListManager: ImageListManager

    // Core properties
    private lateinit var preferencesManager: PreferencesManager
    private var isActivityActive = false
    private var hasStartedSlideshow = false
    private var isBatteryReceiverRegistered = false
    private var isExitReceiverRegistered = false

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "SlideshowActivity"
        private const val MIN_BATTERY_LEVEL = 20
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val batteryLevel = getBatteryLevel()
                Log.d(TAG, "Battery level: $batteryLevel%")

                if (!canContinueBasedOnBattery()) {
                    Log.d(TAG, "Battery conditions no longer met, exiting slideshow")
                    showBatteryWarningAndExit()
                }
            }
        }
    }

    private val exitReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.meerkat.slumberslide.EXIT_SLIDESHOW") {
                Log.d(TAG, "Received exit broadcast - user activity detected")
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        try {
            setContentView(R.layout.activity_slideshow)
            initViews()
            initManagers()

            // Load basic settings first for battery check
            imageListManager.loadAndFilterPhotos()

            setupActivity()

            if (!canContinueBasedOnBattery()) {
                Log.w(TAG, "Battery conditions not met to start slideshow")
                showBatteryWarningAndExit()
                return
            }

            if (imageListManager.getCurrentPhotoList().isEmpty()) {
                Log.e(TAG, "No photos available for current orientation, finishing activity")
                finish()
                return
            }

            isActivityActive = true
            registerBatteryReceiver()
            registerExitReceiver()
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
        brightnessIndicator = findViewById(R.id.brightnessIndicator)

        nextImageView.alpha = 0f
        nextBackgroundImageView.alpha = 0f
        pauseIndicator.visibility = android.view.View.GONE
        gestureHints.visibility = android.view.View.GONE
        brightnessIndicator.visibility = android.view.View.GONE
    }

    private fun initManagers() {
        preferencesManager = PreferencesManager(this)

        uiManager = SlideshowUIManager(this, pauseIndicator, gestureHints, brightnessIndicator)
        imageListManager = ImageListManager(this, preferencesManager)
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
            imageListManager,
            ::onPauseToggle,
            ::onNavigateToNext,
            ::onNavigateToPrevious,
            ::onBrightnessChange,
            ::onExit
        )
    }

    private fun setupActivity() {
        uiManager.setupFullscreen()
        gestureHandler.setupGestureDetection(findViewById(R.id.slideshowContainer))

        // Apply feathering setting to BorderImageViews
        val settings = imageListManager.getSettings()
        currentImageView.featheringAmount = settings.featheringAmount
        nextImageView.featheringAmount = settings.featheringAmount

        // Set initial brightness for slideshow
        gestureHandler.setInitialBrightness(settings.slideshowBrightness)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "Configuration changed: orientation = ${newConfig.orientation}")

        val oldPhotoCount = imageListManager.getCurrentPhotoList().size
        imageListManager.handleOrientationChange()

        Log.d(TAG, "Orientation change: ${oldPhotoCount} -> ${imageListManager.getCurrentPhotoList().size} photos")

        if (imageListManager.getCurrentPhotoList().isEmpty()) {
            Log.w(TAG, "No photos available for new orientation, finishing slideshow")
            finish()
            return
        }

        if (hasStartedSlideshow) {
            showNextImage()
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

    private fun registerExitReceiver() {
        try {
            val filter = IntentFilter("com.meerkat.slumberslide.EXIT_SLIDESHOW")
            registerReceiver(exitReceiver, filter)
            isExitReceiverRegistered = true
            Log.d(TAG, "Exit receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering exit receiver", e)
            isExitReceiverRegistered = false
        }
    }

    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery level", e)
            100
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
        val settings = imageListManager.getSettings()
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
        val settings = imageListManager.getSettings()
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
        }, 2000)
    }

    private fun startSlideshow() {
        if (imageListManager.getCurrentPhotoList().isNotEmpty() && isActivityActive && !hasStartedSlideshow) {
            hasStartedSlideshow = true
            Log.d(TAG, "Starting slideshow with ${imageListManager.getCurrentPhotoList().size} photos")
            showNextImage()
        }
    }

    private fun showNextImage(
        fastTransition: Boolean = false,
        swipeDirection: SlideshowGestureHandler.SwipeDirection? = null,
        resumeAutomaticProgression: Boolean = false
    ) {
        if (imageListManager.getCurrentPhotoList().isEmpty() || !isActivityActive) return

        if (!canContinueBasedOnBattery()) {
            Log.w(TAG, "Battery conditions no longer met during slideshow, exiting")
            showBatteryWarningAndExit()
            return
        }

        val currentPhoto = imageListManager.getCurrentPhoto()
        Log.d(TAG, "Loading image ${imageListManager.getCurrentIndex() + 1}/${imageListManager.getCurrentPhotoList().size}: ${currentPhoto.orientation}")

        imageLoader.loadImage(
            photoInfo = currentPhoto,
            targetView = nextImageView,
            backgroundView = if (imageListManager.getSettings().enableBlurredBackground) nextBackgroundImageView else null,
            onImageReady = { drawable ->
                val settings = imageListManager.getSettings()
                val photoIndex = imageListManager.getCurrentIndex()

                nextImageView.featheringAmount = settings.featheringAmount

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
            },
            onError = {
                val settings = imageListManager.getSettings()
                val photoIndex = imageListManager.getCurrentIndex()

                nextImageView.featheringAmount = settings.featheringAmount

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

        val featheringAmount = imageListManager.getSettings().featheringAmount

        if (viewReferences.currentImageView is BorderImageView) {
            currentImageView = viewReferences.currentImageView
            currentImageView.featheringAmount = featheringAmount
        }

        if (viewReferences.nextImageView is BorderImageView) {
            nextImageView = viewReferences.nextImageView
            nextImageView.featheringAmount = featheringAmount
        }

        currentBackgroundImageView = viewReferences.currentBackgroundView
        nextBackgroundImageView = viewReferences.nextBackgroundView

        Log.d(TAG, "View references synchronized with feathering amount: ${featheringAmount}px")
    }

    private fun scheduleNextImage() {
        if (!isActivityActive || gestureHandler.isPaused()) return

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (isActivityActive && !gestureHandler.isPaused()) {
                imageListManager.moveToNext()
                showNextImage()
            }
        }, imageListManager.getSettings().slideDuration.toLong())
    }

    private fun onPauseToggle() {
        if (gestureHandler.isPaused()) {
            handler.removeCallbacksAndMessages(null)
            zoomManager.pauseZoom()
        } else {
            if (!transitionManager.isTransitioning()) {
                scheduleNextImage()
                zoomManager.resumeZoom(currentImageView, imageListManager.getCurrentIndex(), imageListManager.getSettings())
            }
        }
    }

    private fun onNavigateToNext(swipeDirection: SlideshowGestureHandler.SwipeDirection?) {
        if (transitionManager.isTransitioning()) return

        val wasRunning = !gestureHandler.isPaused()
        if (wasRunning) {
            handler.removeCallbacksAndMessages(null)
        }

        imageListManager.moveToNext()
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

        imageListManager.moveToPrevious()
        showNextImage(
            fastTransition = true,
            swipeDirection = swipeDirection,
            resumeAutomaticProgression = wasRunning
        )
    }

    private fun onExit() {
        finish()
    }

    private fun onBrightnessChange(brightness: Float) {
        // Save the brightness change to preferences for persistence
        val currentSettings = imageListManager.getSettings()
        val updatedSettings = currentSettings.copy(slideshowBrightness = brightness)
        preferencesManager.saveSettings(updatedSettings)
        Log.d(TAG, "Brightness saved: ${(brightness * 100).toInt()}%")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")

        isActivityActive = false
        handler.removeCallbacksAndMessages(null)
        zoomManager.cleanup()
        uiManager.cleanup()

        if (isBatteryReceiverRegistered) {
            try {
                unregisterReceiver(batteryReceiver)
                isBatteryReceiverRegistered = false
                Log.d(TAG, "Battery receiver unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering battery receiver", e)
            }
        }

        if (isExitReceiverRegistered) {
            try {
                unregisterReceiver(exitReceiver)
                isExitReceiverRegistered = false
                Log.d(TAG, "Exit receiver unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering exit receiver", e)
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}