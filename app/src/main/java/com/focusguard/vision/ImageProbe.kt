package com.focusguard.vision

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Display
import com.focusguard.decision.AdaptiveSettings
import com.focusguard.decision.CaptureGate
import com.focusguard.decision.CaptureRisk
import com.focusguard.decision.CaptureScheduler
import com.focusguard.decision.CaptureTarget
import com.focusguard.decision.CaptureTiming
import com.focusguard.decision.GantManOutput
import com.focusguard.decision.ImageAction
import com.focusguard.decision.ImageAdaptation
import com.focusguard.decision.ImageAnalyzer
import com.focusguard.decision.ImageAssessment
import com.focusguard.decision.ImageLevel
import com.focusguard.decision.ImageModels
import com.focusguard.decision.ImageResponder
import com.focusguard.decision.ImageTier
import com.focusguard.decision.ImageVigilance
import com.focusguard.decision.NudeNetOutput
import com.focusguard.decision.VigilanceSettings
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Lo que la detección guarda para que sobreviva a un reinicio: cuándo hubo incidentes y hasta cuándo dura la vigilancia (con la hora del reloj). */
interface ImageHistory {
    fun incidents(): List<Long>
    fun addIncident(nowMs: Long): List<Long>
    fun vigilanceUntilMs(): Long
    fun setVigilanceUntilMs(ms: Long)
}

/**
 * La detección de imágenes: cada cierto tiempo captura la pantalla (solo con ella encendida y fuera de las apps de confianza), la puntúa con GantMan y con
 * NudeNet (si su archivo está), apunta el resultado en [stats] y **actúa** ([ImageResponder]): lo explícito se bloquea ([onBlock]) y lo sugerente ofrece
 * bloquearlo con una notificación ([onOffer]), como mucho una cada [notifyEveryMs]. Tras lo explícito (y, si se elige, tras lo sugerente) entra en **vigilancia**:
 * durante un tiempo captura más seguido lo que puede ser un riesgo ([ImageVigilance]). **No guarda la imagen ni el nombre de la app.** Necesita Android 11 o
 * más (`takeScreenshot` del servicio de accesibilidad).
 */
