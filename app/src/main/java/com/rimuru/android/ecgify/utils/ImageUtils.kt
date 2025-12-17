package com.rimuru.android.ecgify.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageUtils {

    fun createTempImageFile(context: Context): File {
        val fileName = "ecg_temp_${UUID.randomUUID()}.jpg"
        return File(context.cacheDir, fileName)
    }

    fun createTempImageUri(context: Context): Uri {
        val file = createTempImageFile(context)
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri? {
        return try {
            val file = File(context.cacheDir, "ecg_scanned_${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}