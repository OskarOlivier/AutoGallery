// FolderScanner.kt - Handles SAF folder scanning and image analysis
package com.meerkat.slumberslide

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

class FolderScanner(private val context: Context) {

    companion object {
        private const val TAG = "FolderScanner"
        private const val MAX_IMAGES = 1000

        private val SUPPORTED_MIME_TYPES = setOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/bmp",
            "image/gif"
        )
    }

    data class ScanResult(
        val photoInfoList: List<PhotoInfo>,
        val totalFound: Int,
        val isLimited: Boolean,
        val folderName: String,
        val scanTime: Long = System.currentTimeMillis()
    )

    data class ScanProgress(
        val current: Int,
        val total: Int,
        val currentFileName: String
    )

    suspend fun scanFolder(
        folderUri: Uri,
        squareDetectionSensitivity: Float = 0.8f,
        onProgress: (ScanProgress) -> Unit = {}
    ): ScanResult = withContext(Dispatchers.IO) {

        Log.d(TAG, "Starting folder scan: $folderUri")

        try {
            if (!isFolderAccessible(folderUri)) {
                throw IllegalAccessException("Folder is no longer accessible")
            }

            val folderName = getFolderDisplayName(folderUri)
            Log.d(TAG, "Scanning folder: $folderName")

            val imageUris = getImageUrisFromFolder(folderUri) { current, total, filename ->
                onProgress(ScanProgress(current, total, filename))
            }

            Log.d(TAG, "Found ${imageUris.size} image files")

            val limitedUris = imageUris.take(MAX_IMAGES)
            val isLimited = imageUris.size > MAX_IMAGES

            if (isLimited) {
                Log.w(TAG, "Limiting to $MAX_IMAGES images (found ${imageUris.size})")
            }

            val photoInfoList = mutableListOf<PhotoInfo>()

            limitedUris.forEachIndexed { index, uri ->
                yield()

                onProgress(ScanProgress(
                    current = index + 1,
                    total = limitedUris.size,
                    currentFileName = getFileDisplayName(uri)
                ))

                try {
                    val photoInfo = analyzeImageOrientation(uri, squareDetectionSensitivity)
                    if (photoInfo != null) {
                        photoInfoList.add(photoInfo)
                        Log.v(TAG, "Successfully analyzed: ${photoInfo.uri} (${photoInfo.orientation})")
                    } else {
                        Log.w(TAG, "Failed to get dimensions for: $uri")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to analyze image: $uri", e)
                }
            }

            Log.d(TAG, "Successfully analyzed ${photoInfoList.size} images")

            ScanResult(
                photoInfoList = photoInfoList,
                totalFound = imageUris.size,
                isLimited = isLimited,
                folderName = folderName
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error scanning folder: $folderUri", e)
            throw e
        }
    }

    suspend fun isFolderAccessible(folderUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val treeDocumentId = DocumentsContract.getTreeDocumentId(folderUri)

            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(
                folderUri,
                treeDocumentId
            )

            resolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null
            )?.use { cursor ->
                return@withContext cursor.count >= 0
            }

            false
        } catch (e: Exception) {
            Log.w(TAG, "Folder not accessible: $folderUri", e)
            false
        }
    }

    private suspend fun getFolderDisplayName(folderUri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val treeDocumentId = DocumentsContract.getTreeDocumentId(folderUri)
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, treeDocumentId)

            resolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    return@withContext cursor.getString(nameIndex) ?: "Unknown Folder"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get folder name for: $folderUri", e)
        }

        // Fallback: extract from URI path
        try {
            val path = folderUri.toString()
            val decoded = java.net.URLDecoder.decode(path, "UTF-8")
            decoded.substringAfterLast("/").substringAfterLast(":")
        } catch (e: Exception) {
            "Unknown Folder"
        }
    }

    private suspend fun getFileDisplayName(fileUri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            resolver.query(
                fileUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    return@withContext cursor.getString(nameIndex) ?: "Unknown File"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get file name for: $fileUri", e)
        }

        fileUri.lastPathSegment ?: "Unknown File"
    }

    private suspend fun getImageUrisFromFolder(
        folderUri: Uri,
        onProgress: (current: Int, total: Int, filename: String) -> Unit
    ): List<Uri> = withContext(Dispatchers.IO) {

        val imageUris = mutableListOf<Uri>()
        val foldersToScan = mutableListOf<String>()

        // Start with the root folder
        val rootDocumentId = DocumentsContract.getTreeDocumentId(folderUri)
        foldersToScan.add(rootDocumentId)
        Log.d(TAG, "Starting scan with root document ID: $rootDocumentId")

        var filesProcessed = 0

        while (foldersToScan.isNotEmpty()) {
            yield()

            val currentDocumentId = foldersToScan.removeAt(0)
            Log.d(TAG, "Scanning document ID: $currentDocumentId")

            try {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    folderUri,
                    currentDocumentId
                )
                Log.v(TAG, "Built children URI: $childrenUri")

                val resolver = context.contentResolver
                resolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    Log.d(TAG, "Found ${cursor.count} items in document ID: $currentDocumentId")

                    while (cursor.moveToNext()) {
                        yield()

                        val documentId = cursor.getString(0)
                        val displayName = cursor.getString(1) ?: "Unknown"
                        val mimeType = cursor.getString(2)

                        onProgress(++filesProcessed, -1, displayName)
                        Log.v(TAG, "Processing: $displayName (MIME: $mimeType)")

                        if (mimeType != null) {
                            when {
                                SUPPORTED_MIME_TYPES.contains(mimeType.lowercase()) -> {
                                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                                        folderUri,
                                        documentId
                                    )
                                    imageUris.add(documentUri)
                                    Log.d(TAG, "Added image: $displayName (${imageUris.size}/$MAX_IMAGES)")

                                    if (imageUris.size >= MAX_IMAGES) {
                                        Log.d(TAG, "Reached maximum image limit, stopping scan")
                                        return@withContext imageUris
                                    }
                                }

                                mimeType == DocumentsContract.Document.MIME_TYPE_DIR -> {
                                    foldersToScan.add(documentId)
                                    Log.d(TAG, "Added subfolder to scan queue: $displayName")
                                }

                                else -> {
                                    Log.v(TAG, "Skipping non-image file: $displayName (MIME: $mimeType)")
                                }
                            }
                        }
                    }
                } ?: Log.w(TAG, "Query returned null cursor for document ID: $currentDocumentId")

            } catch (e: Exception) {
                Log.w(TAG, "Error scanning folder with document ID: $currentDocumentId", e)
            }
        }

        Log.d(TAG, "Folder scan complete. Found ${imageUris.size} images")
        imageUris
    }

    private suspend fun analyzeImageOrientation(imageUri: Uri, squareDetectionSensitivity: Float): PhotoInfo? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                val width = options.outWidth
                val height = options.outHeight

                if (width > 0 && height > 0) {
                    val aspectRatio = width.toFloat() / height.toFloat()
                    val orientation = OrientationUtils.classifyImageOrientation(aspectRatio, squareDetectionSensitivity)

                    Log.d(TAG, "Image $imageUri: ${width}x${height}, ratio: $aspectRatio, sensitivity: $squareDetectionSensitivity, orientation: $orientation")

                    PhotoInfo(
                        uri = imageUri.toString(),
                        orientation = orientation,
                        aspectRatio = aspectRatio
                    )
                } else {
                    Log.w(TAG, "Could not determine dimensions for: $imageUri")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error analyzing image: $imageUri", e)
            null
        }
    }

    suspend fun getDefaultPicturesFolderSuggestion(): String = withContext(Dispatchers.IO) {
        try {
            "Please navigate to Pictures folder"
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine Pictures folder", e)
            "Please select your photos folder"
        }
    }
}