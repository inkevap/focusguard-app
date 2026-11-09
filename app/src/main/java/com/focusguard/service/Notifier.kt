package com.focusguard.service

import com.focusguard.vision.ImageActions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.focusguard.MainActivity
import com.focusguard.R
import com.focusguard.data.ApprovalsReport
import com.focusguard.decision.LockBlockers
import com.focusguard.decision.Notice
import com.focusguard.decision.NoticeKind

/** Notificaciones sencillas del sistema: fases del Pomodoro, fin de sesión y avisos anticipados. */
class Notifier(private val context: Context) {

    init {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SESSIONS, context.getString(R.string.channel_sessions), NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_NOTICES, context.getString(R.string.channel_notices), NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_LIMIT_STATUS, context.getString(R.string.channel_limit_status), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_APPROVALS, context.getString(R.string.channel_approvals), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    /** El compañero respondió a una solicitud. Sin el permiso de notificaciones no se muestra nada: la respuesta se ve al abrir Aprobaciones. */
    fun notifyApprovalResolved(report: ApprovalsReport) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val (limited, more) = LockBlockers.limit(report.applied)
        val (title, text) = when {
            report.applied.isNotEmpty() -> context.getString(R.string.approval_applied_title) to
                context.getString(R.string.approval_applied_text, limited.joinToString("; ") + if (more > 0) " (+$more)" else "")
            report.failed.isNotEmpty() -> context.getString(R.string.approval_failed_title) to report.failed.first()
            else -> context.getString(R.string.approval_resolved_title) to context.getString(R.string.approval_resolved_text)
        }
        // Una respuesta nueva sustituye a la anterior sin acumular avisos.
        post(
            CHANNEL_APPROVALS, NOTICE_ID, title, text, tag = APPROVAL_TAG, contentIntent = open,
        )
    }

    /** El compañero aceptó la invitación: desde ahora recibe los avisos y puede aprobar. Se abre la app, donde está su estado. */
    fun notifyPartnerConfirmed() {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        post(
            CHANNEL_APPROVALS, NOTICE_ID, context.getString(R.string.partner_confirmed_title), context.getString(R.string.partner_confirmed_text),
            tag = PARTNER_TAG, contentIntent = open,
        )
    }

    fun notifySession(id: Int, title: String, text: String) = post(CHANNEL_SESSIONS, id, title, text)

