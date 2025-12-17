package com.rimuru.android.ecgify.digitization.model

import android.graphics.Bitmap
import android.graphics.Color
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Representation of an Image. It contains the image data itself, and also
 * the color space in which it is stored. Could be GRAY, BGR, RGB or HSV.
 * Analog of utils.graphics.Image from ECGMiner
 */
class EcgImage {
    private var data: Mat
    private var colorSpace: ColorSpace
    val height: Int
    val width: Int
    val channels: Int

    companion object {
        // Color constants
        const val BLACK_GRAY = 0
        const val WHITE_GRAY = 255
        val BLACK_BGR = Triple(0, 0, 0)
        val WHITE_BGR = Triple(255, 255, 255)
        val BLACK_RGB = Triple(0, 0, 0)
        val WHITE_RGB = Triple(255, 255, 255)
        val BLACK_HSV = Triple(0, 0, 0)
        val WHITE_HSV = Triple(0, 0, 255)
    }

    /**
     * Initialization of the image, by default is in BGR format.
     * Constructor from Bitmap
     */
    constructor(bitmap: Bitmap) {
        data = Mat()
        Utils.bitmapToMat(bitmap, data)
        colorSpace = ColorSpace.BGR
        height = data.rows()
        width = data.cols()
        channels = data.channels()
    }

    /**
     * Initialization of the image from Mat
     */
    constructor(mat: Mat, colorSpace: ColorSpace = ColorSpace.BGR) {
        data = mat.clone()
        this.colorSpace = colorSpace
        height = data.rows()
        width = data.cols()
        channels = data.channels()
    }

    /**
     * Constructor from byte array (for PDF conversion support)
     * Note: PDF support would require additional library like PDFRenderer
     */
    constructor(bytes: ByteArray) {
        val inputStream = ByteArrayInputStream(bytes)
        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
        data = Mat()
        Utils.bitmapToMat(bitmap, data)
        colorSpace = ColorSpace.BGR
        height = data.rows()
        width = data.cols()
        channels = data.channels()
    }

    /**
     * Get an slice of image data.
     * Analog of __getitem__
     */
    operator fun get(index: List<Int>): Mat {
        return when (index.size) {
            2 -> data.submat(index[0], index[0] + 1, index[1], index[1] + 1)
            4 -> data.submat(index[0], index[1], index[2], index[3])
            else -> throw IllegalArgumentException("Invalid index size: ${index.size}")
        }
    }

    /**
     * Get pixel value
     */
    operator fun get(row: Int, col: Int): Int {
        return when (colorSpace) {
            ColorSpace.GRAY -> {
                val pixel = data.get(row, col)
                pixel[0].toInt()
            }
            else -> {
                // For color images, return first channel or average?
                val pixel = data.get(row, col)
                pixel[0].toInt()
            }
        }
    }

    /**
     * Get row slice
     */
    operator fun get(row: Int, colRange: IntRange): Mat {
        return data.submat(row, row + 1, colRange.first, colRange.last + 1)
    }

    /**
     * Get column slice
     */
    operator fun get(rowRange: IntRange, col: Int): Mat {
        return data.submat(rowRange.first, rowRange.last + 1, col, col + 1)
    }

    /**
     * Get 2D slice
     */
    operator fun get(rowRange: IntRange, colRange: IntRange): Mat {
        return data.submat(rowRange.first, rowRange.last + 1, colRange.first, colRange.last + 1)
    }

    /**
     * Set an slice of image data.
     * Analog of __setitem__
     */
    operator fun set(index: List<Int>, value: Mat) {
        when (index.size) {
            2 -> value.copyTo(data.submat(index[0], index[0] + 1, index[1], index[1] + 1))
            4 -> value.copyTo(data.submat(index[0], index[1], index[2], index[3]))
            else -> throw IllegalArgumentException("Invalid index size: ${index.size}")
        }
    }

    /**
     * Set pixel value
     */
    operator fun set(row: Int, col: Int, value: Int) {
        when (colorSpace) {
            ColorSpace.GRAY -> data.put(row, col, value.toDouble())
            ColorSpace.BGR -> data.put(row, col, value.toDouble(), value.toDouble(), value.toDouble())
            ColorSpace.RGB -> data.put(row, col, value.toDouble(), value.toDouble(), value.toDouble())
            ColorSpace.HSV -> data.put(row, col, 0.0, 0.0, value.toDouble())
        }
    }

    /**
     * Set row slice
     */
    operator fun set(row: Int, colRange: IntRange, value: Int) {
        for (col in colRange) {
            this[row, col] = value
        }
    }

