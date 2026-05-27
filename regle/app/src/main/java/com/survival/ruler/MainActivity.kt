package com.survival.ruler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.survival.ruler.ui.RulerScreen

class MainActivity : ComponentActivity() {

    private var measurementMode by mutableStateOf(MeasurementMode.BOTH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RulerScreen(
                measurementMode = measurementMode,
                onMeasurementModeChange = { newMode ->
                    measurementMode = newMode
                }
            )
        }
    }
}

enum class MeasurementMode {
    BOTH, CM_ONLY, INCHES_ONLY
}
