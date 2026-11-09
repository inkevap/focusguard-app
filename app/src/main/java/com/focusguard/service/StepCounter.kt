package com.focusguard.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Lee el contador de pasos del teléfono (acumulado desde el último arranque). */
class StepCounter(context: Context) {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    val isAvailable: Boolean get() = sensor != null

    /** Total actual de pasos, o null si no hay sensor, falta el permiso o no responde. */
    suspend fun currentTotal(): Long? {
        val sensor = sensor ?: return null
        return withTimeoutOrNull(READ_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        manager.unregisterListener(this)
                        if (continuation.isActive) continuation.resume(event.values[0].toLong())
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                val registered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                continuation.invokeOnCancellation { manager.unregisterListener(listener) }
                if (!registered) continuation.resume(-1L)
            }
        }?.takeIf { it >= 0 }
    }

    private companion object {
        const val READ_TIMEOUT_MS = 4_000L
    }
}
