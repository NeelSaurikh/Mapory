package com.example.mapory.dataclass

import java.io.Serializable

data class Wind(
    val speed: Double,
    val deg: Int
) : Serializable