package com.rimuru.android.ecgify.ui.home.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rimuru.android.ecgify.MainActivity
import com.rimuru.android.ecgify.digitization.Digitizer
import com.rimuru.android.ecgify.digitization.DigitizationError
import com.rimuru.android.ecgify.digitization.DigitizationResult
import com.rimuru.android.ecgify.digitization.model.Lead
import com.rimuru.android.ecgify.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResultsViewModel : ViewModel() {

    sealed class ResultsUiState {
        object Loading : ResultsUiState()
        data class Success(val result: DigitizationResult) : ResultsUiState()
        data class Error(val message: String) : ResultsUiState()
    }

    private val _uiState = MutableLiveData<ResultsUiState>()
    val uiState: LiveData<ResultsUiState> = _uiState

    private val _saveProgress = MutableLiveData<Int>()
    val saveProgress: LiveData<Int> = _saveProgress

    private var currentJob: Job? = null
    private var lastResult: DigitizationResult? = null

    // Создаем Digitizer с настройками по умолчанию
    private val digitizer = Digitizer(
        layout = Pair(6, 2),          // 6 строк, 2 столбца (стандартная 12-канальная ЭКГ)
        rhythm = listOf(Lead.II),     // ритм-полоска - отведение II
        rpAtRight = false,            // референсные импульсы не справа
        cabrera = false,              // не использовать формат Кабреры
        interpolation = null          // без интерполяции
    )

    fun digitizeImage(context: Context, imageUri: Uri) {
        currentJob?.cancel()

        currentJob = viewModelScope.launch {
            try {
                _uiState.value = ResultsUiState.Loading

                // Проверяем инициализацию OpenCV
                if (!ensureOpenCVInitialized()) {
                    _uiState.value = ResultsUiState.Error("OpenCV не инициализирован. Перезапустите приложение.")
                    return@launch
                }

                // Загружаем изображение
                val bitmap = withContext(Dispatchers.IO) {
                    loadBitmapFromUri(context, imageUri)
                }

                if (bitmap == null) {
                    _uiState.value = ResultsUiState.Error("Не удалось загрузить изображение")
                    return@launch
                }

                // Оцифровка с помощью настоящего ECGMiner
                val result = digitizeWithECGMiner(bitmap)

                lastResult = result
                _uiState.value = ResultsUiState.Success(result)

            } catch (e: DigitizationError) {
                _uiState.value = ResultsUiState.Error("Ошибка оцифровки: ${e.message}")
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = ResultsUiState.Error("Неизвестная ошибка: ${e.message}")
            }
        }
    }

    /**
     * Основной метод оцифровки с использованием ECGMiner
     */
    private fun digitizeWithECGMiner(bitmap: Bitmap): DigitizationResult {
        try {
            // Вызываем основной метод оцифровки
            return digitizer.digitize(bitmap)
        } catch (e: Exception) {
            // Добавляем дополнительную информацию для отладки
            throw DigitizationError("Ошибка в процессе оцифровки: ${e.message}", e)
        }
    }

    /**
     * Проверяет и при необходимости инициализирует OpenCV
     */
    private fun ensureOpenCVInitialized(): Boolean {
        return if (MainActivity.isOpenCVInitialized) {
            true
        } else {
            // Попробуем инициализировать вручную
            val success = OpenCVLoader.initDebug()
            if (success) {
                MainActivity.isOpenCVInitialized = true
                true
            } else {
                false
            }
        }
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveEcgData(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _saveProgress.postValue(10)

                lastResult?.let { result ->
                    // Создаем имя файла
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        .format(Date())

                    // Сохраняем CSV
                    val outputDir = FileUtils.getOutputDirectory(context)
                    val csvFile = File(outputDir, "ecg_${timestamp}.csv")

                    if (!outputDir.exists()) {
                        outputDir.mkdirs()
                    }

                    result.ecgData.saveToCsv(csvFile)
                    _saveProgress.postValue(100)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _saveProgress.postValue(0)
            }
        }
    }

    fun saveTraceImage(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _saveProgress.postValue(10)

                lastResult?.let { result ->
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        .format(Date())

                    val outputDir = FileUtils.getOutputDirectory(context)
                    val imageFile = File(outputDir, "trace_${timestamp}.png")

                    if (!outputDir.exists()) {
                        outputDir.mkdirs()
                    }

                    // Сохраняем изображение с трассировкой
                    result.traceBitmap.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        imageFile.outputStream()
                    )

                    _saveProgress.postValue(100)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _saveProgress.postValue(0)
            }
        }
    }

    fun shareResults(context: Context) {
        // TODO: Реализовать обмен результатами
    }
}