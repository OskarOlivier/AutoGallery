// BootReceiver.kt
package com.meerkat.slumberslide

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val preferencesManager = PreferencesManager(context)
            val settings = preferencesManager.loadSettings()

            if (settings.isEnabled) {
                val serviceIntent = Intent(context, ScreenStateService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}