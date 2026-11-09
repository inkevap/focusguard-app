package com.focusguard.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun BroadcastReceiver.refreshInBackground(context: Context) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            WidgetUpdater.refresh(context.applicationContext)
        } finally {
            pending.finish()
        }
    }
}

/** Lo que tienen en común los dos widgets: se repintan al ponerlos y al quitarlos (si era el último, se apaga el aviso programado). */
abstract class FocusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = refreshInBackground(context)

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = refreshInBackground(context)
}

/** Los botones de acción: bloquear ya, temporizador y ayúdame; durante un bloqueo sin salida, su cuenta atrás. */
class ActionsWidgetProvider : FocusWidgetProvider()

/** El estado: perfiles activos, el siguiente horario y los días de la semana con horario. */
class StatusWidgetProvider : FocusWidgetProvider()

/**
 * Repinta todos los widgets con el aviso propio (cuando algo de lo que enseñan cambia) y con los cambios de hora o de día, que mueven los
 * horarios. No está ligado a ningún widget y no se puede llamar desde otras apps: solo lo avisan el sistema y la alarma de la propia app.
 */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetUpdater.ACTION_REFRESH, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED ->
                refreshInBackground(context)
        }
    }
}
