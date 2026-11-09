package com.focusguard.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.focusguard.MainActivity
import com.focusguard.R
import com.focusguard.decision.WidgetStatus
import com.focusguard.ui.Routes
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * Pinta los widgets. Los botones solo abren la pantalla correspondiente de la app (que pide la confirmación y, si hace falta, el PIN):
 * un widget nunca activa un bloqueo, ni lo quita, por sí solo.
 */
internal object WidgetRenderer {
    private val dayViews = intArrayOf(
        R.id.widget_day_0, R.id.widget_day_1, R.id.widget_day_2, R.id.widget_day_3, R.id.widget_day_4, R.id.widget_day_5, R.id.widget_day_6,
    )
    private val dayOn = intArrayOf(
        R.id.widget_day_0_on, R.id.widget_day_1_on, R.id.widget_day_2_on, R.id.widget_day_3_on, R.id.widget_day_4_on, R.id.widget_day_5_on, R.id.widget_day_6_on,
    )
    private val dayToday = intArrayOf(
        R.id.widget_day_0_today, R.id.widget_day_1_today, R.id.widget_day_2_today, R.id.widget_day_3_today, R.id.widget_day_4_today,
        R.id.widget_day_5_today, R.id.widget_day_6_today,
    )

    // La identidad de un `PendingIntent` no mira los extras: cada destino necesita su propio código.
    private const val REQUEST_QUICK_BLOCK = 1
    private const val REQUEST_TIMER = 2
    private const val REQUEST_PANIC = 3
    private const val REQUEST_APP = 4

    fun actions(context: Context, status: WidgetStatus, now: Instant, zone: ZoneId): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_actions)
        val lock = status.lock
        views.setViewVisibility(R.id.widget_buttons, if (lock == null) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_lock, if (lock == null) View.GONE else View.VISIBLE)
        if (lock == null) {
            views.setOnClickPendingIntent(R.id.widget_quick, open(context, Routes.QUICK_BLOCK, REQUEST_QUICK_BLOCK))
            views.setOnClickPendingIntent(R.id.widget_timer, open(context, Routes.SLEEP, REQUEST_TIMER))
            views.setOnClickPendingIntent(R.id.widget_panic, open(context, Routes.PANIC, REQUEST_PANIC))
        } else {
            views.setTextViewText(R.id.widget_lock_title, lock.title)
            views.setTextViewText(R.id.widget_lock_until, WidgetText.lockUntil(lock, zone))
            views.setTextViewText(R.id.widget_lock_countdown, WidgetText.lockRemaining(lock, now))
        }
        views.setOnClickPendingIntent(R.id.widget_root, open(context, null, REQUEST_APP))
        return views
    }

    fun status(context: Context, status: WidgetStatus, now: Instant, zone: ZoneId): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_status)
        views.setTextViewText(R.id.widget_status_title, WidgetText.title(status))
        views.setTextViewText(R.id.widget_status_detail, WidgetText.detail(status, now, zone))
        val lock = status.lock
        views.setViewVisibility(R.id.widget_status_countdown, if (lock == null) View.GONE else View.VISIBLE)
        if (lock != null) views.setTextViewText(R.id.widget_status_countdown, WidgetText.lockRemaining(lock, now))

        val today = now.atZone(zone).dayOfWeek
        DayOfWeek.values().forEach { day ->
            val id = dayViews[day.value - 1]
            val active = day in status.activeDays
            // El fondo de cada día se elige mostrando una de tres vistas: así se pinta igual en el lanzador y en las pruebas de capturas.
            views.setViewVisibility(dayOn[day.value - 1], if (active) View.VISIBLE else View.GONE)
            views.setViewVisibility(dayToday[day.value - 1], if (!active && day == today) View.VISIBLE else View.GONE)
            views.setContentDescription(id, WidgetText.dayDescription(day, active, day == today))
            views.setTextColor(id, context.getColor(if (active) R.color.widget_on_accent else R.color.widget_text_dim))
        }
        views.setOnClickPendingIntent(R.id.widget_root, open(context, null, REQUEST_APP))
        return views
    }

    /** Nullable: en las pruebas de capturas (sin sistema Android) `getActivity` devuelve null. */
    private fun open(context: Context, route: String?, requestCode: Int): PendingIntent? {
        val intent = route?.let { MainActivity.intent(context, it) } ?: Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