    /**
     * Set column slice
     */
    operator fun set(rowRange: IntRange, col: Int, value: Int) {
        for (row in rowRange) {
            this[row, col] = value
        }
    }

    /**
     * Set 2D slice
     */
    operator fun set(rowRange: IntRange, colRange: IntRange, value: Int) {
        for (row in rowRange) {
            for (col in colRange) {
                this[row, col] = value
            }
        }
    }

    /**
     * Returns the image data.
     */
    fun getData(): Mat {
        return data.clone()
    }

    /**
     * Set the image data.
     */
    fun setData(newData: Mat) {
        data = newData.clone()
    }

    /**
     * Get the white color depending of current image color space.
     * Analog of white property
     */
    val white: Any
        get() = when (colorSpace) {
            ColorSpace.GRAY -> WHITE_GRAY
            ColorSpace.HSV -> WHITE_HSV
            ColorSpace.RGB -> WHITE_RGB
            ColorSpace.BGR -> WHITE_BGR
        }

    /**
     * Get the black color depending of current image color space.
     * Analog of black property
     */
    val black: Any
        get() = when (colorSpace) {
            ColorSpace.GRAY -> BLACK_GRAY
            ColorSpace.HSV -> BLACK_HSV
            ColorSpace.RGB -> BLACK_RGB
            ColorSpace.BGR -> BLACK_BGR
        }

    /**
     * Get a deep copy of the image.
     * Analog of copy()
     */
    fun copy(): EcgImage {
        return EcgImage(data.clone(), colorSpace)
    }

