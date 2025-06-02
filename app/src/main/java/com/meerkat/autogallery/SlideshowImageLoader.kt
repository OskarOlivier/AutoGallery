// SlideshowImageLoader.kt - Updated with 10px edge buffer for fade effect
package com.meerkat.autogallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlin.math.max

class SlideshowImageLoader(private val context: Context) {

    private var screenWidth = 0
    private var screenHeight = 0

    companion object {
        private const val TAG = "SlideshowImageLoader"
    }

    init {
        val metrics = context.resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    fun loadImage(
        photoInfo: PhotoInfo,
        targetView: ImageView,
        backgroundView: ImageView? = null,
        onImageReady: (Drawable) -> Unit,
        onError: () -> Unit
    ) {
        val currentUri = Uri.parse(photoInfo.uri)
        Log.d(TAG, "Loading image: ${photoInfo.orientation} - $currentUri")

        Glide.with(context)
            .load(currentUri)
            .error(R.drawable.ic_photo_library)
            .into(object : SimpleTarget<Drawable>() {
                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    Log.d(TAG, "Image loaded successfully - ${photoInfo.orientation}")

                    val fittedDrawable = applyCustomFitting(resource)
                    targetView.setImageDrawable(fittedDrawable)

                    if (backgroundView != null) {
                        loadBlurredBackground(currentUri, backgroundView) {
                            onImageReady(fittedDrawable)
                        }
                    } else {
                        onImageReady(fittedDrawable)
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    Log.e(TAG, "Failed to load image: $currentUri")
                    targetView.setImageResource(R.drawable.ic_photo_library)

                    if (backgroundView != null) {
                        loadBlurredBackground(currentUri, backgroundView) {
                            onError()
                        }
                    } else {
                        onError()
                    }
                }
            })
    }

    private fun loadBlurredBackground(
        uri: Uri,
        backgroundView: ImageView,
        onComplete: () -> Unit
    ) {
        Glide.with(context)
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
                    val bitmap = drawableToBitmap(resource)
                    val darkenedBitmap = applyDarkenOverlay(bitmap)
                    backgroundView.setImageBitmap(darkenedBitmap)
                    onComplete()
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    Log.w(TAG, "Failed to load blurred background, using solid background")
                    backgroundView.setBackgroundColor(0xFF000000.toInt())
                    onComplete()
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

        // Buffer to ensure image extends beyond screen edges for future fade effect
        val edgeBuffer = 20 // 10px on each edge that touches the screen border

        val scaleFactor = if (imageAspectRatio > screenAspectRatio) {
            // Image is wider relative to height - will touch top/bottom edges
            // Add buffer to height to ensure image extends beyond top/bottom by 10px each
            (screenHeight + edgeBuffer).toFloat() / imageHeight.toFloat()
        } else {
            // Image is taller relative to width - will touch left/right edges
            // Add buffer to width to ensure image extends beyond left/right by 10px each
            (screenWidth + edgeBuffer).toFloat() / imageWidth.toFloat()
        }

        val finalScale = max(scaleFactor, 1.0f)

        if (finalScale == 1.0f) {
            Log.d(TAG, "No scaling needed for image ${imageWidth}x${imageHeight}")
            return drawable
        }

        val scaledWidth = (imageWidth * finalScale).toInt()
        val scaledHeight = (imageHeight * finalScale).toInt()

        Log.d(TAG, "Scaling image ${imageWidth}x${imageHeight} -> ${scaledWidth}x${scaledHeight} " +
                "(factor: ${String.format("%.2f", finalScale)}, includes ${edgeBuffer}px edge buffer)")

        val bitmap = drawableToBitmap(drawable)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        return BitmapDrawable(context.resources, scaledBitmap)
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
}