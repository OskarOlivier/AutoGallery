// MainActivity.kt
package com.meerkat.autogallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
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
            updateUI()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onResume", e)
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
                    if (settings.photoInfoList.isNotEmpty() || settings.selectedPhotos.isNotEmpty()) {
                        startActivity(Intent(this, SlideshowActivity::class.java))
                    } else {
                        Toast.makeText(this, "Please select photos in Settings first", Toast.LENGTH_SHORT).show()
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

    private fun updateUI() {
        try {
            val settings = preferencesManager.loadSettings()
            enableSwitch.isChecked = settings.isEnabled

            // Get photo information
            val photoCount = maxOf(settings.photoInfoList.size, settings.selectedPhotos.size)

            // Create detailed photo count text with orientation information
            val photoCountDetail = if (photoCount > 0) {
                buildString {
                    append("$photoCount photos selected")

                    // Add orientation breakdown if available
                    if (settings.photoInfoList.isNotEmpty()) {
                        val landscapeCount = settings.photoInfoList.count { it.orientation == ImageOrientation.LANDSCAPE }
                        val portraitCount = settings.photoInfoList.count { it.orientation == ImageOrientation.PORTRAIT }
                        val squareCount = settings.photoInfoList.count { it.orientation == ImageOrientation.SQUARE }

                        appendLine()

                        if (settings.enableOrientationFiltering) {
                            // Get current device orientation
                            val currentOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                ImageOrientation.LANDSCAPE
                            } else {
                                ImageOrientation.PORTRAIT
                            }

                            // Calculate available photos for current orientation
                            val availableForCurrentOrientation = settings.photoInfoList.count { photoInfo ->
                                OrientationUtils.shouldShowImage(
                                    photoInfo.orientation,
                                    currentOrientation,
                                    settings.showSquareImagesInBothOrientations
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
                    }
                }
            } else {
                "No photos selected"
            }

            photoCountText.text = photoCountDetail

            statusText.text = when {
                !settings.isEnabled -> "Auto Gallery is disabled"
                photoCount == 0 -> "Please select photos in Settings"
                settings.enableOrientationFiltering -> {
                    val currentOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        ImageOrientation.LANDSCAPE
                    } else {
                        ImageOrientation.PORTRAIT
                    }

                    val availableForCurrentOrientation = if (settings.photoInfoList.isNotEmpty()) {
                        settings.photoInfoList.count { photoInfo ->
                            OrientationUtils.shouldShowImage(
                                photoInfo.orientation,
                                currentOrientation,
                                settings.showSquareImagesInBothOrientations
                            )
                        }
                    } else {
                        photoCount // Fallback for backward compatibility
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
        // Update UI when orientation changes to show correct photo counts
        updateUI()
    }

    private fun checkPermissionsAndSetup() {
        try {
            // Check overlay permission first
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

            // Storage permissions
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

            // Notification permission for Android 13+
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
            // Service will be started when user enables the switch
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up service", e)
        }
    }
}