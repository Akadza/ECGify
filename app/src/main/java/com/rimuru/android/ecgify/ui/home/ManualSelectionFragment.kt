package com.rimuru.android.ecgify.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.rimuru.android.ecgify.databinding.FragmentManualSelectionBinding
import com.rimuru.android.ecgify.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

class ManualSelectionFragment : Fragment() {

    private var _binding: FragmentManualSelectionBinding? = null
    private val binding get() = _binding!!

    private val args by navArgs<ManualSelectionFragmentArgs>()
    private var currentBitmap: Bitmap? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManualSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initDocumentScanner()
        setupClickListeners()

        // Используем анонимный объект как в старом проекте
        binding.documentScanner.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    // Удаляем слушатель после первого вызова
                    binding.documentScanner.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    loadAndSetImage()
                }
            }
        )
    }

    private fun initDocumentScanner() {
        binding.documentScanner.setOnLoadListener { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    private fun loadAndSetImage() {
        val uriString = args.imageUri
        val uri = Uri.parse(uriString)

        binding.progressBar.visibility = View.VISIBLE
        binding.confirmButton.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Используем метод из старого проекта
                val bitmap = loadBitmapFromUri(uri)

                withContext(Dispatchers.Main) {
                    if (bitmap == null) {
                        Toast.makeText(requireContext(), "Не удалось загрузить изображение", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                        return@withContext
                    }

                    currentBitmap = bitmap
                    binding.documentScanner.setImage(bitmap)
                    binding.confirmButton.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
            }
        }
    }

    /**
     * Загружает Bitmap из переданного URI (аналогично старому проекту)
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun setupClickListeners() {
        binding.confirmButton.setOnClickListener {
            cropAndProceed()
        }
    }

    private fun cropAndProceed() {
        binding.progressBar.visibility = View.VISIBLE
        binding.confirmButton.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cropped: Bitmap = binding.documentScanner.getCroppedImage()
                    ?: throw Exception("Не удалось обрезать изображение")

                // Сохраняем в галерею
                val galleryUri = ImageUtils.saveToGallery(
                    requireContext(),
                    cropped,
                    "ECG_${System.currentTimeMillis()}"
                ) ?: throw Exception("Ошибка сохранения в галерею")

                withContext(Dispatchers.Main) {
                    navigateToProcessing(galleryUri.toString())
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.progressBar.visibility = View.GONE
                    binding.confirmButton.isEnabled = true
                }
            }
        }
    }

    private fun navigateToProcessing(uri: String) {
        try {
            val action = ManualSelectionFragmentDirections
                .actionManualSelectionFragmentToResultsFragment(uri)

            findNavController().navigate(action)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Ошибка навигации", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentBitmap?.recycle()
        currentBitmap = null
        _binding = null
    }
}