    /**
     * La detección de imágenes vio algo sugerente: ofrece bloquear lo que hay en pantalla. El botón «Bloquear ahora» avisa al servicio por una acción interna
     * (solo dentro de la app); tocar el aviso abre FocusGuard. Un aviso nuevo sustituye al anterior.
     */
    fun notifyImageOffer() {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), flags)
        val block = PendingIntent.getBroadcast(context, REQUEST_IMAGE_BLOCK, Intent(ImageActions.BLOCK_NOW).setPackage(context.packageName), flags)
        val notification = NotificationCompat.Builder(context, CHANNEL_NOTICES)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(context.getString(R.string.image_offer_title))
            .setContentText(context.getString(R.string.image_offer_text))
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.image_offer_action), block)
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(IMAGE_OFFER_TAG, NOTICE_ID, notification)
    }

    fun cancelImageOffer() {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(IMAGE_OFFER_TAG, NOTICE_ID)
    }

    private fun post(channel: String, id: Int, title: String, text: String, tag: String? = null, contentIntent: PendingIntent? = null) {
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(tag, id, notification)
    }

    /** Notificación fija y silenciosa con lo que llevas y lo que queda de un límite; se actualiza sobre sí misma. */
    fun showLimitStatus(title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_LIMIT_STATUS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(LIMIT_STATUS_TAG, NOTICE_ID, notification)
    }

    fun clearLimitStatus() =
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(LIMIT_STATUS_TAG, NOTICE_ID)

    /** ¿Puede mostrar avisos ahora? Sin el permiso del sistema un aviso se perdería sin que nadie lo vea. */
    fun canNotify(): Boolean {
        val manager = NotificationManagerCompat.from(context)
        return manager.areNotificationsEnabled() &&
            (manager.getNotificationChannel(CHANNEL_NOTICES)?.importance ?: NotificationManager.IMPORTANCE_DEFAULT) != NotificationManager.IMPORTANCE_NONE
    }

    /** Aviso de lo que pasa con un horario o un límite, o el aviso de cada día o de cada semana. */
    fun notifyNotice(notice: Notice) {
        val (title, text) = when (notice.kind) {
            NoticeKind.SCHEDULE_STARTING ->
                context.getString(R.string.notice_start_title, notice.profileTitle) to context.getString(R.string.notice_start_text, notice.minutes)
            NoticeKind.LIMIT_NEARING ->
                context.getString(R.string.notice_limit_title, notice.profileTitle) to context.getString(R.string.notice_limit_text, notice.minutes)
            NoticeKind.SCHEDULE_ENDED ->
                context.getString(R.string.notice_ended_title, notice.profileTitle) to context.getString(R.string.notice_ended_text)
            NoticeKind.PAUSE_ENDING ->
                context.getString(R.string.notice_pause_ending_title, notice.profileTitle) to context.getString(R.string.notice_pause_ending_text, notice.minutes)
            NoticeKind.PAUSE_ENDED ->
                context.getString(R.string.notice_pause_ended_title, notice.profileTitle) to context.getString(R.string.notice_pause_ended_text)
            NoticeKind.QUICK_BLOCK_ENDED ->
                context.getString(R.string.notice_quick_ended_title) to context.getString(R.string.notice_ended_text)
            NoticeKind.STREAK_AT_RISK ->
                context.resources.getQuantityString(R.plurals.notice_streak_risk_title, notice.count, notice.count) to
                    context.resources.getQuantityString(R.plurals.notice_streak_risk_text, notice.minutes, notice.minutes)
            NoticeKind.STREAK_MILESTONE ->
                context.resources.getQuantityString(R.plurals.notice_streak_milestone_title, notice.count, notice.count) to
                    context.getString(R.string.notice_streak_milestone_text)
            NoticeKind.REMINDER -> context.getString(R.string.notice_reminder_title) to notice.text
            NoticeKind.MOOD_PROMPT -> context.getString(R.string.notice_mood_title) to context.getString(R.string.notice_mood_text)
            NoticeKind.TIME_NUDGE ->
                context.getString(R.string.notice_nudge_title) to context.resources.getQuantityString(R.plurals.notice_nudge_text, notice.minutes, notice.minutes)
            NoticeKind.DAILY_MOTIVATION -> context.getString(R.string.notice_daily_title) to notice.text
            NoticeKind.WEEKLY_REPORT -> context.getString(R.string.notice_weekly_title) to
                if (notice.count == 0) context.getString(R.string.notice_weekly_none)
                else context.resources.getQuantityString(R.plurals.notice_weekly_text, notice.count, notice.count)
        }
        // Un aviso del mismo tipo y perfil sustituye al anterior en vez de acumularse en la bandeja.
        post(CHANNEL_NOTICES, NOTICE_ID, title, text, tag = "${notice.kind}:${notice.profileId}")
    }

    private companion object {
        const val NOTICE_ID = 5_000_000
        const val IMAGE_OFFER_TAG = "image_offer"
        const val LIMIT_STATUS_TAG = "limit_status"
        const val CHANNEL_LIMIT_STATUS = "limit_status"
        const val REQUEST_IMAGE_BLOCK = 7_001
        const val CHANNEL_SESSIONS = "sessions"
        const val CHANNEL_NOTICES = "notices"
        const val CHANNEL_APPROVALS = "approvals"
        const val APPROVAL_TAG = "approval"
        const val PARTNER_TAG = "partner"
    }
}
