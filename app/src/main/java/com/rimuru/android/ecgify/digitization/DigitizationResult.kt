package com.rimuru.android.ecgify.digitization

import android.graphics.Bitmap
import com.rimuru.android.ecgify.digitization.model.Rectangle

/**
 * Результат оцифровки ЭКГ
 */
data class DigitizationResult(
    val ecgData: EcgData,           // Цифровые данные сигналов
    val traceBitmap: Bitmap,        // Изображение с трассировкой
    val cropRect: Rectangle         // Прямоугольник обрезки
)