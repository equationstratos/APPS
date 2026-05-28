package com.ruview.uwb

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.uwb.RangingParameters
import androidx.core.uwb.UwbAddress
import androidx.core.uwb.UwbComplexChannel
import androidx.core.uwb.UwbDevice
import androidx.core.uwb.UwbManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlin.math.sqrt

/**
 * UWB ranging via androidx.core.uwb (Jetpack).
 *
 * One device acts as Controller, the other as Controlee. Both run this APK;
 * the address of the peer is hard-coded for the demo (replace with real
 * out-of-band exchange — BLE / QR — in production).
 *
 * Detects presence + motion. Cannot detect breathing through walls.
 */
class UwbActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var dist: TextView
    private lateinit var motion: TextView
    private val history = ArrayDeque<Float>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_uwb)
        dist = findViewById(R.id.dist)
        motion = findViewById(R.id.motion)
        scope.launch { startRanging() }
    }

    private suspend fun startRanging() {
        val mgr = UwbManager.createInstance(this)
        val session = mgr.controllerSessionScope()
        val peer = UwbDevice.createForAddress(
            UwbAddress(byteArrayOf(0x01, 0x02))  // demo peer address
        )
        val params = RangingParameters(
            uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
            sessionId = 0x1234,
            subSessionId = 0,
            sessionKeyInfo = null,
            subSessionKeyInfo = null,
            complexChannel = UwbComplexChannel(channel = 9, preambleIndex = 10),
            peerDevices = listOf(peer),
            updateRateType = RangingParameters.RANGING_UPDATE_RATE_FREQUENT,
        )
        session.prepareSession(params).collect { result ->
            val d = (result as? androidx.core.uwb.RangingResult.RangingResultPosition)
                ?.position?.distance?.value
            if (d != null) {
                dist.text = "distance: %.2f m".format(d)
                history.addLast(d)
                if (history.size > 40) history.removeFirst()
                val mean = history.average().toFloat()
                val sd = sqrt(history.map { (it - mean) * (it - mean) }.average())
                motion.text = "std: %.2f m — %s".format(
                    sd, if (sd > 0.10) "MOTION" else "still"
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