class ImageProbe(
    private val service: AccessibilityService,
    private val gantman: GantManScorer,
    private val nudenet: NudeNetScorer,
    private val stats: ImageProbeStats,
    private val scope: CoroutineScope,
    /** Qué hay en pantalla y cuánto riesgo tiene (null si no se sabe todavía). */
    private val target: () -> CaptureTarget?,
    private val foregroundPackage: () -> String?,
    private val trusted: () -> Set<String>,
    /** El paso de sensibilidad de 1 a 5 que está elegido ahora. */
    private val sensitivity: () -> Int,
    /** Cada cuánto como mucho se ofrece bloquear algo sugerente. */
    private val notifyEveryMs: () -> Long,
    /** Los ajustes de la vigilancia que están elegidos ahora. */
    private val vigilanceSettings: () -> VigilanceSettings,
    /** El modo dinámico: sube la vigilancia y la sensibilidad con los bloqueos recientes y las baja con el tiempo. */
    private val adaptiveSettings: () -> AdaptiveSettings,
    private val history: ImageHistory,
    /** La hora del reloj, para el historial (el de confianza de la app). */
    private val wallNow: () -> Long,
    /** Lo explícito en pantalla: se bloquea la app que estaba en primer plano. */
    private val onBlock: (String?) -> Unit,
    /** Lo sugerente en pantalla: se ofrece bloquearlo con una notificación. */
    private val onOffer: () -> Unit,
) {
    private val scheduler = CaptureScheduler(CaptureTiming())
    private val vigilance = ImageVigilance()
    private var job: Job? = null
    private var lastHash: Int? = null
    private var lastResult: ImageAssessment? = null
    private var responder = ImageResponder()
    private var incidents: List<Long> = emptyList()
    private val hasNudenet by lazy { nudenet.available() }

    /** Lo que dio cada modelo en la última captura, solo para el registro del sistema (no se guarda). */
    @Volatile private var lastRaw = ""

    fun start() {
        if (job?.isActive == true) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) { stats.unsupported(); return }
        if (!gantman.available()) { stats.modelMissing(); return }
        if (!hasNudenet) stats.nudenetMissing()
        // Tras un reinicio del servicio se recupera lo que había: los incidentes y lo que quedaba de vigilancia.
        incidents = history.incidents()
        val left = history.vigilanceUntilMs() - wallNow()
        if (left > 0) vigilance.restore(SystemClock.elapsedRealtime() + left)
        job = scope.launch { loop() }
    }

    fun stop() {
        job?.cancel()
        job = null
        gantman.close()
        nudenet.close()
        lastHash = null
        lastResult = null
        responder = ImageResponder()
        vigilance.clear()
    }

    /**
     * Cada [TICK_MS] mira qué hay en pantalla y, si ya pasó el tiempo que toca a esa app o web (más largo en un navegador, más corto en redes y vídeo, más largo
     * cuanto más seguras salen sus capturas y más corto durante la vigilancia), captura. El tiempo se pregunta de nuevo en cada vuelta: cambiar de app lo cambia al momento.
     */
    private suspend fun loop() {
        val power = service.getSystemService(PowerManager::class.java)
        delay(FIRST_WAIT_MS)
        var lastAt = SystemClock.elapsedRealtime() - NEVER_MS
        while (scope.isActive) {
            val now = target() ?: CaptureTarget(UNKNOWN_KEY, CaptureRisk.NORMAL)
            val settings = vigilanceSettings()
            val tick = SystemClock.elapsedRealtime()
            val level = adaptiveLevel()
            // El nivel del modo dinámico acorta el tiempo de lo que le toca según el alcance elegido, haya o no vigilancia ahora.
            val adaptive = if (level > 0 && ImageVigilance.appliesTo(settings, now)) ImageAdaptation.intervalFactor(level) else 1.0
            val interval = scheduler.intervalMs(now.key, now.risk, tick, vigilance.factor(tick, settings, now) * adaptive)
            stats.rhythm(now.risk, interval, if (settings.enabled) vigilance.remainingMs(tick) else 0)
            stats.adaptive(level, ImageAdaptation.incidentsIn(adaptiveSettings(), incidents, wallNow()), ImageAdaptation.sensitivity(sensitivity(), level))
            if (tick - lastAt >= interval) {
                Log.i(TAG, "captura: intervalo=${interval}ms nivel=${level} riesgo=${now.risk} navegador=${now.isBrowser} vigilancia=${vigilance.remainingMs(tick) / 1000}s ajustes=${settings.encode()}")
                // El tiempo se cuenta desde que empieza la captura, no desde que acaba: al ritmo mínimo la siguiente sale en cuanto termina esta.
                lastAt = tick
                val outcome = captureOnce(power.isInteractive)
                scheduler.record(now.key, outcome.assessment, SystemClock.elapsedRealtime(), outcome.fresh)
                // Una captura reutilizada (la pantalla no cambió y era segura) no es una observación nueva: no se vuelve a decidir con ella.
                if (outcome.fresh) respond(outcome.assessment)
            }
            delay(TICK_MS)
        }
    }

    /** El nivel de alerta del modo dinámico ahora, según los bloqueos recientes. */
    private fun adaptiveLevel(): Int = ImageAdaptation.level(adaptiveSettings(), incidents, wallNow())

    private fun respond(assessment: ImageAssessment?) {
        val now = SystemClock.elapsedRealtime()
        val action = responder.decide(now, assessment, notifyEveryMs())
        if (action != ImageAction.NONE) Log.i(TAG, "decisión=$action app=${foregroundPackage() != null}")
        when (action) {
            ImageAction.BLOCK -> {
                stats.blocked()
                // Un bloqueo es un incidente: se apunta, sube el nivel y la vigilancia dura lo que pida ese nivel.
                incidents = history.addIncident(wallNow())
                startVigilance(now, ImageAdaptation.vigilanceMinutes(vigilanceSettings().minutes, adaptiveLevel()))
                Log.i(TAG, "vigilancia activada: quedan ${vigilance.remainingMs(now) / 1000}s")
                onBlock(foregroundPackage())
            }
            ImageAction.OFFER -> {
                stats.offered()
                if (vigilanceSettings().afterSuggestive) startVigilance(now, vigilanceSettings().minutes)
                onOffer()
            }
            ImageAction.NONE -> Unit
        }
    }

    /** Empieza o renueva la vigilancia y la guarda con la hora del reloj para que sobreviva a un reinicio. */
    private fun startVigilance(elapsedNow: Long, minutes: Int) {
        val settings = vigilanceSettings()
        if (!settings.enabled) return
        vigilance.trigger(elapsedNow, settings, minutes)
        history.setVigilanceUntilMs(wallNow() + minutes * 60_000L)
    }

    private class Outcome(val assessment: ImageAssessment?, val fresh: Boolean)

    private suspend fun captureOnce(screenOn: Boolean): Outcome {
        if (CaptureGate.skipReason(screenOn, foregroundPackage(), trusted(), unchanged = false) != null) {
            stats.skipped()
            return Outcome(null, fresh = false)
        }
        val startedAt = SystemClock.elapsedRealtime()
        val shot = take()
        if (shot !is Shot.Ok) {
            stats.failed(SystemClock.elapsedRealtime() - startedAt, (shot as? Shot.Failed)?.code)
            return Outcome(null, fresh = false)
        }
        return withContext(Dispatchers.Default) {
            val small = shot.small
            try {
                val captureMs = SystemClock.elapsedRealtime() - startedAt
                val hash = fingerprint(small)
                // Solo se reutiliza lo seguro: si lo último fue sospechoso se vuelve a puntuar, para poder confirmarlo o descartarlo.
                if (hash == lastHash && lastResult?.level == ImageLevel.SAFE) {
                    stats.unchanged(captureMs)
                    return@withContext Outcome(lastResult, fresh = false)
                }
                lastHash = hash
                val scoreStart = SystemClock.elapsedRealtime()
                val result = analyze(small, shot.letterbox)
                val scoreMs = SystemClock.elapsedRealtime() - scoreStart
                lastResult = result
                if (result == null) {
                    stats.failed(captureMs, null)
                } else {
                    val shown = result.explicit?.probability ?: 0.0
                    stats.scored(captureMs, scoreMs, shown, doubtful = result.suspicious && result.level == ImageLevel.SAFE, level = result.level)
                    Log.i(TAG, "${result.level} explícito=${"%.3f".format(shown)} sugerente=${"%.3f".format(result.suggestive?.probability ?: 0.0)} modelos=${result.explicit?.modelsRun ?: result.suggestive?.modelsRun} captura=${captureMs}ms modelos=${scoreMs}ms | $lastRaw")
                }
                Outcome(result, fresh = true)
            } finally {
                small.recycle()
                shot.letterbox?.recycle()
            }
        }
    }

    /**
     * Pasa la captura por GantMan y, si está, por NudeNet. Los umbrales de la sensibilidad dependen de qué modelos votaron: con los dos se usan los del voto de
     * los dos; si NudeNet no pudo puntuar esta captura, se usan los de GantMan solo (los otros dejarían pasar demasiado de lo que GantMan sospecha por su cuenta).
     */
    private fun analyze(small: Bitmap, letterbox: Bitmap?): ImageAssessment? {
        val g = gantman.scores(small) ?: return null
        val n = letterbox?.let { nudenet.scores(it) }
        lastRaw = "gantman explícito=${"%.3f".format(GantManOutput.explicitProbability(g))} sexy=${"%.3f".format(GantManOutput.suggestiveProbability(g))} nudenet expuestas=${n?.let { "%.3f".format(NudeNetOutput.explicitProbability(it)) }} cubiertas=${n?.let { "%.3f".format(NudeNetOutput.suggestiveProbability(it)) }}"
        val both = n != null
        val explicitModels = if (both) listOf(gantman.explicitSpec, nudenet.explicitSpec) else listOf(gantman.explicitSpec)
        val suggestiveModels = if (both) listOf(gantman.suggestiveSpec, nudenet.suggestiveSpec) else listOf(gantman.suggestiveSpec)
        return ImageAnalyzer.analyze(explicitModels, suggestiveModels, ImageAdaptation.sensitivity(sensitivity(), adaptiveLevel()), if (both) ImageModels.GANTMAN_NUDENET else ImageModels.GANTMAN) { spec, tier ->
            val explicit = tier == ImageTier.EXPLICIT
            when (spec.id) {
                gantman.explicitSpec.id, gantman.suggestiveSpec.id -> if (explicit) GantManOutput.explicitProbability(g) else GantManOutput.suggestiveProbability(g)
                else -> n?.let { if (explicit) NudeNetOutput.explicitProbability(it) else NudeNetOutput.suggestiveProbability(it) }
            }
        }
    }

    private sealed interface Shot {
        /** [small]: la pantalla reducida de golpe a 224×224 (GantMan). [letterbox]: sin deformarla, a 320×320 con relleno negro (NudeNet); null si NudeNet no está. */
        class Ok(val small: Bitmap, val letterbox: Bitmap?) : Shot
        class Failed(val code: Int?) : Shot
    }

    /** Captura la pantalla y la deja ya reducida: la imagen entera (1080×2400) no se guarda en ningún sitio más que el momento de reducirla. */
    private suspend fun take(): Shot = suspendCancellableCoroutine { cont ->
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val shot = runCatching {
                        val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: return@runCatching null
                        val full = hardware.copy(Bitmap.Config.ARGB_8888, false)
                        hardware.recycle()
                        // Se reduce de una vez a lo que usan los modelos: lo que entra en el procesado ya no es la pantalla entera.
                        val small = Bitmap.createScaledBitmap(full, PROBE_SIZE, PROBE_SIZE, true)
                        val letterbox = if (hasNudenet) letterbox(full) else null
                        if (small !== full) full.recycle()
                        Shot.Ok(small, letterbox)
                    }.getOrNull()
                    buffer.close()
                    cont.resume(shot ?: Shot.Failed(null))
                }

                override fun onFailure(errorCode: Int) {
                    cont.resume(Shot.Failed(errorCode))
                }
            },
        )
    }

    /** La pantalla escalada para que su lado largo mida 320, sin deformarla, en la esquina de arriba a la izquierda de un cuadrado negro: como prepara NudeNet sus imágenes. */
    private fun letterbox(full: Bitmap): Bitmap {
        val scale = NudeNetScorer.SIZE.toFloat() / maxOf(full.width, full.height)
        val w = (full.width * scale).roundToInt().coerceIn(1, NudeNetScorer.SIZE)
        val h = (full.height * scale).roundToInt().coerceIn(1, NudeNetScorer.SIZE)
        val scaled = Bitmap.createScaledBitmap(full, w, h, true)
        val out = Bitmap.createBitmap(NudeNetScorer.SIZE, NudeNetScorer.SIZE, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.BLACK)
            drawBitmap(scaled, 0f, 0f, null)
        }
        if (scaled !== full) scaled.recycle()
        return out
    }

    /** Una huella barata de la imagen: si no cambió desde la última, no hace falta puntuarla otra vez. */
    private fun fingerprint(bitmap: Bitmap): Int {
        var hash = 17
        val step = PROBE_SIZE / FINGERPRINT_GRID
        for (y in 0 until FINGERPRINT_GRID) for (x in 0 until FINGERPRINT_GRID) {
            // Se descartan los bits bajos: un parpadeo de brillo no cuenta como cambio.
            hash = hash * 31 + (bitmap.getPixel(x * step, y * step) and 0xF0F0F0)
        }
        return hash
    }

    companion object {
        private const val TAG = "FGimg"
        private const val FIRST_WAIT_MS = 5_000L
        // El mínimo entre capturas es de 1 s: mirar solo cada 2 s lo dejaría en 2 s, así que se mira cada medio segundo.
        private const val TICK_MS = 500L
        private const val NEVER_MS = 24 * 60 * 60_000L
        private const val UNKNOWN_KEY = "?"
        private const val PROBE_SIZE = 224
        private const val FINGERPRINT_GRID = 16
    }
}