    /**
     * Save image in PNG.
     * Note: In Android, we typically save to file system or gallery
     */
    fun save(path: String): Boolean {
        try {
            // Convert to BGR for saving (OpenCV uses BGR)
            val tempImage = this.copy()
            tempImage.toBGR()

            // Create bitmap and save
            val bitmap = tempImage.toBitmap()

            FileOutputStream(path).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Save image to Bitmap
     */
    fun toBitmap(): Bitmap {
        // Make sure we're in BGR for OpenCV
        val temp = if (colorSpace != ColorSpace.BGR) {
            val tempMat = Mat()
            when (colorSpace) {
                ColorSpace.GRAY -> Imgproc.cvtColor(data, tempMat, Imgproc.COLOR_GRAY2BGR)
                ColorSpace.RGB -> Imgproc.cvtColor(data, tempMat, Imgproc.COLOR_RGB2BGR)
                ColorSpace.HSV -> Imgproc.cvtColor(data, tempMat, Imgproc.COLOR_HSV2BGR)
                else -> data.clone()
            }
            tempMat
        } else {
            data.clone()
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(temp, bitmap)
        return bitmap
    }

    /**
     * Crop the image.
     * Analog of crop()
     */
    fun crop(r: Rectangle) {
        val tl = r.topLeft
        val br = r.bottomRight
        data = data.submat(
            tl.y.roundToInt(),
            br.y.roundToInt(),
            tl.x.roundToInt(),
            br.x.roundToInt()
        )
        // Update dimensions
        // Note: We can't update height/width as they are val
        // We'll create a new instance instead
    }

    /**
     * Thresholds the image, if a pixel is smaller than the threshold, it is
     * set to 0, otherwise it is set to a certain value.
     * Analog of threshold()
     */
    fun threshold(thres: Int, value: Int): EcgImage {
        val result = Mat()
        Imgproc.threshold(data, result, thres.toDouble(), value.toDouble(), Imgproc.THRESH_BINARY)
        val newImage = EcgImage(result, colorSpace)
        newImage.toGray()
        return newImage
    }

    /**
     * Creates a line in the image.
     * Analog of line()
     */
    fun line(p1: Point, p2: Point, color: Triple<Int, Int, Int>, thickness: Int) {
        // Convert to BGR for drawing if not already
        if (colorSpace != ColorSpace.BGR) {
            toBGR()
        }

        Imgproc.line(
            data,
            org.opencv.core.Point(p1.x.toDouble(), p1.y.toDouble()),
            org.opencv.core.Point(p2.x.toDouble(), p2.y.toDouble()),
            Scalar(color.first.toDouble(), color.second.toDouble(), color.third.toDouble()),
            thickness
        )
    }

    /**
     * Check if image is in GRAY space.
     */
    fun isGray(): Boolean = colorSpace == ColorSpace.GRAY

    /**
     * Check if image is in BGR space.
     */
    fun isBGR(): Boolean = colorSpace == ColorSpace.BGR

    /**
     * Check if image is in RGB space.
     */
    fun isRGB(): Boolean = colorSpace == ColorSpace.RGB

    /**
     * Check if image is in HSV space.
     */
    fun isHSV(): Boolean = colorSpace == ColorSpace.HSV

    /**
     * Converts image into GRAY color space.
     */
    fun toGray() {
        when (colorSpace) {
            ColorSpace.RGB -> {
                val temp = Mat()
                Imgproc.cvtColor(data, temp, Imgproc.COLOR_RGB2GRAY)
                data = temp
            }
            ColorSpace.BGR -> {
                val temp = Mat()
                Imgproc.cvtColor(data, temp, Imgproc.COLOR_BGR2GRAY)
                data = temp
            }
            ColorSpace.HSV -> {
                // HSV -> BGR -> GRAY (OpenCV doesn't have direct HSV to GRAY)
                val tempBgr = Mat()
                Imgproc.cvtColor(data, tempBgr, Imgproc.COLOR_HSV2BGR)
                Imgproc.cvtColor(tempBgr, data, Imgproc.COLOR_BGR2GRAY)
            }
            ColorSpace.GRAY -> {
                // Already in GRAY
                return
            }
        }
        colorSpace = ColorSpace.GRAY
    }

    /**
     * Converts image into BGR color space.
     */
    fun toBGR() {
        when (colorSpace) {
            ColorSpace.GRAY -> {
                val temp = Mat()
                Imgproc.cvtColor(data, temp, Imgproc.COLOR_GRAY2BGR)
                data = temp
            }
            ColorSpace.RGB -> {
                val temp = Mat()
                Imgproc.cvtColor(data, temp, Imgproc.COLOR_RGB2BGR)
                data = temp
            }
            ColorSpace.HSV -> {
                val temp = Mat()
                Imgproc.cvtColor(data, temp, Imgproc.COLOR_HSV2BGR)
                data = temp
            }
            ColorSpace.BGR -> {
                // Already in BGR
                return
            }
        }
        colorSpace = ColorSpace.BGR
    }

    /**
     * Converts image into RGB color space.
     */
    fun toRGB() {
        when (colorSpace) {
            ColorSpace.GRAY -> {
                val temp = Mat()
                Imgproc.cvtColor(data, temp, Imgproc.COLOR_GRAY2RGB)
                data = temp
            }
            ColorSpace.BGR -> {
                val temp = Mat()
                Imgproc.cvtColor(data, temp, Imgproc.COLOR_BGR2RGB)
                data = temp
            }
            ColorSpace.HSV -> {
                // HSV -> BGR -> RGB
                val tempBgr = Mat()
                Imgproc.cvtColor(data, tempBgr, Imgproc.COLOR_HSV2BGR)
                Imgproc.cvtColor(tempBgr, data, Imgproc.COLOR_BGR2RGB)
            }
            ColorSpace.RGB -> {
                // Already in RGB
                return
            }
        }
        colorSpace = ColorSpace.RGB
    }

    /**
     * Converts image into HSV color space.
     */
    fun toHSV() {
        when (colorSpace) {
            ColorSpace.GRAY -> {
                // GRAY -> BGR -> HSV (GRAY doesn't have color information for HSV)
                val tempBgr = Mat()
                Imgproc.cvtColor(data, tempBgr, Imgproc.COLOR_GRAY2BGR)
                Imgproc.cvtColor(tempBgr, data, Imgproc.COLOR_BGR2HSV)
            }
            ColorSpace.BGR -> {
                val temp = Mat()
                Imgproc.cvtColor(data, temp, Imgproc.COLOR_BGR2HSV)
                data = temp
            }
            ColorSpace.RGB -> {
                // RGB -> BGR -> HSV
                val tempBgr = Mat()
                Imgproc.cvtColor(data, tempBgr, Imgproc.COLOR_RGB2BGR)
                Imgproc.cvtColor(tempBgr, data, Imgproc.COLOR_BGR2HSV)
            }
            ColorSpace.HSV -> {
                // Already in HSV
                return
            }
        }
        colorSpace = ColorSpace.HSV
    }

    /**
     * For compatibility with existing code
     */
    fun to_GRAY() = toGray()
    fun to_BGR() = toBGR()
    fun to_RGB() = toRGB()
    fun to_HSV() = toHSV()

    /**
     * Get string representation
     */
    override fun toString(): String {
        return "EcgImage(${width}x${height}, $colorSpace, channels=$channels)"
    }
}