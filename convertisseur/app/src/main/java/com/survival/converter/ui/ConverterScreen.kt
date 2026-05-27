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

val accentBlue = Color(0xFF4A9FFF)
val bgColor = Color(0xFF1A1A2E)
val darkNavy = Color(0xFF0F3460)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen() {
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
            color = accentBlue,
            fontSize = 28.sp,
            modifier = Modifier.padding(16.dp)
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = darkNavy,
            contentColor = accentBlue,
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
            val convertFn: (Double, String, String) -> Double = when (selectedTab) {
                0 -> ConverterLogic::convertLength
                1 -> ConverterLogic::convertTemperature
                2 -> ConverterLogic::convertVolume
                3 -> ConverterLogic::convertWeight
                else -> { v, _, _ -> v }
            }

            val units = when (selectedTab) {
                0 -> listOf("km", "miles", "mètres", "yards", "pieds")
                1 -> listOf("°C", "°F", "K")
                2 -> listOf("litres", "gallons (US)", "ml", "fl oz")
                3 -> listOf("kg", "lbs", "grammes", "oz")
                else -> emptyList()
            }

            val defaults = when (selectedTab) {
                0 -> Pair("km", "miles")
                1 -> Pair("°C", "°F")
                2 -> Pair("litres", "gallons (US)")
                3 -> Pair("kg", "lbs")
                else -> Pair("", "")
            }

            var fromUnit by remember(selectedTab) { mutableStateOf(defaults.first) }
            var toUnit by remember(selectedTab) { mutableStateOf(defaults.second) }

            ConversionUI(
                inputValue = inputValue,
                onInputChange = { inputValue = it },
                fromUnit = fromUnit,
                toUnit = toUnit,
                units = units,
                onFromUnitChange = { fromUnit = it },
                onToUnitChange = { toUnit = it },
                onConvert = { input ->
                    val output = convertFn(input, fromUnit, toUnit)
                    result = ConversionResult(input, fromUnit, output, toUnit)
                }
            )

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
        colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Text("Convertir", color = Color.White)
    }
}

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
            colors = ButtonDefaults.buttonColors(containerColor = darkNavy),
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
