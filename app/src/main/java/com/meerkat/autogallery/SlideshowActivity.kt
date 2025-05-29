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
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import android.widget.ImageView
import java.io.File
import kotlin.random.Random

class SlideshowActivity : AppCompatActivity() {

    private lateinit var currentImageView: ImageView
    private lateinit var nextImageView: ImageView
    private lateinit var backgroundImageView: ImageView

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var settings: GallerySettings
    private val handler = Handler(Looper.getMainLooper())
    private var currentPhotoIndex = 0
    private var photoList = mutableListOf<String>()
    private var slideshowRunnable: Runnable? = null

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        registerScreenStateReceiver()
        startSlideshow()
    }

    private fun initViews() {
        currentImageView = findViewById(R.id.currentImageView)
        nextImageView = findViewById(R.id.nextImageView)
        backgroundImageView = findViewById(R.id.backgroundImageView)

        // Initially hide the next image view
        nextImageView.alpha = 0f
    }

    private fun loadSettings() {
        preferencesManager = PreferencesManager(this)
        settings = preferencesManager.loadSettings()
        photoList = settings.selectedPhotos.toMutableList()

        if (photoList.isEmpty()) {
            finish()
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
                0L
            }
        }
    }

    private fun registerScreenStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    private fun startSlideshow() {
        if (photoList.isNotEmpty()) {
            showNextImage()
        }
    }

    private fun showNextImage() {
        if (photoList.isEmpty()) return

        val currentUri = Uri.parse(photoList[currentPhotoIndex])

        // Load image into next ImageView
        Glide.with(this)
            .load(currentUri)
            .centerInside()
            .into(object : SimpleTarget<android.graphics.drawable.Drawable>() {
                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable,
                    transition: Transition<in android.graphics.drawable.Drawable>?
                ) {
                    nextImageView.setImageDrawable(resource)

                    // Load blurred background if enabled
                    if (settings.enableBlurredBackground) {
                        loadBlurredBackground(currentUri)
                    }

                    // Perform transition animation
                    performTransition()
                }
            })
    }

    private fun loadBlurredBackground(uri: Uri) {
        Glide.with(this)
            .asBitmap()
            .load(uri)
            .transform(CenterCrop())
            .into(object : SimpleTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val blurredBitmap = createSimpleBlur(resource)
                    backgroundImageView.setImageBitmap(blurredBitmap)
                }
            })
    }

    private fun createSimpleBlur(originalBitmap: Bitmap): Bitmap {
        // Create a simple blur effect by scaling down and up
        val width = originalBitmap.width
        val height = originalBitmap.height

        // Scale down to 1/8 size
        val smallBitmap = Bitmap.createScaledBitmap(originalBitmap, width / 8, height / 8, false)

        // Scale back up with filtering for blur effect
        val blurredBitmap = Bitmap.createScaledBitmap(smallBitmap, width, height, true)

        // Create a new bitmap for the final result
        val resultBitmap = blurredBitmap.copy(Bitmap.Config.ARGB_8888, true)

        // Apply color overlay for better visual effect
        val canvas = Canvas(resultBitmap)
        canvas.drawColor(0x40000000) // Semi-transparent black overlay

        return resultBitmap
    }

    private fun performTransition() {
        val animator = when (settings.transitionType) {
            TransitionType.FADE -> createFadeTransition()
            TransitionType.SLIDE_LEFT -> createSlideTransition(-1f, 0f)
            TransitionType.SLIDE_RIGHT -> createSlideTransition(1f, 0f)
            TransitionType.SLIDE_UP -> createSlideTransition(0f, -1f)
            TransitionType.SLIDE_DOWN -> createSlideTransition(0f, 1f)
            TransitionType.ZOOM_IN -> createZoomTransition(0.5f, 1f)
            TransitionType.ZOOM_OUT -> createZoomTransition(1.5f, 1f)
        }

        animator.start()

        // Schedule next image
        scheduleNextImage()
    }

    private fun createFadeTransition(): AnimatorSet {
        val fadeOut = ObjectAnimator.ofFloat(currentImageView, "alpha", 1f, 0f)
        val fadeIn = ObjectAnimator.ofFloat(nextImageView, "alpha", 0f, 1f)

        fadeOut.duration = 500
        fadeIn.duration = 500

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(fadeOut, fadeIn)

        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                swapImageViews()
            }
        })

        return animatorSet
    }

    private fun createSlideTransition(deltaX: Float, deltaY: Float): AnimatorSet {
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()

        val slideOut: ObjectAnimator
        val slideIn: ObjectAnimator

        if (deltaX != 0f) {
            // Horizontal slide
            slideOut = ObjectAnimator.ofFloat(
                currentImageView, "translationX", 0f, deltaX * screenWidth
            )
            slideIn = ObjectAnimator.ofFloat(
                nextImageView, "translationX", -deltaX * screenWidth, 0f
            )
        } else {
            // Vertical slide
            slideOut = ObjectAnimator.ofFloat(
                currentImageView, "translationY", 0f, deltaY * screenHeight
            )
            slideIn = ObjectAnimator.ofFloat(
                nextImageView, "translationY", -deltaY * screenHeight, 0f
            )
        }

        nextImageView.alpha = 1f

        slideOut.duration = 800
        slideIn.duration = 800

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(slideOut, slideIn)

        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                swapImageViews()
                currentImageView.translationX = 0f
                currentImageView.translationY = 0f
                nextImageView.translationX = 0f
                nextImageView.translationY = 0f
            }
        })

        return animatorSet
    }

    private fun createZoomTransition(startScale: Float, endScale: Float): AnimatorSet {
        val scaleOut = ObjectAnimator.ofFloat(currentImageView, "scaleX", 1f, endScale)
        val scaleOutY = ObjectAnimator.ofFloat(currentImageView, "scaleY", 1f, endScale)
        val fadeOut = ObjectAnimator.ofFloat(currentImageView, "alpha", 1f, 0f)

        val scaleIn = ObjectAnimator.ofFloat(nextImageView, "scaleX", startScale, 1f)
        val scaleInY = ObjectAnimator.ofFloat(nextImageView, "scaleY", startScale, 1f)
        val fadeIn = ObjectAnimator.ofFloat(nextImageView, "alpha", 0f, 1f)

        val duration = 800L
        scaleOut.duration = duration
        scaleOutY.duration = duration
        fadeOut.duration = duration
        scaleIn.duration = duration
        scaleInY.duration = duration
        fadeIn.duration = duration

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleOut, scaleOutY, fadeOut, scaleIn, scaleInY, fadeIn)

        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                swapImageViews()
                currentImageView.scaleX = 1f
                currentImageView.scaleY = 1f
                nextImageView.scaleX = 1f
                nextImageView.scaleY = 1f
            }
        })

        return animatorSet
    }

    private fun swapImageViews() {
        val temp = currentImageView
        currentImageView = nextImageView
        nextImageView = temp

        nextImageView.alpha = 0f
    }

    private fun scheduleNextImage() {
        slideshowRunnable?.let { handler.removeCallbacks(it) }

        slideshowRunnable = Runnable {
            currentPhotoIndex = (currentPhotoIndex + 1) % photoList.size
            showNextImage()
        }

        handler.postDelayed(slideshowRunnable!!, settings.slideDuration.toLong())
    }

    override fun onDestroy() {
        super.onDestroy()
        slideshowRunnable?.let { handler.removeCallbacks(it) }
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}