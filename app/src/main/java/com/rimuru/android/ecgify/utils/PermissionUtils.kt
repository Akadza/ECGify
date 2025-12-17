package com.rimuru.android.ecgify.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object PermissionUtils {

    fun isCameraGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun isStorageGranted(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun requestCamera(
        launcher: ActivityResultLauncher<String>,
        activity: Activity,
        onDenied: (() -> Unit)? = null
    ) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)) {
            showRationale(activity, "Камера нужна для съёмки ЭКГ", launcher, Manifest.permission.CAMERA)
        } else {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    fun requestStorage(
        launcher: ActivityResultLauncher<String>,
        activity: Activity,
        onDenied: (() -> Unit)? = null
    ) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            showRationale(activity, "Доступ к галерее нужен для выбора фото ЭКГ", launcher, permission)
        } else {
            launcher.launch(permission)
        }
    }

    private fun showRationale(
        activity: Activity,
        message: String,
        launcher: ActivityResultLauncher<String>,
        permission: String
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Разрешение требуется")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> launcher.launch(permission) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }
}