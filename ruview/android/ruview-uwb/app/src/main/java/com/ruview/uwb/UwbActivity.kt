package com.ruview.uwb

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.uwb.UwbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UWB capability probe.
 *
 * Demonstrates that the device exposes the AndroidX UWB stack. Actual ranging
 * needs an out-of-band exchange of session keys with a peer device (BLE/QR);
 * that wiring belongs in a real deployment, not in a demo APK.
 *
 * Once a peer is provisioned, the Jetpack flow is:
 *   val mgr = UwbManager.createInstance(this)
 *   val scope = mgr.controllerSessionScope()
 *   scope.prepareSession(RangingParameters(...)).collect { result -> ... }
 */
class UwbActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_uwb)
        val dist: TextView = findViewById(R.id.dist)
        val motion: TextView = findViewById(R.id.motion)

        scope.launch {
            val status = withContext(Dispatchers.IO) {
                try {
                    UwbManager.createInstance(this@UwbActivity)
                    "UWB stack available — provision a peer to start ranging"
                } catch (t: Throwable) {
                    "UWB unavailable: ${t.message}"
                }
            }
            dist.text = status
            motion.text = "ranging: stub (see source comments)"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}