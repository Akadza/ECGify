package com.rimuru.android.ecgify.digitization.postprocessing

import com.rimuru.android.ecgify.digitization.DigitizationError
import com.rimuru.android.ecgify.digitization.EcgData
import com.rimuru.android.ecgify.digitization.model.*
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Postprocessor of an ECG image.
 * Analog of Postprocessor.py from ECGMiner
 */
class Postprocessor(
    private val layout: Pair<Int, Int>,      // (rows, cols)
    private val rhythm: List<Lead>,
    private val rpAtRight: Boolean,
    private val cabrera: Boolean,
    private val interpolation: Int? = null
) {

    /**
     * Post process the raw signals, getting the signals of 12 leads and an image with the trace.
     *
     * @param rawSignals List with the list of points of each signal
     * @param ecgCrop Crop of the ECG gridline with the signals
     * @return Pair of ECG data and image of the trace
     */
    fun postprocess(
        rawSignals: List<List<Point>>,
        ecgCrop: EcgImage
    ): Pair<EcgData, EcgImage> {
        val (signals, refPulses) = segment(rawSignals)
        val data = vectorize(signals, refPulses)
        val trace = getTrace(ecgCrop, signals, refPulses)
        return Pair(data, trace)
    }

    /**
     * Segments the raw signals, removing the digital reference pulses and identifying
     * the 0mV and 1mV y-coordinate levels.
     * Analog of __segment
     */
    private fun segment(
        rawSignals: List<List<Point>>
    ): Pair<List<List<Point>>, List<Pair<Int, Int>>> {
        val PIXEL_EPS = 5  // tolerance in y-coord for flat level
        val MIN_PULSE_WIDTH = 20  // required k points to consider a valid level

        val refPulses = mutableListOf<Pair<Int, Int>>()
        val cleanedSignals = mutableListOf<List<Point>>()

        for (signal in rawSignals) {
            val points = if (rpAtRight) signal.reversed() else signal.toMutableList()

            // --- Step 1: Detect how many consecutive values are 0mV ---
            var y0mV = points.first().y.toInt()
            var k = 1
            for (i in 1 until points.size) {
                if (abs(points[i].y - y0mV) < PIXEL_EPS) {
                    k++
                } else {
                    break
                }
            }

            // --- Step 2: Detect 1mV value (next flat sequence) ---
            var y1mV = 0
            var count = 1
            var inSequence = false
            val minPulseWidth = maxOf(10, 3 * k)
            var end1mV = points.size

            for (i in (k + 1) until points.size) {
                if (abs(points[i].y - points[i - 1].y) < PIXEL_EPS) {
                    count++

                    if (count == minPulseWidth && y1mV == 0) {
                        y1mV = points[i].y.toInt()
                        inSequence = true
                    }
                } else {
                    if (inSequence) {
                        end1mV = i
                        break
                    }
                    count = 1
                }
            }

            // --- Step 3: Skip k points after the end of the 1mV sequence ---
            val cutIndex = minOf(end1mV + k, points.size)
            val cleaned = points.subList(cutIndex, points.size)

            cleanedSignals.add(cleaned)
            refPulses.add(Pair(y0mV, y1mV))
        }

        // Compute median difference between 0mV and 1mV coordinates
        val diffs = refPulses.map { pulse -> pulse.first - pulse.second }
        val medianDiff = if (diffs.isNotEmpty()) {
            diffs.sorted()[diffs.size / 2]
        } else {
            0
        }.coerceAtLeast(10)

        // Keep 0mV constant, but compute 1mV as 0mV minus the median diff
        val adjustedRefPulses = refPulses.map { pulse ->
            Pair(pulse.first, pulse.first - medianDiff)
        }

        return Pair(cleanedSignals, adjustedRefPulses)
    }

    /**
     * Vectorize the signals, normalizing them and storing them in ECG data.
     * Analog of __vectorize
     */
    private fun vectorize(
        signals: List<List<Point>>,
        refPulses: List<Pair<Int, Int>>
    ): EcgData {
        val (NROWS, NCOLS) = layout
        val ORDER = if (cabrera) EcgFormat.CABRERA else EcgFormat.STANDARD

        // Find maximum signal length
        val maxLen = signals.maxOfOrNull { it.size } ?: 0

        // Pad all signals to closest multiple number of ECG ncols
        val maxDiff = maxLen % NCOLS
        val maxPad = if (maxDiff == 0) 0 else NCOLS - maxDiff
        val totalObs = interpolation ?: (maxLen + maxPad)

        // Linear interpolation to get a certain number of observations
        val interpSignals = Array(signals.size) { FloatArray(totalObs) }
        for (i in signals.indices) {
            val signal = signals[i].map { it.y }.toFloatArray()
            interpSignals[i] = linearInterpolate(signal, totalObs)
        }

        // Create map for lead signals
        val leadSignals = mutableMapOf<Lead, List<Float>>()

        for (i in ORDER.indices) {
            val lead = ORDER[i]
            val isRhythm = lead in rhythm

            val r = if (isRhythm) {
                rhythm.indexOf(lead) + NROWS
            } else {
                i % NROWS
            }

            val c = if (isRhythm) 0 else i / NROWS

            // Reference pulses
            val volt0 = refPulses[r].first
            val volt1 = refPulses[r].second

            if (volt0 == volt1) {
                throw DigitizationError("Reference pulses have not been detected correctly")
            }

            // Get correspondent part of the signal for current lead
            val signal = interpSignals[r]
            val obsNum = signal.size / (if (isRhythm) 1 else NCOLS)
            val startIdx = c * obsNum
            val endIdx = (c + 1) * obsNum

            // Extract and scale signal
            val extracted = signal.copyOfRange(startIdx, endIdx)
            val scaled = extracted.map { y ->
                ((volt0 - y) * (1.0 / (volt0 - volt1))).toFloat()
            }

            // Round voltages to 4 decimals
            val rounded = scaled.map { roundToDecimals(it, 4) }

            // Cabrera format -aVR
            val finalSignal = if (cabrera && lead == Lead.aVR) {
                rounded.map { -it }
            } else {
                rounded
            }

            leadSignals[lead] = finalSignal
        }

        // Calculate duration (assuming 25 mm/sec and 10 mm/mV standard)
        val sampleRate = 500f  // Hz (standard sampling rate for ECG)
        val duration = totalObs / sampleRate  // seconds

        return EcgData(
            leads = leadSignals,
            samplingRate = sampleRate,
            duration = duration
        )
    }

    /**
     * Get the trace of the extraction algorithm performed over the ECG image.
     * Analog of __get_trace
     */
    private fun getTrace(
        ecg: EcgImage,
        signals: List<List<Point>>,
        refPulses: List<Pair<Int, Int>>
    ): EcgImage {
        val (NROWS, NCOLS) = layout
        val ORDER = if (cabrera) EcgFormat.CABRERA else EcgFormat.STANDARD
        val H_SPACE = 20

        val trace = ecg.copy()
        trace.toBGR()

        // Colors for drawing leads
        val colors = listOf(
            Triple(0, 0, 255),      // Blue
            Triple(0, 255, 0),      // Green
            Triple(255, 0, 0),      // Red
            Triple(0, 200, 255),    // Light Blue
            Triple(255, 255, 0),    // Yellow
            Triple(255, 0, 255),    // Magenta
            Triple(0, 0, 125),      // Dark Blue
            Triple(0, 125, 0),      // Dark Green
            Triple(125, 0, 0),      // Dark Red
            Triple(0, 100, 125),    // Dark Light Blue
            Triple(125, 125, 0),    // Dark Yellow
            Triple(125, 0, 125)     // Dark Magenta
        )

        // Draw ref pulse dot lines
        for (pulse in refPulses) {
            val (volt0, volt1) = pulse

            for (x in 0 until ecg.width step H_SPACE) {
                // Volt_0
                trace.line(
                    Point(x.toFloat(), volt0.toFloat()),
                    Point((x + H_SPACE / 2).toFloat(), volt0.toFloat()),
                    Triple(0, 0, 0),
                    thickness = 1
                )

                // Volt_1
                trace.line(
                    Point(x.toFloat(), volt1.toFloat()),
                    Point((x + H_SPACE / 2).toFloat(), volt1.toFloat()),
                    Triple(0, 0, 0),
                    thickness = 1
                )
            }
        }

        // Draw signals
        for (i in ORDER.indices) {
            val lead = ORDER[i]
            val isRhythm = lead in rhythm

            val r = if (isRhythm) {
                rhythm.indexOf(lead) + NROWS
            } else {
                i % NROWS
            }

            val c = if (isRhythm) 0 else i / NROWS

            if (r < signals.size) {
                val signal = signals[r]
                val obsNum = signal.size / (if (isRhythm) 1 else NCOLS)
                val startIdx = c * obsNum
                val endIdx = (c + 1) * obsNum

                if (startIdx < signal.size && endIdx <= signal.size) {
                    val leadSignal = signal.subList(startIdx, endIdx)
                    val color = colors[i % colors.size]

                    for (j in 0 until leadSignal.size - 1) {
                        val p1 = leadSignal[j]
                        val p2 = leadSignal[j + 1]
                        trace.line(p1, p2, color, thickness = 2)
                    }
                }
            }
        }

        return trace
    }

    /**
     * Linear interpolation
     */
    private fun linearInterpolate(signal: FloatArray, newLength: Int): FloatArray {
        if (signal.isEmpty()) return FloatArray(newLength)
        if (newLength == 0) return FloatArray(0)
        if (newLength == 1) return floatArrayOf(signal.first())

        val result = FloatArray(newLength)
        val scale = (signal.size - 1).toFloat() / (newLength - 1)

        for (i in 0 until newLength) {
            val pos = i * scale
            val index = pos.toInt()
            val frac = pos - index

            if (index < signal.size - 1) {
                result[i] = signal[index] * (1 - frac) + signal[index + 1] * frac
            } else {
                result[i] = signal.last()
            }
        }

        return result
    }

    /**
     * Round to specified number of decimals
     */
    private fun roundToDecimals(value: Float, decimals: Int): Float {
        val factor = 10.0.pow(decimals)
        return (value * factor).roundToInt() / factor.toFloat()
    }
}

// Extension function for Double power
private fun Double.pow(exp: Int): Double {
    var result = 1.0
    repeat(exp) { result *= this }
    return result
}