package com.focusguard.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.focusguard.data.PartnerEventOutbox
import com.focusguard.model.PartnerEventType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Sin políticas, solo sirve para que Android exija desactivar el administrador antes de
 * desinstalar la app (H4). Esa pantalla la protege la guardia de ajustes del modo estricto.
 * Si se desactiva, el compañero lo sabe (I4).
 */
class FocusDeviceAdminReceiver : DeviceAdminReceiver(), KoinComponent {
    private val partnerEvents: PartnerEventOutbox by inject()

    override fun onDisabled(context: Context, intent: Intent) {
        partnerEvents.record(PartnerEventType.PROTECTION_OFF, "administrador del dispositivo")
    }
}
