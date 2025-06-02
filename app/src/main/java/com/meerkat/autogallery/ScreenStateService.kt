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
import android.util.Log
import androidx.core.app.NotificationCompat

class ScreenStateService : Service() {

    private lateinit var preferencesManager: PreferencesManager
    private var isScreenOff = false
    private var slideshowStarted = false

    companion object {
        private const val TAG = "ScreenStateService"
        private const val CHANNEL_ID = "auto_gallery_service"
        private const val NOTIFICATION_ID = 1001
        private const val MIN_BATTERY_LEVEL = 20 // Minimum battery level to start slideshow
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Screen state changed: ${intent?.action}")
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen turned off")
                    isScreenOff = true
                    checkAndStartSlideshow()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    Log.d(TAG, "Screen turned on")
                    isScreenOff = false
                    slideshowStarted = false
                }
            }
        }
    }

    // Battery level receiver to monitor battery changes
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val batteryLevel = getBatteryLevel()
                Log.d(TAG, "Battery level changed: $batteryLevel%")

                // If slideshow is running and battery is too low, we'll let SlideshowActivity handle it
                // This receiver mainly logs battery changes for debugging
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        preferencesManager = PreferencesManager(this)
        createNotificationChannel()
        registerScreenStateReceiver()
        registerBatteryReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerScreenStateReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenStateReceiver, filter)
            Log.d(TAG, "Screen state receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering screen state receiver", e)
        }
    }

    private fun registerBatteryReceiver() {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            registerReceiver(batteryReceiver, filter)
            Log.d(TAG, "Battery receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering battery receiver", e)
        }
    }

    private fun checkAndStartSlideshow() {
        Log.d(TAG, "Checking if slideshow should start - isScreenOff: $isScreenOff, slideshowStarted: $slideshowStarted")

        if (!isScreenOff || slideshowStarted) {
            Log.d(TAG, "Not starting slideshow - conditions not met")
            return
        }

        val settings = preferencesManager.loadSettings()
        if (!settings.isEnabled || settings.selectedPhotos.isEmpty()) {
            Log.d(TAG, "Not starting slideshow - disabled or no photos selected")
            return
        }

        // Check battery level first
        val batteryLevel = getBatteryLevel()
        if (!isBatteryLevelSufficient()) {
            Log.d(TAG, "Not starting slideshow - battery too low ($batteryLevel%)")
            return
        }

        // Check charging conditions
        if (settings.enableOnCharging && !settings.enableAlways) {
            if (!isDeviceCharging()) {
                Log.d(TAG, "Not starting slideshow - device not charging")
                return
            }
        }

        Log.d(TAG, "Starting slideshow with delay (battery: $batteryLevel%)")
        // Start slideshow with a shorter delay since we made the activity more robust
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isScreenOff && !slideshowStarted) {
                Log.d(TAG, "Actually starting slideshow now")
                startSlideshow()
            } else {
                Log.d(TAG, "Slideshow start cancelled - screen state changed")
            }
        }, 1500) // Reduced delay to 1.5 seconds
    }

    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            Log.d(TAG, "Current battery level: $batteryLevel%")
            batteryLevel
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery level", e)
            100 // Assume full battery if we can't get the level
        }
    }

    private fun isBatteryLevelSufficient(): Boolean {
        val batteryLevel = getBatteryLevel()
        return batteryLevel > MIN_BATTERY_LEVEL
    }

    private fun isDeviceCharging(): Boolean {
        return try {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val isCharging = batteryManager.isCharging
            Log.d(TAG, "Device charging status: $isCharging")
            isCharging
        } catch (e: Exception) {
            Log.e(TAG, "Error checking charging status", e)
            false
        }
    }

    private fun startSlideshow() {
        try {
            slideshowStarted = true
            val intent = Intent(this, SlideshowActivity::class.java).apply {
                // Use flags that ensure the slideshow starts cleanly without showing MainActivity
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION

                // Add extra to indicate this is started from service
                putExtra("STARTED_FROM_SERVICE", true)
            }
            startActivity(intent)
            Log.d(TAG, "Slideshow activity started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting slideshow activity", e)
            slideshowStarted = false
        }
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

        // Add battery level to notification
        val batteryLevel = getBatteryLevel()
        val batteryStatus = if (isBatteryLevelSufficient()) {
            "Battery: $batteryLevel%"
        } else {
            "Battery too low: $batteryLevel%"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto Gallery")
            .setContentText("Monitoring screen state • $batteryStatus")
            .setSmallIcon(R.drawable.ic_photo_library)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        try {
            unregisterReceiver(screenStateReceiver)
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receivers", e)
        }
    }

    // Utility methods for other classes to use
    fun getCurrentBatteryLevel(): Int = getBatteryLevel()
    fun isBatterySufficient(): Boolean = isBatteryLevelSufficient()
}