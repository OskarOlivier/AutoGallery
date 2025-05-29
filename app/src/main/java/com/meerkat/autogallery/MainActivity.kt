// MainActivity.kt
package com.meerkat.autogallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
        setContentView(R.layout.activity_main)

        preferencesManager = PreferencesManager(this)
        initViews()
        setupClickListeners()
        checkPermissionsAndSetup()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun initViews() {
        enableSwitch = findViewById(R.id.enableSwitch)
        statusText = findViewById(R.id.statusText)
        settingsButton = findViewById(R.id.settingsButton)
        testButton = findViewById(R.id.testButton)
        photoCountText = findViewById(R.id.photoCountText)
    }

    private fun setupClickListeners() {
        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            val settings = preferencesManager.loadSettings().copy(isEnabled = isChecked)
            preferencesManager.saveSettings(settings)

            if (isChecked) {
                startService(Intent(this, ScreenStateService::class.java))
            } else {
                stopService(Intent(this, ScreenStateService::class.java))
            }
            updateUI()
        }

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        testButton.setOnClickListener {
            val settings = preferencesManager.loadSettings()
            if (settings.selectedPhotos.isNotEmpty()) {
                startActivity(Intent(this, SlideshowActivity::class.java))
            } else {
                Toast.makeText(this, "Please select photos in Settings first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI() {
        val settings = preferencesManager.loadSettings()
        enableSwitch.isChecked = settings.isEnabled

        val photoCount = settings.selectedPhotos.size
        photoCountText.text = if (photoCount > 0) {
            "$photoCount photos selected"
        } else {
            "No photos selected"
        }

        statusText.text = when {
            !settings.isEnabled -> "Auto Gallery is disabled"
            photoCount == 0 -> "Please select photos in Settings"
            else -> "Auto Gallery is active - will start on screen timeout"
        }
    }

    private fun checkPermissionsAndSetup() {
        // Check overlay permission first
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
            return
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
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
    }

    private fun setupService() {
        updateUI()
        // Service will be started when user enables the switch
    }
}