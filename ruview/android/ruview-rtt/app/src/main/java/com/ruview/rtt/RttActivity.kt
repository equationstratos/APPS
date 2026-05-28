package com.ruview.rtt

import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlin.math.sqrt

/**
 * 802.11mc FTM ranging via WifiRttManager.
 *
 * Ranges every ~250 ms to the first FTM-capable AP found by a Wi-Fi scan.
 * Motion = std-dev of last N distance samples > threshold.
 *
 * Requires an FTM-capable AP (Google Wifi, some MikroTik/Asus models).
 */
class RttActivity : AppCompatActivity() {
    private lateinit var dist: TextView
    private lateinit var motion: TextView
    private lateinit var rtt: WifiRttManager
    private lateinit var wifi: WifiManager
    private val history = ArrayDeque<Double>()
    private val WINDOW = 40
    private val MOTION_STD_MM = 250.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rtt)
        dist = findViewById(R.id.dist)
        motion = findViewById(R.id.motion)
        rtt = getSystemService(WifiRttManager::class.java)
        wifi = getSystemService(WifiManager::class.java)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1
            )
            return
        }
        startRanging()
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, gr: IntArray) {
        super.onRequestPermissionsResult(rc, p, gr)
        if (gr.firstOrNull() == PackageManager.PERMISSION_GRANTED) startRanging()
    }

    private fun startRanging() {
        if (!rtt.isAvailable) {
            dist.text = "RTT unavailable on this device"
            return
        }
        val target: ScanResult = wifi.scanResults.firstOrNull { it.is80211mcResponder }
            ?: run { dist.text = "no FTM-capable AP found"; return }
        loop(target)
    }

    private fun loop(target: ScanResult) {
        val req = RangingRequest.Builder().addAccessPoint(target).build()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        rtt.startRanging(req, mainExecutor, object : RangingResultCallback() {
            override fun onRangingFailure(code: Int) {
                dist.text = "ranging failed ($code)"
                dist.postDelayed({ loop(target) }, 500)
            }
            override fun onRangingResults(results: MutableList<RangingResult>) {
                val r = results.firstOrNull()
                if (r != null && r.status == RangingResult.STATUS_SUCCESS) {
                    val mm = r.distanceMm.toDouble()
                    dist.text = "distance: %.2f m".format(mm / 1000.0)
                    history.addLast(mm)
                    if (history.size > WINDOW) history.removeFirst()
                    if (history.size >= 8) {
                        val mean = history.average()
                        val sd = sqrt(history.map { (it - mean) * (it - mean) }.average())
                        motion.text = "std: %.0f mm — %s".format(
                            sd, if (sd > MOTION_STD_MM) "MOTION" else "still"
                        )
                    }
                }
                dist.postDelayed({ loop(target) }, 250)
            }
        })
    }
}
