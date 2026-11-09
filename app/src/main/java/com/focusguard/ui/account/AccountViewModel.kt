package com.focusguard.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.BuildConfig
import com.focusguard.data.AccountRepository
import com.focusguard.data.AccountState
import com.focusguard.data.ApprovalExecutor
import com.focusguard.data.ChangeGuard
import com.focusguard.data.GuardResult
import com.focusguard.data.HelpingRepository
import com.focusguard.data.PartnerApprovals
import com.focusguard.data.PartnerRepository
import com.focusguard.data.PartnerState
import com.focusguard.decision.Approvals
import com.focusguard.decision.PartnerPanel
import com.focusguard.ui.askPartner
import com.focusguard.server.HelpedInfo
import com.focusguard.server.PartnerInfo
import com.focusguard.server.PartnerRemoval
import com.focusguard.data.SettingsRepository
import com.focusguard.server.ApiResult
import com.focusguard.server.ServerUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(
    private val settings: SettingsRepository,
    private val account: AccountRepository,
    private val partner: PartnerRepository,
    private val partnerApprovals: PartnerApprovals,
    private val guard: ChangeGuard,
    private val helpingRepository: HelpingRepository,
    profileRepository: com.focusguard.data.ProfileRepository,
) : ViewModel() {

    val serverUrl: StateFlow<String> = settings.serverUrl
    val state: StateFlow<AccountState> = account.state
    val partnerState: StateFlow<PartnerState> = partner.state
    val partners: StateFlow<List<PartnerInfo>> = partner.partners
    val partnerNotices: StateFlow<Boolean> = settings.partnerNotices
    val panel: StateFlow<PartnerPanel> = settings.partnerPanel

    /** Los perfiles, para elegir de cuáles se avisa cuando bloquean algo. */
    val profileNames: StateFlow<List<Pair<Long, String>>> = profileRepository.profiles
        .map { list -> list.map { it.id to it.title } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val helping: StateFlow<List<HelpedInfo>> = helpingRepository.helping

    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    init {
        // Al abrir la pantalla se pregunta al servidor si el correo ya se confirmó.
        if (account.state.value.signedIn) viewModelScope.launch {
            account.refresh()
            refreshPartnerAndHelping()
        }
    }

    fun clearMessage() {
        message = null
    }

    /** Guarda la dirección del servidor; solo con la sesión cerrada y solo `https` (en depuración, también `http` local). */
    fun saveServerUrl(raw: String) {
        if (account.state.value.signedIn) {
            message = "Cierra la sesión antes de cambiar de servidor."
            return
        }
        val url = ServerUrl.normalize(raw, BuildConfig.DEBUG)
        if (url == null) {
            message = "La dirección tiene que empezar por https:// y no llevar nada detrás del dominio."
            return
        }
        // De otro servidor salen las listas de bloqueo: cambiar uno ya puesto debilita la protección y pasa por las aprobaciones.
        if (serverUrl.value.isNotBlank() && url != serverUrl.value) {
            val blocked = guard.blockedMessage(ApprovalExecutor.KEY_SERVER_CHANGE, Approvals.ACTION_SERVER_CHANGE, url)
            if (blocked != null) {
                message = blocked
                return
            }
        }
        message = if (account.changeServer(url)) "Dirección guardada." else "No se pudo guardar la dirección."
    }

    fun register(email: String, password: String) = run(
        check = validate(email, password),
        action = { account.register(email, password) },
        success = "Cuenta creada. Te hemos enviado un correo para confirmar tu dirección.",
    )

    fun login(email: String, password: String) = run(
        check = validate(email, password),
        action = { account.login(email, password) },
        success = "Sesión iniciada.",
    )

    fun forgotPassword(email: String) = run(
        check = if (email.isBlank()) "Escribe tu correo." else null,
        action = { account.requestPasswordReset(email) },
        success = "Si ese correo tiene cuenta, te hemos enviado un enlace para elegir una contraseña nueva.",
    )

    fun resendVerification() = run(null, { account.resendVerification() }, "Te hemos enviado otro enlace. Mira también la carpeta de spam.")

    fun refresh() = run(null, { account.refresh().also { refreshPartnerAndHelping() } }, null)

    /**
     * Cerrar la sesión con un compañero activo callaría sus avisos: lo tiene que aprobar él. Sin compañero (o con una
     * invitación que aún no aceptó) es inmediato.
     */
    fun signOut() {
        if (!partner.isActive) {
            viewModelScope.launch {
                account.signOut()
                message = "Sesión cerrada."
            }
            return
        }
        askPartner(ApprovalExecutor.KEY_SIGN_OUT, Approvals.ACTION_SIGN_OUT)
    }

    val parentalMode: StateFlow<Boolean> get() = settings.parentalMode

    /**
     * Modo parental (I7): encenderlo es libre y necesita un compañero activo (con los avisos encendidos, para que sepa qué pasa);
     * apagarlo lo tiene que aprobar él.
     */
    fun setParentalMode(enabled: Boolean) {
        when {
            enabled && !partner.isActive -> message = "Primero necesitas un compañero activo: él aprobará los cambios."
            enabled -> {
                guard.enableParental()
                message = "Modo parental activado. Desde ahora, los cambios que debiliten la protección los aprueba tu compañero."
                viewModelScope.launch { partnerApprovals.sync() }
            }
            !partner.isActive -> settings.setParentalMode(false)
            else -> askPartner(ApprovalExecutor.KEY_PARENTAL_OFF, Approvals.ACTION_PARENTAL_OFF)
        }
    }

    /** Encender los avisos es libre; apagarlos con un compañero activo lo tiene que aprobar él. */
    fun setPartnerNotices(enabled: Boolean) {
        if (enabled || !partner.isActive) settings.setPartnerNotices(enabled) else askPartner(ApprovalExecutor.KEY_PARTNER_NOTICES_OFF, Approvals.ACTION_PARTNER_NOTICES_OFF)
    }

    /**
     * Cambia el panel del compañero. Poner más avisos o más protecciones es libre; quitar alguno, con un compañero activo, se lo pide a él
     * (lo que se pide se aplica entero cuando lo apruebe).
     */
    fun setPanel(next: PartnerPanel) {
        val current = settings.partnerPanel.value
        if (next == current) return
        if (!current.weakens(next) || !partner.isActive) {
            settings.setPartnerPanel(next)
            return
        }
        val result = guard.check(ApprovalExecutor.KEY_PARTNER_PANEL, Approvals.ACTION_PARTNER_PANEL, next.encode(), viaPartner = true)
        (result as? com.focusguard.data.GuardResult.Blocked)?.let { message = it.message }
        viewModelScope.launch { partnerApprovals.sync() }
    }

    /** Con un compañero activo nunca se permite sin pedírselo (`viaPartner`); la solicitud sale ya hacia el servidor para que reciba el correo. */
    private fun askPartner(actionKey: String, actionId: Int) {
        message = askPartner(guard, partnerApprovals, actionKey, actionId) ?: return
    }

    fun invitePartner(email: String) = run(
        check = if (!validEmail(email)) "Escribe el correo de tu compañero." else null,
        action = { partner.invite(email) },
        success = "Invitación enviada. Tu compañero tiene que abrir el enlace del correo y aceptar.",
    )

    fun refreshPartner() = run(null, { refreshPartnerAndHelping() }, null)

    /** Quién es mi compañero y a quién ayudo van juntos (cada uno cambia con la aceptación del otro): una sola vuelta, las dos peticiones a la vez. */
    private suspend fun refreshPartnerAndHelping(): ApiResult<Unit> = coroutineScope {
        val helping = async { helpingRepository.refresh() }
        partner.refresh().also { helping.await() }
    }

    /** Cancelar una invitación es libre; quitar a quien ya aceptó se lo pide a él. Sin [id], al compañero principal. */
    fun removePartner(id: String? = null) = run(null, { partner.remove(id) }) {
        when (it) {
            PartnerRemoval.Done -> "Invitación cancelada."
            is PartnerRemoval.NeedsApproval -> "Se lo hemos pedido a tu compañero. Cuando lo apruebe por correo, dejará de serlo."
        }
    }

    private fun validEmail(email: String) = email.isNotBlank() && email.contains('@')

    private fun validate(email: String, password: String) = when {
        !validEmail(email) -> "Escribe un correo válido."
        password.length < 8 -> "La contraseña necesita al menos 8 caracteres."
        else -> null
    }

    private fun run(check: String?, action: suspend () -> ApiResult<Unit>, success: String?) = run(check, action) { success }

    private fun <T> run(check: String?, action: suspend () -> ApiResult<T>, success: (T) -> String?) {
        if (busy) return
        if (check != null) {
            message = check
            return
        }
        busy = true
        viewModelScope.launch {
            try {
                when (val result = action()) {
                    is ApiResult.Ok -> success(result.value)?.let { message = it }
                    is ApiResult.Failure -> message = result.error.message
                }
            } finally {
                busy = false
            }
        }
    }
}
