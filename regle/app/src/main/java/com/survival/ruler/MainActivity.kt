package com.survival.ruler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RulerApp()
        }
    }
}

@Composable
fun RulerApp() {
    val context = LocalContext.current

    val displayMetrics = context.resources.displayMetrics
    val dpi = displayMetrics.ydpi
    val pxPerCm = dpi / 2.54f
    val pxPerInch = dpi

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E))
    ) {
        CentimeterRulerView(
            pxPerCm = pxPerCm,
            modifier = Modifier.align(Alignment.CenterStart).width(80.dp).fillMaxHeight()
        )
        InchRulerView(
            pxPerInch = pxPerInch,
            modifier = Modifier.align(Alignment.CenterEnd).width(80.dp).fillMaxHeight()
        )
    }
}

@Composable
private fun CentimeterRulerView(
    pxPerCm: Float,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val markColor = Color(0xFF4A9FFF)
    val textColor = Color.White

    Canvas(modifier = modifier.fillMaxHeight()) {
        val canvasHeight = size.height
        val majorMarkLength = 24f
        val minorMarkLength = 12f
        val markWidth = 2.5f
        val markerStartX = size.width - majorMarkLength - 2f

        // Calculate number of units to draw
        val numUnits = (canvasHeight / pxPerCm).toInt() + 1

        for (i in 0..numUnits) {
            val yPos = i * pxPerCm

            if (yPos > canvasHeight) break

            // Draw major mark (every cm)
            drawRect(
                color = markColor,
                topLeft = Offset(markerStartX, yPos - markWidth / 2),
                size = androidx.compose.ui.geometry.Size(majorMarkLength, markWidth)
            )

            // Draw number (every cm, starting from 1)
            if (i > 0) {
                val textLayout = textMeasurer.measure(
                    text = i.toString(),
                    style = TextStyle(fontSize = 11.sp, color = textColor)
                )
                drawText(
                    textLayout,
                    color = textColor,
                    topLeft = Offset(
                        markerStartX - textLayout.size.width - 6f,
                        yPos - textLayout.size.height / 2
                    )
                )
            }

            // Draw minor marks (every mm, 9 between each cm)
            for (j in 1..9) {
                val minorYPos = yPos + (j * pxPerCm / 10f)
                if (minorYPos < canvasHeight) {
                    drawRect(
                        color = markColor,
                        topLeft = Offset(markerStartX + (majorMarkLength - minorMarkLength), minorYPos - markWidth / 2),
                        size = androidx.compose.ui.geometry.Size(minorMarkLength, markWidth)
                    )
                }
            }
        }
    }
}

@Composable
private fun InchRulerView(
    pxPerInch: Float,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val markColor = Color(0xFF4A9FFF)
    val textColor = Color.White

    Canvas(modifier = modifier.fillMaxHeight()) {
        val canvasHeight = size.height
        val majorMarkLength = 24f
        val minorMarkLength = 12f
        val markWidth = 2.5f
        val markerStartX = 2f

        // Calculate number of units to draw
        val numUnits = (canvasHeight / pxPerInch).toInt() + 1

        for (i in 0..numUnits) {
            val yPos = i * pxPerInch

            if (yPos > canvasHeight) break

            // Draw major mark (every inch)
            drawRect(
                color = markColor,
                topLeft = Offset(markerStartX, yPos - markWidth / 2),
                size = androidx.compose.ui.geometry.Size(majorMarkLength, markWidth)
            )

            // Draw number (every inch, starting from 1)
            if (i > 0) {
                val textLayout = textMeasurer.measure(
                    text = i.toString(),
                    style = TextStyle(fontSize = 11.sp, color = textColor)
                )
                drawText(
                    textLayout,
                    color = textColor,
                    topLeft = Offset(
                        markerStartX + majorMarkLength + 6f,
                        yPos - textLayout.size.height / 2
                    )
                )
            }

            // Draw minor marks (every 0.1 inch, 9 between each inch)
            for (j in 1..9) {
                val minorYPos = yPos + (j * pxPerInch / 10f)
                if (minorYPos < canvasHeight) {
                    drawRect(
                        color = markColor,
                        topLeft = Offset(markerStartX, minorYPos - markWidth / 2),
                        size = androidx.compose.ui.geometry.Size(minorMarkLength, markWidth)
                    )
                }
            }
        }
    }
}
