// app/ui/state/DigitizationUiState.kt
package com.rimuru.android.ecgify.ui.state

import com.rimuru.android.ecgify.digitization.model.Lead

data class DigitizationUiState(
    // Настройки
    val layoutRows: Int = 6,
    val layoutCols: Int = 2,
    val selectedRhythm: List<Lead> = emptyList(),
    val rpAtRight: Boolean = false,
    val cabreraFormat: Boolean = false,
    val interpolationValue: String = "",

    // Файлы
    val selectedFiles: List<String> = emptyList(),
    val outputDirectory: String = "",

    // Состояние
    val isProcessing: Boolean = false,
    val progress: Int = 0,
    val currentProcessingFile: String = "",

    // Результаты
    val lastResultPath: String? = null,
    val errorMessage: String? = null,

    // Валидация
    val isLayoutValid: Boolean = true,
    val isOutputDirValid: Boolean = true
)

sealed class DigitizationEvent {
    data class UpdateLayout(val rows: Int, val cols: Int) : DigitizationEvent()
    data class UpdateRhythm(val leads: List<Lead>) : DigitizationEvent()
    data class UpdateRpPosition(val atRight: Boolean) : DigitizationEvent()
    data class UpdateCabreraFormat(val enabled: Boolean) : DigitizationEvent()
    data class UpdateInterpolation(val value: String) : DigitizationEvent()
    data class SelectFiles(val filePaths: List<String>) : DigitizationEvent()
    data class SelectOutputDir(val dirPath: String) : DigitizationEvent()
    object StartDigitization : DigitizationEvent()
    object CancelDigitization : DigitizationEvent()
    object ClearError : DigitizationEvent()
    object ResetState : DigitizationEvent()
}