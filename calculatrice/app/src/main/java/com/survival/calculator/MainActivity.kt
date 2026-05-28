package com.survival.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CalculatorApp() }
    }
}

@Composable
fun CalculatorApp() {
    val bg = Color(0xFF1A1A2E)
    val accent = Color(0xFF4A9FFF)
    val navy = Color(0xFF0F3460)

    var display by remember { mutableStateOf("0") }
    var operand1 by remember { mutableStateOf(0.0) }
    var operator by remember { mutableStateOf("") }
    var newInput by remember { mutableStateOf(true) }

    fun onDigit(d: String) {
        display = if (newInput || display == "0") d else display + d
        newInput = false
    }

    fun onDot() {
        if (newInput) { display = "0."; newInput = false }
        else if (!display.contains('.')) display += "."
    }

    fun calc(): Double {
        val b = display.toDoubleOrNull() ?: 0.0
        return when (operator) {
            "+" -> operand1 + b
            "-" -> operand1 - b
            "×" -> operand1 * b
            "÷" -> if (b != 0.0) operand1 / b else 0.0
            else -> b
        }
    }

    fun onOp(op: String) {
        if (operator.isNotEmpty() && !newInput) {
            val res = calc()
            display = formatResult(res)
            operand1 = res
        } else {
            operand1 = display.toDoubleOrNull() ?: 0.0
        }
        operator = op
        newInput = true
    }

    fun onEquals() {
        if (operator.isNotEmpty()) {
            display = formatResult(calc())
            operator = ""
            newInput = true
        }
    }

    fun onClear() { display = "0"; operand1 = 0.0; operator = ""; newInput = true }

    Column(Modifier.fillMaxSize().background(bg).padding(16.dp), verticalArrangement = Arrangement.Bottom) {
        Text("Calculatrice", color = accent, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp))
        Box(Modifier.fillMaxWidth().background(navy, RoundedCornerShape(12.dp)).padding(24.dp)) {
            Text(display, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Light,
                textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))

        val rows = listOf(
            listOf("C", "±", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "=", "")
        )

        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.spacedBy(8.dp)) {
                row.forEach { label ->
                    if (label.isEmpty()) { Spacer(Modifier.weight(1f)) }
                    else {
                        val isOp = label in listOf("÷", "×", "-", "+", "=")
                        val isAct = label in listOf("C", "±", "%")
                        Box(Modifier.weight(1f).aspectRatio(1f)
                            .background(if (isOp) accent else if (isAct) navy else Color(0xFF16213E), RoundedCornerShape(50))
                            .clickable {
                                when (label) {
                                    "C" -> onClear()
                                    "±" -> { val v = display.toDoubleOrNull() ?: 0.0; display = formatResult(-v) }
                                    "%" -> { val v = display.toDoubleOrNull() ?: 0.0; display = formatResult(v / 100) }
                                    "÷", "×", "-", "+" -> onOp(label)
                                    "=" -> onEquals()
                                    "." -> onDot()
                                    else -> onDigit(label)
                                }
                            }, Alignment.Center
                        ) {
                            Text(label, color = if (isOp) bg else Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

fun formatResult(d: Double): String {
    return if (d == d.toLong().toDouble()) d.toLong().toString() else "%.4f".format(d).trimEnd('0').trimEnd('.')
}
