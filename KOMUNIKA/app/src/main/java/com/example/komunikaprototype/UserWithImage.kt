package com.example.komunikaprototype

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Class to store user information with profile image
 */
data class UserWithImage(
    val username: String,
    var profileImageBase64: String? = null,
    val endpointId: String? = null,
    val role: String = ""
) {
    // Decode base64 string to bitmap
    fun getProfileBitmap(): Bitmap? {
        return if (profileImageBase64 != null) {
            try {
                val decodedBytes = Base64.decode(profileImageBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    companion object {
        // Convert bitmap to base64 string for transmission
        fun bitmapToBase64(bitmap: Bitmap): String {
            val outputStream = ByteArrayOutputStream()
            // Compress and resize the bitmap to reduce size
            val resizedBitmap = getResizedBitmap(bitmap, 150)
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()
            return Base64.encodeToString(byteArray, Base64.DEFAULT)
        }

        // Resize bitmap to prevent large data transfers
        private fun getResizedBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
            var width = bitmap.width
            var height = bitmap.height
            
            val bitmapRatio = width.toFloat() / height.toFloat()
            if (bitmapRatio > 1) {
                // Width is greater than height
                width = maxSize
                height = (width / bitmapRatio).toInt()
            } else {
                // Height is greater than width
                height = maxSize
                width = (height * bitmapRatio).toInt()
            }
            
            return Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
    }
} 