// SlideshowActivity.kt - Fixed null safety issues
package com.meerkat.autogallery

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

class SlideshowActivity : AppCompatActivity() {

    private lateinit var currentImageView: ImageView
    private lateinit var nextImageView: ImageView
    private lateinit var currentBackgroundImageView: ImageView
    private lateinit var nextBackgroundImageView: ImageView
    private lateinit var pauseIndicator: LinearLayout
    private lateinit var gestureHints: LinearLayout

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var settings: GallerySettings
    private val handler = Handler(Looper.getMainLooper())
    private var currentPhotoIndex = 0
    private var currentPhotoList = mutableListOf<PhotoInfo>()
    private var allPhotoList = mutableListOf<PhotoInfo>()
    private var currentDeviceOrientation: ImageOrientation = ImageOrientation.LANDSCAPE
    private var slideshowRunnable: Runnable? = null
    private var currentZoomAnimator: ObjectAnimator? = null
    private var isActivityActive = false
    private var hasStartedSlideshow = false
    private var startTime = 0L
    private var shouldIgnoreScreenOn = true
    private var isReceiverRegistered = false

    // Gesture handling variables
    private lateinit var gestureDetector: GestureDetectorCompat
    private var isPaused = false
    private var isTransitioning = false
    private var lastGestureTime = 0L

    // Screen dimension variables for custom image fitting
    private var screenWidth = 0
    private var screenHeight = 0

