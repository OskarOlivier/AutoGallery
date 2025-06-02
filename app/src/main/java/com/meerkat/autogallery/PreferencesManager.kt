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
            putString("folder_info", folderInfoToJson(settings.folderInfo))
            putString("photo_info_list", photoInfoListToJson(settings.photoInfoList))
            putInt("slide_duration", settings.slideDuration)
            putString("order_type", settings.orderType.name)
            putString("transition_type", settings.transitionType.name)
            putString("zoom_type", settings.zoomType.name)
            putInt("zoom_amount", settings.zoomAmount)
            putBoolean("enable_blurred_background", settings.enableBlurredBackground)
            putString("battery_management_mode", settings.batteryManagementMode.name)
            putBoolean("enable_orientation_filtering", settings.enableOrientationFiltering)
            putFloat("square_detection_sensitivity", settings.squareDetectionSensitivity)
            putBoolean("enable_feathering", settings.enableFeathering)
            apply()
        }
    }

    fun loadSettings(): GallerySettings {
        val folderInfoJson = prefs.getString("folder_info", null)
        val folderInfo = if (folderInfoJson != null) {
            folderInfoFromJson(folderInfoJson)
        } else {
            FolderInfo()
        }

        val photoInfoListJson = prefs.getString("photo_info_list", null)
        val photoInfoList = if (photoInfoListJson != null) {
            photoInfoListFromJson(photoInfoListJson)
        } else {
            emptyList()
        }

        val batteryManagementMode = try {
            BatteryManagementMode.valueOf(
                prefs.getString("battery_management_mode", BatteryManagementMode.CHARGING_ONLY.name)!!
            )
        } catch (e: Exception) {
            BatteryManagementMode.CHARGING_ONLY
        }

        return GallerySettings(
            isEnabled = prefs.getBoolean("is_enabled", false),
            photoInfoList = photoInfoList,
            folderInfo = folderInfo,
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
            batteryManagementMode = batteryManagementMode,
            enableOrientationFiltering = prefs.getBoolean("enable_orientation_filtering", true),
            squareDetectionSensitivity = prefs.getFloat("square_detection_sensitivity", 0.8f).coerceIn(0.5f, 1.0f),
            enableFeathering = prefs.getBoolean("enable_feathering", true)
        )
    }

    private fun folderInfoToJson(folderInfo: FolderInfo): String {
        val jsonObject = JSONObject().apply {
            put("uri", folderInfo.uri)
            put("displayName", folderInfo.displayName)
            put("lastScanTime", folderInfo.lastScanTime)
            put("totalImagesFound", folderInfo.totalImagesFound)
            put("isLimited", folderInfo.isLimited)
        }
        return jsonObject.toString()
    }

    private fun folderInfoFromJson(json: String): FolderInfo {
        return try {
            val jsonObject = JSONObject(json)
            FolderInfo(
                uri = jsonObject.optString("uri", ""),
                displayName = jsonObject.optString("displayName", ""),
                lastScanTime = jsonObject.optLong("lastScanTime", 0L),
                totalImagesFound = jsonObject.optInt("totalImagesFound", 0),
                isLimited = jsonObject.optBoolean("isLimited", false)
            )
        } catch (e: Exception) {
            FolderInfo()
        }
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