package com.survival.converter

object ConverterLogic {

    // Length conversions (to meters)
    fun kmToMeters(km: Double) = km * 1000
    fun milesToMeters(miles: Double) = miles * 1609.34
    fun yardsToMeters(yards: Double) = yards * 0.9144
    fun feetToMeters(feet: Double) = feet * 0.3048

    fun metersToKm(m: Double) = m / 1000
    fun metersToMiles(m: Double) = m / 1609.34
    fun metersToYards(m: Double) = m / 0.9144
    fun metersToFeet(m: Double) = m / 0.3048

    // Temperature conversions
    fun celsiusToFahrenheit(c: Double) = (c * 9 / 5) + 32
    fun celsiusToKelvin(c: Double) = c + 273.15
    fun fahrenheitToCelsius(f: Double) = (f - 32) * 5 / 9
    fun fahrenheitToKelvin(f: Double) = fahrenheitToCelsius(f) + 273.15
    fun kelvinToCelsius(k: Double) = k - 273.15
    fun kelvinToFahrenheit(k: Double) = celsiusToFahrenheit(kelvinToCelsius(k))

    // Volume conversions (to liters)
    fun litersToGallonsUS(l: Double) = l / 3.785
    fun litersToGallonsImp(l: Double) = l / 4.546
    fun litersToMilliliters(l: Double) = l * 1000
    fun litersToFluidOunces(l: Double) = l * 33.814

    fun gallonsUSToLiters(gal: Double) = gal * 3.785
    fun gallonsImpToLiters(gal: Double) = gal * 4.546
    fun millilitersToLiters(ml: Double) = ml / 1000
    fun fluidOuncesToLiters(fl: Double) = fl / 33.814

    // Weight conversions (to kilograms)
    fun kilogramsToPounds(kg: Double) = kg * 2.20462
    fun kilogramsToGrams(kg: Double) = kg * 1000
    fun kilogramsToOunces(kg: Double) = kg * 35.274

    fun poundsToKilograms(lb: Double) = lb / 2.20462
    fun gramsToKilograms(g: Double) = g / 1000
    fun ouncesToKilograms(oz: Double) = oz / 35.274
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
