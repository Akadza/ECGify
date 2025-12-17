package com.rimuru.android.ecgify.digitization

import android.graphics.Bitmap
import com.rimuru.android.ecgify.digitization.model.*
import com.rimuru.android.ecgify.digitization.preprocessing.Preprocessor
import com.rimuru.android.ecgify.digitization.extraction.SignalExtractor
import com.rimuru.android.ecgify.digitization.postprocessing.Postprocessor
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import java.io.File
import java.io.FileWriter
import java.lang.Exception

/**
 * Главный координатор оцифровки ЭКГ.
 * Аналог digitization.Digitizer из ECGMiner
 */
class Digitizer(
    private val layout: Pair<Int, Int>,         // (rows, cols)
    private val rhythm: List<Lead>,
    private val rpAtRight: Boolean,
    private val cabrera: Boolean,
    private val interpolation: Int? = null
) {
    // Компоненты обработки
    private val preprocessor = Preprocessor()
    private val signalExtractor = SignalExtractor(layout.first + rhythm.size)
    private val postprocessor = Postprocessor(layout, rhythm, rpAtRight, cabrera, interpolation)

    /**
     * Основной метод оцифровки
     * Аналог digitize(path: str) -> None
     */
    fun digitize(ecgBitmap: Bitmap): DigitizationResult {
        try {
            // 1. Конвертируем Bitmap в EcgImage
            val ecg = EcgImage(ecgBitmap)
            val frame = ecg.copy()

            // 2. Предобработка
            val (ecgCrop, rect) = preprocessor.preprocess(ecg)

            // 3. Извлечение сигналов
            val rawSignals = signalExtractor.extractSignals(ecgCrop)

            // 4. Постобработка
            val (data, trace) = postprocessor.postprocess(rawSignals, ecgCrop)

            // 5. Создаем изображение с трассировкой
            val traceBitmap = createTraceBitmap(frame, trace, rect)

            return DigitizationResult(
                ecgData = data,
                traceBitmap = traceBitmap,
                cropRect = rect
            )

        } catch (e: Exception) {
            throw DigitizationError("Ошибка оцифровки: ${e.message}")
        }
    }

    /**
     * Создает Bitmap с трассировкой (аналог сохранения _trace.png)
     */
    private fun createTraceBitmap(original: EcgImage, trace: EcgImage, rect: Rectangle): Bitmap {
        // Копируем оригинальное изображение
        val result = original.copy()

        // Получаем данные оригинального изображения и трассировки
        val resultData = result.getData()
        val traceData = trace.getData()

        // Вставляем трассировку в область обрезки
        val tl = rect.topLeft.toIntPoint()
        val br = rect.bottomRight.toIntPoint()

        // Определяем область для вставки
        val roi = Rect(tl.x, tl.y, br.x - tl.x, br.y - tl.y)

        // Убедимся, что размеры совпадают
        val traceRect = Rect(0, 0, traceData.cols(), traceData.rows())
        val actualRoi = if (roi.width > traceData.cols() || roi.height > traceData.rows()) {
            Rect(roi.x, roi.y, traceData.cols(), traceData.rows())
        } else {
            roi
        }

        // Копируем данные трассировки в соответствующую область
        traceData.copyTo(resultData.submat(actualRoi))

        // Обновляем данные в результате
        result.setData(resultData)

        return result.toBitmap()
    }
}

/**
 * Результат оцифровки ЭКГ
 */
data class DigitizationResult(
    val ecgData: EcgData,           // Цифровые данные сигналов
    val traceBitmap: Bitmap,        // Изображение с трассировкой
    val cropRect: Rectangle         // Прямоугольник обрезки
)

/**
 * Данные ЭКГ (аналог DataFrame в Python)
 * Содержит сигналы всех отведений
 */
data class EcgData(
    val leads: Map<Lead, List<Float>>,  // Сигналы по отведениям
    val samplingRate: Float = 500f,     // Частота дискретизации (Гц)
    val duration: Float                 // Длительность записи (сек)
) {
    /**
     * Сохраняет данные в CSV файл (аналог data.to_csv())
     */
    fun saveToCsv(file: File) {
        FileWriter(file).use { writer ->
            // Заголовок
            val header = leads.keys.joinToString(",") { it.name }
            writer.write("$header\n")

            // Определяем максимальную длину сигнала
            val maxLength = leads.values.maxOfOrNull { it.size } ?: 0

            // Данные построчно
            for (i in 0 until maxLength) {
                val row = leads.keys.joinToString(",") { lead ->
                    val signal = leads[lead]
                    if (signal != null && i < signal.size) {
                        signal[i].toString()
                    } else {
                        ""
                    }
                }
                writer.write("$row\n")
            }
        }
    }
}

/**
 * Вспомогательная функция для преобразования Point в IntPoint
 */
fun Point.toIntPoint(): IntPoint {
    return IntPoint(x.toInt(), y.toInt())
}

/**
 * Точка с целочисленными координатами
 */
data class IntPoint(val x: Int, val y: Int)