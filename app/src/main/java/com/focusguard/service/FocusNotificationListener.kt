package com.focusguard.service

import android.app.Notification
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.focusguard.R
import com.focusguard.data.BlockGate
import com.focusguard.data.NotificationLog
import com.focusguard.data.SettingsRepository
import com.focusguard.data.TrustedClock
import com.focusguard.decision.NotificationRules
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Quita las notificaciones de las apps bloqueadas y las guarda para verlas después (J1), y esconde 24 h el aviso del
 * sistema de que FocusGuard sigue en segundo plano (J2). Sin el acceso a notificaciones, o con las opciones apagadas,
 * no hace nada. El contenido solo se guarda en el teléfono.
 */
class FocusNotificationListener : NotificationListenerService(), KoinComponent {

    private val settings: SettingsRepository by inject()
    private val gate: BlockGate by inject()
    private val log: NotificationLog by inject()
    private val clock: TrustedClock by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recentChecks = HashMap<String, Pair<Long, Boolean>>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val options = settings.notifications.value
        if (!options.captureBlocked && !options.hideBackgroundNotice) return
        val notification = sbn.notification
        val extras = notification.extras

        val isNotice = NotificationRules.isBackgroundNotice(
            options, sbn.packageName,
            { listOfNotNull(extras.getCharSequence(Notification.EXTRA_TITLE), extras.getCharSequence(Notification.EXTRA_TEXT)).map { it.toString() } },
            getString(R.string.app_name),
        )
        if (isNotice) {
            snoozeNotification(sbn.key, TimeUnit.HOURS.toMillis(NotificationRules.HIDE_HOURS))
            return
        }
        val capture = NotificationRules.shouldCapture(
            settings = options,
            own = sbn.packageName == packageName,
            ongoing = sbn.isOngoing,
            groupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            category = notification.category,
        ) { isBlocked(sbn.packageName) }
        if (!capture) return
        val atMs = clock.nowMs()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)
        // Solo se quita de la barra una vez guardada: si el guardado falla, el usuario la sigue viendo.
        scope.launch {
            if (runCatching { log.record(atMs, sbn.packageName, title, text) }.isSuccess) cancelNotification(sbn.key)
        }
    }

    /** Una app que manda muchas seguidas se decide una vez cada pocos segundos, no por notificación. */
    private fun isBlocked(packageName: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        recentChecks[packageName]?.takeIf { now - it.first < CHECK_TTL_MS }?.let { return it.second }
        val blocked = gate.isBlocked(packageName)
        recentChecks[packageName] = now to blocked
        return blocked
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHECK_TTL_MS = 2_000L
    }
}
