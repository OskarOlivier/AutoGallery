// GallerySettings.kt
package com.meerkat.autogallery

data class GallerySettings(
    val isEnabled: Boolean = false,
    val selectedPhotos: Set<String> = emptySet(),
    val slideDuration: Int = 5000, // milliseconds
    val orderType: OrderType = OrderType.RANDOM,
    val transitionType: TransitionType = TransitionType.FADE,
    val enableBlurredBackground: Boolean = true,
    val enableOnCharging: Boolean = false,
    val enableAlways: Boolean = true
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