    companion object {
        private const val TAG = "SlideshowActivity"
        private const val MIN_SWIPE_DISTANCE = 100 // pixels
        private const val MIN_SWIPE_VELOCITY = 800 // pixels per second
        private const val GESTURE_DEBOUNCE_TIME = 200L // ms
        private const val EDGE_THRESHOLD = 50 // pixels
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
            getScreenDimensions()
            setContentView(R.layout.activity_slideshow)
            setupFullscreen()
            setupUIVisibilityListener()
            initViews()
            setupGestureDetection()
            loadSettings()

            if (currentPhotoList.isEmpty()) {
                Log.e(TAG, "No photos available for current orientation, finishing activity")
                finish()
                return
            }

            isActivityActive = true
            registerScreenStateReceiver()
            showGestureHintsIfNeeded()

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

    private fun getScreenDimensions() {
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        currentDeviceOrientation = OrientationUtils.getCurrentDeviceOrientation(screenWidth, screenHeight)
        Log.d(TAG, "Screen dimensions: ${screenWidth}x${screenHeight}, orientation: $currentDeviceOrientation")
    }

    private fun setupFullscreen() {
        window.setFlags(
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
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                window.setDecorFitsSystemWindows(false)
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }

        Log.d(TAG, "Fullscreen setup completed for Android ${Build.VERSION.SDK_INT}")
    }

    private fun setupUIVisibilityListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.setOnApplyWindowInsetsListener { _, insets ->
                val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
                if (systemBars.top > 0 || systemBars.bottom > 0) {
                    window.insetsController?.hide(WindowInsets.Type.systemBars())
                }
                insets
            }
        } else {
            window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
                if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = (
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
        pauseIndicator.visibility = View.GONE
        gestureHints.visibility = View.GONE
    }

    private fun setupGestureDetection() {
        val gestureListener = object : GestureDetector.SimpleOnGestureListener() {

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!shouldProcessGesture()) return false
                Log.d(TAG, "Single tap detected")
                togglePauseResume()
                provideFeedback(if (isPaused) "pause" else "resume")
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!shouldProcessGesture()) return false
                Log.d(TAG, "Double tap detected - exiting slideshow")
                showExitFeedback()
                provideFeedback("exit")
                finish()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (!shouldProcessGesture() || !isValidGesture(e1, e2)) return false

                // Safe access to e1 properties since e1 can be null
                e1?.let { startEvent ->
                    val deltaX = e2.x - startEvent.x
                    val deltaY = e2.y - startEvent.y

                    // Check if it's a horizontal swipe
                    if (abs(deltaX) > abs(deltaY) &&
                        abs(deltaX) > MIN_SWIPE_DISTANCE &&
                        abs(velocityX) > MIN_SWIPE_VELOCITY) {

                        if (deltaX > 0) {
                            // Swipe right - go to previous image
                            Log.d(TAG, "Swipe right - previous image")
                            goToPreviousImage()
                            provideFeedback("previous")
                        } else {
                            // Swipe left - go to next image
                            Log.d(TAG, "Swipe left - next image")
                            goToNextImage()
                            provideFeedback("next")
                        }
                        return true
                    }
                } ?: return false

                return false
            }
        }

        gestureDetector = GestureDetectorCompat(this, gestureListener)

        // Set touch listener on the main container
        findViewById<View>(R.id.slideshowContainer).setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
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

    private fun provideFeedback(action: String) {
        // Subtle haptic feedback
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not provide haptic feedback", e)
        }

        // Hide gesture hints if they're showing
        if (gestureHints.visibility == View.VISIBLE) {
            hideGestureHints()
        }
    }

    private fun showGestureHintsIfNeeded() {
        val prefs = getSharedPreferences("gesture_hints", Context.MODE_PRIVATE)
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

    private fun hideGestureHints() {
        if (gestureHints.visibility == View.VISIBLE) {
            gestureHints.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction { gestureHints.visibility = View.GONE }
                .start()
        }
    }

    private fun togglePauseResume() {
        if (isTransitioning) {
            Log.d(TAG, "Ignoring pause/resume during transition")
            return
        }

        isPaused = !isPaused
        Log.d(TAG, "Slideshow ${if (isPaused) "paused" else "resumed"}")

        if (isPaused) {
            // Cancel scheduled next image
            slideshowRunnable?.let { handler.removeCallbacks(it) }
            // Cancel zoom animation
            currentZoomAnimator?.cancel()
            showPauseIndicator()
        } else {
            // Resume slideshow
            hidePauseIndicator()
            if (!isTransitioning) {
                scheduleNextImage()
                startSubtleZoomEffect()
            }
        }
    }

    private fun goToPreviousImage() {
        if (isTransitioning) return

        // Pause automatic slideshow temporarily
        val wasRunning = !isPaused
        if (wasRunning) {
            slideshowRunnable?.let { handler.removeCallbacks(it) }
        }

        // Move to previous index
        currentPhotoIndex = if (currentPhotoIndex == 0) {
            currentPhotoList.size - 1
        } else {
            currentPhotoIndex - 1
        }

        // Show the image immediately with fast transition
        showNextImage(fastTransition = true)

        // Resume if it was running
        if (wasRunning && !isPaused) {
            // Will be scheduled after transition completes
        }
    }

    private fun goToNextImage() {
        if (isTransitioning) return

        // Pause automatic slideshow temporarily
        val wasRunning = !isPaused
        if (wasRunning) {
            slideshowRunnable?.let { handler.removeCallbacks(it) }
        }

        // Move to next index
        moveToNextPhoto()

        // Show the image immediately with fast transition
        showNextImage(fastTransition = true)

        // Resume if it was running
        if (wasRunning && !isPaused) {
            // Will be scheduled after transition completes
        }
    }

    private fun showPauseIndicator() {
        val pauseIcon = pauseIndicator.findViewById<ImageView>(R.id.pauseIcon)
        pauseIcon.setImageResource(R.drawable.ic_pause)

        pauseIndicator.visibility = View.VISIBLE
        pauseIndicator.alpha = 0f
        pauseIndicator.animate()
            .alpha(0.9f)
            .setDuration(200)
            .start()
    }

    private fun hidePauseIndicator() {
        pauseIndicator.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                pauseIndicator.visibility = View.GONE
            }
            .start()
    }

    private fun showExitFeedback() {
        // Brief visual feedback before exiting
        val overlay = View(this).apply {
            setBackgroundColor(0x40FFFFFF)
        }

        val container = findViewById<View>(R.id.slideshowContainer) as android.widget.FrameLayout
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

    private fun loadSettings() {
        preferencesManager = PreferencesManager(this)
        settings = preferencesManager.loadSettings()

        allPhotoList = settings.photoInfoList.toMutableList()

        if (allPhotoList.isEmpty() && settings.selectedPhotos.isNotEmpty()) {
            allPhotoList = settings.selectedPhotos.map { uri ->
                PhotoInfo(
                    uri = uri,
                    orientation = ImageOrientation.SQUARE,
                    aspectRatio = 1.0f
                )
            }.toMutableList()
        }

        Log.d(TAG, "Loaded ${allPhotoList.size} total photos")
        filterPhotosByOrientation()

        if (currentPhotoList.isEmpty()) {
            Log.w(TAG, "No photos available for current orientation")
            return
        }

        sortPhotoList()
        Log.d(TAG, "Using ${currentPhotoList.size} photos for current orientation ($currentDeviceOrientation)")
    }

    private fun filterPhotosByOrientation() {
        currentPhotoList = if (settings.enableOrientationFiltering) {
            allPhotoList.filter { photoInfo ->
                OrientationUtils.shouldShowImage(
                    photoInfo.orientation,
                    currentDeviceOrientation,
                    settings.showSquareImagesInBothOrientations
                )
            }.toMutableList()
        } else {
            allPhotoList.toMutableList()
        }

        Log.d(TAG, "Filtered from ${allPhotoList.size} to ${currentPhotoList.size} photos for orientation $currentDeviceOrientation")
    }

    private fun sortPhotoList() {
        when (settings.orderType) {
            OrderType.RANDOM -> currentPhotoList.shuffle()
            OrderType.ALPHABETICAL -> currentPhotoList.sortBy { it.uri }
            OrderType.DATE_MODIFIED -> sortByDateModified()
            OrderType.DATE_CREATED -> sortByDateCreated()
        }
    }

    private fun sortByDateModified() {
        currentPhotoList.sortBy { photoInfo ->
            try {
                val uri = Uri.parse(photoInfo.uri)
                val cursor = contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_MODIFIED), null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        it.getLong(0)
                    } else 0L
                } ?: 0L
            } catch (e: Exception) {
                Log.w(TAG, "Error getting date modified for ${photoInfo.uri}", e)
                0L
            }
        }
    }

    private fun sortByDateCreated() {
        currentPhotoList.sortBy { photoInfo ->
            try {
                val uri = Uri.parse(photoInfo.uri)
                val cursor = contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_ADDED), null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        it.getLong(0)
                    } else 0L
                } ?: 0L
            } catch (e: Exception) {
                Log.w(TAG, "Error getting date created for ${photoInfo.uri}", e)
                0L
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "Configuration changed: orientation = ${newConfig.orientation}")

        getScreenDimensions()
        val oldPhotoCount = currentPhotoList.size
        filterPhotosByOrientation()

        Log.d(TAG, "Orientation change: ${oldPhotoCount} -> ${currentPhotoList.size} photos")

        if (currentPhotoList.isEmpty()) {
            Log.w(TAG, "No photos available for new orientation, finishing slideshow")
            finish()
            return
        }

        sortPhotoList()
        currentPhotoIndex = 0

        if (hasStartedSlideshow) {
            slideshowRunnable?.let { handler.removeCallbacks(it) }
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
        if (currentPhotoList.isNotEmpty() && isActivityActive && !hasStartedSlideshow) {
            hasStartedSlideshow = true
            Log.d(TAG, "Starting slideshow with ${currentPhotoList.size} photos for orientation $currentDeviceOrientation")
            showNextImage()
        }
    }

    private fun showNextImage(fastTransition: Boolean = false) {
        if (currentPhotoList.isEmpty() || !isActivityActive) return

        isTransitioning = true
        val currentPhotoInfo = currentPhotoList[currentPhotoIndex]
        val currentUri = Uri.parse(currentPhotoInfo.uri)

        Log.d(TAG, "Loading image ${currentPhotoIndex + 1}/${currentPhotoList.size}: ${currentPhotoInfo.orientation} - $currentUri")

        Glide.with(this)
            .load(currentUri)
            .error(R.drawable.ic_photo_library)
            .into(object : SimpleTarget<Drawable>() {
                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    if (!isActivityActive) return

                    Log.d(TAG, "Image loaded successfully - ${currentPhotoInfo.orientation}")

                    val fittedDrawable = applyCustomFitting(resource)
                    nextImageView.setImageDrawable(fittedDrawable)
                    setInitialScaleForNextImage()

                    if (settings.enableBlurredBackground) {
                        loadBlurredBackground(currentUri, fastTransition)
                    } else {
                        performTransition(fastTransition)
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    Log.e(TAG, "Failed to load image: $currentUri")
                    nextImageView.setImageResource(R.drawable.ic_photo_library)
                    setInitialScaleForNextImage()

                    if (settings.enableBlurredBackground) {
                        loadBlurredBackground(currentUri, fastTransition)
                    } else {
                        performTransition(fastTransition)
                    }
                }
            })
    }

    private fun applyCustomFitting(drawable: Drawable): Drawable {
        val imageWidth = drawable.intrinsicWidth
        val imageHeight = drawable.intrinsicHeight

        if (imageWidth <= 0 || imageHeight <= 0) {
            Log.w(TAG, "Invalid image dimensions, using original")
            return drawable
        }

        val imageAspectRatio = imageWidth.toFloat() / imageHeight.toFloat()
        val screenAspectRatio = screenWidth.toFloat() / screenHeight.toFloat()

        val scaleFactor = if (imageAspectRatio > screenAspectRatio) {
            screenHeight.toFloat() / imageHeight.toFloat()
        } else {
            screenWidth.toFloat() / imageWidth.toFloat()
        }

        val finalScale = max(scaleFactor, 1.0f)

        if (finalScale == 1.0f) {
            Log.d(TAG, "No scaling needed for image ${imageWidth}x${imageHeight}")
            return drawable
        }

        Log.d(TAG, "Scaling image ${imageWidth}x${imageHeight} by factor ${finalScale}")

        val bitmap = drawableToBitmap(drawable)
        val scaledWidth = (imageWidth * finalScale).toInt()
        val scaledHeight = (imageHeight * finalScale).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        return BitmapDrawable(resources, scaledBitmap)
    }

    private fun setInitialScaleForNextImage() {
        val zoomScale = 1f + (settings.zoomAmount / 100f)

        if (settings.zoomType == ZoomType.SINE_WAVE) {
            if (currentPhotoIndex % 2 == 1) {
                nextImageView.scaleX = zoomScale
                nextImageView.scaleY = zoomScale
                Log.d(TAG, "Set initial scale $zoomScale for image at index $currentPhotoIndex")
            } else {
                nextImageView.scaleX = 1.0f
                nextImageView.scaleY = 1.0f
                Log.d(TAG, "Set initial scale 1.0 for image at index $currentPhotoIndex")
            }
        } else {
            nextImageView.scaleX = 1.0f
            nextImageView.scaleY = 1.0f
        }
    }

    private fun loadBlurredBackground(uri: Uri, fastTransition: Boolean = false) {
        Glide.with(this)
            .load(uri)
            .transform(
                CenterCrop(),
                BlurTransformation(25, 3)
            )
            .error(R.drawable.ic_photo_library)
            .into(object : SimpleTarget<Drawable>() {
                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    if (!isActivityActive) return

                    val bitmap = drawableToBitmap(resource)
                    val darkenedBitmap = applyDarkenOverlay(bitmap)
                    nextBackgroundImageView.setImageBitmap(darkenedBitmap)
                    performTransition(fastTransition)
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    Log.w(TAG, "Failed to load blurred background, using solid background")
                    nextBackgroundImageView.setBackgroundColor(0xFF000000.toInt())
                    performTransition(fastTransition)
                }
            })
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }

    private fun applyDarkenOverlay(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        canvas.drawColor(0x40000000)
        return result
    }

    private fun performTransition(fastTransition: Boolean = false) {
        if (!isActivityActive) return

        val duration = if (fastTransition) 250L else 500L

        val animator = when (settings.transitionType) {
            TransitionType.FADE -> createFadeTransition(duration)
            TransitionType.SLIDE_LEFT -> createSlideTransition(-1f, 0f, duration)
            TransitionType.SLIDE_RIGHT -> createSlideTransition(1f, 0f, duration)
            TransitionType.SLIDE_UP -> createSlideTransition(0f, -1f, duration)
            TransitionType.SLIDE_DOWN -> createSlideTransition(0f, 1f, duration)
        }

        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                super.onAnimationEnd(animation)
                isTransitioning = false
                // Schedule next image only if not paused and not a manual fast transition
                if (!fastTransition && !isPaused) {
                    scheduleNextImage()
                }
            }
        })

        animator.start()
    }

    private fun createFadeTransition(duration: Long = 500L): AnimatorSet {
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
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (isActivityActive) {
                    swapImageViews()
                    if (settings.enableBlurredBackground) {
                        swapBackgroundViews()
                    }
                    if (!isPaused) {
                        startSubtleZoomEffect()
                    }
                }
            }
        })

        return animatorSet
    }

    private fun createSlideTransition(deltaX: Float, deltaY: Float, duration: Long = 800L): AnimatorSet {
        val screenWidthFloat = screenWidth.toFloat()
        val screenHeightFloat = screenHeight.toFloat()

        val imageSlideOut: ObjectAnimator
        val imageSlideIn: ObjectAnimator
        val backgroundSlideOut: ObjectAnimator
        val backgroundSlideIn: ObjectAnimator

        if (deltaX != 0f) {
            imageSlideOut = ObjectAnimator.ofFloat(
                currentImageView, "translationX", 0f, deltaX * screenWidthFloat
            )
            imageSlideIn = ObjectAnimator.ofFloat(
                nextImageView, "translationX", -deltaX * screenWidthFloat, 0f
            )
            backgroundSlideOut = ObjectAnimator.ofFloat(
                currentBackgroundImageView, "translationX", 0f, deltaX * screenWidthFloat
            )
            backgroundSlideIn = ObjectAnimator.ofFloat(
                nextBackgroundImageView, "translationX", -deltaX * screenWidthFloat, 0f
            )
        } else {
            imageSlideOut = ObjectAnimator.ofFloat(
                currentImageView, "translationY", 0f, deltaY * screenHeightFloat
            )
            imageSlideIn = ObjectAnimator.ofFloat(
                nextImageView, "translationY", -deltaY * screenHeightFloat, 0f
            )
            backgroundSlideOut = ObjectAnimator.ofFloat(
                currentBackgroundImageView, "translationY", 0f, deltaY * screenHeightFloat
            )
            backgroundSlideIn = ObjectAnimator.ofFloat(
                nextBackgroundImageView, "translationY", -deltaY * screenHeightFloat, 0f
            )
        }

        nextImageView.alpha = 1f
        nextBackgroundImageView.alpha = 1f

        imageSlideOut.duration = duration
        imageSlideIn.duration = duration
        backgroundSlideOut.duration = duration
        backgroundSlideIn.duration = duration

        val animatorSet = AnimatorSet()

        if (settings.enableBlurredBackground) {
            animatorSet.playTogether(imageSlideOut, imageSlideIn, backgroundSlideOut, backgroundSlideIn)
        } else {
            animatorSet.playTogether(imageSlideOut, imageSlideIn)
        }

        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (isActivityActive) {
                    swapImageViews()
                    if (settings.enableBlurredBackground) {
                        swapBackgroundViews()
                    }
                    if (!isPaused) {
                        startSubtleZoomEffect()
                    }
                }
            }
        })

        return animatorSet
    }

    private fun startSubtleZoomEffect() {
        if (!isActivityActive || isPaused) return

        currentZoomAnimator?.cancel()

        val zoomScale = 1f + (settings.zoomAmount / 100f)

        val (startScale, endScale) = when (settings.zoomType) {
            ZoomType.SAWTOOTH -> Pair(1f, zoomScale)
            ZoomType.SINE_WAVE -> {
                if (currentPhotoIndex % 2 == 0) {
                    Pair(1f, zoomScale)
                } else {
                    Pair(zoomScale, 1f)
                }
            }
        }

        Log.d(TAG, "Starting zoom effect: ${startScale} -> ${endScale} for photo index $currentPhotoIndex (zoom amount: ${settings.zoomAmount}%)")

        val scaleXAnimator = ObjectAnimator.ofFloat(currentImageView, "scaleX", startScale, endScale)
        val scaleYAnimator = ObjectAnimator.ofFloat(currentImageView, "scaleY", startScale, endScale)

        val duration = settings.slideDuration.toLong()
        scaleXAnimator.duration = duration
        scaleYAnimator.duration = duration

        val interpolator = AccelerateDecelerateInterpolator()
        scaleXAnimator.interpolator = interpolator
        scaleYAnimator.interpolator = interpolator

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleXAnimator, scaleYAnimator)
        animatorSet.start()

        currentZoomAnimator = scaleXAnimator
    }

    private fun swapImageViews() {
        val temp = currentImageView
        currentImageView = nextImageView
        nextImageView = temp

        nextImageView.alpha = 0f
        nextImageView.scaleX = 1f
        nextImageView.scaleY = 1f
        nextImageView.translationX = 0f
        nextImageView.translationY = 0f
    }

    private fun swapBackgroundViews() {
        val temp = currentBackgroundImageView
        currentBackgroundImageView = nextBackgroundImageView
        nextBackgroundImageView = temp

        nextBackgroundImageView.alpha = 0f
        nextBackgroundImageView.scaleX = 1f
        nextBackgroundImageView.scaleY = 1f
        nextBackgroundImageView.translationX = 0f
        nextBackgroundImageView.translationY = 0f
    }

    private fun moveToNextPhoto() {
        currentPhotoIndex = (currentPhotoIndex + 1) % currentPhotoList.size
    }

    private fun scheduleNextImage() {
        if (!isActivityActive || isPaused) return

        slideshowRunnable?.let { handler.removeCallbacks(it) }

        slideshowRunnable = Runnable {
            if (isActivityActive && !isPaused) {
                moveToNextPhoto()
                showNextImage()
            }
        }

        handler.postDelayed(slideshowRunnable!!, settings.slideDuration.toLong())
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")

        isActivityActive = false
        shouldIgnoreScreenOn = false
        slideshowRunnable?.let { handler.removeCallbacks(it) }
        currentZoomAnimator?.cancel()

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

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called")
    }
}