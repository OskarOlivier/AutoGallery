// MainActivity.kt
package com.meerkat.autogallery

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textview.MaterialTextView

class MainActivity : AppCompatActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var enableSwitch: SwitchMaterial
    private lateinit var statusText: MaterialTextView
    private lateinit var settingsButton: MaterialButton
    private lateinit var testButton: MaterialButton
    private lateinit var photoCountText: MaterialTextView
    private var isBatteryReceiverRegistered = false

    companion object {
        private const val MIN_BATTERY_LEVEL = 20
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                updateUI()
            }
        }
    }

    private val multiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            setupService()
        } else {
            Toast.makeText(this, "Permissions required for Auto Gallery to work", Toast.LENGTH_LONG).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            checkAndRequestPermissions()
        } else {
            Toast.makeText(this, "Overlay permission required for Auto Gallery", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_main)
            preferencesManager = PreferencesManager(this)
            initViews()
            setupClickListeners()
            checkPermissionsAndSetup()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
            Toast.makeText(this, "Error starting app: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            registerBatteryReceiver()
            updateUI()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onResume", e)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterBatteryReceiver()
    }

    private fun registerBatteryReceiver() {
        if (!isBatteryReceiverRegistered) {
            try {
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                registerReceiver(batteryReceiver, filter)
                isBatteryReceiverRegistered = true
            } catch (e: Exception) {
                Log.e("MainActivity", "Error registering battery receiver", e)
            }
        }
    }

    private fun unregisterBatteryReceiver() {
        if (isBatteryReceiverRegistered) {
            try {
                unregisterReceiver(batteryReceiver)
                isBatteryReceiverRegistered = false
            } catch (e: Exception) {
                Log.e("MainActivity", "Error unregistering battery receiver", e)
            }
        }
    }

    private fun initViews() {
        try {
            enableSwitch = findViewById(R.id.enableSwitch)
            statusText = findViewById(R.id.statusText)
            settingsButton = findViewById(R.id.settingsButton)
            testButton = findViewById(R.id.testButton)
            photoCountText = findViewById(R.id.photoCountText)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing views", e)
            throw e
        }
    }

    private fun setupClickListeners() {
        try {
            enableSwitch.setOnCheckedChangeListener { _, isChecked ->
                try {
                    val settings = preferencesManager.loadSettings().copy(isEnabled = isChecked)
                    preferencesManager.saveSettings(settings)

                    if (isChecked) {
                        val serviceIntent = Intent(this, ScreenStateService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }
                    } else {
                        stopService(Intent(this, ScreenStateService::class.java))
                    }
                    updateUI()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error toggling service", e)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            settingsButton.setOnClickListener {
                try {
                    startActivity(Intent(this, SettingsActivity::class.java))
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error starting settings", e)
                    Toast.makeText(this, "Error opening settings", Toast.LENGTH_SHORT).show()
                }
            }

            testButton.setOnClickListener {
                try {
                    val settings = preferencesManager.loadSettings()
                    if (settings.photoInfoList.isNotEmpty()) {
                        if (!canStartSlideshowBasedOnBattery(settings.batteryManagementMode)) {
                            showBatteryError(settings.batteryManagementMode)
                            return@setOnClickListener
                        }
                        startActivity(Intent(this, SlideshowActivity::class.java))
                    } else if (settings.folderInfo.uri.isNotEmpty()) {
                        Toast.makeText(this, "No photos found in selected folder. Try refreshing in Settings.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Please select a folder in Settings first", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error starting slideshow", e)
                    Toast.makeText(this, "Error starting slideshow", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up click listeners", e)
        }
    }

    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error getting battery level", e)
            100
        }
    }

    private fun isDeviceCharging(): Boolean {
        return try {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.isCharging
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking charging status", e)
            false
        }
    }

    private fun canStartSlideshowBasedOnBattery(batteryManagementMode: BatteryManagementMode): Boolean {
        val batteryLevel = getBatteryLevel()
        val isCharging = isDeviceCharging()

        return when (batteryManagementMode) {
            BatteryManagementMode.CHARGING_ONLY -> isCharging
            BatteryManagementMode.BATTERY_LEVEL_ONLY -> batteryLevel > MIN_BATTERY_LEVEL
        }
    }

    private fun showBatteryError(batteryManagementMode: BatteryManagementMode) {
        val batteryLevel = getBatteryLevel()
        val isCharging = isDeviceCharging()

        val message = when (batteryManagementMode) {
            BatteryManagementMode.CHARGING_ONLY -> {
                "Device must be charging to start slideshow (currently ${if (isCharging) "charging" else "not charging"})"
            }
            BatteryManagementMode.BATTERY_LEVEL_ONLY -> {
                "Battery level too low ($batteryLevel%) to start slideshow. Minimum: ${MIN_BATTERY_LEVEL + 1}%"
            }
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun getBatteryStatusText(settings: GallerySettings): String {
        val batteryLevel = getBatteryLevel()
        val isCharging = isDeviceCharging()

        return when (settings.batteryManagementMode) {
            BatteryManagementMode.CHARGING_ONLY -> {
                buildString {
                    append("🔋 $batteryLevel%")
                    if (isCharging) {
                        append(" (Charging - Ready)")
                    } else {
                        append(" (Not charging - Waiting for charger)")
                    }
                }
            }
            BatteryManagementMode.BATTERY_LEVEL_ONLY -> {
                buildString {
                    append("🔋 $batteryLevel%")
                    if (isCharging) {
                        append(" (Charging)")
                    }
                    if (batteryLevel <= MIN_BATTERY_LEVEL) {
                        append(" - Too low for slideshow")
                    } else {
                        append(" - Ready")
                    }
                }
            }
        }
    }

    private fun updateUI() {
        try {
            val settings = preferencesManager.loadSettings()
            enableSwitch.isChecked = settings.isEnabled

            val photoCount = settings.photoInfoList.size
            val folderInfo = settings.folderInfo

            val photoCountDetail = if (folderInfo.uri.isNotEmpty() && photoCount > 0) {
                buildString {
                    append("📁 ${folderInfo.displayName}")
                    appendLine()
                    append("$photoCount photos scanned")

                    val landscapeCount = settings.photoInfoList.count { it.orientation == ImageOrientation.LANDSCAPE }
                    val portraitCount = settings.photoInfoList.count { it.orientation == ImageOrientation.PORTRAIT }
                    val squareCount = settings.photoInfoList.count { it.orientation == ImageOrientation.SQUARE }

                    appendLine()

                    if (settings.enableOrientationFiltering) {
                        val currentOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            ImageOrientation.LANDSCAPE
                        } else {
                            ImageOrientation.PORTRAIT
                        }

                        val availableForCurrentOrientation = settings.photoInfoList.count { photoInfo ->
                            OrientationUtils.shouldShowImage(
                                photoInfo.orientation,
                                currentOrientation
                            )
                        }

                        val orientationName = if (currentOrientation == ImageOrientation.LANDSCAPE) "landscape" else "portrait"
                        append("📱 $availableForCurrentOrientation available in $orientationName mode")
                        appendLine()
                        append("🏞️ $landscapeCount landscape • 📱 $portraitCount portrait • ⬜ $squareCount square")
                    } else {
                        append("🏞️ $landscapeCount landscape • 📱 $portraitCount portrait • ⬜ $squareCount square")
                        appendLine()
                        append("(All photos will be shown - orientation filtering disabled)")
                    }

                    if (folderInfo.lastScanTime > 0) {
                        val timeAgo = System.currentTimeMillis() - folderInfo.lastScanTime
                        val timeText = when {
                            timeAgo < 60000 -> "just now"
                            timeAgo < 3600000 -> "${timeAgo / 60000} minutes ago"
                            timeAgo < 86400000 -> "${timeAgo / 3600000} hours ago"
                            else -> "${timeAgo / 86400000} days ago"
                        }
                        appendLine()
                        append("🕒 Last scanned: $timeText")
                    }

                    appendLine()
                    append(getBatteryStatusText(settings))
                }
            } else if (folderInfo.uri.isNotEmpty()) {
                buildString {
                    append("📁 ${folderInfo.displayName}")
                    appendLine()
                    append("No photos found in folder")
                    appendLine()
                    append("Use Settings → Refresh to rescan")
                    appendLine()
                    append(getBatteryStatusText(settings))
                }
            } else {
                buildString {
                    append("No folder selected")
                    appendLine()
                    append("Please select a folder in Settings")
                    appendLine()
                    append(getBatteryStatusText(settings))
                }
            }

            photoCountText.text = photoCountDetail

            statusText.text = when {
                !settings.isEnabled -> "Auto Gallery is disabled"
                folderInfo.uri.isEmpty() -> "Please select a folder in Settings"
                !canStartSlideshowBasedOnBattery(settings.batteryManagementMode) -> {
                    when (settings.batteryManagementMode) {
                        BatteryManagementMode.CHARGING_ONLY -> "Waiting for device to be plugged in"
                        BatteryManagementMode.BATTERY_LEVEL_ONLY -> {
                            val batteryLevel = getBatteryLevel()
                            "Battery level too low ($batteryLevel%) for slideshow"
                        }
                    }
                }
                photoCount == 0 -> "No photos found in selected folder"
                settings.enableOrientationFiltering -> {
                    val currentOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        ImageOrientation.LANDSCAPE
                    } else {
                        ImageOrientation.PORTRAIT
                    }

                    val availableForCurrentOrientation = settings.photoInfoList.count { photoInfo ->
                        OrientationUtils.shouldShowImage(
                            photoInfo.orientation,
                            currentOrientation
                        )
                    }

                    if (availableForCurrentOrientation > 0) {
                        "Auto Gallery is active - will start on screen timeout"
                    } else {
                        val orientationName = if (currentOrientation == ImageOrientation.LANDSCAPE) "landscape" else "portrait"
                        "No photos available for $orientationName mode"
                    }
                }
                else -> "Auto Gallery is active - will start on screen timeout"
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating UI", e)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateUI()
    }

    private fun checkPermissionsAndSetup() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
                return
            }

            checkAndRequestPermissions()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking permissions", e)
            Toast.makeText(this, "Error checking permissions", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        try {
            val permissions = mutableListOf<String>()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                }
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            if (permissions.isNotEmpty()) {
                multiplePermissionsLauncher.launch(permissions.toTypedArray())
            } else {
                setupService()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error requesting permissions", e)
        }
    }

    private fun setupService() {
        try {
            updateUI()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up service", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterBatteryReceiver()
    }
}