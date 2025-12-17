package com.rimuru.android.ecgify.digitization.model

enum class Lead(val value: Int) {
    I(0),
    II(1),
    III(2),
    aVR(3),
    aVL(4),
    aVF(5),
    V1(6),
    V2(7),
    V3(8),
    V4(9),
    V5(10),
    V6(11);

    companion object {
        fun fromName(name: String): Lead? {
            return values().find { it.name == name }
        }
    }
}