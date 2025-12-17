package com.rimuru.android.ecgify.ui.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import com.labters.documentscanner.DocumentScannerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.rimuru.android.ecgify.databinding.BottomSheetChooseSourceBinding
import com.rimuru.android.ecgify.utils.ImageUtils
import java.io.File

class SourceBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetChooseSourceBinding? = null
    private val binding get() = _binding!!

    private var tempUri: Uri? = null

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) sendUriToManual(tempUri!!)
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) sendUriToManual(uri)
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
            tempUri = ImageUtils.createTempImageUri(requireContext())
            cameraLauncher.launch(tempUri)
            dismiss()
        }

        binding.optionGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
            dismiss()
        }
    }

    private fun sendUriToManual(uri: Uri) {
        val action = HomeFragmentDirections
            .actionHomeFragmentToManualSelectionFragment(uri.toString())

        (parentFragment as? NavHostFragment)
            ?.navController
            ?.navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
