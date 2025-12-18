package com.rimuru.android.ecgify.utils

import android.content.Context
import android.os.Environment
import java.io.File

object FileUtils {
    fun getOutputDirectory(context: Context): File {
        return if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ECGify")
        } else {
            File(context.filesDir, "ECGify")
        }.apply {
            if (!exists()) mkdirs()
        }
    }
}