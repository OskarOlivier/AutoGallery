// SlideshowActivity.kt
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
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import android.widget.ImageView
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlin.math.max
import kotlin.random.Random

class SlideshowActivity : AppCompatActivity() {

    private lateinit var currentImageView: ImageView
    private lateinit var nextImageView: ImageView
    private lateinit var currentBackgroundImageView: ImageView
    private lateinit var nextBackgroundImageView: ImageView

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var settings: GallerySettings
    private val handler = Handler(Looper.getMainLooper())
    private var currentPhotoIndex = 0
    private var currentPhotoList = mutableListOf<PhotoInfo>() // Filtered by orientation
    private var allPhotoList = mutableListOf<PhotoInfo>() // All photos
    private var currentDeviceOrientation: ImageOrientation = ImageOrientation.LANDSCAPE
    private var slideshowRunnable: Runnable? = null
    private var currentZoomAnimator: ObjectAnimator? = null
    private var isActivityActive = false
    private var hasStartedSlideshow = false
    private var startTime = 0L
    private var shouldIgnoreScreenOn = true
    private var isReceiverRegistered = false

    // Screen dimension variables for custom image fitting
    private var screenWidth = 0
    private var screenHeight = 0

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

                    // Ignore screen on events for the first 5 seconds after activity start
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
            // Get screen dimensions first
            getScreenDimensions()

            setContentView(R.layout.activity_slideshow)

            // Setup modern fullscreen AFTER setContentView
            setupFullscreen()
            setupUIVisibilityListener()

            initViews()
            loadSettings()

            if (currentPhotoList.isEmpty()) {
                Log.e(TAG, "No photos available for current orientation, finishing activity")
                finish()
                return
            }

            isActivityActive = true

            // Register receiver immediately but with logic to ignore initial screen on
            registerScreenStateReceiver()

            // Start slideshow after a short delay
            handler.postDelayed({
                if (!isFinishing && isActivityActive) {
                    Log.d(TAG, "Starting slideshow after delay")
                    startSlideshow()
                }
            }, 500) // Shorter delay

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
        // Set window flags first
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

        // Handle display cutouts (notches) for Android 9+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Modern fullscreen approach for Android 11+
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

