package com.ruview.viewer

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * Listens on UDP 5005 for JSON detections broadcast by the Python sensor
 * (ruview --publish) and renders the latest rescue alert.
 *
 * Contract: ruview/core/alert.py — RescueAlert payload.
 */
class ViewerActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var status: TextView
    private lateinit var rate: TextView
    private lateinit var motion: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)
        status = findViewById(R.id.status)
        rate = findViewById(R.id.rate)
        motion = findViewById(R.id.motion)
        scope.launch { listen() }
    }

    private suspend fun listen() = coroutineScope {
        val sock = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(5005))
        }
        val buf = ByteArray(4096)
        while (isActive) {
            val pkt = DatagramPacket(buf, buf.size)
            sock.receive(pkt)
            val json = JSONObject(String(pkt.data, 0, pkt.length, Charsets.UTF_8))
            val alert = json.optString("alert", "CLEAR")
            val rateCpm = json.opt("rate_cpm")
            val motionScore = json.optDouble("motion_score", 0.0)
            withContext(Dispatchers.Main) {
                status.text = alert
                rate.text = "rate: ${rateCpm ?: "—"} cpm"
                motion.text = "motion: %.4f".format(motionScore)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
