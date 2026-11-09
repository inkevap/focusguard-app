package com.focusguard.boot

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import com.focusguard.data.LockStyleRepository
import com.focusguard.data.ProfileRepository
import com.focusguard.data.ServiceHealth
import com.focusguard.data.StrictRepository
import com.focusguard.data.TrustedClock
import com.focusguard.decision.BootShield
import com.focusguard.decision.StrictMode
import com.focusguard.lock.LockScreenContent
import com.focusguard.lock.LockScreenView
import com.focusguard.model.LockPalettes
import com.focusguard.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * La superposición del bloqueo de arranque (H5). Solo se pone si hay algo que proteger y el servicio de accesibilidad aún no ha dado
 * señal; se quita en cuanto la da o a los cinco minutos del arranque, lo que pase antes: nunca deja el teléfono tapado para siempre.
 * Con la pantalla de bloqueo de siempre (mismo color y emoji), sin botón de volver.
 */
class BootShieldService : Service(), KoinComponent {
    private val health: ServiceHealth by inject()
    private val profiles: ProfileRepository by inject()
    private val strict: StrictRepository by inject()
    private val clock: TrustedClock by inject()
    private val lockStyle: LockStyleRepository by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null

    /** El arranque en marcha: una segunda orden (otro aviso de arranque) no monta un segundo escudo. */
    private var starting: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (starting?.isActive != true) starting = scope.launch { run() }
        return START_NOT_STICKY
    }

    private suspend fun run() {
        if (!BootShield.shouldShow(SystemClock.elapsedRealtime(), health.lastBeat != null, somethingToProtect = true)) return stopSelf()
        val now = clock.now()
        val local = clock.localAt(now)
        val protect = BootShield.hasSomethingToProtect(profiles.profiles.first(), StrictMode.isActive(strict.config.value, local, now), local, now)
        // Entre decidir y poner la vista pudo darse la señal, o pasar el tiempo (leer la base al arrancar tarda): se vuelve a mirar.
        val age = SystemClock.elapsedRealtime()
        if (!BootShield.shouldShow(age, health.lastBeat != null, protect) || !show()) return stopSelf()
        handler.postDelayed({ stopSelf() }, (BootShield.MAX_AGE_MS - age).coerceAtLeast(0))
        // Se quita en cuanto el servicio dé señal, sin preguntar cada poco.
        health.beats.first { it != null }
        stopSelf()
    }

    /** Pone la vista; `false` si no se pudo (sin permiso de superposición no hay escudo, y no es un fallo de la app). */
    private fun show(): Boolean {
        val style = lockStyle.style.value
        val content = LockScreenContent(
            LockPalettes.byId(style.paletteId), style.emoji, getString(R.string.boot_shield_body), unlockLabel = null, motivation = null,
            title = getString(R.string.boot_shield_title),
        )
        val shield = LockScreenView(this, content)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE,
        )
        return try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(shield, params)
            view = shield
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        view?.let { runCatching { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it) } }
        view = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
