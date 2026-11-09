package com.focusguard.data

import com.focusguard.decision.WrongBlockReport
import com.focusguard.server.AccountStore
import com.focusguard.server.ApiResult
import com.focusguard.server.ServerApi

/** Envía al servidor un bloqueo que el usuario cree erróneo (B10). Es una acción expresa con respuesta inmediata: sin cola de reintentos. */
class WrongBlockReporter(private val api: ServerApi, private val account: AccountStore) {
    enum class Outcome { SENT, NEEDS_ACCOUNT, FAILED }

    suspend fun send(report: WrongBlockReport): Outcome = when {
        !account.hasSession -> Outcome.NEEDS_ACCOUNT
        api.reportWrongBlock(report.kind.wire, report.value) is ApiResult.Ok -> Outcome.SENT
        else -> Outcome.FAILED
    }
}
