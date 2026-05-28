package com.survival.bmi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BMIApp() }
    }
}

@Composable
fun BMIApp() {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    val navy = Color(0xFF0F3460)

    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }

    val bmi = remember(weight, height) {
        val w = weight.toDoubleOrNull() ?: 0.0
        val h = (height.toDoubleOrNull() ?: 0.0) / 100.0
        if (h > 0) w / (h * h) else 0.0
    }

    val category = when {
        bmi == 0.0 -> ""
        bmi < 18.5 -> "Insuffisance pondérale"
        bmi < 25.0 -> "Poids normal"
        bmi < 30.0 -> "Surpoids"
        bmi < 35.0 -> "Obésité modérée"
        bmi < 40.0 -> "Obésité sévère"
        else -> "Obésité morbide"
    }

    val color = when {
        bmi == 0.0 -> Color.White
        bmi < 18.5 || bmi >= 30.0 -> Color(0xFFFF6B6B)
        bmi < 25.0 -> Color(0xFF00D084)
        else -> Color(0xFFFFD700)
    }

    val colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
        focusedBorderColor = accent, unfocusedBorderColor = navy,
        focusedLabelColor = accent, unfocusedLabelColor = Color.White
    )

    Column(
        Modifier.fillMaxSize().background(bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Indice de Masse Corporelle", color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = weight, onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Poids (kg)") }, colors = colors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = height, onValueChange = { height = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Taille (cm)") }, colors = colors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(32.dp))

        if (bmi > 0) {
            Box(Modifier.fillMaxWidth().background(navy, RoundedCornerShape(16.dp)).padding(24.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.1f".format(bmi), color = color, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                    Text(category, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
