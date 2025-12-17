package com.rimuru.android.ecgify.digitization

import com.rimuru.android.ecgify.digitization.model.Lead
import java.io.File
import java.io.FileWriter

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