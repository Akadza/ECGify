package com.rimuru.android.ecgify.digitization.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rimuru.android.ecgify.digitization.Digitizer
import com.rimuru.android.ecgify.digitization.DigitizationError
import com.rimuru.android.ecgify.digitization.DigitizationResult
import com.rimuru.android.ecgify.digitization.model.Lead
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class DigitizationViewModel : ViewModel() {

    // Параметры оцифровки
    private var layout: Pair<Int, Int> = Pair(6, 2) // 6x2 по умолчанию
    private var rhythm: List<Lead> = emptyList()
    private var rpAtRight: Boolean = false
    private var cabrera: Boolean = false
    private var interpolation: Int? = null
    private var outputDir: File? = null

    // Состояние
    private val _state = MutableStateFlow(DigitizationState())
    val state: StateFlow<DigitizationState> = _state.asStateFlow()

    private var currentJob: Job? = null

    fun updateLayout(rows: Int, cols: Int) {
        layout = Pair(rows, cols)
    }

    fun updateRhythm(leads: List<Lead>) {
        rhythm = leads
    }

    fun updateRpAtRight(atRight: Boolean) {
        rpAtRight = atRight
    }

    fun updateCabrera(enabled: Boolean) {
        cabrera = enabled
    }

    fun updateInterpolation(value: Int?) {
        interpolation = value
    }

    fun updateOutputDir(dir: File) {
        outputDir = dir
    }

    fun digitizeSingle(bitmap: Bitmap, fileName: String) {
        currentJob?.cancel()

        currentJob = viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    isProcessing = true,
                    progress = 0,
                    currentFile = fileName,
                    totalFiles = 1,
                    error = null
                )

                // Симуляция прогресса (можно убрать в реальном приложении)
                for (i in 0..100 step 10) {
                    delay(100)
                    _state.value = _state.value.copy(progress = i)
                }

                // Создание Digitizer и оцифровка
                val digitizer = Digitizer(layout, rhythm, rpAtRight, cabrera, interpolation)
                val result = digitizer.digitize(bitmap)

                _state.value = _state.value.copy(
                    isProcessing = false,
                    progress = 100,
                    results = listOf(result),
                    isComplete = true
                )

            } catch (e: DigitizationError) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    error = "Ошибка оцифровки: ${e.message}"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    error = "Неизвестная ошибка: ${e.message}"
                )
            }
        }
    }

    fun digitizeBatch(bitmaps: List<Pair<Bitmap, String>>) {
        currentJob?.cancel()

        currentJob = viewModelScope.launch {
            try {
                val results = mutableListOf<DigitizationResult>()

                _state.value = _state.value.copy(
                    isProcessing = true,
                    progress = 0,
                    currentFile = "",
                    totalFiles = bitmaps.size,
                    error = null,
                    results = emptyList()
                )

                for (indexedPair in bitmaps.withIndex()) {
                    val index = indexedPair.index
                    val bitmapFilePair = indexedPair.value
                    val bitmap = bitmapFilePair.first
                    val fileName = bitmapFilePair.second

                    // Обновляем прогресс
                    val progress = ((index + 1) * 100) / bitmaps.size
                    _state.value = _state.value.copy(
                        currentFile = fileName,
                        progress = progress
                    )

                    // Оцифровка каждого файла
                    val digitizer = Digitizer(layout, rhythm, rpAtRight, cabrera, interpolation)
                    val result = digitizer.digitize(bitmap)
                    results.add(result)

                    delay(100) // Задержка для демонстрации прогресса
                }

                _state.value = _state.value.copy(
                    isProcessing = false,
                    progress = 100,
                    results = results,
                    isComplete = true
                )

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    error = "Ошибка пакетной оцифровки: ${e.message}"
                )
            }
        }
    }

    fun cancelProcessing() {
        currentJob?.cancel()
        _state.value = DigitizationState()
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
    }
}