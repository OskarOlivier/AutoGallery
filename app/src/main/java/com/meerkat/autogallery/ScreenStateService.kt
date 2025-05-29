// ScreenStateService.kt
package com.meerkat.autogallery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ScreenStateService : Service() {

    private lateinit var preferencesManager: PreferencesManager
    private var isScreenOff = false
    private var slideshowStarted = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOff = true
                    checkAndStartSlideshow()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    isScreenOff = false
                    slideshowStarted = false
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        createNotificationChannel()
        registerScreenStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerScreenStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    private fun checkAndStartSlideshow() {
        if (!isScreenOff || slideshowStarted) return

        val settings = preferencesManager.loadSettings()
        if (!settings.isEnabled || settings.selectedPhotos.isEmpty()) return

        // Check charging conditions
        if (settings.enableOnCharging && !settings.enableAlways) {
            if (!isDeviceCharging()) return
        }

        // Start slideshow with a slight delay to ensure screen is properly off
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isScreenOff && !slideshowStarted) {
                startSlideshow()
            }
        }, 2000) // 2 second delay
    }

    private fun isDeviceCharging(): Boolean {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.isCharging
    }

    private fun startSlideshow() {
        slideshowStarted = true
        val intent = Intent(this, SlideshowActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Gallery Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Auto Gallery running in background"
                setSound(null, null)
                enableVibration(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto Gallery")
            .setContentText("Monitoring screen state for slideshow")
            .setSmallIcon(R.drawable.ic_photo_library)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }

    companion object {
        private const val CHANNEL_ID = "auto_gallery_service"
        private const val NOTIFICATION_ID = 1001
    }
}