package com.rimuru.android.ecgify.digitization.preprocessing

import com.rimuru.android.ecgify.digitization.model.EcgImage
import com.rimuru.android.ecgify.digitization.model.Point
import com.rimuru.android.ecgify.digitization.model.Rectangle
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point as CvPoint
import org.opencv.core.Rect as CvRect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * Preprocessor to clean and binarize an ECG image.
 * Analog of Preprocessor.py from ECGMiner
 */
class Preprocessor {

    /**
     * Initialization of the preprocessor.
     */
    init {
        // No initialization needed
    }

    /**
     * Preprocess an ECG image.
     *
     * @param ecg ECG image
     * @return Pair of cropped binarized image and rectangle of the cropped area
     */
    fun preprocess(ecg: EcgImage): Pair<EcgImage, Rectangle> {
        val ecgCopy = ecg.copy()
        val rect = imgPartitioning(ecgCopy)
        val cropped = ecgCopy.crop(rect)  // теперь возвращает новый EcgImage
        val processed = gridlineRemoval(cropped)
        return Pair(processed, rect)
    }

    /**
     * Get the rectangle which contains the grid with the ECG signals.
     * Analog of __img_partitioning
     */
    private fun imgPartitioning(ecg: EcgImage): Rectangle {
        // Convert to BGR if needed
        val bgrMat = if (ecg.channels == 1) {
            val mat = Mat()
            Imgproc.cvtColor(ecg.getData(), mat, Imgproc.COLOR_GRAY2BGR)
            mat
        } else {
            ecg.getData().clone()
        }

        // Find edges with Canny operator
        val edges = Mat()
        Imgproc.Canny(bgrMat, edges, 50.0, 200.0)

        // Suzuki's contour tracing algorithm
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            edges, contours, hierarchy,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE
        )

        // Bound rectangles
        val rects = mutableListOf<CvRect>()
        for (contour in contours) {
            val contour2f = MatOfPoint2f()
            contour.convertTo(contour2f, CvType.CV_32F)

            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(
                contour2f, approx,
                0.01 * Imgproc.arcLength(contour2f, true), true
            )

            // Convert back to MatOfPoint for boundingRect
            val approxPoints = MatOfPoint()
            approx.convertTo(approxPoints, CvType.CV_32S)

            val rect = Imgproc.boundingRect(approxPoints)
            rects.add(rect)
        }

        // Filter large rectangles
        val filteredRects = filterLargeRectangles(rects, bgrMat.cols(), bgrMat.rows(), 0.05f)

        // Merge overlapping rectangles
        val mergedRects = mergeOverlappingRectangles(filteredRects)

        // Get largest contour
        val sortedRects = mergedRects.sortedByDescending { rect -> rect.width * rect.height }
        if (sortedRects.isEmpty()) {
            // If no rectangles found, return the entire image
            return Rectangle(
                Point(0f, 0f),
                Point(ecg.width.toFloat(), ecg.height.toFloat())
            )
        }

        val largestRect = sortedRects[0]
        val x = largestRect.x
        val y = largestRect.y
        val w = largestRect.width
        val h = largestRect.height

