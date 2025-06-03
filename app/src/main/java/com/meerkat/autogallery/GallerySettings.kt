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

enum class BatteryManagementMode(val displayName: String) {
    CHARGING_ONLY("Only when charging"),
    BATTERY_LEVEL_ONLY("Only when battery >20%")
}

data class FolderInfo(
    val uri: String = "",
    val displayName: String = "",
    val lastScanTime: Long = 0L,
    val totalImagesFound: Int = 0,
    val isLimited: Boolean = false
)

data class GallerySettings(
    val isEnabled: Boolean = false,
    val photoInfoList: List<PhotoInfo> = emptyList(),
    val folderInfo: FolderInfo = FolderInfo(),
    val slideDuration: Int = 5000, // milliseconds
    val orderType: OrderType = OrderType.RANDOM,
    val transitionType: TransitionType = TransitionType.FADE,
    val zoomType: ZoomType = ZoomType.SAWTOOTH,
    val zoomAmount: Int = 3, // zoom percentage (0-5, representing 100%-105%)
    val enableBlurredBackground: Boolean = true,
    val batteryManagementMode: BatteryManagementMode = BatteryManagementMode.CHARGING_ONLY,
    val enableOrientationFiltering: Boolean = true,
    val squareDetectionSensitivity: Float = 0.8f, // 0.5 = very sensitive (few squares), 1.0 = less sensitive (more squares)
    val enableFeathering: Boolean = true,
    val slideshowBrightness: Float = 1.0f // 0.0f (dim) to 1.0f (full brightness) for slideshow window
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
    fun classifyImageOrientation(aspectRatio: Float, squareDetectionSensitivity: Float = 0.8f): ImageOrientation {
        // Calculate square detection range based on sensitivity
        // sensitivity 1.0 = strict (narrow range, fewer squares): ~0.9-1.1
        // sensitivity 0.5 = relaxed (wide range, more squares): ~0.75-1.33
        val baseRange = 0.2f // Base range around 1.0
        val sensitivityRange = baseRange * (2.0f - squareDetectionSensitivity) // Invert sensitivity

        val lowerThreshold = 1.0f - sensitivityRange
        val upperThreshold = 1.0f + sensitivityRange

        return when {
            aspectRatio > upperThreshold -> ImageOrientation.LANDSCAPE
            aspectRatio < lowerThreshold -> ImageOrientation.PORTRAIT
            else -> ImageOrientation.SQUARE
        }
    }

    fun getCurrentDeviceOrientation(screenWidth: Int, screenHeight: Int): ImageOrientation {
        val deviceAspectRatio = screenWidth.toFloat() / screenHeight.toFloat()
        return if (deviceAspectRatio > 1.0f) ImageOrientation.LANDSCAPE else ImageOrientation.PORTRAIT
    }

    fun shouldShowImage(imageOrientation: ImageOrientation, deviceOrientation: ImageOrientation): Boolean {
        return when (imageOrientation) {
            ImageOrientation.SQUARE -> true // Always show square images
            ImageOrientation.LANDSCAPE -> deviceOrientation == ImageOrientation.LANDSCAPE
            ImageOrientation.PORTRAIT -> deviceOrientation == ImageOrientation.PORTRAIT
        }
    }
}