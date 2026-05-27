package com.survival.ruler.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.survival.ruler.DisplayMetricsHelper
import com.survival.ruler.MeasurementMode

@Composable
fun RulerScreen(
    measurementMode: MeasurementMode,
    onMeasurementModeChange: (MeasurementMode) -> Unit
) {
    val context = LocalContext.current
    val dpi = DisplayMetricsHelper.getScreenDpi(context)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(Color(0xFF1A1A2E))
    ) {
        when (measurementMode) {
            MeasurementMode.BOTH -> {
                CentimeterRuler(modifier = Modifier.align(Alignment.CenterStart), context = context)
                InchRuler(modifier = Modifier.align(Alignment.CenterEnd), context = context)
            }
            MeasurementMode.CM_ONLY -> {
                CentimeterRuler(modifier = Modifier.align(Alignment.CenterStart), context = context)
            }
            MeasurementMode.INCHES_ONLY -> {
                InchRuler(modifier = Modifier.align(Alignment.CenterEnd), context = context)
            }
        }

        // Toggle button at bottom center
        ToggleButton(
            measurementMode = measurementMode,
            onMeasurementModeChange = onMeasurementModeChange,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun CentimeterRuler(
    modifier: Modifier = Modifier,
    context: Context
) {
    val dpi = DisplayMetricsHelper.getScreenDpi(context)
    val pxPerCm = DisplayMetricsHelper.getPxPerCm(dpi)

    Box(
        modifier = modifier
            .width(pxPerCm.dp)
            .fillMaxHeight()
            .background(Color(0xFF0F3460))
    ) {
        RulerMarks(
            rulerType = RulerType.CENTIMETER,
            pxPerUnit = pxPerCm,
            modifier = Modifier.fillMaxHeight()
        )
    }
}

@Composable
private fun InchRuler(
    modifier: Modifier = Modifier,
    context: Context
) {
    val dpi = DisplayMetricsHelper.getScreenDpi(context)
    val pxPerInch = DisplayMetricsHelper.getPxPerInch(dpi)

    Box(
        modifier = modifier
            .width(pxPerInch.dp)
            .fillMaxHeight()
            .background(Color(0xFF0F3460))
    ) {
        RulerMarks(
            rulerType = RulerType.INCH,
            pxPerUnit = pxPerInch,
            modifier = Modifier.fillMaxHeight()
        )
    }
}

@Composable
private fun ToggleButton(
    measurementMode: MeasurementMode,
    onMeasurementModeChange: (MeasurementMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonText = when (measurementMode) {
        MeasurementMode.BOTH -> "Toggle: Both"
        MeasurementMode.CM_ONLY -> "Toggle: Centimeters"
        MeasurementMode.INCHES_ONLY -> "Toggle: Inches"
    }

    Button(
        onClick = {
            val nextMode = when (measurementMode) {
                MeasurementMode.BOTH -> MeasurementMode.CM_ONLY
                MeasurementMode.CM_ONLY -> MeasurementMode.INCHES_ONLY
                MeasurementMode.INCHES_ONLY -> MeasurementMode.BOTH
            }
            onMeasurementModeChange(nextMode)
        },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF4A9FFF),
            contentColor = Color.White
        )
    ) {
        Text(buttonText, fontSize = 12.sp)
    }
}

enum class RulerType {
    CENTIMETER, INCH
}
