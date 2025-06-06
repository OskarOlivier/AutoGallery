// SettingsActivity.kt
package com.meerkat.slumberslide

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView
import android.widget.Spinner
import kotlinx.coroutines.*
import kotlin.math.round

class SettingsActivity : AppCompatActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var settings: SlideshowSettings
    private lateinit var folderScanner: FolderScanner

    private lateinit var selectFolderButton: MaterialButton
    private lateinit var refreshFolderButton: MaterialButton
    private lateinit var folderInfoText: MaterialTextView
    private lateinit var scanProgressIndicator: CircularProgressIndicator
    private lateinit var scanProgressText: MaterialTextView
    private lateinit var slideDurationSlider: Slider
    private lateinit var slideDurationText: MaterialTextView
    private lateinit var orderTypeSpinner: Spinner
    private lateinit var transitionTypeSpinner: Spinner
    private lateinit var zoomTypeSpinner: Spinner
    private lateinit var zoomAmountSlider: Slider
    private lateinit var zoomAmountText: MaterialTextView
    private lateinit var blurredBackgroundSwitch: SwitchMaterial
    private lateinit var batteryManagementRadioGroup: RadioGroup
    private lateinit var chargingOnlyRadio: MaterialRadioButton
    private lateinit var batteryLevelOnlyRadio: MaterialRadioButton
    private lateinit var orientationFilteringSwitch: SwitchMaterial
    private lateinit var squareDetectionSlider: Slider
    private lateinit var squareDetectionText: MaterialTextView
    private lateinit var featheringSlider: Slider
    private lateinit var featheringText: MaterialTextView
    private lateinit var slideshowBrightnessSlider: Slider
    private lateinit var slideshowBrightnessText: MaterialTextView
    private lateinit var checkPermissionsButton: MaterialButton
    private lateinit var permissionStatusText: MaterialTextView
    private lateinit var orientationStatsText: MaterialTextView

    private val scanningScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { selectedFolderUri ->
            contentResolver.takePersistableUriPermission(
                selectedFolderUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            scanSelectedFolder(selectedFolderUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Auto Gallery Settings"

        preferencesManager = PreferencesManager(this)
        folderScanner = FolderScanner(this)
        settings = preferencesManager.loadSettings()

        initViews()
        setupSpinners()
        loadCurrentSettings()
        setupListeners()
        checkPermissionStatus()
        updateFolderInfo()
        updateOrientationStats()
    }

    private fun initViews() {
        selectFolderButton = findViewById(R.id.selectFolderButton)
        refreshFolderButton = findViewById(R.id.refreshFolderButton)
        folderInfoText = findViewById(R.id.folderInfoText)
        scanProgressIndicator = findViewById(R.id.scanProgressIndicator)
        scanProgressText = findViewById(R.id.scanProgressText)
        slideDurationSlider = findViewById(R.id.slideDurationSlider)
        slideDurationText = findViewById(R.id.slideDurationText)
        orderTypeSpinner = findViewById(R.id.orderTypeSpinner)
        transitionTypeSpinner = findViewById(R.id.transitionTypeSpinner)
        zoomTypeSpinner = findViewById(R.id.zoomTypeSpinner)
        zoomAmountSlider = findViewById(R.id.zoomAmountSlider)
        zoomAmountText = findViewById(R.id.zoomAmountText)
        blurredBackgroundSwitch = findViewById(R.id.blurredBackgroundSwitch)
        batteryManagementRadioGroup = findViewById(R.id.batteryManagementRadioGroup)
        chargingOnlyRadio = findViewById(R.id.chargingOnlyRadio)
        batteryLevelOnlyRadio = findViewById(R.id.batteryLevelOnlyRadio)
        orientationFilteringSwitch = findViewById(R.id.orientationFilteringSwitch)
        squareDetectionSlider = findViewById(R.id.squareDetectionSlider)
        squareDetectionText = findViewById(R.id.squareDetectionText)
        featheringSlider = findViewById(R.id.featheringSlider)
        featheringText = findViewById(R.id.featheringText)
        slideshowBrightnessSlider = findViewById(R.id.slideshowBrightnessSlider)
        slideshowBrightnessText = findViewById(R.id.slideshowBrightnessText)
        checkPermissionsButton = findViewById(R.id.checkPermissionsButton)
        permissionStatusText = findViewById(R.id.permissionStatusText)
        orientationStatsText = findViewById(R.id.orientationStatsText)

        scanProgressIndicator.visibility = View.GONE
        scanProgressText.visibility = View.GONE
    }

    private fun setupSpinners() {
        val orderTypes = OrderType.values().map { it.displayName }
        val orderAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, orderTypes)
        orderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        orderTypeSpinner.adapter = orderAdapter

        val transitionTypes = TransitionType.values().map { it.displayName }
        val transitionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, transitionTypes)
        transitionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        transitionTypeSpinner.adapter = transitionAdapter

        val zoomTypes = ZoomType.values().map { it.displayName }
        val zoomAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, zoomTypes)
        zoomAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        zoomTypeSpinner.adapter = zoomAdapter
    }

    /**
     * Helper function to round a value to the nearest valid step for a slider
     * This prevents crashes when loading saved values that don't align with step sizes
     */
    private fun roundToSliderStep(value: Float, valueFrom: Float, stepSize: Float): Float {
        val steps = round((value - valueFrom) / stepSize)
        return valueFrom + (steps * stepSize)
    }

    private fun loadCurrentSettings() {
        slideDurationSlider.value = (settings.slideDuration / 1000).toFloat()
        updateSlideDurationText(settings.slideDuration / 1000)

        orderTypeSpinner.setSelection(settings.orderType.ordinal)
        transitionTypeSpinner.setSelection(settings.transitionType.ordinal)
        zoomTypeSpinner.setSelection(settings.zoomType.ordinal)

        zoomAmountSlider.value = settings.zoomAmount.toFloat()
        updateZoomAmountText(settings.zoomAmount)

        blurredBackgroundSwitch.isChecked = settings.enableBlurredBackground

        when (settings.batteryManagementMode) {
            BatteryManagementMode.CHARGING_ONLY -> chargingOnlyRadio.isChecked = true
            BatteryManagementMode.BATTERY_LEVEL_ONLY -> batteryLevelOnlyRadio.isChecked = true
        }

        orientationFilteringSwitch.isChecked = settings.enableOrientationFiltering

        // Round square detection sensitivity to nearest valid step (0.05)
        val roundedSquareSensitivity = roundToSliderStep(settings.squareDetectionSensitivity, 0.5f, 0.05f)
        squareDetectionSlider.value = roundedSquareSensitivity
        updateSquareDetectionText(roundedSquareSensitivity)

        // Round feathering amount to nearest valid step (5.0)
        val roundedFeathering = roundToSliderStep(settings.featheringAmount, 0f, 5f)
        featheringSlider.value = roundedFeathering
        updateFeatheringText(roundedFeathering)

        // Round slideshow brightness to nearest valid step (0.05)
        val roundedBrightness = roundToSliderStep(settings.slideshowBrightness, 0.1f, 0.05f)
        slideshowBrightnessSlider.value = roundedBrightness
        updateSlideshowBrightnessText(roundedBrightness)
    }

    private fun setupListeners() {
        selectFolderButton.setOnClickListener {
            showFolderSelectionTip()
        }

        refreshFolderButton.setOnClickListener {
            refreshFolder()
        }

        slideDurationSlider.addOnChangeListener { _, value, _ ->
            updateSlideDurationText(value.toInt())
        }

        zoomAmountSlider.addOnChangeListener { _, value, _ ->
            updateZoomAmountText(value.toInt())
        }

        squareDetectionSlider.addOnChangeListener { _, value, _ ->
            updateSquareDetectionText(value)
        }

        featheringSlider.addOnChangeListener { _, value, _ ->
            updateFeatheringText(value)
        }

        slideshowBrightnessSlider.addOnChangeListener { _, value, _ ->
            updateSlideshowBrightnessText(value)
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

        squareDetectionSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                saveCurrentSettings()
                updateOrientationStats()
            }
        })

        featheringSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                saveCurrentSettings()
            }
        })

        slideshowBrightnessSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                saveCurrentSettings()
            }
        })

        blurredBackgroundSwitch.setOnCheckedChangeListener { _, _ -> saveCurrentSettings() }
        batteryManagementRadioGroup.setOnCheckedChangeListener { _, _ -> saveCurrentSettings() }
        orientationFilteringSwitch.setOnCheckedChangeListener { _, _ ->
            saveCurrentSettings()
            updateOrientationStats()
        }

        checkPermissionsButton.setOnClickListener {
            requestAllPermissions()
        }
    }

    private fun showFolderSelectionTip() {
        Toast.makeText(
            this,
            "Please navigate to your Pictures folder or another folder containing photos",
            Toast.LENGTH_LONG
        ).show()

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            folderPickerLauncher.launch(null)
        }, 1000)
    }

    private fun scanSelectedFolder(folderUri: Uri) {
        scanningScope.launch {
            try {
                showScanProgress(true)

                val scanResult = folderScanner.scanFolder(folderUri, settings.squareDetectionSensitivity) { progress ->
                    lifecycleScope.launch {
                        updateScanProgress(progress)
                    }
                }

                val newFolderInfo = FolderInfo(
                    uri = folderUri.toString(),
                    displayName = scanResult.folderName,
                    lastScanTime = scanResult.scanTime,
                    totalImagesFound = scanResult.totalFound,
                    isLimited = scanResult.isLimited
                )

                settings = settings.copy(
                    folderInfo = newFolderInfo,
                    photoInfoList = scanResult.photoInfoList
                )

                saveCurrentSettings()
                updateFolderInfo()
                updateOrientationStats()

                val message = buildString {
                    append("Scan complete! ")
                    append("Found ${scanResult.photoInfoList.size} usable images")
                    if (scanResult.isLimited) {
                        append(" (limited to 1000)")
                    }
                }
                Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Log.e("SettingsActivity", "Error scanning folder", e)
                Toast.makeText(
                    this@SettingsActivity,
                    "Error scanning folder: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                showScanProgress(false)
            }
        }
    }

    private fun refreshFolder() {
        if (settings.folderInfo.uri.isEmpty()) {
            Toast.makeText(this, "No folder selected", Toast.LENGTH_SHORT).show()
            return
        }

        val folderUri = Uri.parse(settings.folderInfo.uri)
        scanSelectedFolder(folderUri)
    }

    private fun showScanProgress(show: Boolean) {
        if (show) {
            scanProgressIndicator.visibility = View.VISIBLE
            scanProgressText.visibility = View.VISIBLE
            selectFolderButton.isEnabled = false
            refreshFolderButton.isEnabled = false
        } else {
            scanProgressIndicator.visibility = View.GONE
            scanProgressText.visibility = View.GONE
            selectFolderButton.isEnabled = true
            refreshFolderButton.isEnabled = true
        }
    }

    private fun updateScanProgress(progress: FolderScanner.ScanProgress) {
        val text = if (progress.total > 0) {
            "Scanning: ${progress.current}/${progress.total}\n${progress.currentFileName}"
        } else {
            "Scanning: ${progress.current} files\n${progress.currentFileName}"
        }
        scanProgressText.text = text
    }

    private fun updateFolderInfo() {
        val folderInfo = settings.folderInfo

        if (folderInfo.uri.isEmpty()) {
            folderInfoText.text = "No folder selected\n\nPlease select a folder containing your photos"
            refreshFolderButton.isEnabled = false
            return
        }

        val timeText = if (folderInfo.lastScanTime > 0) {
            val timeAgo = System.currentTimeMillis() - folderInfo.lastScanTime
            when {
                timeAgo < 60000 -> "just now"
                timeAgo < 3600000 -> "${timeAgo / 60000} minutes ago"
                timeAgo < 86400000 -> "${timeAgo / 3600000} hours ago"
                else -> "${timeAgo / 86400000} days ago"
            }
        } else {
            "never"
        }

        val infoText = buildString {
            append("📁 ${folderInfo.displayName}\n")
            append("🕒 Last scanned: $timeText\n")
            append("📸 Images found: ${settings.photoInfoList.size}")

            if (folderInfo.isLimited) {
                append(" (limited from ${folderInfo.totalImagesFound})")
            } else if (folderInfo.totalImagesFound != settings.photoInfoList.size) {
                append(" (${folderInfo.totalImagesFound} total files)")
            }
        }

        folderInfoText.text = infoText
        refreshFolderButton.isEnabled = true
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

    private fun updateSquareDetectionText(sensitivity: Float) {
        val percentage = (sensitivity * 100).toInt()
        squareDetectionText.text = "Sensitivity: $percentage% (${if (sensitivity > 0.75f) "Strict" else "Relaxed"})"
    }

    private fun updateZoomAmountText(zoomAmount: Int) {
        zoomAmountText.text = "${100 + zoomAmount}% zoom"
    }

    private fun updateFeatheringText(featheringAmount: Float) {
        val amount = featheringAmount.toInt()
        featheringText.text = if (amount == 0) {
            "No feathering"
        } else {
            "${amount}px feathering"
        }
    }

    private fun updateSlideshowBrightnessText(brightness: Float) {
        val percentage = (brightness * 100).toInt()
        slideshowBrightnessText.text = "$percentage% brightness"
    }

    private fun updateOrientationStats() {
        val photoInfoList = settings.photoInfoList

        if (photoInfoList.isEmpty()) {
            orientationStatsText.text = "No images scanned"
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

            if (!orientationFilteringSwitch.isChecked) {
                appendLine()
                append("❌ Orientation filtering disabled (all images shown)")
            }
        }

        orientationStatsText.text = stats
    }

    private fun saveCurrentSettings() {
        val batteryManagementMode = when (batteryManagementRadioGroup.checkedRadioButtonId) {
            R.id.chargingOnlyRadio -> BatteryManagementMode.CHARGING_ONLY
            R.id.batteryLevelOnlyRadio -> BatteryManagementMode.BATTERY_LEVEL_ONLY
            else -> BatteryManagementMode.CHARGING_ONLY
        }

        val newSettings = settings.copy(
            slideDuration = (slideDurationSlider.value * 1000).toInt(),
            orderType = OrderType.values()[orderTypeSpinner.selectedItemPosition],
            transitionType = TransitionType.values()[transitionTypeSpinner.selectedItemPosition],
            zoomType = ZoomType.values()[zoomTypeSpinner.selectedItemPosition],
            zoomAmount = zoomAmountSlider.value.toInt(),
            enableBlurredBackground = blurredBackgroundSwitch.isChecked,
            batteryManagementMode = batteryManagementMode,
            enableOrientationFiltering = orientationFilteringSwitch.isChecked,
            squareDetectionSensitivity = squareDetectionSlider.value,
            featheringAmount = featheringSlider.value,
            slideshowBrightness = slideshowBrightnessSlider.value
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
        scanningScope.cancel()
        saveCurrentSettings()
    }

    private fun checkPermissionStatus() {
        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required on older versions
        }

        val hasOverlayPermission = Settings.canDrawOverlays(this)

        val status = when {
            hasNotificationPermission && hasOverlayPermission -> "✅ All permissions granted"
            !hasNotificationPermission -> "⚠️ Notification permission recommended (for service status)"
            !hasOverlayPermission -> "❌ Overlay permission required"
            else -> "⚠️ Some permissions missing"
        }

        permissionStatusText.text = status
    }

    private fun requestAllPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
            return
        }

        // Only check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        Toast.makeText(this, "All permissions already granted!", Toast.LENGTH_SHORT).show()
        checkPermissionStatus()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        checkPermissionStatus()
        if (granted) {
            Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notification permission denied. Service status won't be shown in notification.", Toast.LENGTH_LONG).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissionStatus()
        if (Settings.canDrawOverlays(this)) {
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