package com.rimuru.android.ecgify.digitization.model

data class Point(
    val x: Float,
    val y: Float
) {
    fun toIntPoint(): IntPoint = IntPoint(x.toInt(), y.toInt())
}

data class IntPoint(
    val x: Int,
    val y: Int
) {
    fun toFloatPoint(): Point = Point(x.toFloat(), y.toFloat())
}