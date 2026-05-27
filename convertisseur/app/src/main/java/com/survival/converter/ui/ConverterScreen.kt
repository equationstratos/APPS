package com.survival.converter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.survival.converter.ConverterLogic
import com.survival.converter.ConversionResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen() {
    val bgColor = Color(0xFF1A1A2E)
    val accentColor = Color(0xFFE94560)
    val darkNavy = Color(0xFF0F3460)

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Longueur", "Température", "Volume", "Poids")

    var inputValue by remember { mutableStateOf("1.0") }
    var result by remember { mutableStateOf<ConversionResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Convertisseur",
            color = accentColor,
            fontSize = 28.sp,
            modifier = Modifier.padding(16.dp)
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = darkNavy,
            contentColor = accentColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 12.sp) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            when (selectedTab) {
                0 -> LengthConverter(inputValue, { inputValue = it }, { result = it })
                1 -> TemperatureConverter(inputValue, { inputValue = it }, { result = it })
                2 -> VolumeConverter(inputValue, { inputValue = it }, { result = it })
                3 -> WeightConverter(inputValue, { inputValue = it }, { result = it })
            }

            if (result != null) {
                Text(
                    text = result!!.formatted,
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 24.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LengthConverter(
    inputValue: String,
    onInputChange: (String) -> Unit,
    onResult: (ConversionResult) -> Unit
) {
    val units = listOf("km", "miles", "mètres", "yards", "pieds")
    var fromUnit by remember { mutableStateOf("km") }
    var toUnit by remember { mutableStateOf("miles") }
    var fromDropdownOpen by remember { mutableStateOf(false) }
    var toDropdownOpen by remember { mutableStateOf(false) }

    ConversionUI(
        inputValue = inputValue,
        onInputChange = onInputChange,
        fromUnit = fromUnit,
        toUnit = toUnit,
        units = units,
        onFromUnitChange = { fromUnit = it },
        onToUnitChange = { toUnit = it },
        onConvert = { input ->
            val result = when {
                fromUnit == "km" && toUnit == "miles" -> ConverterLogic.kmToMeters(input).let { ConverterLogic.metersToMiles(it) }
                fromUnit == "km" && toUnit == "mètres" -> ConverterLogic.kmToMeters(input)
                fromUnit == "miles" && toUnit == "km" -> ConverterLogic.milesToMeters(input).let { ConverterLogic.metersToKm(it) }
                fromUnit == "miles" && toUnit == "mètres" -> ConverterLogic.milesToMeters(input)
                fromUnit == "mètres" && toUnit == "km" -> ConverterLogic.metersToKm(input)
                fromUnit == "mètres" && toUnit == "miles" -> ConverterLogic.metersToMiles(input)
                else -> input
            }
            onResult(ConversionResult(input, fromUnit, result, toUnit))
        }
    )
}

@Composable
private fun TemperatureConverter(
    inputValue: String,
    onInputChange: (String) -> Unit,
    onResult: (ConversionResult) -> Unit
) {
    val units = listOf("°C", "°F", "K")
    var fromUnit by remember { mutableStateOf("°C") }
    var toUnit by remember { mutableStateOf("°F") }

    ConversionUI(
        inputValue = inputValue,
        onInputChange = onInputChange,
        fromUnit = fromUnit,
        toUnit = toUnit,
        units = units,
        onFromUnitChange = { fromUnit = it },
        onToUnitChange = { toUnit = it },
        onConvert = { input ->
            val result = when {
                fromUnit == "°C" && toUnit == "°F" -> ConverterLogic.celsiusToFahrenheit(input)
                fromUnit == "°C" && toUnit == "K" -> ConverterLogic.celsiusToKelvin(input)
                fromUnit == "°F" && toUnit == "°C" -> ConverterLogic.fahrenheitToCelsius(input)
                fromUnit == "°F" && toUnit == "K" -> ConverterLogic.fahrenheitToKelvin(input)
                fromUnit == "K" && toUnit == "°C" -> ConverterLogic.kelvinToCelsius(input)
                fromUnit == "K" && toUnit == "°F" -> ConverterLogic.kelvinToFahrenheit(input)
                else -> input
            }
            onResult(ConversionResult(input, fromUnit, result, toUnit))
        }
    )
}

@Composable
private fun VolumeConverter(
    inputValue: String,
    onInputChange: (String) -> Unit,
    onResult: (ConversionResult) -> Unit
) {
    val units = listOf("litres", "gallons (US)", "ml", "fl oz")
    var fromUnit by remember { mutableStateOf("litres") }
    var toUnit by remember { mutableStateOf("gallons (US)") }

    ConversionUI(
        inputValue = inputValue,
        onInputChange = onInputChange,
        fromUnit = fromUnit,
        toUnit = toUnit,
        units = units,
        onFromUnitChange = { fromUnit = it },
        onToUnitChange = { toUnit = it },
        onConvert = { input ->
            val result = when {
                fromUnit == "litres" && toUnit == "gallons (US)" -> ConverterLogic.litersToGallonsUS(input)
                fromUnit == "litres" && toUnit == "ml" -> ConverterLogic.litersToMilliliters(input)
                fromUnit == "gallons (US)" && toUnit == "litres" -> ConverterLogic.gallonsUSToLiters(input)
                fromUnit == "ml" && toUnit == "litres" -> ConverterLogic.millilitersToLiters(input)
                else -> input
            }
            onResult(ConversionResult(input, fromUnit, result, toUnit))
        }
    )
}

@Composable
private fun WeightConverter(
    inputValue: String,
    onInputChange: (String) -> Unit,
    onResult: (ConversionResult) -> Unit
) {
    val units = listOf("kg", "lbs", "grammes", "oz")
    var fromUnit by remember { mutableStateOf("kg") }
    var toUnit by remember { mutableStateOf("lbs") }

    ConversionUI(
        inputValue = inputValue,
        onInputChange = onInputChange,
        fromUnit = fromUnit,
        toUnit = toUnit,
        units = units,
        onFromUnitChange = { fromUnit = it },
        onToUnitChange = { toUnit = it },
        onConvert = { input ->
            val result = when {
                fromUnit == "kg" && toUnit == "lbs" -> ConverterLogic.kilogramsToPounds(input)
                fromUnit == "kg" && toUnit == "grammes" -> ConverterLogic.kilogramsToGrams(input)
                fromUnit == "lbs" && toUnit == "kg" -> ConverterLogic.poundsToKilograms(input)
                fromUnit == "grammes" && toUnit == "kg" -> ConverterLogic.gramsToKilograms(input)
                else -> input
            }
            onResult(ConversionResult(input, fromUnit, result, toUnit))
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversionUI(
    inputValue: String,
    onInputChange: (String) -> Unit,
    fromUnit: String,
    toUnit: String,
    units: List<String>,
    onFromUnitChange: (String) -> Unit,
    onToUnitChange: (String) -> Unit,
    onConvert: (Double) -> Unit
) {
    val accentColor = Color(0xFFE94560)
    val darkNavy = Color(0xFF0F3460)

    OutlinedTextField(
        value = inputValue,
        onValueChange = onInputChange,
        label = { Text("Valeur") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = Color.White)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UnitDropdown(
            label = "De",
            selectedUnit = fromUnit,
            units = units,
            onSelect = onFromUnitChange,
            modifier = Modifier.weight(1f)
        )
        UnitDropdown(
            label = "À",
            selectedUnit = toUnit,
            units = units,
            onSelect = onToUnitChange,
            modifier = Modifier.weight(1f)
        )
    }

    Button(
        onClick = {
            val input = inputValue.toDoubleOrNull() ?: return@Button
            onConvert(input)
        },
        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Text("Convertir", color = Color.White)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(
    label: String,
    selectedUnit: String,
    units: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("$label: $selectedUnit", color = Color.White, fontSize = 12.sp)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            units.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit) },
                    onClick = {
                        onSelect(unit)
                        expanded = false
                    }
                )
            }
        }
    }
}
