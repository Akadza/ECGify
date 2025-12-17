package com.rimuru.android.ecgify.digitization.extraction

import com.rimuru.android.ecgify.digitization.DigitizationError
import com.rimuru.android.ecgify.digitization.model.EcgImage
import com.rimuru.android.ecgify.digitization.model.Point
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Signal extractor of an ECG image.
 * Analog of SignalExtractor.py from ECGMiner
 */
class SignalExtractor(private val n: Int) {

    /**
     * Extract the signals of the ECG image.
     *
     * @param ecg ECG image from which to extract the signals
     * @return List with the list of points of each signal
     */
    fun extractSignals(ecg: EcgImage): List<List<Point>> {
        val N = ecg.width
        val LEN = 2  // Cache values index
        val SCORE = 3  // Cache values index

        val rois = getROI(ecg)
        val mean: (List<Int>) -> Double = { cluster -> (cluster.first() + cluster.last()) / 2.0 }

        // Cache for dynamic programming
        val cache = mutableMapOf<Pair<Int, List<Int>>, Array<Array<Any?>>>()

        for (col in 1 until N) {
            val prevClusters = getClusters(ecg, col - 1)
            if (prevClusters.isEmpty()) continue

            val clusters = getClusters(ecg, col)

            for (c in clusters) {
                // Initialize cache for current column and cluster
                cache[Pair(col, c)] = Array(n) { arrayOfNulls<Any?>(4) }

                for (roiIndex in 0 until n) {
                    val costs = mutableMapOf<List<Int>, Double>()

                    for (pc in prevClusters) {
                        val node = Pair(col - 1, pc)
                        val ctr = ceil(mean(pc)).toInt()

                        // Initialize cache for previous node if needed
                        if (!cache.containsKey(node)) {
                            val value = arrayOf<Any?>(ctr, null, 1, 0.0)
                            cache[node] = Array(n) { value.copyOf() }
                        }

                        val prevStats = cache[node]!![roiIndex]
                        val prevScore = prevStats[SCORE] as Double
                        val d = abs(ctr - rois[roiIndex]).toDouble()  // Vertical distance to ROI
                        val g = gap(pc, c)  // Disconnection level
                        val cost = prevScore + d + (N / 10.0) * g

                        costs[pc] = cost
                    }

                    // Find best previous cluster
                    val best = costs.minByOrNull { it.value }?.key

                    if (best != null) {
                        val y = ceil(mean(best)).toInt()
                        val p = Pair(col - 1, best)
                        val l = (cache[p]!![roiIndex][LEN] as Int) + 1
                        val s = costs[best]!!

                        cache[Pair(col, c)]!![roiIndex] = arrayOf(y, p, l, s)
                    }
                }
            }
        }

        // Backtracking
        return backtracking(cache, rois)
    }

    /**
     * Get the coordinates of the ROI of the ECG image.
     * Analog of __get_roi
     */
    private fun getROI(ecg: EcgImage): List<Int> {
        val WINDOW = 10
        val SHIFT = (WINDOW - 1) / 2
        val stds = DoubleArray(ecg.height)

        // Calculate standard deviation for sliding window
        for (i in 0 until ecg.height - WINDOW + 1) {
            val x0 = 0
            val x1 = ecg.width
            val y0 = i
            val y1 = i + WINDOW - 1

            // Extract window and calculate std
            val window = ecg.get(y0..y1, x0..x1)
            val flat = DoubleArray(window.rows() * window.cols())

            var index = 0
            for (row in 0 until window.rows()) {
                for (col in 0 until window.cols()) {
                    flat[index++] = window.get(row, col)[0]
                }
            }

            val std = calculateStd(flat)
            stds[i + SHIFT] = std
        }

        // Find peaks
        val minDistance = (ecg.height * 0.1).toInt()
        val peaks = findPeaks(stds, minDistance)

        // Sort by std value and take first n
        val sortedPeaks = peaks.sortedByDescending { stds[it] }

        if (sortedPeaks.size < n) {
            throw DigitizationError("The indicated number of ROIs could not be detected.")
        }

        return sortedPeaks.take(n).sorted()
    }

    /**
     * Get the clusters of a certain column of an ECG.
     * The clusters are regions of consecutive black pixels.
     * Analog of __get_clusters
     */
    private fun getClusters(ecg: EcgImage, col: Int): List<List<Int>> {
        val BLACK = 0
        val blackRows = mutableListOf<Int>()

        // Find all black pixels in column
        for (row in 0 until ecg.height) {
            if (ecg[row, col] == BLACK) {
                blackRows.add(row)
            }
        }

        // Group consecutive rows into clusters
        return groupConsecutive(blackRows)
    }

