package com.survival.ruler.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RulerMarks(
    rulerType: RulerType,
    pxPerUnit: Float,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val accentColor = Color(0xFF4A9FFF)
    val darkNavy = Color(0xFF0F3460)

    Canvas(modifier = modifier.fillMaxWidth()) {
        val canvasHeight = size.height
        val majorMarkLength = 20f
        val minorMarkLength = 10f
        val markWidth = 2f

        // Draw marks and numbers
        when (rulerType) {
            RulerType.CENTIMETER -> {
                // Assuming screen is ~15cm tall at typical DPI
                val numUnits = (canvasHeight / pxPerUnit).toInt() + 1

                for (i in 0..numUnits) {
                    val yPos = i * pxPerUnit

                    if (yPos > canvasHeight) break

                    // Draw major mark every cm
                    drawRect(
                        color = accentColor,
                        topLeft = Offset(size.width - majorMarkLength, yPos - markWidth / 2),
                        size = androidx.compose.ui.geometry.Size(majorMarkLength, markWidth)
                    )

                    // Draw number every cm
                    if (i % 1 == 0 && i > 0) {
                        val textLayout = textMeasurer.measure(
                            text = i.toString(),
                            style = TextStyle(fontSize = 10.sp, color = accentColor)
                        )
                        drawText(
                            textLayout,
                            color = accentColor,
                            topLeft = Offset(
                                size.width - majorMarkLength - textLayout.size.width - 8f,
                                yPos - textLayout.size.height / 2
                            )
                        )
                    }

                    // Draw 5 minor marks between major marks
                    for (j in 1..9) {
                        val minorYPos = yPos + (j * pxPerUnit / 10)
                        if (minorYPos < canvasHeight) {
                            drawRect(
                                color = accentColor,
                                topLeft = Offset(
                                    size.width - minorMarkLength,
                                    minorYPos - markWidth / 2
                                ),
                                size = androidx.compose.ui.geometry.Size(minorMarkLength, markWidth)
                            )
                        }
                    }
                }
            }

            RulerType.INCH -> {
                // Assuming screen is ~6 inches tall at typical DPI
                val numUnits = (canvasHeight / pxPerUnit).toInt() + 1

                for (i in 0..numUnits) {
                    val yPos = i * pxPerUnit

                    if (yPos > canvasHeight) break

                    // Draw major mark every inch
                    drawRect(
                        color = accentColor,
                        topLeft = Offset(size.width - majorMarkLength, yPos - markWidth / 2),
                        size = androidx.compose.ui.geometry.Size(majorMarkLength, markWidth)
                    )

                    // Draw number every inch
                    if (i % 1 == 0 && i > 0) {
                        val textLayout = textMeasurer.measure(
                            text = i.toString(),
                            style = TextStyle(fontSize = 10.sp, color = accentColor)
                        )
                        drawText(
                            textLayout,
                            color = accentColor,
                            topLeft = Offset(
                                size.width - majorMarkLength - textLayout.size.width - 8f,
                                yPos - textLayout.size.height / 2
                            )
                        )
                    }

                    // Draw 9 minor marks between major marks (for 0.1 inch intervals)
                    for (j in 1..9) {
                        val minorYPos = yPos + (j * pxPerUnit / 10)
                        if (minorYPos < canvasHeight) {
                            drawRect(
                                color = accentColor,
                                topLeft = Offset(
                                    size.width - minorMarkLength,
                                    minorYPos - markWidth / 2
                                ),
                                size = androidx.compose.ui.geometry.Size(minorMarkLength, markWidth)
                            )
                        }
                    }
                }
            }
        }
    }
}
