package com.focusguard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.ChangeGuard
import com.focusguard.data.PartnerApprovals
import kotlinx.coroutines.launch

/**
 * Pide al compañero activo un cambio que se aplicará cuando él lo apruebe. La solicitud sale ya hacia el servidor para que le llegue el correo.
 * Devuelve el aviso para el usuario, o `null` si no hay a quién pedírselo (el compañero dejó de estar activo): el cambio se puede aplicar ya.
 */
internal fun ViewModel.askPartner(guard: ChangeGuard, approvals: PartnerApprovals, actionKey: String, actionId: Int): String? {
    val blocked = guard.askPartner(actionKey, actionId) ?: return null
    if (blocked.requested) viewModelScope.launch { approvals.sync() }
    return blocked.message
}
