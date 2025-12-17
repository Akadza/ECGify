package com.rimuru.android.ecgify.digitization.viewmodel

import com.rimuru.android.ecgify.digitization.DigitizationResult

data class DigitizationState(
    val isProcessing: Boolean = false,
    val progress: Int = 0,
    val currentFile: String = "",
    val totalFiles: Int = 0,
    val error: String? = null,
    val results: List<DigitizationResult> = emptyList(),
    val isComplete: Boolean = false
)