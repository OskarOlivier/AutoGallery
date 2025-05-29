// PreferencesManager.kt
package com.meerkat.autogallery

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("auto_gallery_prefs", Context.MODE_PRIVATE)

    fun saveSettings(settings: GallerySettings) {
        prefs.edit().apply {
            putBoolean("is_enabled", settings.isEnabled)

            // Save old format for backward compatibility
            putStringSet("selected_photos", settings.selectedPhotos)

            // Save new photo info list as JSON
            putString("photo_info_list", photoInfoListToJson(settings.photoInfoList))

            putInt("slide_duration", settings.slideDuration)
            putString("order_type", settings.orderType.name)
            putString("transition_type", settings.transitionType.name)
            putString("zoom_type", settings.zoomType.name)
            putInt("zoom_amount", settings.zoomAmount)
            putBoolean("enable_blurred_background", settings.enableBlurredBackground)
            putBoolean("enable_on_charging", settings.enableOnCharging)
            putBoolean("enable_always", settings.enableAlways)
            putBoolean("enable_orientation_filtering", settings.enableOrientationFiltering)
            putBoolean("show_square_images_in_both_orientations", settings.showSquareImagesInBothOrientations)
            apply()
        }
    }

    fun loadSettings(): GallerySettings {
        // Load photo info list first (new format)
        val photoInfoListJson = prefs.getString("photo_info_list", null)
        val photoInfoList = if (photoInfoListJson != null) {
            photoInfoListFromJson(photoInfoListJson)
        } else {
            // Fallback to old format for backward compatibility
            val oldPhotos = prefs.getStringSet("selected_photos", emptySet()) ?: emptySet()
            // Convert old format to new format (without orientation info)
            oldPhotos.map { uri ->
                PhotoInfo(
                    uri = uri,
                    orientation = ImageOrientation.SQUARE, // Default to square for old data
                    aspectRatio = 1.0f
                )
            }
        }

        // Also maintain the old selectedPhotos set for backward compatibility
        val selectedPhotos = photoInfoList.map { it.uri }.toSet()

        return GallerySettings(
            isEnabled = prefs.getBoolean("is_enabled", false),
            selectedPhotos = selectedPhotos,
            photoInfoList = photoInfoList,
            slideDuration = prefs.getInt("slide_duration", 5000),
            orderType = try {
                OrderType.valueOf(prefs.getString("order_type", OrderType.RANDOM.name)!!)
            } catch (e: Exception) {
                OrderType.RANDOM
            },
            transitionType = try {
                TransitionType.valueOf(prefs.getString("transition_type", TransitionType.FADE.name)!!)
            } catch (e: Exception) {
                TransitionType.FADE
            },
            zoomType = try {
                ZoomType.valueOf(prefs.getString("zoom_type", ZoomType.SAWTOOTH.name)!!)
            } catch (e: Exception) {
                ZoomType.SAWTOOTH
            },
            zoomAmount = prefs.getInt("zoom_amount", 3).coerceIn(0, 5),
            enableBlurredBackground = prefs.getBoolean("enable_blurred_background", true),
            enableOnCharging = prefs.getBoolean("enable_on_charging", false),
            enableAlways = prefs.getBoolean("enable_always", true),
            enableOrientationFiltering = prefs.getBoolean("enable_orientation_filtering", true),
            showSquareImagesInBothOrientations = prefs.getBoolean("show_square_images_in_both_orientations", true)
        )
    }

    private fun photoInfoListToJson(photoInfoList: List<PhotoInfo>): String {
        val jsonArray = JSONArray()
        photoInfoList.forEach { photoInfo ->
            val jsonObject = JSONObject().apply {
                put("uri", photoInfo.uri)
                put("orientation", photoInfo.orientation.name)
                put("aspectRatio", photoInfo.aspectRatio)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }

    private fun photoInfoListFromJson(json: String): List<PhotoInfo> {
        return try {
            val jsonArray = JSONArray(json)
            val photoInfoList = mutableListOf<PhotoInfo>()

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val photoInfo = PhotoInfo(
                    uri = jsonObject.getString("uri"),
                    orientation = try {
                        ImageOrientation.valueOf(jsonObject.getString("orientation"))
                    } catch (e: Exception) {
                        ImageOrientation.SQUARE
                    },
                    aspectRatio = jsonObject.optDouble("aspectRatio", 1.0).toFloat()
                )
                photoInfoList.add(photoInfo)
            }

            photoInfoList
        } catch (e: Exception) {
            emptyList()
        }
    }
}