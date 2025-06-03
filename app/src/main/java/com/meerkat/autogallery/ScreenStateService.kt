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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat

class ScreenStateService : Service() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var powerManager: PowerManager
    private val handler = Handler(Looper.getMainLooper())

    private var isScreenInteractive = true
    private var lastInteractionTime = 0L
    private var screenTimeoutMs = 30000L // Default 30 seconds
    private var idleThresholdMs = 29000L // Default timeout - 1 second
    private var slideshowStarted = false
    private var isBatteryReceiverRegistered = false
    private var isScreenStateReceiverRegistered = false

    companion object {
        private const val TAG = "ScreenStateService"
        private const val CHANNEL_ID = "auto_gallery_service"
        private const val NOTIFICATION_ID = 1001
        private const val MIN_BATTERY_LEVEL = 20
        private const val IDLE_CHECK_INTERVAL = 2000L // Check every 2 seconds
    }

    private val idleCheckRunnable = object : Runnable {
        override fun run() {
            checkIdleState()
            if (!slideshowStarted) {
                handler.postDelayed(this, IDLE_CHECK_INTERVAL)
            }
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    Log.d(TAG, "Screen became interactive: ${intent.action}")
                    onUserActivity()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen turned off")
                    isScreenInteractive = false
                    // Don't immediately trigger - let idle detection handle it
                }
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val batteryLevel = getBatteryLevel()
                Log.d(TAG, "Battery level changed: $batteryLevel%")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        preferencesManager = PreferencesManager(this)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        createNotificationChannel()
        loadScreenTimeoutSettings()
        registerReceivers()
        startIdleMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun loadScreenTimeoutSettings() {
        try {
            // Get system screen timeout setting
            screenTimeoutMs = Settings.System.getLong(
                contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                30000L // Default 30 seconds if can't read setting
            )

            // Set idle threshold to 1 second before screen timeout
            idleThresholdMs = maxOf(screenTimeoutMs - 1000L, 5000L) // Minimum 5 seconds

            Log.d(TAG, "Screen timeout: ${screenTimeoutMs}ms, Idle threshold: ${idleThresholdMs}ms")
        } catch (e: Exception) {
            Log.w(TAG, "Could not read screen timeout setting, using defaults", e)
            screenTimeoutMs = 30000L
            idleThresholdMs = 29000L
        }
    }

    private fun registerReceivers() {
        registerScreenStateReceiver()
        registerBatteryReceiver()
    }

    private fun registerScreenStateReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenStateReceiver, filter)
            isScreenStateReceiverRegistered = true
            Log.d(TAG, "Screen state receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering screen state receiver", e)
        }
    }

    private fun registerBatteryReceiver() {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            registerReceiver(batteryReceiver, filter)
            isBatteryReceiverRegistered = true
            Log.d(TAG, "Battery receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering battery receiver", e)
        }
    }

    private fun startIdleMonitoring() {
        lastInteractionTime = System.currentTimeMillis()
        isScreenInteractive = powerManager.isInteractive
        slideshowStarted = false

        Log.d(TAG, "Starting idle monitoring - screen interactive: $isScreenInteractive")
        handler.post(idleCheckRunnable)
    }

    private fun onUserActivity() {
        lastInteractionTime = System.currentTimeMillis()
        isScreenInteractive = powerManager.isInteractive
        slideshowStarted = false

        Log.d(TAG, "User activity detected, resetting idle timer")

        // Restart idle monitoring if it was stopped
        handler.removeCallbacks(idleCheckRunnable)
        handler.post(idleCheckRunnable)
    }

    private fun checkIdleState() {
        val currentTime = System.currentTimeMillis()
        val idleTime = currentTime - lastInteractionTime
        val currentlyInteractive = powerManager.isInteractive

        Log.v(TAG, "Idle check - Time since last activity: ${idleTime}ms, Screen interactive: $currentlyInteractive, Threshold: ${idleThresholdMs}ms")

        // Update screen interactive state
        if (currentlyInteractive != isScreenInteractive) {
            isScreenInteractive = currentlyInteractive
            if (currentlyInteractive) {
                onUserActivity() // Reset if screen became interactive
                return
            }
        }

        // Check if we should start slideshow
        if (!slideshowStarted && idleTime >= idleThresholdMs) {
            Log.d(TAG, "Idle threshold reached (${idleTime}ms >= ${idleThresholdMs}ms)")
            checkAndStartSlideshow()
        }
    }

    private fun checkAndStartSlideshow() {
        Log.d(TAG, "Checking if slideshow should start - slideshowStarted: $slideshowStarted")

        if (slideshowStarted) {
            Log.d(TAG, "Slideshow already started")
            return
        }

        val settings = preferencesManager.loadSettings()
        if (!settings.isEnabled || settings.folderInfo.uri.isEmpty() || settings.photoInfoList.isEmpty()) {
            Log.d(TAG, "Not starting slideshow - disabled, no folder selected, or no photos in folder")
            return
        }

        val canStartBasedOnBattery = checkBatteryConditions(settings.batteryManagementMode)
        if (!canStartBasedOnBattery) {
            Log.d(TAG, "Not starting slideshow - battery conditions not met")
            return
        }

        Log.d(TAG, "Starting slideshow (battery management: ${settings.batteryManagementMode})")
        slideshowStarted = true

        // Stop idle monitoring since slideshow is starting
        handler.removeCallbacks(idleCheckRunnable)

        handler.postDelayed({
            if (slideshowStarted) {
                Log.d(TAG, "Actually starting slideshow now")
                startSlideshow()
            } else {
                Log.d(TAG, "Slideshow start cancelled - user activity detected")
            }
        }, 500) // Small delay to ensure smooth transition
    }

    private fun checkBatteryConditions(batteryManagementMode: BatteryManagementMode): Boolean {
        val batteryLevel = getBatteryLevel()
        val isCharging = isDeviceCharging()

        return when (batteryManagementMode) {
            BatteryManagementMode.CHARGING_ONLY -> {
                Log.d(TAG, "Battery mode: CHARGING_ONLY - charging: $isCharging")
                isCharging
            }
            BatteryManagementMode.BATTERY_LEVEL_ONLY -> {
                Log.d(TAG, "Battery mode: BATTERY_LEVEL_ONLY - level: $batteryLevel% (required: >$MIN_BATTERY_LEVEL%)")
                batteryLevel > MIN_BATTERY_LEVEL
            }
        }
    }

    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            Log.v(TAG, "Current battery level: $batteryLevel%")
            batteryLevel
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery level", e)
            100
        }
    }

    private fun isDeviceCharging(): Boolean {
        return try {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val isCharging = batteryManager.isCharging
            Log.v(TAG, "Device charging status: $isCharging")
            isCharging
        } catch (e: Exception) {
            Log.e(TAG, "Error checking charging status", e)
            false
        }
    }

    private fun startSlideshow() {
        try {
            val intent = Intent(this, SlideshowActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION

                putExtra("STARTED_FROM_SERVICE", true)
            }
            startActivity(intent)
            Log.d(TAG, "Slideshow activity started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting slideshow activity", e)
            slideshowStarted = false
            // Restart idle monitoring on error
            startIdleMonitoring()
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

        val settings = preferencesManager.loadSettings()
        val batteryLevel = getBatteryLevel()
        val isCharging = isDeviceCharging()

        val batteryStatus = when (settings.batteryManagementMode) {
            BatteryManagementMode.CHARGING_ONLY -> {
                if (isCharging) {
                    "Charging: Ready to start"
                } else {
                    "Not charging: Waiting for charger"
                }
            }
            BatteryManagementMode.BATTERY_LEVEL_ONLY -> {
                if (batteryLevel > MIN_BATTERY_LEVEL) {
                    "Battery: $batteryLevel% (Ready)"
                } else {
                    "Battery: $batteryLevel% (Too low)"
                }
            }
        }

        val folderStatus = if (settings.folderInfo.uri.isNotEmpty()) {
            if (settings.photoInfoList.isNotEmpty()) {
                "${settings.photoInfoList.size} photos from ${settings.folderInfo.displayName}"
            } else {
                "No photos in ${settings.folderInfo.displayName}"
            }
        } else {
            "No folder selected"
        }

        val idleTimeoutText = "Idle timeout: ${idleThresholdMs / 1000}s"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto Gallery")
            .setContentText("$folderStatus • $batteryStatus")
            .setSubText(idleTimeoutText)
            .setSmallIcon(R.drawable.ic_photo_library)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")

        // Stop idle monitoring
        handler.removeCallbacks(idleCheckRunnable)

        // Unregister receivers
        if (isScreenStateReceiverRegistered) {
            try {
                unregisterReceiver(screenStateReceiver)
                isScreenStateReceiverRegistered = false
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering screen state receiver", e)
            }
        }

        if (isBatteryReceiverRegistered) {
            try {
                unregisterReceiver(batteryReceiver)
                isBatteryReceiverRegistered = false
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering battery receiver", e)
            }
        }
    }

    fun getCurrentBatteryLevel(): Int = getBatteryLevel()
    fun isBatterySufficient(): Boolean = getBatteryLevel() > MIN_BATTERY_LEVEL
    fun getIdleThresholdSeconds(): Int = (idleThresholdMs / 1000).toInt()
}