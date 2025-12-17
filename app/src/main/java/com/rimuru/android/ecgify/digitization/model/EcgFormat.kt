package com.rimuru.android.ecgify.digitization.model

object EcgFormat {
    val STANDARD = listOf(
        Lead.I,
        Lead.II,
        Lead.III,
        Lead.aVR,
        Lead.aVL,
        Lead.aVF,
        Lead.V1,
        Lead.V2,
        Lead.V3,
        Lead.V4,
        Lead.V5,
        Lead.V6
    )

    val CABRERA = listOf(
        Lead.aVL,
        Lead.I,
        Lead.aVR,
        Lead.II,
        Lead.aVF,
        Lead.III,
        Lead.V1,
        Lead.V2,
        Lead.V3,
        Lead.V4,
        Lead.V5,
        Lead.V6
    )
}