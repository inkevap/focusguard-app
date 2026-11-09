package com.focusguard.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.focusguard.data.ProfileRepository
import com.focusguard.data.TrustedClock
import com.focusguard.decision.WidgetStatus
import com.focusguard.model.Profile
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.context.GlobalContext

/** Repinta los widgets con el estado de ahora y deja un aviso para cuando algo de lo que enseñan cambie. */
object WidgetUpdater {
    const val ACTION_REFRESH = "com.focusguard.widget.REFRESH"
    private const val TAG = "WidgetUpdater"

    /** Aunque el aviso llegue a la vez que un cambio de perfiles, los repintados van de uno en uno. */
    private val lock = Mutex()

    /** Nunca antes de esto: así un aviso pasado no se convierte en un bucle de repintados. */
    private const val MIN_DELAY_MS = 30_000L

    /** Los widgets se repintan cuando cambian los perfiles (nuevo bloqueo, pausa, horario); sin widgets puestos, no hace nada. */
    @OptIn(FlowPreview::class)
    fun start(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            GlobalContext.get().get<ProfileRepository>().profiles.debounce(500).collect { refresh(app, it) }
        }
    }

    /** [profiles] son los de ahora si ya se tienen a mano; si no, se leen. */
    suspend fun refresh(context: Context, profiles: List<Profile>? = null, zone: ZoneId = ZoneId.systemDefault()) = lock.withLock {
        // Un fallo al pintar (el lanzador murió, un `RemoteViews` demasiado grande) no debe tumbar la app ni el colector.
        try {
            repaint(context, profiles, zone)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron pintar los widgets", e)
        }
    }

    private suspend fun repaint(context: Context, profiles: List<Profile>?, zone: ZoneId) {
        val manager = AppWidgetManager.getInstance(context)
        val actionIds = manager.getAppWidgetIds(ComponentName(context, ActionsWidgetProvider::class.java))
        val statusIds = manager.getAppWidgetIds(ComponentName(context, StatusWidgetProvider::class.java))
        if (actionIds.isEmpty() && statusIds.isEmpty()) {
            cancelAlarm(context)
            return
        }
        val koin = GlobalContext.get()
        val now = koin.get<TrustedClock>().now()
        val status = WidgetStatus.of(profiles ?: koin.get<ProfileRepository>().profiles.first(), now, zone)
        if (actionIds.isNotEmpty()) manager.updateAppWidget(actionIds, WidgetRenderer.actions(context, status, now, zone))
        if (statusIds.isNotEmpty()) manager.updateAppWidget(statusIds, WidgetRenderer.status(context, status, now, zone))
        status.refreshAt?.let { scheduleAlarm(context, it.toEpochMilli() - now.toEpochMilli()) } ?: cancelAlarm(context)
    }

    /**
     * Un aviso aproximado (no hace falta permiso de alarmas exactas): un par de minutos tarde no importa en un widget. Se mide en tiempo
     * desde el arranque, no en hora del reloj: el reloj del teléfono se puede mover y la hora de confianza no se fía de él.
     */
    private fun scheduleAlarm(context: Context, inMs: Long) {
        val at = SystemClock.elapsedRealtime() + inMs.coerceAtLeast(MIN_DELAY_MS)
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, at, refreshIntent(context))
    }

    internal fun cancelAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))
    }

    private fun refreshIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
