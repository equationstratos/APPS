package com.survival.network

import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpeedTestException(message: String) : Exception(message)

/** Receives the phase completion in [0, 1] and the live value (ms or Mbit/s). */
typealias ProgressListener = suspend (fraction: Float, value: Double) -> Unit

object SpeedTester {
    private const val LATENCY_URL = "https://speed.cloudflare.com/__down?bytes=0"
    private const val DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=25000000"
    private const val UPLOAD_URL = "https://speed.cloudflare.com/__up"
    private const val DOWNLOAD_BYTES = 25_000_000L
    private const val UPLOAD_MAX_BYTES = 25_000_000L
    private const val UPLOAD_FIRST_REQUEST = 256 * 1024
    private const val UPLOAD_MAX_REQUEST = 8 * 1024 * 1024
    private const val LATENCY_SAMPLES = 5
    private const val TIMEOUT_MS = 10_000
    private const val PHASE_NS = 10_000_000_000L
    private const val FAST_REQUEST_NS = 1_000_000_000L
    private const val REPORT_INTERVAL_NS = 100_000_000L
    private const val BUFFER_SIZE = 64 * 1024

    /** Median round trip of [LATENCY_SAMPLES] empty requests, in milliseconds. */
    suspend fun measureLatency(onProgress: ProgressListener): Double {
        val samples = DoubleArray(LATENCY_SAMPLES)
        for (i in 0 until LATENCY_SAMPLES) {
            samples[i] = request(LATENCY_URL) { connection ->
                val start = System.nanoTime()
                val code = connection.responseCode
                val elapsedNs = System.nanoTime() - start
                checkStatus(code)
                // Draining the body lets the next request reuse the connection (no new handshake).
                connection.inputStream.use { it.readBytes() }
                elapsedNs / 1_000_000.0
            }
            onProgress((i + 1).toFloat() / LATENCY_SAMPLES, samples[i])
        }
        samples.sort()
        return samples[LATENCY_SAMPLES / 2]
    }

    /** Streams a 25 MB payload for at most 10 s, in Mbit/s. */
    suspend fun measureDownload(onProgress: ProgressListener): Double = request(DOWNLOAD_URL) { connection ->
        checkStatus(connection.responseCode)
        val buffer = ByteArray(BUFFER_SIZE)
        val start = System.nanoTime()
        var received = 0L
        var elapsedNs = 0L
        var lastReport = start
        connection.inputStream.use { input ->
            while (elapsedNs < PHASE_NS) {
                ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                received += read
                val now = System.nanoTime()
                elapsedNs = now - start
                if (now - lastReport >= REPORT_INTERVAL_NS) {
                    lastReport = now
                    onProgress(progressOf(elapsedNs, received, DOWNLOAD_BYTES), mbps(received, elapsedNs))
                }
            }
        }
        if (received == 0L || elapsedNs <= 0L) throw SpeedTestException("Aucune donnée reçue du serveur.")
        mbps(received, elapsedNs)
    }

    /**
     * Sends random bytes for at most 10 s, in Mbit/s. Successive POST requests grow while they
     * complete quickly; only bytes acknowledged by the server count, so socket buffering does not
     * inflate the result.
     */
    suspend fun measureUpload(onProgress: ProgressListener): Double {
        val payload = Random.nextBytes(BUFFER_SIZE)
        val start = System.nanoTime()
        var confirmedBytes = 0L
        var confirmedNs = 0L
        var pendingBytes = 0L
        var requestSize = UPLOAD_FIRST_REQUEST
        var lastReport = start
        while (confirmedBytes < UPLOAD_MAX_BYTES && System.nanoTime() - start < PHASE_NS) {
            val size = min(requestSize.toLong(), UPLOAD_MAX_BYTES - confirmedBytes).toInt()
            val requestStart = System.nanoTime()
            pendingBytes = 0L
            val completed = request(UPLOAD_URL) { connection ->
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/octet-stream")
                connection.setFixedLengthStreamingMode(size)
                val output = connection.outputStream
                while (pendingBytes < size) {
                    ensureActive()
                    val chunk = min(payload.size.toLong(), size - pendingBytes).toInt()
                    output.write(payload, 0, chunk)
                    pendingBytes += chunk
                    val now = System.nanoTime()
                    val elapsedNs = now - start
                    if (now - lastReport >= REPORT_INTERVAL_NS) {
                        lastReport = now
                        val sent = confirmedBytes + pendingBytes
                        onProgress(progressOf(elapsedNs, sent, UPLOAD_MAX_BYTES), mbps(sent, elapsedNs))
                    }
                    if (elapsedNs >= PHASE_NS && pendingBytes < size) return@request false
                }
                output.close()
                checkStatus(connection.responseCode)
                // Draining the reply lets the next POST reuse the connection (no new handshake).
                connection.inputStream.use { it.readBytes() }
                true
            }
            if (!completed) break
            confirmedBytes += size
            confirmedNs = System.nanoTime() - start
            if (System.nanoTime() - requestStart < FAST_REQUEST_NS && requestSize < UPLOAD_MAX_REQUEST) {
                requestSize *= 2
            }
        }
        return when {
            confirmedBytes > 0L -> mbps(confirmedBytes, confirmedNs)
            pendingBytes > 0L -> mbps(pendingBytes, System.nanoTime() - start)
            else -> throw SpeedTestException("Aucune donnée n'a pu être envoyée.")
        }
    }

    private suspend fun <T> request(
        url: String,
        block: suspend CoroutineScope.(HttpURLConnection) -> T
    ): T = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            // Without this, transparent gzip could inflate the measured throughput.
            connection.setRequestProperty("Accept-Encoding", "identity")
            // Blocking socket calls ignore coroutine cancellation: closing the connection unblocks them.
            // UNDISPATCHED enters the try block at once, so an early cancellation still disconnects.
            val abortOnCancel = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    connection.disconnect()
                }
            }
            try {
                block(this, connection)
            } finally {
                abortOnCancel.cancel()
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ensureActive()
            throw SpeedTestException(e.toFrenchMessage())
        } catch (e: SecurityException) {
            // Thrown when a system firewall blocks this app's network access.
            ensureActive()
            throw SpeedTestException("Accès réseau refusé à l'application par le système.")
        } catch (e: RuntimeException) {
            // HttpURLConnection may throw unchecked exceptions once disconnected from another thread.
            ensureActive()
            throw SpeedTestException("Connexion interrompue pendant le test.")
        }
    }

    private fun checkStatus(code: Int) {
        if (code in 200..299) return
        throw SpeedTestException(
            if (code == 429) {
                "Trop de tests rapprochés : le serveur limite les requêtes. Réessayez dans quelques minutes."
            } else {
                "Réponse inattendue du serveur (code $code)."
            }
        )
    }

    private fun IOException.toFrenchMessage(): String = when (this) {
        is SocketTimeoutException -> "Délai de 10 s dépassé : le serveur ne répond pas."
        is UnknownHostException -> "Serveur introuvable : vérifiez l'accès à Internet."
        is ConnectException -> "Connexion au serveur de test impossible."
        is SSLException -> "Connexion sécurisée impossible (portail captif ou réseau filtré ?)."
        else -> "Connexion interrompue pendant le test."
    }

    private fun progressOf(elapsedNs: Long, bytes: Long, totalBytes: Long): Float =
        max(elapsedNs.toDouble() / PHASE_NS, bytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)

    private fun mbps(bytes: Long, elapsedNs: Long): Double =
        if (elapsedNs <= 0L) 0.0 else bytes * 8_000.0 / elapsedNs
}
