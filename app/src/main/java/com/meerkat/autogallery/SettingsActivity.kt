// SettingsActivity.kt
package com.meerkat.autogallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView
import android.widget.Spinner

class SettingsActivity : AppCompatActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var settings: GallerySettings

    private lateinit var selectPhotosButton: MaterialButton
    private lateinit var selectedPhotosRecycler: RecyclerView
    private lateinit var slideDurationSlider: Slider
    private lateinit var slideDurationText: MaterialTextView
    private lateinit var orderTypeSpinner: Spinner
    private lateinit var transitionTypeSpinner: Spinner
    private lateinit var blurredBackgroundCheckbox: MaterialCheckBox
    private lateinit var chargingOnlyCheckbox: MaterialCheckBox
    private lateinit var alwaysEnabledCheckbox: MaterialCheckBox
    private lateinit var checkPermissionsButton: MaterialButton
    private lateinit var permissionStatusText: MaterialTextView

    private var selectedPhotoUris = mutableSetOf<String>()
    private lateinit var photoAdapter: SelectedPhotosAdapter

    private val multiplePhotoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            // Convert URIs to persistent strings and add to selection
            uris.forEach { uri ->
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                selectedPhotoUris.add(uri.toString())
            }
            updateSelectedPhotos()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Auto Gallery Settings"

        preferencesManager = PreferencesManager(this)
        settings = preferencesManager.loadSettings()
        selectedPhotoUris = settings.selectedPhotos.toMutableSet()

        initViews()
        setupSpinners()
        loadCurrentSettings()
        setupListeners()
        checkPermissionStatus()
    }

    private fun initViews() {
        selectPhotosButton = findViewById(R.id.selectPhotosButton)
        selectedPhotosRecycler = findViewById(R.id.selectedPhotosRecycler)
        slideDurationSlider = findViewById(R.id.slideDurationSlider)
        slideDurationText = findViewById(R.id.slideDurationText)
        orderTypeSpinner = findViewById(R.id.orderTypeSpinner)
        transitionTypeSpinner = findViewById(R.id.transitionTypeSpinner)
        blurredBackgroundCheckbox = findViewById(R.id.blurredBackgroundCheckbox)
        chargingOnlyCheckbox = findViewById(R.id.chargingOnlyCheckbox)
        alwaysEnabledCheckbox = findViewById(R.id.alwaysEnabledCheckbox)
        checkPermissionsButton = findViewById(R.id.checkPermissionsButton)
        permissionStatusText = findViewById(R.id.permissionStatusText)

        // Setup RecyclerView for selected photos
        photoAdapter = SelectedPhotosAdapter(selectedPhotoUris.toMutableList()) { position ->
            removePhoto(position)
        }
        selectedPhotosRecycler.layoutManager = GridLayoutManager(this, 3)
        selectedPhotosRecycler.adapter = photoAdapter
    }

    private fun setupSpinners() {
        // Order type spinner
        val orderTypes = OrderType.values().map { it.displayName }
        val orderAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, orderTypes)
        orderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        orderTypeSpinner.adapter = orderAdapter

        // Transition type spinner
        val transitionTypes = TransitionType.values().map { it.displayName }
        val transitionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, transitionTypes)
        transitionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        transitionTypeSpinner.adapter = transitionAdapter
    }

    private fun loadCurrentSettings() {
        slideDurationSlider.value = (settings.slideDuration / 1000).toFloat()
        updateSlideDurationText(settings.slideDuration / 1000)

        orderTypeSpinner.setSelection(settings.orderType.ordinal)
        transitionTypeSpinner.setSelection(settings.transitionType.ordinal)

        blurredBackgroundCheckbox.isChecked = settings.enableBlurredBackground
        chargingOnlyCheckbox.isChecked = settings.enableOnCharging
        alwaysEnabledCheckbox.isChecked = settings.enableAlways

        updateSelectedPhotos()
    }

    private fun setupListeners() {
        selectPhotosButton.setOnClickListener {
            multiplePhotoPickerLauncher.launch("image/*")
        }

        slideDurationSlider.addOnChangeListener { _, value, _ ->
            updateSlideDurationText(value.toInt())
        }

        // Save settings when any option changes
        val saveSettingsListener = {
            saveCurrentSettings()
        }

        slideDurationSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                saveCurrentSettings()
            }
        })

        blurredBackgroundCheckbox.setOnCheckedChangeListener { _, _ -> saveCurrentSettings() }
        chargingOnlyCheckbox.setOnCheckedChangeListener { _, _ -> saveCurrentSettings() }
        alwaysEnabledCheckbox.setOnCheckedChangeListener { _, _ -> saveCurrentSettings() }

        checkPermissionsButton.setOnClickListener {
            requestAllPermissions()
        }
    }

    private fun updateSlideDurationText(seconds: Int) {
        slideDurationText.text = "${seconds}s per slide"
    }

    private fun updateSelectedPhotos() {
        photoAdapter.updatePhotos(selectedPhotoUris.toMutableList())
        selectPhotosButton.text = if (selectedPhotoUris.isEmpty()) {
            "Select Photos"
        } else {
            "Add More Photos (${selectedPhotoUris.size} selected)"
        }
    }

    private fun removePhoto(position: Int) {
        val photosList = selectedPhotoUris.toMutableList()
        if (position < photosList.size) {
            selectedPhotoUris.remove(photosList[position])
            updateSelectedPhotos()
            saveCurrentSettings()
        }
    }

    private fun saveCurrentSettings() {
        val newSettings = GallerySettings(
            isEnabled = settings.isEnabled,
            selectedPhotos = selectedPhotoUris,
            slideDuration = (slideDurationSlider.value * 1000).toInt(),
            orderType = OrderType.values()[orderTypeSpinner.selectedItemPosition],
            transitionType = TransitionType.values()[transitionTypeSpinner.selectedItemPosition],
            enableBlurredBackground = blurredBackgroundCheckbox.isChecked,
            enableOnCharging = chargingOnlyCheckbox.isChecked,
            enableAlways = alwaysEnabledCheckbox.isChecked
        )

        preferencesManager.saveSettings(newSettings)
        settings = newSettings
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        saveCurrentSettings()
    }

    private fun checkPermissionStatus() {
        val hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required for older versions
        }

        val hasOverlayPermission = Settings.canDrawOverlays(this)

        val status = when {
            hasStoragePermission && hasNotificationPermission && hasOverlayPermission -> "✅ All permissions granted"
            !hasStoragePermission -> "❌ Storage permission required"
            !hasNotificationPermission -> "❌ Notification permission required"
            !hasOverlayPermission -> "❌ Overlay permission required"
            else -> "⚠️ Some permissions missing"
        }

        permissionStatusText.text = status
    }

    private fun requestAllPermissions() {
        // Check overlay permission first
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
            return
        }

        val permissions = mutableListOf<String>()

        // Storage permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            multiplePermissionsLauncher.launch(permissions.toTypedArray())
        } else {
            Toast.makeText(this, "All permissions already granted!", Toast.LENGTH_SHORT).show()
            checkPermissionStatus()
        }
    }

    private val multiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        checkPermissionStatus()
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Some permissions denied. App may not work properly.", Toast.LENGTH_LONG).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissionStatus()
        if (Settings.canDrawOverlays(this)) {
            // Now check other permissions
            requestAllPermissions()
        } else {
            Toast.makeText(this, "Overlay permission is required for Auto Gallery to work", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissionStatus()
    }
}