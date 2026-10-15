package com.survival.counter

import android.content.Context
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    private val store by lazy { CounterStore(getSharedPreferences("survie", Context.MODE_PRIVATE)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { SurvieTheme { AppScreen(store) } }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val direction = volumeDirection(keyCode) ?: return super.onKeyDown(keyCode, event)
        // Auto-repeat from a held key is ignored so a long press cannot inflate the count.
        val isRepeat = (event?.repeatCount ?: 0) > 0
        if (!isRepeat && store.add(direction * store.step)) {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        return true
    }

    // Consuming the key-up too keeps the system volume panel and sound from reacting.
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        volumeDirection(keyCode) != null || super.onKeyUp(keyCode, event)

    private fun volumeDirection(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> 1
        KeyEvent.KEYCODE_VOLUME_DOWN -> -1
        else -> null
    }
}