    /**
     * Compute the gap between two clusters.
     * It is the vertical white space between them.
     * This gap will be 0 if they are in direct contact with each other.
     * Analog of __gap
     */
    private fun gap(pc: List<Int>, c: List<Int>): Int {
        val pcMin = pc.first()
        val pcMax = pc.last()
        val cMin = c.first()
        val cMax = c.last()

        var d = 0

        when {
            pcMin <= cMin && pcMax <= cMax -> {
                // pc is above c
                d = (cMin - pcMax - 1).coerceAtLeast(0)
            }
            pcMin >= cMin && pcMax >= cMax -> {
                // pc is below c
                d = (pcMin - cMax - 1).coerceAtLeast(0)
            }
            // Otherwise clusters are adjacent (overlap or touch)
        }

        return d
    }

    /**
     * Performs a backtracking process over the cache of links between clusters
     * to extract the signals.
     * Analog of __backtracking
     */
    private fun backtracking(
        cache: Map<Pair<Int, List<Int>>, Array<Array<Any?>>>,
        rois: List<Int>
    ): List<List<Point>> {
        val X_COORD = 0  // Cache keys: first element of Pair
        val CLUSTER = 1  // Cache keys: second element of Pair
        val Y_COORD = 0  // Cache values indices
        val PREV = 1
        val LEN = 2

        val mean: (List<Int>) -> Double = { cluster -> (cluster.first() + cluster.last()) / 2.0 }

        val rawSignals = MutableList(n) { emptyList<Point>() }

        for (roiIndex in 0 until n) {
            val roi = rois[roiIndex]

            // Find maximum signal length
            val maxLen = cache.values.maxOfOrNull { stats ->
                (stats[roiIndex]?.get(LEN) as? Int) ?: 0
            } ?: 0

            // Candidates with maximum length
            val candNodes = cache.filter { (_, stats) ->
                (stats[roiIndex]?.get(LEN) as? Int) == maxLen
            }.keys

            // Best last point is the one closest to ROI
            val best = candNodes.minByOrNull { node ->
                abs(ceil(mean(node.second)).toInt() - roi)
            } ?: continue

            // Collect backward path
            val rawSignal = mutableListOf<Point>()
            val clusters = mutableListOf<List<Int>>()

            var current: Pair<Int, List<Int>>? = best
            while (current != null) {
                val stats = cache[current]!![roiIndex]
                val y = stats[Y_COORD] as Int
                rawSignal.add(Point(current.first.toFloat(), y.toFloat()))
                clusters.add(current.second)
                current = stats[PREV] as? Pair<Int, List<Int>>
            }

            // Reverse path (since we collected from end)
            rawSignal.reverse()
            clusters.reverse()

            // Peak delineation
            val roiDist = rawSignal.map { abs(it.y - roi) }.toFloatArray()
            val peaks = findPeaks(roiDist.map { it.toDouble() }.toDoubleArray())

            for (peak in peaks) {
                if (peak > 0 && peak < clusters.size) {
                    val cluster = clusters[peak - 1]
                    val farthest = cluster.maxByOrNull { abs(it - roi) } ?: continue
                    rawSignal[peak] = Point(rawSignal[peak].x, farthest.toFloat())
                }
            }

            rawSignals[roiIndex] = rawSignal
        }

        return rawSignals
    }

    /**
     * Group consecutive integers
     */
    private fun groupConsecutive(numbers: List<Int>): List<List<Int>> {
        if (numbers.isEmpty()) return emptyList()

        val groups = mutableListOf<MutableList<Int>>()
        var currentGroup = mutableListOf(numbers[0])

        for (i in 1 until numbers.size) {
            if (numbers[i] == numbers[i - 1] + 1) {
                currentGroup.add(numbers[i])
            } else {
                groups.add(currentGroup)
                currentGroup = mutableListOf(numbers[i])
            }
        }

        groups.add(currentGroup)
        return groups
    }

    /**
     * Find peaks in a signal
     */
    private fun findPeaks(signal: DoubleArray, minDistance: Int = 1): List<Int> {
        val peaks = mutableListOf<Int>()

        for (i in 1 until signal.size - 1) {
            if (signal[i] > signal[i - 1] && signal[i] > signal[i + 1]) {
                // Check minimum distance
                if (peaks.isEmpty() || i - peaks.last() >= minDistance) {
                    peaks.add(i)
                }
            }
        }

        return peaks
    }

    /**
     * Calculate standard deviation
     */
    private fun calculateStd(array: DoubleArray): Double {
        if (array.isEmpty()) return 0.0

        val mean = array.average()
        val variance = array.map { (it - mean).pow(2) }.average()
        return kotlin.math.sqrt(variance)
    }

    /**
     * Find peaks in FloatArray
     */
    private fun findPeaks(signal: FloatArray, minDistance: Int = 1): List<Int> {
        return findPeaks(signal.map { it.toDouble() }.toDoubleArray(), minDistance)
    }
}

// Extension function for Double power
private fun Double.pow(exp: Int): Double {
    var result = 1.0
    repeat(exp) { result *= this }
    return result
}