// ScreenStateReceiver.kt
package com.meerkat.slumberslide

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ScreenStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenStateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val preferencesManager = PreferencesManager(context)
        val settings = preferencesManager.loadSettings()

        if (!settings.isEnabled) {
            Log.d(TAG, "SlumberSlide disabled, ignoring screen state change")
            return
        }

        Log.d(TAG, "Screen state changed: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                // Screen turned off - the service will handle idle detection
                Log.d(TAG, "Screen turned off - service will monitor idle time")
            }
            Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                // User activity detected - notify service and stop any running slideshow
                Log.d(TAG, "User activity detected: ${intent.action}")

                // Stop any running slideshow by sending exit intent
                try {
                    val slideshowIntent = Intent(context, SlideshowActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("EXIT_SLIDESHOW", true)
                    }

                    // Send broadcast to slideshow if it's running
                    val exitIntent = Intent("com.meerkat.slumberslide.EXIT_SLIDESHOW")
                    context.sendBroadcast(exitIntent)

                } catch (e: Exception) {
                    Log.w(TAG, "Could not send exit intent to slideshow", e)
                }
            }
        }
    }
}