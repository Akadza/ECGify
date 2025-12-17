package com.rimuru.android.ecgify.digitization

/**
 * Error occurred during digitization process.
 * Analog of DigitizationError from utils.error.DigitizationError
 */
class DigitizationError : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}