        return Rectangle(
            Point(x.toFloat(), y.toFloat()),
            Point((x + w).toFloat(), (y + h).toFloat())
        )
    }

    /**
     * Removes the gridline of an ECG image.
     * Analog of __gridline_removal
     */
    private fun gridlineRemoval(ecg: EcgImage): EcgImage {
        val ecgData = ecg.getData()

        // Make sure we have BGR image for HSV conversion
        val bgrMat = if (ecg.channels == 1) {
            val mat = Mat()
            Imgproc.cvtColor(ecgData, mat, Imgproc.COLOR_GRAY2BGR)
            mat
        } else {
            ecgData.clone()
        }

        // Convert to HSV
        val hsv = Mat()
        Imgproc.cvtColor(bgrMat, hsv, Imgproc.COLOR_BGR2HSV)

        // Create mask for light pixels (gridlines)
        val lower = Scalar(0.0, 0.0, 168.0)
        val upper = Scalar(255.0, 255.0, 255.0)
        val mask = Mat()
        Core.inRange(hsv, lower, upper, mask)

        // Apply mask
        val masked = Mat()
        Core.bitwise_and(bgrMat, bgrMat, masked, mask)

        // Convert to grayscale
        val gray = Mat()
        Imgproc.cvtColor(masked, gray, Imgproc.COLOR_BGR2GRAY)

        // Create EcgImage from grayscale
        val grayImage = EcgImage(gray)

        // Otsu binarization
        val threshold = binarize(grayImage)

        // Apply threshold
        val binary = Mat()
        Imgproc.threshold(
            grayImage.getData(), binary,
            threshold.toDouble(), 255.0, Imgproc.THRESH_BINARY
        )

        grayImage.setData(binary)

        // Outline borders
        val outlined = outlineBorders(grayImage)
        return outlined
    }

    /**
     * Performs the Otsu's Thresholding algorithm.
     * Analog of __binarize
     */
    private fun binarize(ecg: EcgImage): Int {
        val L = 256
        val data = ecg.getData()
        val N = data.rows() * data.cols()

        // Calculate histogram
        val histogram = IntArray(L) { 0 }

        for (row in 0 until data.rows()) {
            for (col in 0 until data.cols()) {
                val pixelValue = data.get(row, col)[0].toInt()
                if (pixelValue in 0 until L) {
                    histogram[pixelValue]++
                }
            }
        }

        // Normalize histogram
        val p = DoubleArray(L) { histogram[it].toDouble() / N }

        // Helper functions
        fun omega(k: Int): Double = p.take(k).sum()
        fun mu(k: Int): Double = p.take(k).mapIndexed { i, p_i -> (i + 1) * p_i }.sum()

        val muT = mu(L)

        // Calculate sigma_b for each k
        val sigmaBValues = DoubleArray(L)
        for (k in 0 until L) {
            val omegaK = omega(k)
            if (omegaK != 0.0 && omegaK != 1.0) {
                val muK = mu(k)
                sigmaBValues[k] = (muT * omegaK - muK).pow(2.0) / (omegaK * (1 - omegaK))
            } else {
                sigmaBValues[k] = 0.0
            }
        }

        // Find k with maximum sigma_b
        var maxSigmaB = 0.0
        var bestK = 0
        for (k in 0 until L) {
            if (sigmaBValues[k] > maxSigmaB) {
                maxSigmaB = sigmaBValues[k]
                bestK = k
            }
        }

        return bestK
    }

    /**
     * Outlines an ECG image by joining disconnected signals.
     * Analog of __outline_borders
     */
    private fun outlineBorders(ecg: EcgImage): EcgImage {
        val WHITE = 255.0
        val BLACK = 0.0
        val MAX_DIST = (0.02 * ecg.width).toInt()
        val data = ecg.getData()

        // Delete thick black lines in borders
        // Top and bottom borders
        for (row in (0..9) + (ecg.height - 10 until ecg.height)) {
            var blackCount = 0
            for (col in 0 until ecg.width) {
                if (data.get(row, col)[0] == BLACK) {
                    blackCount++
                }
            }
            val proportion = blackCount.toFloat() / ecg.width
            if (proportion >= 0.95f) {
                for (col in 0 until ecg.width) {
                    data.put(row, col, WHITE)
                }
            }
        }

        // Left and right borders
        for (col in (0..9) + (ecg.width - 10 until ecg.width)) {
            var blackCount = 0
            for (row in 0 until ecg.height) {
                if (data.get(row, col)[0] == BLACK) {
                    blackCount++
                }
            }
            val proportion = blackCount.toFloat() / ecg.height
            if (proportion >= 0.95f) {
                for (row in 0 until ecg.height) {
                    data.put(row, col, WHITE)
                }
            }
        }

        // Find rows with non-white pixels
        val nonWhiteRows = mutableListOf<Int>()
        for (row in 0 until ecg.height) {
            var hasBlack = false
            for (col in 0 until ecg.width) {
                if (data.get(row, col)[0] == BLACK) {
                    hasBlack = true
                    break
                }
            }
            if (hasBlack) {
                nonWhiteRows.add(row)
            }
        }

        if (nonWhiteRows.isNotEmpty()) {
            val firstRow = nonWhiteRows.first()
            val lastRow = nonWhiteRows.last()

            // Process first and last non-white rows
            for (row in listOf(firstRow, lastRow)) {
                val blackCols = mutableListOf<Int>()
                for (col in 0 until ecg.width) {
                    if (data.get(row, col)[0] == BLACK) {
                        blackCols.add(col)
                    }
                }

                // Connect nearby black pixels
                for (i in 0 until blackCols.size - 1) {
                    val p1 = blackCols[i]
                    val p2 = blackCols[i + 1]
                    if (abs(p1 - p2) <= MAX_DIST) {
                        for (col in p1..p2) {
                            data.put(row, col, BLACK)
                        }
                    }
                }
            }
        }

        ecg.setData(data)
        return ecg
    }

    /**
     * Filter large rectangles
     */
    private fun filterLargeRectangles(
        rects: List<CvRect>,
        imageWidth: Int,
        imageHeight: Int,
        minAreaRatio: Float = 0.05f
    ): List<CvRect> {
        val minArea = minAreaRatio * imageWidth * imageHeight
        return rects.filter { rect -> rect.width * rect.height >= minArea }
    }

    /**
     * Merge overlapping rectangles
     */
    private fun mergeOverlappingRectangles(rects: List<CvRect>): List<CvRect> {
        if (rects.isEmpty()) return emptyList()

        // Determine minimum image size needed
        val maxX = rects.maxOf { rect -> rect.x + rect.width }
        val maxY = rects.maxOf { rect -> rect.y + rect.height }

        val canvas = Mat.zeros(Size(maxX.toDouble() + 1, maxY.toDouble() + 1), CvType.CV_8UC1)

        for (rect in rects) {
            Imgproc.rectangle(
                canvas,
                CvPoint(rect.x.toDouble(), rect.y.toDouble()),
                CvPoint((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble()),
                Scalar(255.0),
                -1
            )
        }

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            canvas, contours, hierarchy,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        )

        val mergedRects = contours.map { contour ->
            Imgproc.boundingRect(contour)
        }

        return mergedRects
    }
}