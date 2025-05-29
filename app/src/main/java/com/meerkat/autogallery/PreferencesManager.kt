// PreferencesManager.kt
package com.meerkat.autogallery

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("auto_gallery_prefs", Context.MODE_PRIVATE)

    fun saveSettings(settings: GallerySettings) {
        prefs.edit().apply {
            putBoolean("is_enabled", settings.isEnabled)
            putStringSet("selected_photos", settings.selectedPhotos)
            putInt("slide_duration", settings.slideDuration)
            putString("order_type", settings.orderType.name)
            putString("transition_type", settings.transitionType.name)
            putString("zoom_type", settings.zoomType.name)
            putInt("zoom_amount", settings.zoomAmount)
            putBoolean("enable_blurred_background", settings.enableBlurredBackground)
            putBoolean("enable_on_charging", settings.enableOnCharging)
            putBoolean("enable_always", settings.enableAlways)
            apply()
        }
    }

    fun loadSettings(): GallerySettings {
        return GallerySettings(
            isEnabled = prefs.getBoolean("is_enabled", false),
            selectedPhotos = prefs.getStringSet("selected_photos", emptySet()) ?: emptySet(),
            slideDuration = prefs.getInt("slide_duration", 5000),
            orderType = try {
                OrderType.valueOf(prefs.getString("order_type", OrderType.RANDOM.name)!!)
            } catch (e: Exception) {
                OrderType.RANDOM
            },
            transitionType = try {
                TransitionType.valueOf(prefs.getString("transition_type", TransitionType.FADE.name)!!)
            } catch (e: Exception) {
                TransitionType.FADE
            },
            zoomType = try {
                ZoomType.valueOf(prefs.getString("zoom_type", ZoomType.SAWTOOTH.name)!!)
            } catch (e: Exception) {
                ZoomType.SAWTOOTH
            },
            zoomAmount = prefs.getInt("zoom_amount", 3).coerceIn(0, 5),
            enableBlurredBackground = prefs.getBoolean("enable_blurred_background", true),
            enableOnCharging = prefs.getBoolean("enable_on_charging", false),
            enableAlways = prefs.getBoolean("enable_always", true)
        )
    }
}