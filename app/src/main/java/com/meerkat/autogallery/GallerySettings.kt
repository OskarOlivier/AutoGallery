// GallerySettings.kt
package com.meerkat.autogallery

data class PhotoInfo(
    val uri: String,
    val orientation: ImageOrientation,
    val aspectRatio: Float
)

enum class ImageOrientation {
    LANDSCAPE,    // Wider than tall (aspect ratio > 1.2)
    PORTRAIT,     // Taller than wide (aspect ratio < 0.8)
    SQUARE        // Nearly square (0.8 <= aspect ratio <= 1.2)
}

data class GallerySettings(
    val isEnabled: Boolean = false,
    val selectedPhotos: Set<String> = emptySet(), // Keep for backward compatibility
    val photoInfoList: List<PhotoInfo> = emptyList(), // New orientation-aware storage
    val slideDuration: Int = 5000, // milliseconds
    val orderType: OrderType = OrderType.RANDOM,
    val transitionType: TransitionType = TransitionType.FADE,
    val zoomType: ZoomType = ZoomType.SAWTOOTH,
    val zoomAmount: Int = 3, // zoom percentage (0-5, representing 100%-105%)
    val enableBlurredBackground: Boolean = true,
    val enableOnCharging: Boolean = false,
    val enableAlways: Boolean = true,
    val enableOrientationFiltering: Boolean = true, // New setting to enable/disable orientation filtering
    val showSquareImagesInBothOrientations: Boolean = true // Whether to show square images in both orientations
)

enum class OrderType(val displayName: String) {
    RANDOM("Random"),
    ALPHABETICAL("Alphabetical"),
    DATE_MODIFIED("Date Modified"),
    DATE_CREATED("Date Created")
}

enum class TransitionType(val displayName: String) {
    FADE("Fade"),
    SLIDE_LEFT("Slide Left"),
    SLIDE_RIGHT("Slide Right"),
    SLIDE_UP("Slide Up"),
    SLIDE_DOWN("Slide Down"),
}

enum class ZoomType(val displayName: String) {
    SAWTOOTH("Sawtooth (Always Zoom In)"),
    SINE_WAVE("Sine Wave (Alternating)")
}

// Helper functions for orientation detection
object OrientationUtils {
    fun classifyImageOrientation(aspectRatio: Float): ImageOrientation {
        return when {
            aspectRatio > 1.2f -> ImageOrientation.LANDSCAPE
            aspectRatio < 0.8f -> ImageOrientation.PORTRAIT
            else -> ImageOrientation.SQUARE
        }
    }

    fun getCurrentDeviceOrientation(screenWidth: Int, screenHeight: Int): ImageOrientation {
        val deviceAspectRatio = screenWidth.toFloat() / screenHeight.toFloat()
        return if (deviceAspectRatio > 1.0f) ImageOrientation.LANDSCAPE else ImageOrientation.PORTRAIT
    }

    fun shouldShowImage(imageOrientation: ImageOrientation, deviceOrientation: ImageOrientation, showSquareInBoth: Boolean): Boolean {
        return when (imageOrientation) {
            ImageOrientation.SQUARE -> showSquareInBoth // Show square images in both orientations if enabled
            ImageOrientation.LANDSCAPE -> deviceOrientation == ImageOrientation.LANDSCAPE
            ImageOrientation.PORTRAIT -> deviceOrientation == ImageOrientation.PORTRAIT
        }
    }
}