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
        val result = original.copy()
        val resultMat = result.getData()  // Оригинальный размер

        val traceMat = trace.getData()    // Размер обрезанной области (после preprocess)

        val tl = rect.topLeft.toIntPoint()
        val br = rect.bottomRight.toIntPoint()

        // Защищаем от выхода за границы оригинального изображения
        val x = tl.x.coerceIn(0, result.width)
        val y = tl.y.coerceIn(0, result.height)
        val maxW = result.width - x
        val maxH = result.height - y
        val w = (br.x - tl.x).coerceAtMost(maxW)
        val h = (br.y - tl.y).coerceAtMost(maxH)

        // Если область некорректная — просто возвращаем оригинал без трассировки
        if (w <= 0 || h <= 0) {
            return result.toBitmap()
        }

        // Обрезаем trace, если он больше нужной области (на всякий случай)
        val traceW = traceMat.cols().coerceAtMost(w)
        val traceH = traceMat.rows().coerceAtMost(h)

        val roiInResult = org.opencv.core.Rect(x, y, traceW, traceH)
        val roiInTrace = org.opencv.core.Rect(0, 0, traceW, traceH)

        // Копируем только подходящую часть
        val tracePart = traceMat.submat(roiInTrace)
        tracePart.copyTo(resultMat.submat(roiInResult))

        return result.toBitmap()
    }
}