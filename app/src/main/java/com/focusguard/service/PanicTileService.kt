package com.focusguard.service

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.focusguard.MainActivity
import com.focusguard.ui.Routes

/** Botón «Ayúdame» en los ajustes rápidos. Abre la pantalla de pánico: el tiempo se elige allí, no se activa de un toque. */
class PanicTileService : TileService() {

    override fun onTileAdded() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val intent = MainActivity.intent(this, Routes.PANIC)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
