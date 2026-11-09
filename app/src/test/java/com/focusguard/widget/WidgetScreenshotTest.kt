package com.focusguard.widget

import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.focusguard.decision.WidgetStatus
import com.focusguard.model.Profile
import com.focusguard.model.ScheduleInterval
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test

/** Los widgets tal como los pinta el lanzador: el mismo `RemoteViews` que se manda, aplicado a un contenedor del tamaño de la celda. */
class WidgetScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.Light.NoActionBar")

    private val now = Instant.parse("2026-10-26T10:00:00Z")
    private val zone = ZoneOffset.UTC
    private val work = Profile(
        1, "Trabajo",
        intervals = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
            .map { ScheduleInterval(it, 9 * 60, 18 * 60) },
    )
    private val night = Profile(2, "Noche", intervals = listOf(ScheduleInterval(DayOfWeek.MONDAY, 22 * 60, 6 * 60)))

    private fun snapshot(views: RemoteViews, widthDp: Int, heightDp: Int) {
        val density = paparazzi.context.resources.displayMetrics.density
        val parent = FrameLayout(paparazzi.context)
        val view: View = views.apply(paparazzi.context, parent)
        parent.addView(view)
        paparazzi.snapshot(parent.also { it.layoutParams = FrameLayout.LayoutParams((widthDp * density).toInt(), (heightDp * density).toInt()) })
    }

    private fun status(vararg profiles: Profile) = WidgetStatus.of(profiles.toList(), now, zone)

    @Test fun widgetAccionesLibre() =
        snapshot(WidgetRenderer.actions(paparazzi.context, status(), now, zone), 300, 90)

    @Test fun widgetAccionesConBloqueoSinSalida() =
        snapshot(WidgetRenderer.actions(paparazzi.context, status(Profile.panic(now.plusSeconds(25 * 60), "Dormir").copy(id = 9)), now, zone), 300, 90)

    @Test fun widgetEstadoConPerfiles() =
        snapshot(WidgetRenderer.status(paparazzi.context, status(work, night), now, zone), 300, 140)

    @Test fun widgetEstadoSinPerfiles() =
        snapshot(WidgetRenderer.status(paparazzi.context, status(), now, zone), 300, 140)

    @Test fun widgetEstadoConAyudame() =
        snapshot(WidgetRenderer.status(paparazzi.context, status(work, Profile.panic(now.plusSeconds(90 * 60)).copy(id = 9)), now, zone), 300, 140)
}
