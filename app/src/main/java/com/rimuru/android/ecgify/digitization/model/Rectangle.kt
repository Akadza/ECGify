package com.rimuru.android.ecgify.digitization.model

data class Rectangle(
    val topLeft: Point,
    val bottomRight: Point
) {
    val width: Float get() = bottomRight.x - topLeft.x
    val height: Float get() = bottomRight.y - topLeft.y

    fun toIntRect(): IntRect = IntRect(
        topLeft.toIntPoint(),
        bottomRight.toIntPoint()
    )
}

data class IntRect(
    val topLeft: IntPoint,
    val bottomRight: IntPoint
) {
    val width: Int get() = bottomRight.x - topLeft.x
    val height: Int get() = bottomRight.y - topLeft.y

    fun toFloatRect(): Rectangle = Rectangle(
        topLeft.toFloatPoint(),
        bottomRight.toFloatPoint()
    )
}