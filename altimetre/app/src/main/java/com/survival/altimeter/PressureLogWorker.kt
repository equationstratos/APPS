package com.survival.altimeter

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val READ_TIMEOUT_MS = 10_000L
private const val READ_SAMPLES = 5
private const val INTERVAL_MINUTES = 15L

/**
 * Background reading for the weather history: wakes about every 15 minutes, listens to the
 * barometer just long enough for a few values (10 s at most), stores their median and lets go.
 */
class PressureLogWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!AppPrefs(context).backgroundLogging) return Result.success()
        val sensorManager = context.getSystemService(SensorManager::class.java) ?: return Result.success()
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return Result.success()
        val hPa = readStationPressure(sensorManager, sensor) ?: return Result.success()
        withContext(Dispatchers.IO) {
            HistoryStore.add(context, PressureSample(System.currentTimeMillis(), hPa))
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "releve_pression"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PressureLogWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES).build()
            try {
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            } catch (e: IllegalStateException) {
                // WorkManager unavailable: the history is still filled while the app is open.
            }
        }

        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NAME)
            } catch (e: IllegalStateException) {
                // Nothing was scheduled.
            }
        }
    }
}

/** Median of the first few barometer values, or null if the sensor stays silent for [READ_TIMEOUT_MS]. */
private suspend fun readStationPressure(sensorManager: SensorManager, sensor: Sensor): Double? {
    val values = ArrayList<Double>()
    withTimeoutOrNull(READ_TIMEOUT_MS) {
        callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val value = event?.values?.firstOrNull() ?: return
                    trySend(value.toDouble())
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (!sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
                close()
            }
            awaitClose { sensorManager.unregisterListener(listener) }
        }.take(READ_SAMPLES).collect { values.add(it) }
    }
    return medianOrNull(values.filter { isPlausibleStationPressure(it) })
}