        // Initially hide the next image views
        nextImageView.alpha = 0f
        nextBackgroundImageView.alpha = 0f
    }

    private fun loadSettings() {
        preferencesManager = PreferencesManager(this)
        settings = preferencesManager.loadSettings()

        // Load all photos
        allPhotoList = settings.photoInfoList.toMutableList()

        // If no photo info available (backward compatibility), create basic info
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

        // Filter photos by orientation
        filterPhotosByOrientation()

        if (currentPhotoList.isEmpty()) {
            Log.w(TAG, "No photos available for current orientation")
            return
        }

        // Sort photos based on order type
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
            // If orientation filtering is disabled, show all photos
            allPhotoList.toMutableList()
        }

        Log.d(TAG, "Filtered from ${allPhotoList.size} to ${currentPhotoList.size} photos for orientation $currentDeviceOrientation")

        // Log breakdown for debugging
        if (settings.enableOrientationFiltering) {
            val landscapeCount = allPhotoList.count { it.orientation == ImageOrientation.LANDSCAPE }
            val portraitCount = allPhotoList.count { it.orientation == ImageOrientation.PORTRAIT }
            val squareCount = allPhotoList.count { it.orientation == ImageOrientation.SQUARE }
            Log.d(TAG, "Available photos: $landscapeCount landscape, $portraitCount portrait, $squareCount square")
        }
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

        // Update screen dimensions and device orientation
        getScreenDimensions()

        // Re-filter photos for new orientation
        val oldPhotoCount = currentPhotoList.size
        filterPhotosByOrientation()

        Log.d(TAG, "Orientation change: ${oldPhotoCount} -> ${currentPhotoList.size} photos")

        if (currentPhotoList.isEmpty()) {
            Log.w(TAG, "No photos available for new orientation, finishing slideshow")
            finish()
            return
        }

        // Re-sort the filtered list
        sortPhotoList()

        // Reset to first photo in the new orientation's list
        currentPhotoIndex = 0

        // If slideshow is running, restart with new photo list
        if (hasStartedSlideshow) {
            // Cancel current slideshow timing
            slideshowRunnable?.let { handler.removeCallbacks(it) }

            // Start new photo immediately
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

    private fun showNextImage() {
        if (currentPhotoList.isEmpty() || !isActivityActive) return

        val currentPhotoInfo = currentPhotoList[currentPhotoIndex]
        val currentUri = Uri.parse(currentPhotoInfo.uri)

        Log.d(TAG, "Loading image ${currentPhotoIndex + 1}/${currentPhotoList.size}: ${currentPhotoInfo.orientation} - $currentUri")

        // Load image with custom fitting logic
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

                    // Apply custom fitting logic
                    val fittedDrawable = applyCustomFitting(resource)
                    nextImageView.setImageDrawable(fittedDrawable)

                    // Set correct initial scale for SINE_WAVE pattern BEFORE transition
                    setInitialScaleForNextImage()

                    // Load blurred background if enabled
                    if (settings.enableBlurredBackground) {
                        loadBlurredBackground(currentUri)
                    } else {
                        performTransition()
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    Log.e(TAG, "Failed to load image: $currentUri")
                    nextImageView.setImageResource(R.drawable.ic_photo_library)
                    setInitialScaleForNextImage()

                    if (settings.enableBlurredBackground) {
                        loadBlurredBackground(currentUri)
                    } else {
                        performTransition()
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

        // Calculate aspect ratios
        val imageAspectRatio = imageWidth.toFloat() / imageHeight.toFloat()
        val screenAspectRatio = screenWidth.toFloat() / screenHeight.toFloat()

        // Calculate scale factor to ensure image touches at least 2 edges
        val scaleFactor = if (imageAspectRatio > screenAspectRatio) {
            // Image is wider than screen - scale to height
            screenHeight.toFloat() / imageHeight.toFloat()
        } else {
            // Image is taller than screen - scale to width
            screenWidth.toFloat() / imageWidth.toFloat()
        }

        // Only scale up if the image is smaller than the screen
        val finalScale = max(scaleFactor, 1.0f)

        if (finalScale == 1.0f) {
            Log.d(TAG, "No scaling needed for image ${imageWidth}x${imageHeight}")
            return drawable
        }

        Log.d(TAG, "Scaling image ${imageWidth}x${imageHeight} by factor ${finalScale}")

        // Create scaled bitmap
        val bitmap = drawableToBitmap(drawable)
        val scaledWidth = (imageWidth * finalScale).toInt()
        val scaledHeight = (imageHeight * finalScale).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        return BitmapDrawable(resources, scaledBitmap)
    }

    private fun setInitialScaleForNextImage() {
        // Calculate zoom scale from zoom amount setting (1-5 becomes 1.01-1.05)
        val zoomScale = 1f + (settings.zoomAmount / 100f)

        // For SINE_WAVE pattern, set the correct starting scale for the next image
        if (settings.zoomType == ZoomType.SINE_WAVE) {
            if (currentPhotoIndex % 2 == 1) {
                // Current image being loaded is odd index, should start zoomed
                nextImageView.scaleX = zoomScale
                nextImageView.scaleY = zoomScale
                Log.d(TAG, "Set initial scale $zoomScale for image at index $currentPhotoIndex")
            } else {
                // Current image being loaded is even index, should start normal
                nextImageView.scaleX = 1.0f
                nextImageView.scaleY = 1.0f
                Log.d(TAG, "Set initial scale 1.0 for image at index $currentPhotoIndex")
            }
        } else {
            // For SAWTOOTH, always start at normal scale
            nextImageView.scaleX = 1.0f
            nextImageView.scaleY = 1.0f
        }
    }

    private fun loadBlurredBackground(uri: Uri) {
        // Use Glide with high-quality BlurTransformation
        Glide.with(this)
            .load(uri)
            .transform(
                CenterCrop(),
                BlurTransformation(25, 3) // radius: 25, sampling: 3 for high quality
            )
            .error(R.drawable.ic_photo_library)
            .into(object : SimpleTarget<Drawable>() {
                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    if (!isActivityActive) return

                    // Apply additional darkening overlay for better contrast
                    val bitmap = drawableToBitmap(resource)
                    val darkenedBitmap = applyDarkenOverlay(bitmap)
                    nextBackgroundImageView.setImageBitmap(darkenedBitmap)

                    // Now perform the transition with both image and background ready
                    performTransition()
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    Log.w(TAG, "Failed to load blurred background, using solid background")
                    // Set a solid dark background as fallback
                    nextBackgroundImageView.setBackgroundColor(0xFF000000.toInt())
                    // Continue with transition
                    performTransition()
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

        // Apply color overlay for better visual effect
        canvas.drawColor(0x40000000) // Semi-transparent black overlay

        return result
    }

    private fun performTransition() {
        if (!isActivityActive) return

        val animator = when (settings.transitionType) {
            TransitionType.FADE -> createFadeTransition()
            TransitionType.SLIDE_LEFT -> createSlideTransition(-1f, 0f)
            TransitionType.SLIDE_RIGHT -> createSlideTransition(1f, 0f)
            TransitionType.SLIDE_UP -> createSlideTransition(0f, -1f)
            TransitionType.SLIDE_DOWN -> createSlideTransition(0f, 1f)
        }

        animator.start()

        // Schedule next image
        scheduleNextImage()
    }

    private fun createFadeTransition(): AnimatorSet {
        val imageFadeOut = ObjectAnimator.ofFloat(currentImageView, "alpha", 1f, 0f)
        val imageFadeIn = ObjectAnimator.ofFloat(nextImageView, "alpha", 0f, 1f)

        val backgroundFadeOut = ObjectAnimator.ofFloat(currentBackgroundImageView, "alpha", 1f, 0f)
        val backgroundFadeIn = ObjectAnimator.ofFloat(nextBackgroundImageView, "alpha", 0f, 1f)

        imageFadeOut.duration = 500
        imageFadeIn.duration = 500
        backgroundFadeOut.duration = 500
        backgroundFadeIn.duration = 500

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
                    startSubtleZoomEffect()
                }
            }
        })

        return animatorSet
    }

    private fun createSlideTransition(deltaX: Float, deltaY: Float): AnimatorSet {
        val screenWidthFloat = screenWidth.toFloat()
        val screenHeightFloat = screenHeight.toFloat()

        val imageSlideOut: ObjectAnimator
        val imageSlideIn: ObjectAnimator
        val backgroundSlideOut: ObjectAnimator
        val backgroundSlideIn: ObjectAnimator

        if (deltaX != 0f) {
            // Horizontal slide
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
            // Vertical slide
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

        imageSlideOut.duration = 800
        imageSlideIn.duration = 800
        backgroundSlideOut.duration = 800
        backgroundSlideIn.duration = 800

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
                    startSubtleZoomEffect()
                }
            }
        })

        return animatorSet
    }

    private fun startSubtleZoomEffect() {
        if (!isActivityActive) return

        // Cancel any existing zoom animation
        currentZoomAnimator?.cancel()

        // Calculate zoom scale from zoom amount setting (1-5 becomes 1.01-1.05)
        val zoomScale = 1f + (settings.zoomAmount / 100f)

        // Determine zoom direction based on zoom type and current photo index
        val (startScale, endScale) = when (settings.zoomType) {
            ZoomType.SAWTOOTH -> {
                // Always zoom in from 1.0 to zoomScale
                Pair(1f, zoomScale)
            }
            ZoomType.SINE_WAVE -> {
                // Alternate zoom direction based on photo index
                if (currentPhotoIndex % 2 == 0) {
                    // Even index: zoom in
                    Pair(1f, zoomScale)
                } else {
                    // Odd index: zoom out (start from zoomed and go to normal)
                    Pair(zoomScale, 1f)
                }
            }
        }

        Log.d(TAG, "Starting zoom effect: ${startScale} -> ${endScale} for photo index $currentPhotoIndex (zoom amount: ${settings.zoomAmount}%)")

        // Create zoom animation from current scale to target scale
        val scaleXAnimator = ObjectAnimator.ofFloat(currentImageView, "scaleX", startScale, endScale)
        val scaleYAnimator = ObjectAnimator.ofFloat(currentImageView, "scaleY", startScale, endScale)

        // Set duration to match slide duration
        val duration = settings.slideDuration.toLong()
        scaleXAnimator.duration = duration
        scaleYAnimator.duration = duration

        // Use AccelerateDecelerateInterpolator for ease in/out effect
        val interpolator = AccelerateDecelerateInterpolator()
        scaleXAnimator.interpolator = interpolator
        scaleYAnimator.interpolator = interpolator

        // Create animator set to play both scale animations together
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleXAnimator, scaleYAnimator)
        animatorSet.start()

        // Store reference to the animator set for potential cancellation
        currentZoomAnimator = scaleXAnimator
    }

    private fun swapImageViews() {
        val temp = currentImageView
        currentImageView = nextImageView
        nextImageView = temp

        // Reset the next view for the next cycle
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

        // Reset the next background view for the next cycle
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
        if (!isActivityActive) return

        slideshowRunnable?.let { handler.removeCallbacks(it) }

        slideshowRunnable = Runnable {
            if (isActivityActive) {
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