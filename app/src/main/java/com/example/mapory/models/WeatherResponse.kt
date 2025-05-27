package com.example.mapory.models

import com.example.mapory.dataclass.Clouds
import com.example.mapory.dataclass.Coord
import com.example.mapory.dataclass.Main
import com.example.mapory.dataclass.Sys
import com.example.mapory.dataclass.Weather
import com.example.mapory.dataclass.Wind
import java.io.Serializable


data class WeatherResponse (
    val coord: Coord,
    val weather : List<Weather>,
    val base : String,
    val main : Main,
    val visibility: Int,
    val wind: Wind,
    val clouds: Clouds,
    val dt: Int,
    val sys: Sys,
    val id : Int,
    val name: String,
    val cod: Int
) : Serializable