package com.survival.network

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TestPhase(val description: String) {
    LATENCY("latence"),
    DOWNLOAD("débit descendant"),
    UPLOAD("débit montant")
}

data class SpeedTestState(
    val phase: TestPhase? = null,
    val phaseProgress: Float = 0f,
    val liveValue: Double = 0.0,
    val latencyMs: Double? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val message: String? = null,
    val isError: Boolean = false
) {
    val isRunning: Boolean get() = phase != null
}

/** Owns the speed test so that a configuration change does not interrupt it. */
class SpeedTestViewModel(application: Application) : ViewModel() {
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var testJob: Job? = null

    var state by mutableStateOf(SpeedTestState())
        private set

    var history by mutableStateOf(emptyList<SpeedResult>())
        private set

    init {
        scope.launch {
            val stored = withContext(Dispatchers.IO) {
                SpeedHistory.decode(prefs.getString(KEY_HISTORY, null))
            }
            history = (history + stored).distinct()
                .sortedByDescending { it.timeMillis }
                .take(SpeedHistory.MAX_ENTRIES)
        }
    }

    fun start(connection: ConnectionType) {
        if (testJob?.isActive == true) return
        testJob = scope.launch {
            state = SpeedTestState(phase = TestPhase.LATENCY)
            try {
                val latency = SpeedTester.measureLatency { f, v -> report(TestPhase.LATENCY, f, v) }
                state = state.copy(latencyMs = latency, phase = TestPhase.DOWNLOAD, phaseProgress = 0f, liveValue = 0.0)
                val download = SpeedTester.measureDownload { f, v -> report(TestPhase.DOWNLOAD, f, v) }
                state = state.copy(downloadMbps = download, phase = TestPhase.UPLOAD, phaseProgress = 0f, liveValue = 0.0)
                val upload = SpeedTester.measureUpload { f, v -> report(TestPhase.UPLOAD, f, v) }
                state = state.copy(uploadMbps = upload, phase = null, phaseProgress = 0f, liveValue = 0.0)
                record(SpeedResult(System.currentTimeMillis(), connection, latency, download, upload))
            } catch (e: SpeedTestException) {
                // A failure caused by cancel() must not replace the "Test annulé." message.
                ensureActive()
                val failedPhase = state.phase?.description ?: "réseau"
                state = state.copy(
                    phase = null,
                    phaseProgress = 0f,
                    liveValue = 0.0,
                    message = "Test interrompu ($failedPhase) : ${e.message}",
                    isError = true
                )
            }
        }
    }

    fun cancel() {
        val job = testJob ?: return
        job.cancel()
        testJob = null
        state = state.copy(phase = null, phaseProgress = 0f, liveValue = 0.0, message = "Test annulé.", isError = false)
    }

    fun clearHistory() {
        history = emptyList()
        prefs.edit { remove(KEY_HISTORY) }
    }

    // Hopping to the main thread drops updates from a test that was cancelled meanwhile.
    private suspend fun report(phase: TestPhase, fraction: Float, value: Double) {
        withContext(Dispatchers.Main.immediate) {
            state = state.copy(phase = phase, phaseProgress = fraction.coerceIn(0f, 1f), liveValue = value)
        }
    }

    private fun record(result: SpeedResult) {
        history = (listOf(result) + history).take(SpeedHistory.MAX_ENTRIES)
        prefs.edit { putString(KEY_HISTORY, SpeedHistory.encode(history)) }
    }

    override fun onCleared() {
        scope.cancel()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SpeedTestViewModel(application) as T
    }

    private companion object {
        const val PREFS_NAME = "survie"
        const val KEY_HISTORY = "reseau_historique"
    }
}
