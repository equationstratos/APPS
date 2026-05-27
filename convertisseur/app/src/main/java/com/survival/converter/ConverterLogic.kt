package com.survival.converter

object ConverterLogic {

    fun convertLength(value: Double, from: String, to: String): Double {
        if (from == to) return value
        val meters = when (from) {
            "km" -> value * 1000
            "miles" -> value * 1609.34
            "mètres" -> value
            "yards" -> value * 0.9144
            "pieds" -> value * 0.3048
            else -> value
        }
        return when (to) {
            "km" -> meters / 1000
            "miles" -> meters / 1609.34
            "mètres" -> meters
            "yards" -> meters / 0.9144
            "pieds" -> meters / 0.3048
            else -> meters
        }
    }

    fun convertTemperature(value: Double, from: String, to: String): Double {
        if (from == to) return value
        val celsius = when (from) {
            "°C" -> value
            "°F" -> (value - 32) * 5 / 9
            "K" -> value - 273.15
            else -> value
        }
        return when (to) {
            "°C" -> celsius
            "°F" -> (celsius * 9 / 5) + 32
            "K" -> celsius + 273.15
            else -> celsius
        }
    }

    fun convertVolume(value: Double, from: String, to: String): Double {
        if (from == to) return value
        val liters = when (from) {
            "litres" -> value
            "gallons (US)" -> value * 3.785
            "ml" -> value / 1000
            "fl oz" -> value / 33.814
            else -> value
        }
        return when (to) {
            "litres" -> liters
            "gallons (US)" -> liters / 3.785
            "ml" -> liters * 1000
            "fl oz" -> liters * 33.814
            else -> liters
        }
    }

    fun convertWeight(value: Double, from: String, to: String): Double {
        if (from == to) return value
        val kg = when (from) {
            "kg" -> value
            "lbs" -> value / 2.20462
            "grammes" -> value / 1000
            "oz" -> value / 35.274
            else -> value
        }
        return when (to) {
            "kg" -> kg
            "lbs" -> kg * 2.20462
            "grammes" -> kg * 1000
            "oz" -> kg * 35.274
            else -> kg
        }
    }
}

data class ConversionResult(
    val input: Double,
    val inputUnit: String,
    val output: Double,
    val outputUnit: String
) {
    val formatted: String
        get() = "%.4g %s = %.4g %s".format(input, inputUnit, output, outputUnit)
}
