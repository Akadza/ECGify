package com.rimuru.android.ecgify.ui.home

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.rimuru.android.ecgify.R
import com.rimuru.android.ecgify.databinding.FragmentManualSelectionBinding
import com.rimuru.android.ecgify.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class ManualSelectionFragment : Fragment() {

    private var _binding: FragmentManualSelectionBinding? = null
    private val binding get() = _binding!!

    private val args by navArgs<ManualSelectionFragmentArgs>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentManualSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = loadBitmap(Uri.parse(args.imageUri))
            withContext(Dispatchers.Main) {
                if (bitmap == null) {
                    Toast.makeText(requireContext(), "Ошибка загрузки", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                    return@withContext
                }

                binding.documentScanner.setImage(bitmap)
                binding.documentScanner.setOnLoadListener { loading ->
                    binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                }
            }
        }

        binding.confirmButton.setOnClickListener { cropAndProceed() }
    }

    private fun cropAndProceed() {
        binding.progressBar.visibility = View.VISIBLE
        binding.confirmButton.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cropped = binding.documentScanner.getCroppedImage()
                    ?: throw Exception("Ошибка обрезки")

                val uri = ImageUtils.saveBitmapToCache(requireContext(), cropped)
                    ?: throw Exception("Не удалось сохранить")

                withContext(Dispatchers.Main) {
                    navigateToProcessing(uri.toString())
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), e.message, Toast.LENGTH_LONG).show()
                    binding.progressBar.visibility = View.GONE
                    binding.confirmButton.isEnabled = true
                }
            }
        }
    }

    private fun navigateToProcessing(uri: String) {
        val action = ManualSelectionFragmentDirections
            .actionManualSelectionFragmentToResultsFragment(uri)
        findNavController().navigate(action)
    }

    private fun loadBitmap(uri: Uri): Bitmap? =
        requireContext().contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
