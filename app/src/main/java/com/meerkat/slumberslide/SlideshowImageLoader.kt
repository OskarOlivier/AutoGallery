// SlideshowImageLoader.kt - Image loading and background processing
package com.meerkat.slumberslide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
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

        Glide.with(context)
            .load(currentUri)
            .error(R.drawable.ic_photo_library)
            .into(object : SimpleTarget<Drawable>() {
                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?
                ) {
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
                    val enlargedBitmap = enlargeBackground(bitmap, 1.05f)
                    val darkenedBitmap = applyDarkenOverlay(enlargedBitmap)
                    backgroundView.setImageBitmap(darkenedBitmap)
                    onComplete()
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    backgroundView.setBackgroundColor(0xFF000000.toInt())
                    onComplete()
                }
            })
    }

    private fun applyCustomFitting(drawable: Drawable): Drawable {
        val imageWidth = drawable.intrinsicWidth
        val imageHeight = drawable.intrinsicHeight

        if (imageWidth <= 0 || imageHeight <= 0) {
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
            return drawable
        }

        val scaledWidth = (imageWidth * finalScale).toInt()
        val scaledHeight = (imageHeight * finalScale).toInt()

        val bitmap = drawableToBitmap(drawable)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        return BitmapDrawable(context.resources, scaledBitmap)
    }

    private fun enlargeBackground(bitmap: Bitmap, scaleFactor: Float): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        val newWidth = (originalWidth * scaleFactor).toInt()
        val newHeight = (originalHeight * scaleFactor).toInt()

        val enlargedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

        val targetBitmap = Bitmap.createBitmap(originalWidth, originalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(targetBitmap)

        val offsetX = (originalWidth - newWidth) / 2f
        val offsetY = (originalHeight - newHeight) / 2f

        canvas.drawBitmap(enlargedBitmap, offsetX, offsetY, null)

        return targetBitmap
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