package com.rimuru.android.ecgify.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rimuru.android.ecgify.databinding.BottomSheetChooseSourceBinding
import com.rimuru.android.ecgify.utils.ImageUtils
import com.rimuru.android.ecgify.utils.PermissionUtils

class SourceBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetChooseSourceBinding? = null
    private val binding get() = _binding!!

    private var tempUri: Uri? = null

    // Создаем callback для передачи URI
    private var onImageSelected: ((Uri) -> Unit)? = null

    fun setOnImageSelectedListener(listener: (Uri) -> Unit) {
        onImageSelected = listener
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            tempUri = ImageUtils.createTempImageUri(requireContext())
            cameraLauncher.launch(tempUri)
        } else {
            showPermissionDeniedDialog("Камера")
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            galleryLauncher.launch("image/*")
        } else {
            showPermissionDeniedDialog("Хранилище")
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempUri != null) {
            onImageSelected?.invoke(tempUri!!)
            dismiss()
        } else {
            Toast.makeText(requireContext(), "Не удалось сделать фото", Toast.LENGTH_SHORT).show()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onImageSelected?.invoke(uri)
            dismiss()
        } else {
            Toast.makeText(requireContext(), "Не удалось выбрать изображение", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetChooseSourceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.optionCamera.setOnClickListener {
            if (PermissionUtils.isCameraGranted(requireContext())) {
                tempUri = ImageUtils.createTempImageUri(requireContext())
                cameraLauncher.launch(tempUri)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        binding.optionGallery.setOnClickListener {
            val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

            if (PermissionUtils.isStorageGranted(requireContext())) {
                galleryLauncher.launch("image/*")
            } else {
                storagePermissionLauncher.launch(permission)
            }
        }
    }

    private fun showPermissionDeniedDialog(permissionName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Разрешение отклонено")
            .setMessage("Для работы с $permissionName необходимо предоставить разрешение в настройках приложения")
            .setPositiveButton("Настройки") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}