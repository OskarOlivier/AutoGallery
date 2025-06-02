// SettingsActivity.kt
package com.meerkat.autogallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
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
import kotlinx.coroutines.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var settings: GallerySettings

    private lateinit var selectPhotosButton: MaterialButton
    private lateinit var selectedPhotosRecycler: RecyclerView
    private lateinit var slideDurationSlider: Slider
    private lateinit var slideDurationText: MaterialTextView
    private lateinit var orderTypeSpinner: Spinner
    private lateinit var transitionTypeSpinner: Spinner
    private lateinit var zoomTypeSpinner: Spinner
    private lateinit var zoomAmountSlider: Slider
    private lateinit var zoomAmountText: MaterialTextView
    private lateinit var blurredBackgroundCheckbox: MaterialCheckBox
    private lateinit var chargingOnlyCheckbox: MaterialCheckBox
    private lateinit var alwaysEnabledCheckbox: MaterialCheckBox
    private lateinit var orientationFilteringCheckbox: MaterialCheckBox
    private lateinit var squareImagesCheckbox: MaterialCheckBox
    private lateinit var featheringCheckbox: MaterialCheckBox
    private lateinit var checkPermissionsButton: MaterialButton
    private lateinit var permissionStatusText: MaterialTextView
    private lateinit var orientationStatsText: MaterialTextView

    private var photoInfoList = mutableListOf<PhotoInfo>()
    private lateinit var photoAdapter: SelectedPhotosAdapter

    // Coroutine scope for image analysis
    private val analysisScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val multiplePhotoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            analyzeAndAddPhotos(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Auto Gallery Settings"

        preferencesManager = PreferencesManager(this)
        settings = preferencesManager.loadSettings()
        photoInfoList = settings.photoInfoList.toMutableList()

        initViews()
        setupSpinners()
        loadCurrentSettings()
        setupListeners()
        checkPermissionStatus()
        updateOrientationStats()
    }

    private fun initViews() {
        selectPhotosButton = findViewById(R.id.selectPhotosButton)
        selectedPhotosRecycler = findViewById(R.id.selectedPhotosRecycler)
        slideDurationSlider = findViewById(R.id.slideDurationSlider)
        slideDurationText = findViewById(R.id.slideDurationText)
        orderTypeSpinner = findViewById(R.id.orderTypeSpinner)
        transitionTypeSpinner = findViewById(R.id.transitionTypeSpinner)
        zoomTypeSpinner = findViewById(R.id.zoomTypeSpinner)
        zoomAmountSlider = findViewById(R.id.zoomAmountSlider)
        zoomAmountText = findViewById(R.id.zoomAmountText)
        blurredBackgroundCheckbox = findViewById(R.id.blurredBackgroundCheckbox)
        chargingOnlyCheckbox = findViewById(R.id.chargingOnlyCheckbox)
        alwaysEnabledCheckbox = findViewById(R.id.alwaysEnabledCheckbox)
        orientationFilteringCheckbox = findViewById(R.id.orientationFilteringCheckbox)
        squareImagesCheckbox = findViewById(R.id.squareImagesCheckbox)
        featheringCheckbox = findViewById(R.id.featheringCheckbox)
        checkPermissionsButton = findViewById(R.id.checkPermissionsButton)
        permissionStatusText = findViewById(R.id.permissionStatusText)
        orientationStatsText = findViewById(R.id.orientationStatsText)

        // Setup RecyclerView for selected photos
        photoAdapter = SelectedPhotosAdapter(photoInfoList.map { it.uri }.toMutableList()) { position ->
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

        // Zoom type spinner
        val zoomTypes = ZoomType.values().map { it.displayName }
        val zoomAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, zoomTypes)
        zoomAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        zoomTypeSpinner.adapter = zoomAdapter
    }

    private fun loadCurrentSettings() {
        slideDurationSlider.value = (settings.slideDuration / 1000).toFloat()
        updateSlideDurationText(settings.slideDuration / 1000)

        orderTypeSpinner.setSelection(settings.orderType.ordinal)
        transitionTypeSpinner.setSelection(settings.transitionType.ordinal)
        zoomTypeSpinner.setSelection(settings.zoomType.ordinal)

        zoomAmountSlider.value = settings.zoomAmount.toFloat()
        updateZoomAmountText(settings.zoomAmount)

        blurredBackgroundCheckbox.isChecked = settings.enableBlurredBackground
        chargingOnlyCheckbox.isChecked = settings.enableOnCharging
        alwaysEnabledCheckbox.isChecked = settings.enableAlways
        orientationFilteringCheckbox.isChecked = settings.enableOrientationFiltering
        squareImagesCheckbox.isChecked = settings.showSquareImagesInBothOrientations
        featheringCheckbox.isChecked = settings.enableFeathering

        updateSelectedPhotos()
    }

    private fun setupListeners() {
        selectPhotosButton.setOnClickListener {
            multiplePhotoPickerLauncher.launch("image/*")
        }

        slideDurationSlider.addOnChangeListener { _, value, _ ->
            updateSlideDurationText(value.toInt())
        }

        zoomAmountSlider.addOnChangeListener { _, value, _ ->
            updateZoomAmountText(value.toInt())
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

        zoomAmountSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                saveCurrentSettings()
            }
        })

        blurredBackgroundCheckbox.setOnCheckedChangeListener { _, _ -> saveCurrentSettings() }
        chargingOnlyCheckbox.setOnCheckedChangeListener { _, _ -> saveCurrentSettings() }
        alwaysEnabledCheckbox.setOnCheckedChangeListener { _, _ -> saveCurrentSettings() }
        orientationFilteringCheckbox.setOnCheckedChangeListener { _, _ ->
            saveCurrentSettings()
            updateOrientationStats()
        }
        squareImagesCheckbox.setOnCheckedChangeListener { _, _ ->
            saveCurrentSettings()
            updateOrientationStats()
        }
        featheringCheckbox.setOnCheckedChangeListener { _, _ -> saveCurrentSettings() }

        checkPermissionsButton.setOnClickListener {
            requestAllPermissions()
        }
    }

    private fun analyzeAndAddPhotos(uris: List<Uri>) {
        Toast.makeText(this, "Analyzing ${uris.size} images...", Toast.LENGTH_SHORT).show()

        analysisScope.launch {
            val newPhotos = mutableListOf<PhotoInfo>()

            // Analyze images in background thread
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    try {
                        // Take persistent permission
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )

                        // Analyze image dimensions
                        val photoInfo = analyzeImageOrientation(uri)
                        if (photoInfo != null) {
                            newPhotos.add(photoInfo)
                        }
                    } catch (e: Exception) {
                        Log.e("SettingsActivity", "Error analyzing image $uri", e)
                    }
                }
            }

            // Update UI on main thread
            if (newPhotos.isNotEmpty()) {
                photoInfoList.addAll(newPhotos)
                updateSelectedPhotos()
                saveCurrentSettings()
                updateOrientationStats()

                Toast.makeText(
                    this@SettingsActivity,
                    "Added ${newPhotos.size} images",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun analyzeImageOrientation(uri: Uri): PhotoInfo? {
        return withContext(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(inputStream, null, options)

                    val width = options.outWidth
                    val height = options.outHeight

                    if (width > 0 && height > 0) {
                        val aspectRatio = width.toFloat() / height.toFloat()
                        val orientation = OrientationUtils.classifyImageOrientation(aspectRatio)

                        Log.d("SettingsActivity", "Image $uri: ${width}x${height}, ratio: $aspectRatio, orientation: $orientation")

                        PhotoInfo(
                            uri = uri.toString(),
                            orientation = orientation,
                            aspectRatio = aspectRatio
                        )
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Error analyzing image $uri", e)
                null
            }
        }
    }

    private fun updateSlideDurationText(seconds: Int) {
        val text = if (seconds < 60) {
            "${seconds}s per slide"
        } else {
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            if (remainingSeconds == 0) {
                "${minutes}m per slide"
            } else {
                "${minutes}m ${remainingSeconds}s per slide"
            }
        }
        slideDurationText.text = text
    }

    private fun updateZoomAmountText(zoomAmount: Int) {
        zoomAmountText.text = "${100 + zoomAmount}% zoom"
    }

    private fun updateSelectedPhotos() {
        photoAdapter.updatePhotos(photoInfoList.map { it.uri }.toMutableList())
        selectPhotosButton.text = if (photoInfoList.isEmpty()) {
            "Select Photos"
        } else {
            "Add More Photos (${photoInfoList.size} selected)"
        }
    }

    private fun updateOrientationStats() {
        if (photoInfoList.isEmpty()) {
            orientationStatsText.text = "No images selected"
            return
        }

        val landscapeCount = photoInfoList.count { it.orientation == ImageOrientation.LANDSCAPE }
        val portraitCount = photoInfoList.count { it.orientation == ImageOrientation.PORTRAIT }
        val squareCount = photoInfoList.count { it.orientation == ImageOrientation.SQUARE }

        val stats = buildString {
            append("📊 Image Analysis: ")
            append("🏞️ ${landscapeCount} landscape, ")
            append("📱 ${portraitCount} portrait, ")
            append("⬜ ${squareCount} square")

            if (orientationFilteringCheckbox.isChecked) {
                appendLine()
                append("✅ Orientation filtering enabled")
                if (squareImagesCheckbox.isChecked) {
                    append(" (square images shown in both orientations)")
                }
            } else {
                appendLine()
                append("❌ Orientation filtering disabled (all images shown)")
            }
        }

        orientationStatsText.text = stats
    }

    private fun removePhoto(position: Int) {
        if (position < photoInfoList.size) {
            photoInfoList.removeAt(position)
            updateSelectedPhotos()
            saveCurrentSettings()
            updateOrientationStats()
        }
    }

    private fun saveCurrentSettings() {
        val newSettings = GallerySettings(
            isEnabled = settings.isEnabled,
            selectedPhotos = photoInfoList.map { it.uri }.toSet(),
            photoInfoList = photoInfoList,
            slideDuration = (slideDurationSlider.value * 1000).toInt(),
            orderType = OrderType.values()[orderTypeSpinner.selectedItemPosition],
            transitionType = TransitionType.values()[transitionTypeSpinner.selectedItemPosition],
            zoomType = ZoomType.values()[zoomTypeSpinner.selectedItemPosition],
            zoomAmount = zoomAmountSlider.value.toInt(),
            enableBlurredBackground = blurredBackgroundCheckbox.isChecked,
            enableOnCharging = chargingOnlyCheckbox.isChecked,
            enableAlways = alwaysEnabledCheckbox.isChecked,
            enableOrientationFiltering = orientationFilteringCheckbox.isChecked,
            showSquareImagesInBothOrientations = squareImagesCheckbox.isChecked,
            enableFeathering = featheringCheckbox.isChecked
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
        analysisScope.cancel()
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