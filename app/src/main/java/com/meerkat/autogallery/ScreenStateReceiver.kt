// ScreenStateReceiver.kt
package com.meerkat.autogallery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val preferencesManager = PreferencesManager(context)
        val settings = preferencesManager.loadSettings()

        if (!settings.isEnabled) return

        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                // Service will handle the screen off event
                // This receiver is mainly for system-level events
            }
            Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                // Stop any running slideshow
                val slideshowIntent = Intent(context, SlideshowActivity::class.java)
                slideshowIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                // The slideshow activity will detect screen on and finish itself
            }
        }
    }
}
