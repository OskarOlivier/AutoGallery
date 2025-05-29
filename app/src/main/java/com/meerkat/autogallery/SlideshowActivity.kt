// SlideshowActivity.kt
package com.meerkat.autogallery

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import android.widget.ImageView
import jp.wasabeef.glide.transformations.BlurTransformation
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
    private var photoList = mutableListOf<String>()
    private var slideshowRunnable: Runnable? = null
    private var currentZoomAnimator: ObjectAnimator? = null
    private var isActivityActive = false
    private var hasStartedSlideshow = false
    private var startTime = 0L
    private var shouldIgnoreScreenOn = true

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
                    // This prevents the activity from finishing itself when it turns the screen on
                    if (shouldIgnoreScreenOn && timeSinceStart < 5000) {
                        Log.d(TAG, "Ignoring screen on event - activity just started")
                        // After 10 seconds, stop ignoring screen on events
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
            // Setup fullscreen and keep screen on
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

            setContentView(R.layout.activity_slideshow)

            // Hide navigation bar
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )

            initViews()
            loadSettings()

            if (photoList.isEmpty()) {
                Log.e(TAG, "No photos available, finishing activity")
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
        photoList = settings.selectedPhotos.toMutableList()

        Log.d(TAG, "Loaded ${photoList.size} photos")

        if (photoList.isEmpty()) {
            return
        }

        // Sort photos based on order type
        sortPhotoList()
    }

    private fun sortPhotoList() {
        when (settings.orderType) {
            OrderType.RANDOM -> photoList.shuffle()
            OrderType.ALPHABETICAL -> photoList.sort()
            OrderType.DATE_MODIFIED -> sortByDateModified()
            OrderType.DATE_CREATED -> sortByDateCreated()
        }
    }

    private fun sortByDateModified() {
        photoList.sortBy { uriString ->
            try {
                val uri = Uri.parse(uriString)
                val cursor = contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_MODIFIED), null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        it.getLong(0)
                    } else 0L
                } ?: 0L
            } catch (e: Exception) {
                Log.w(TAG, "Error getting date modified for $uriString", e)
                0L
            }
        }
    }

    private fun sortByDateCreated() {
        photoList.sortBy { uriString ->
            try {
                val uri = Uri.parse(uriString)
                val cursor = contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_ADDED), null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        it.getLong(0)
                    } else 0L
                } ?: 0L
            } catch (e: Exception) {
                Log.w(TAG, "Error getting date created for $uriString", e)
                0L
            }
        }
    }

    private fun registerScreenStateReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenStateReceiver, filter)
            Log.d(TAG, "Screen state receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering screen state receiver", e)
        }
    }

    private fun startSlideshow() {
        if (photoList.isNotEmpty() && isActivityActive && !hasStartedSlideshow) {
            hasStartedSlideshow = true
            Log.d(TAG, "Starting slideshow with ${photoList.size} photos")
            showNextImage()
        }
    }

    private fun showNextImage() {
        if (photoList.isEmpty() || !isActivityActive) return

        val currentUri = Uri.parse(photoList[currentPhotoIndex])
        Log.d(TAG, "Loading image ${currentPhotoIndex + 1}/${photoList.size}: $currentUri")

        // Load image into next ImageView with error handling
        Glide.with(this)
            .load(currentUri)
            .centerInside()
            .error(R.drawable.ic_photo_library) // Show placeholder on error
            .into(object : SimpleTarget<android.graphics.drawable.Drawable>() {
                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable,
                    transition: Transition<in android.graphics.drawable.Drawable>?
                ) {
                    if (!isActivityActive) return

                    Log.d(TAG, "Image loaded successfully")
                    nextImageView.setImageDrawable(resource)

                    // Load blurred background if enabled
                    if (settings.enableBlurredBackground) {
                        loadBlurredBackground(currentUri)
                    } else {
                        performTransition()
                    }
                }

                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    Log.e(TAG, "Failed to load image: $currentUri")
                    // Set a placeholder and continue
                    nextImageView.setImageResource(R.drawable.ic_photo_library)

                    // Still try to load background or proceed with transition
                    if (settings.enableBlurredBackground) {
                        loadBlurredBackground(currentUri)
                    } else {
                        performTransition()
                    }
                }
            })
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
            .into(object : SimpleTarget<android.graphics.drawable.Drawable>() {
                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable,
                    transition: Transition<in android.graphics.drawable.Drawable>?
                ) {
                    if (!isActivityActive) return

                    // Apply additional darkening overlay for better contrast
                    val bitmap = drawableToBitmap(resource)
                    val darkenedBitmap = applyDarkenOverlay(bitmap)
                    nextBackgroundImageView.setImageBitmap(darkenedBitmap)

                    // Now perform the transition with both image and background ready
                    performTransition()
                }

                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    Log.w(TAG, "Failed to load blurred background, using solid background")
                    // Set a solid dark background as fallback
                    nextBackgroundImageView.setBackgroundColor(0xFF000000.toInt())
                    // Continue with transition
                    performTransition()
                }
            })
    }

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap {
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
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()

        val imageSlideOut: ObjectAnimator
        val imageSlideIn: ObjectAnimator
        val backgroundSlideOut: ObjectAnimator
        val backgroundSlideIn: ObjectAnimator

        if (deltaX != 0f) {
            // Horizontal slide
            imageSlideOut = ObjectAnimator.ofFloat(
                currentImageView, "translationX", 0f, deltaX * screenWidth
            )
            imageSlideIn = ObjectAnimator.ofFloat(
                nextImageView, "translationX", -deltaX * screenWidth, 0f
            )
            backgroundSlideOut = ObjectAnimator.ofFloat(
                currentBackgroundImageView, "translationX", 0f, deltaX * screenWidth
            )
            backgroundSlideIn = ObjectAnimator.ofFloat(
                nextBackgroundImageView, "translationX", -deltaX * screenWidth, 0f
            )
        } else {
            // Vertical slide
            imageSlideOut = ObjectAnimator.ofFloat(
                currentImageView, "translationY", 0f, deltaY * screenHeight
            )
            imageSlideIn = ObjectAnimator.ofFloat(
                nextImageView, "translationY", -deltaY * screenHeight, 0f
            )
            backgroundSlideOut = ObjectAnimator.ofFloat(
                currentBackgroundImageView, "translationY", 0f, deltaY * screenHeight
            )
            backgroundSlideIn = ObjectAnimator.ofFloat(
                nextBackgroundImageView, "translationY", -deltaY * screenHeight, 0f
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

        // Create zoom animation from current scale to 1.03 (3% zoom)
        val scaleXAnimator = ObjectAnimator.ofFloat(currentImageView, "scaleX", 1f, 1.03f)
        val scaleYAnimator = ObjectAnimator.ofFloat(currentImageView, "scaleY", 1f, 1.03f)

        // Set duration to match slide duration
        val duration = settings.slideDuration.toLong()
        scaleXAnimator.duration = duration
        scaleYAnimator.duration = duration

        // Use AccelerateDecelerateInterpolator for ease in/out effect
        val interpolator = AccelerateDecelerateInterpolator()
        scaleXAnimator.interpolator = interpolator
        scaleYAnimator.interpolator = interpolator

        // Create animator set to play both scale animations together
        currentZoomAnimator = ObjectAnimator()
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
        currentPhotoIndex = (currentPhotoIndex + 1) % photoList.size
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

        try {
            unregisterReceiver(screenStateReceiver)
            Log.d(TAG, "Screen state receiver unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receiver (may not have been registered)", e)
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