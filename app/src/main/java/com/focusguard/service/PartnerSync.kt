package com.focusguard.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.focusguard.data.ApprovalApplier
import com.focusguard.data.ApprovalsReport
import com.focusguard.data.BlocklistRepository
import com.focusguard.data.DnsQueryLog
import com.focusguard.data.PartnerEventOutbox
import com.focusguard.data.PartnerApprovals
import com.focusguard.data.PartnerRepository
import com.focusguard.data.UrgeReportSharing
import com.focusguard.data.SyncRepository
import com.focusguard.server.ApiResult
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Envío de sucesos y señal de vida al servidor, en segundo plano y solo con red. */
object PartnerSync {
    private const val PERIODIC = "partner-sync"
    private const val NOW = "partner-sync-now"
    private const val START = "partner-sync-start"
    private const val PERIOD_HOURS = 6L

    /** Lo periódico (latido y listas) solo corre en el envío periódico y al arrancar; los envíos por un suceso grave solo vacían la cola. */
    internal const val KEY_PERIODIC = "periodic"

    private val needsNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Cada 6 horas: mucho menos que el plazo de 48 h tras el que el servidor avisa de que la app dejó de dar señales. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<PartnerSyncWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(needsNetwork)
            .setInputData(workDataOf(KEY_PERIODIC to true))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Al abrir la app: lo periódico (listas y latido) también corre ya, sin esperar a las 6 horas. */
    fun syncAtStart(context: Context) {
        val request = OneTimeWorkRequestBuilder<PartnerSyncWorker>()
            .setConstraints(needsNetwork)
            .setInputData(workDataOf(KEY_PERIODIC to true))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(START, ExistingWorkPolicy.KEEP, request)
    }

    /** Un suceso grave no espera a las 6 horas. */
    fun flushSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<PartnerSyncWorker>()
            .setConstraints(needsNetwork)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        // Con uno ya esperando se aprovecha: el envío vuelve a leer la cola hasta vaciarla, y sin red no se acumulan peticiones.
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }
}

class PartnerSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val outbox: PartnerEventOutbox by inject()
    private val partner: PartnerRepository by inject()
    private val blocklists: BlocklistRepository by inject()
    private val sync: SyncRepository by inject()
    private val dnsLog: DnsQueryLog by inject()
    private val urgeSharing: UrgeReportSharing by inject()
    private val partnerApprovals: PartnerApprovals by inject()
    private val applier: ApprovalApplier by inject()
    private val notifier: Notifier by inject()

    override suspend fun doWork(): Result {
        // El compañero pudo aceptar (o irse) sin que se abriera la app: sin esto el teléfono no se enteraría.
        partner.refresh()
        // Un aviso que no llegó (teléfono apagado, sin Google Play) no deja una aprobación sin aplicar.
        partnerApprovals.sync()
        applier.applyApproved().takeUnless { it.isEmpty }?.let { notifier.notifyApprovalResolved(ApprovalsReport(true, it.applied, it.failed)) }
        val periodic = inputData.getBoolean(PartnerSync.KEY_PERIODIC, false)
        // El total de consultas DNS de ayer entra en la cola antes de enviarla.
        if (periodic) {
            dnsLog.report()
            urgeSharing.report()
        }
        val flushed = outbox.flush()
        val beat = if (periodic) outbox.heartbeat() else ApiResult.Ok(Unit)
        // Las listas de contenido adulto se renuevan en el envío periódico (y al arrancar la app).
        val lists = if (periodic) blocklists.sync() else ApiResult.Ok(Unit)
        // Se reintenta solo ante fallos que pueden pasar (red, servidor); una sesión caducada espera a que el usuario entre otra vez.
        // La sincronización entre dispositivos (si está activada) corre también en el envío periódico; un fallo suyo no se reintenta aparte: toca otra vez en 6 horas.
        if (periodic) sync.syncNow()
        val retry = listOf(flushed, beat, lists).any { it is ApiResult.Failure && it.error.retryable }
        return if (retry) Result.retry() else Result.success()
    }
}
