package com.focusguard.ui.account

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.data.AccountState
import com.focusguard.data.PartnerState
import com.focusguard.decision.PartnerPanel
import com.focusguard.data.PartnerRepository
import com.focusguard.server.HelpedInfo
import com.focusguard.server.PartnerInfo
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.PasswordField
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SecureWindowEffect
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.StatusChip
import com.focusguard.ui.components.SwitchRow
import com.focusguard.ui.components.rememberMessageSnackbar

@Composable
fun AccountScreen(viewModel: AccountViewModel, onBack: () -> Unit, onOpenBackup: () -> Unit) {
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val account by viewModel.state.collectAsStateWithLifecycle()
    val partner by viewModel.partnerState.collectAsStateWithLifecycle()
    val partners by viewModel.partners.collectAsStateWithLifecycle()
    val partnerNotices by viewModel.partnerNotices.collectAsStateWithLifecycle()
    val panel by viewModel.panel.collectAsStateWithLifecycle()
    val profileNames by viewModel.profileNames.collectAsStateWithLifecycle()
    val parentalMode by viewModel.parentalMode.collectAsStateWithLifecycle()
    val helping by viewModel.helping.collectAsStateWithLifecycle()
    val snackbar = rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)
    // Pide una contraseña: sin capturas de pantalla ni vista previa en apps recientes.
    SecureWindowEffect()
    Scaffold(
        topBar = {
            FgTopBar("Cuenta", onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        AccountContent(
            serverUrl = serverUrl,
            account = account,
            partner = partner,
            partnerNotices = partnerNotices,
            parentalMode = parentalMode,
            helping = helping,
            partners = partners,
            panel = panel,
            profileNames = profileNames,
            busy = viewModel.busy,
            actions = AccountActions(
                saveServerUrl = viewModel::saveServerUrl,
                register = viewModel::register,
                login = viewModel::login,
                forgotPassword = viewModel::forgotPassword,
                resendVerification = viewModel::resendVerification,
                refresh = viewModel::refresh,
                signOut = viewModel::signOut,
                invitePartner = viewModel::invitePartner,
                refreshPartner = viewModel::refreshPartner,
                removePartner = { viewModel.removePartner() },
                removePartnerById = { viewModel.removePartner(it) },
                setPartnerNotices = viewModel::setPartnerNotices,
                setParentalMode = viewModel::setParentalMode,
                setPanel = viewModel::setPanel,
                openBackup = onOpenBackup,
            ),
            modifier = Modifier.padding(padding),
        )
    }
}

class AccountActions(
    val saveServerUrl: (String) -> Unit,
    val register: (String, String) -> Unit,
    val login: (String, String) -> Unit,
    val forgotPassword: (String) -> Unit,
    val resendVerification: () -> Unit,
    val refresh: () -> Unit,
    val signOut: () -> Unit,
    val invitePartner: (String) -> Unit,
    val refreshPartner: () -> Unit,
    val removePartner: () -> Unit,
    val setPartnerNotices: (Boolean) -> Unit,
    val setParentalMode: (Boolean) -> Unit,
    val openBackup: () -> Unit,
    val removePartnerById: (String) -> Unit = {},
    val setPanel: (PartnerPanel) -> Unit = {},
)

@Composable
internal fun AccountContent(serverUrl: String, account: AccountState, partner: PartnerState, partnerNotices: Boolean, parentalMode: Boolean, helping: List<HelpedInfo>, busy: Boolean, actions: AccountActions, modifier: Modifier = Modifier, partners: List<PartnerInfo> = emptyList(), panel: PartnerPanel = PartnerPanel(), profileNames: List<Pair<Long, String>> = emptyList()) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AppCard {
                SectionTitle("Para qué sirve la cuenta")
                Hint(
                    "Con una cuenta tu compañero de responsabilidad puede recibir avisos y aprobar cambios por correo, y puedes " +
                        "guardar una copia cifrada de tu configuración. El bloqueo sigue funcionando sin cuenta y sin internet.",
                )
            }
        }
        item { ServerCard(serverUrl, editable = !account.signedIn, busy = busy, onSave = actions.saveServerUrl) }
        if (account.signedIn) {
            item { SignedInCard(account, busy, actions) }
            item {
                AppCard {
                    SectionTitle("Copia de seguridad", FgIcons.Shield)
                    Hint("Guarda tu configuración cifrada en tu servidor para recuperarla si cambias de teléfono.")
                    SecondaryButton("Abrir", actions.openBackup, Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            item { PartnerCard(partner, partnerNotices, parentalMode, verified = account.emailVerified == true, busy = busy, actions = actions, partners = partners) }
            if (partner is PartnerState.Active) item { PartnerPanelCard(panel, parentalMode, profileNames, actions.setPanel) }
            if (helping.isNotEmpty()) item { HelpingCard(helping, partner, busy, actions) }
        } else if (serverUrl.isNotBlank()) {
            item { SignInCard(busy, actions) }
        }
    }
}

@Composable
private fun ServerCard(serverUrl: String, editable: Boolean, busy: Boolean, onSave: (String) -> Unit) {
    var text by rememberSaveable(serverUrl) { mutableStateOf(serverUrl) }
    AppCard {
        SectionTitle("Servidor")
        Hint(
            if (editable) "La dirección de tu servidor FocusGuard. Solo se aceptan direcciones https."
            else "Para cambiar de servidor, cierra antes la sesión.",
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Dirección (https://…)") },
            singleLine = true,
            enabled = editable && !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        if (editable) SecondaryButton("Guardar dirección", { onSave(text) }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !busy && text.isNotBlank() && text != serverUrl)
    }
}

@Composable
private fun SignInCard(busy: Boolean, actions: AccountActions) {
    var email by rememberSaveable { mutableStateOf("") }
    // Sin rememberSaveable: la contraseña no debe acabar en el estado guardado de la actividad.
    var password by remember { mutableStateOf("") }
    AppCard {
        SectionTitle("Entrar o crear cuenta")
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        PasswordField("Contraseña (8 caracteres o más)", password, { password = it }, Modifier.padding(top = 8.dp))
        PrimaryButton("Iniciar sesión", { actions.login(email, password) }, Modifier.fillMaxWidth().padding(top = 12.dp), enabled = !busy)
        SecondaryButton("Crear cuenta", { actions.register(email, password) }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !busy)
        SecondaryButton("Olvidé mi contraseña", { actions.forgotPassword(email) }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !busy)
    }
}

@Composable
private fun SignedInCard(account: AccountState, busy: Boolean, actions: AccountActions) {
    AppCard {
        SectionTitle(account.email.orEmpty())
        when (account.emailVerified) {
            true -> StatusChip("Correo confirmado")
            false -> StatusChip("Falta confirmar el correo")
            null -> StatusChip("Sin comprobar")
        }
        if (account.emailVerified == false) {
            Hint("Abre el enlace del correo que te enviamos. Hasta entonces no podrás invitar a un compañero ni recuperar el PIN por correo.")
            SecondaryButton("Reenviar el enlace", actions.resendVerification, Modifier.fillMaxWidth(), enabled = !busy)
        }
        SecondaryButton("Comprobar de nuevo", actions.refresh, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !busy)
        Hint(if (account.deviceRegistered) "Este teléfono está registrado en tu cuenta." else "Este teléfono aún no está registrado: se hará al tener conexión.")
        SecondaryButton("Cerrar sesión", actions.signOut, Modifier.fillMaxWidth(), enabled = !busy)
    }
}

/** Las personas que me eligieron como compañero (I3); si yo no tengo compañero, puedo pedirles que lo sean también (relación mutua). */
@Composable
internal fun HelpingCard(helping: List<HelpedInfo>, partner: PartnerState, busy: Boolean, actions: AccountActions) {
    AppCard {
        SectionTitle("Personas a las que ayudas", FgIcons.Users)
        Hint(
            "Te eligieron como su compañero: te escriben por correo cuando quieren debilitar su protección y tú decides desde el enlace. " +
                "Si tú también quieres que alguien te ayude, puede ser una de ellas: así os cuidáis los dos.",
        )
        helping.forEach { person ->
            Column(Modifier.padding(top = 8.dp)) {
                Text(person.email, style = MaterialTheme.typography.titleMedium)
                if (person.mutual) StatusChip("Os ayudáis los dos")
                if (person.pending > 0) {
                    Hint(if (person.pending == 1) "1 solicitud espera tu respuesta en el correo." else "${person.pending} solicitudes esperan tu respuesta en el correo.")
                }
                if (!person.mutual && partner == PartnerState.None) {
                    SecondaryButton("Que también sea mi compañero", { actions.invitePartner(person.email) }, Modifier.fillMaxWidth(), enabled = !busy)
                }
            }
        }
    }
}

@Composable
private fun PartnerCard(partner: PartnerState, notices: Boolean, parentalMode: Boolean, verified: Boolean, busy: Boolean, actions: AccountActions, partners: List<PartnerInfo> = emptyList()) {
    var email by rememberSaveable { mutableStateOf("") }
    var another by rememberSaveable { mutableStateOf("") }
    AppCard {
        SectionTitle("Compañero de responsabilidad", FgIcons.Users)
        when (partner) {
            PartnerState.None -> {
                Hint(
                    "Una persona de confianza a la que le llega un aviso cuando intentas desactivar la protección, y que aprueba " +
                        "por correo los cambios que la debilitan. No necesita cuenta ni instalar nada.",
                )
                if (!verified) {
                    Hint("Confirma antes tu correo para poder invitarle.")
                } else {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Correo de tu compañero") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton("Invitar", { actions.invitePartner(email) }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !busy)
                }
            }
            is PartnerState.Invited -> {
                StatusChip("Invitación enviada")
                Hint("${partner.email} tiene que abrir el enlace del correo y aceptar (vale 7 días). Hasta entonces no recibe nada.")
                SecondaryButton("Comprobar de nuevo", actions.refreshPartner, Modifier.fillMaxWidth(), enabled = !busy)
                if (partners.size <= 1) SecondaryButton("Cancelar la invitación", actions.removePartner, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !busy)
            }
            is PartnerState.Active -> {
                StatusChip("Compañero activo")
                Text(partner.email, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Hint(
                    "Recibe aviso cuando intentas apagar la protección o usas el pánico, y aprueba los cambios que la debilitan " +
                        "(elígelo en Aprobaciones). Cerrar la sesión también necesita su permiso.",
                )
                SwitchRow(
                    "Avisarle de lo que pasa",
                    "Solo se envía qué ocurrió, cuándo y el nombre de la app o la web; nunca lo que ves en pantalla. Apagarlo lo tiene que aprobar él.",
                    notices,
                    actions.setPartnerNotices,
                )
                SwitchRow(
                    "Modo parental",
                    "Para un teléfono de un hijo o una hija: todo cambio que debilite la protección lo aprueba su padre o su madre, sin retardos ni frases. " +
                        "Quitar este modo también lo tiene que aprobar.",
                    parentalMode,
                    actions.setParentalMode,
                )
                if (partners.size <= 1) {
                    SecondaryButton("Pedir que me quite", actions.removePartner, Modifier.fillMaxWidth(), enabled = !busy)
                    Hint("Quitarle lo tiene que aprobar él por correo.")
                }
            }
        }
        // Con compañero se pueden sumar más (hasta tres): una solicitud les llega a todos y basta con que uno la apruebe.
        if (partner != PartnerState.None) {
            if (partners.size > 1) {
                Text("Tus compañeros", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                partners.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.email, style = MaterialTheme.typography.bodyLarge)
                            Hint(if (p.accepted) "Activo" else "Invitación pendiente")
                        }
                        TextButton({ actions.removePartnerById(p.id) }, enabled = !busy) { Text(if (p.accepted) "Pedir que me quite" else "Cancelar") }
                    }
                }
            }
            if (verified && partners.size < PartnerRepository.MAX_PARTNERS) {
                Text("Añadir otro compañero", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                Hint("Hasta ${PartnerRepository.MAX_PARTNERS}. Cada cambio que pidas les llega a todos y basta con que uno lo apruebe; los avisos le llegan a cada uno según lo que haya elegido.")
                OutlinedTextField(
                    value = another,
                    onValueChange = { another = it },
                    label = { Text("Correo de otro compañero") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton("Invitar", { actions.invitePartner(another); another = "" }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !busy && another.isNotBlank())
            }
        }
    }
}
