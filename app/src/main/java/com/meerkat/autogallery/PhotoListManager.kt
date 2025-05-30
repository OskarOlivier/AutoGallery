// PhotoListManager.kt - Handles photo filtering, sorting, and navigation
package com.meerkat.autogallery

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

class PhotoListManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {

    private lateinit var settings: GallerySettings
    private var allPhotoList = mutableListOf<PhotoInfo>()
    private var currentPhotoList = mutableListOf<PhotoInfo>()
    private var currentPhotoIndex = 0
    private var currentDeviceOrientation: ImageOrientation = ImageOrientation.LANDSCAPE

    companion object {
        private const val TAG = "PhotoListManager"
    }

    fun loadAndFilterPhotos() {
        settings = preferencesManager.loadSettings()
        allPhotoList = settings.photoInfoList.toMutableList()

        // Backward compatibility for old photo format
        if (allPhotoList.isEmpty() && settings.selectedPhotos.isNotEmpty()) {
            allPhotoList = settings.selectedPhotos.map { uri ->
                PhotoInfo(
                    uri = uri,
                    orientation = ImageOrientation.SQUARE,
                    aspectRatio = 1.0f
                )
            }.toMutableList()
        }

        Log.d(TAG, "Loaded ${allPhotoList.size} total photos")
        updateDeviceOrientation()
        filterPhotosByOrientation()

        if (currentPhotoList.isEmpty()) {
            Log.w(TAG, "No photos available for current orientation")
            return
        }

        sortPhotoList()
        Log.d(TAG, "Using ${currentPhotoList.size} photos for current orientation ($currentDeviceOrientation)")
    }

    fun handleOrientationChange() {
        updateDeviceOrientation()
        val oldPhotoCount = currentPhotoList.size
        filterPhotosByOrientation()

        if (currentPhotoList.isNotEmpty()) {
            sortPhotoList()
            currentPhotoIndex = 0
        }

        Log.d(TAG, "Orientation change: $oldPhotoCount -> ${currentPhotoList.size} photos")
    }

    private fun updateDeviceOrientation() {
        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        currentDeviceOrientation = OrientationUtils.getCurrentDeviceOrientation(screenWidth, screenHeight)
        Log.d(TAG, "Device orientation: $currentDeviceOrientation (${screenWidth}x${screenHeight})")
    }

    private fun filterPhotosByOrientation() {
        currentPhotoList = if (settings.enableOrientationFiltering) {
            allPhotoList.filter { photoInfo ->
                OrientationUtils.shouldShowImage(
                    photoInfo.orientation,
                    currentDeviceOrientation,
                    settings.showSquareImagesInBothOrientations
                )
            }.toMutableList()
        } else {
            allPhotoList.toMutableList()
        }

        Log.d(TAG, "Filtered from ${allPhotoList.size} to ${currentPhotoList.size} photos for orientation $currentDeviceOrientation")
    }

    private fun sortPhotoList() {
        when (settings.orderType) {
            OrderType.RANDOM -> currentPhotoList.shuffle()
            OrderType.ALPHABETICAL -> currentPhotoList.sortBy { it.uri }
            OrderType.DATE_MODIFIED -> sortByDateModified()
            OrderType.DATE_CREATED -> sortByDateCreated()
        }
    }

    private fun sortByDateModified() {
        currentPhotoList.sortBy { photoInfo ->
            try {
                val uri = Uri.parse(photoInfo.uri)
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.Images.Media.DATE_MODIFIED),
                    null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        it.getLong(0)
                    } else 0L
                } ?: 0L
            } catch (e: Exception) {
                Log.w(TAG, "Error getting date modified for ${photoInfo.uri}", e)
                0L
            }
        }
    }

    private fun sortByDateCreated() {
        currentPhotoList.sortBy { photoInfo ->
            try {
                val uri = Uri.parse(photoInfo.uri)
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.Images.Media.DATE_ADDED),
                    null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        it.getLong(0)
                    } else 0L
                } ?: 0L
            } catch (e: Exception) {
                Log.w(TAG, "Error getting date created for ${photoInfo.uri}", e)
                0L
            }
        }
    }

    fun moveToNext() {
        currentPhotoIndex = (currentPhotoIndex + 1) % currentPhotoList.size
    }

    fun moveToPrevious() {
        currentPhotoIndex = if (currentPhotoIndex == 0) {
            currentPhotoList.size - 1
        } else {
            currentPhotoIndex - 1
        }
    }

    fun getCurrentPhoto(): PhotoInfo {
        return if (currentPhotoList.isNotEmpty()) {
            currentPhotoList[currentPhotoIndex]
        } else {
            PhotoInfo("", ImageOrientation.SQUARE, 1.0f)
        }
    }

    fun getCurrentPhotoList(): List<PhotoInfo> = currentPhotoList

    fun getCurrentIndex(): Int = currentPhotoIndex

    fun getSettings(): GallerySettings = settings
}