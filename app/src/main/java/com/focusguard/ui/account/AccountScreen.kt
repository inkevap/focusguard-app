package com.focusguard.ui.account

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.data.AccountState
import com.focusguard.data.PartnerRepository
import com.focusguard.data.PartnerState
import com.focusguard.decision.PartnerPanel
import com.focusguard.server.HelpedInfo
import com.focusguard.server.PartnerInfo
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgHero
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.FgSwitchRow
import com.focusguard.ui.components.PasswordField
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SecureWindowEffect
import com.focusguard.ui.components.SubPageHost
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.icons.FgIcons
import com.focusguard.ui.profiles.BottomAction
import com.focusguard.ui.profiles.GroupStack

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
        onBack = onBack,
        snackbar = snackbar,
    )
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

/** Las subpantallas de Cuenta: lo que no cabe en una fila. */
private enum class AccountPage(val title: String, val panel: PanelList? = null) {
    SERVER("Servidor"),
    SIGN_IN("Entrar o crear cuenta"),
    PARTNER("Compañero"),
    INVITE("Invitar a un compañero"),
    HELPING("Personas a las que ayudas"),
    PANEL("Qué se le avisa y qué se protege"),
    PANEL_NOTICES(PanelList.NOTICES.title, PanelList.NOTICES),
    PANEL_BLOCKED(PanelList.BLOCKED.title, PanelList.BLOCKED),
    PANEL_AREAS(PanelList.AREAS.title, PanelList.AREAS),
    PANEL_SCREENS(PanelList.SCREENS.title, PanelList.SCREENS),
}

private fun PanelList.page() = when (this) {
    PanelList.NOTICES -> AccountPage.PANEL_NOTICES
    PanelList.BLOCKED -> AccountPage.PANEL_BLOCKED
    PanelList.AREAS -> AccountPage.PANEL_AREAS
    PanelList.SCREENS -> AccountPage.PANEL_SCREENS
}

@Composable
internal fun AccountContent(
    serverUrl: String, account: AccountState, partner: PartnerState, partnerNotices: Boolean, parentalMode: Boolean, helping: List<HelpedInfo>, busy: Boolean,
    actions: AccountActions, modifier: Modifier = Modifier, partners: List<PartnerInfo> = emptyList(), panel: PartnerPanel = PartnerPanel(),
    profileNames: List<Pair<Long, String>> = emptyList(), onBack: () -> Unit = {}, snackbar: SnackbarHostState? = null,
) {
    var page by rememberSaveable { mutableStateOf<AccountPage?>(null) }
    // El modo del panel que se está editando se comparte entre sus listas.
    var parentalTab by rememberSaveable { mutableStateOf(parentalMode) }
    val verified = account.emailVerified == true

    // Al entrar o salir de la sesión, o al cambiar el compañero, la subpantalla de turno puede dejar de tener sentido.
    LaunchedEffect(account.signedIn) {
        if (account.signedIn) {
            if (page == AccountPage.SIGN_IN) page = null
        } else if (page != null && page != AccountPage.SERVER && page != AccountPage.SIGN_IN) {
            page = null
        }
    }
    LaunchedEffect(partner, partners.size) {
        if (page == AccountPage.INVITE) page = AccountPage.PARTNER
    }

    SubPageHost(
        page = page,
        onBack = { page = null },
        root = {
            FgScreen("Cuenta", modifier = modifier, onBack = onBack, snackbar = snackbar) {
                if (account.signedIn) {
                    item {
                        FgHero(
                            account.email.orEmpty(),
                            pill = when (account.emailVerified) {
                                true -> "Correo confirmado"
                                false -> "Falta confirmar el correo"
                                null -> "Sin comprobar"
                            },
                        )
                    }
                    item {
                        FgGroup(
                            footer = if (account.emailVerified == false) {
                                "Abre el enlace del correo que te enviamos. Hasta entonces no podrás invitar a un compañero ni recuperar el PIN por correo."
                            } else null,
                        ) {
                            if (account.emailVerified == false) FgActionRow("Reenviar el enlace", actions.resendVerification, enabled = !busy)
                            FgActionRow(
                                "Comprobar de nuevo", actions.refresh, enabled = !busy,
                                subtitle = if (account.deviceRegistered) "Este teléfono está registrado en tu cuenta." else "Este teléfono aún no está registrado: se hará al tener conexión.",
                            )
                        }
                    }
                    item {
                        FgGroup {
                            FgRow("Servidor", value = serverUrl, onClick = { page = AccountPage.SERVER })
                            FgRow(
                                "Compañero de responsabilidad", icon = FgIcons.Users, onClick = { page = AccountPage.PARTNER },
                                value = when (partner) {
                                    PartnerState.None -> "Ninguno"
                                    is PartnerState.Invited -> "Invitado"
                                    is PartnerState.Active -> "Activo"
                                },
                            )
                            if (helping.isNotEmpty()) FgRow("Personas a las que ayudas", icon = FgIcons.Users, value = helping.size.toString(), onClick = { page = AccountPage.HELPING })
                            FgRow(
                                "Copia de seguridad", icon = FgIcons.Shield, onClick = actions.openBackup,
                                subtitle = "Guarda tu configuración cifrada en tu servidor para recuperarla si cambias de teléfono.",
                            )
                        }
                    }
                    item { FgGroup { FgActionRow("Cerrar sesión", actions.signOut, danger = true, enabled = !busy) } }
                } else {
                    item {
                        FgHero(
                            "Sin cuenta",
                            body = "Con una cuenta tu compañero de responsabilidad puede recibir avisos y aprobar cambios por correo, y puedes " +
                                "guardar una copia cifrada de tu configuración. El bloqueo sigue funcionando sin cuenta y sin internet.",
                        )
                    }
                    item {
                        FgGroup {
                            FgRow("Servidor", value = serverUrl.ifBlank { "Sin configurar" }, onClick = { page = AccountPage.SERVER })
                            if (serverUrl.isNotBlank()) FgRow("Entrar o crear cuenta", icon = FgIcons.Users, onClick = { page = AccountPage.SIGN_IN })
                        }
                    }
                }
            }
        },
        sub = { current ->
            val back = { page = null }
            when (current) {
                AccountPage.SERVER -> ServerPage(serverUrl, editable = !account.signedIn, busy = busy, onSave = actions.saveServerUrl, snackbar = snackbar, onBack = back)
                AccountPage.SIGN_IN -> SignInPage(busy, actions, snackbar, back)
                AccountPage.PARTNER -> FgScreen(current.title, onBack = back, snackbar = snackbar) {
                    item {
                        PartnerCard(
                            partner, partnerNotices, parentalMode, verified = verified, busy = busy, actions = actions, partners = partners,
                            onInvite = { page = AccountPage.INVITE }, onOpenPanel = { page = AccountPage.PANEL },
                        )
                    }
                }
                AccountPage.INVITE -> InvitePage(partner, busy, actions, snackbar, onBack = { page = AccountPage.PARTNER })
                AccountPage.HELPING -> FgScreen(current.title, onBack = back, snackbar = snackbar) { item { HelpingCard(helping, partner, busy, actions) } }
                AccountPage.PANEL -> FgScreen(current.title, onBack = { page = AccountPage.PARTNER }, snackbar = snackbar) {
                    item { PartnerPanelRoot(panel, parentalMode, parentalTab, { parentalTab = it }, { page = it.page() }) }
                }
                else -> FgScreen(current.title, onBack = { page = AccountPage.PANEL }, snackbar = snackbar) {
                    item { PartnerPanelList(current.panel ?: PanelList.NOTICES, panel, parentalTab, profileNames, actions.setPanel) }
                }
            }
        },
    )
}

@Composable
private fun ServerPage(serverUrl: String, editable: Boolean, busy: Boolean, onSave: (String) -> Unit, snackbar: SnackbarHostState?, onBack: () -> Unit) {
    var text by rememberSaveable(serverUrl) { mutableStateOf(serverUrl) }
    FgScreen(
        "Servidor", onBack = onBack, snackbar = snackbar,
        bottomBar = {
            if (editable) BottomAction {
                PrimaryButton("Guardar dirección", { onSave(text) }, Modifier.fillMaxWidth(), enabled = !busy && text.isNotBlank() && text != serverUrl)
            }
        },
    ) {
        item {
            FgGroup(
                footer = if (editable) "La dirección de tu servidor FocusGuard. Solo se aceptan direcciones https."
                else "Para cambiar de servidor, cierra antes la sesión.",
            ) {
                FgCell {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Dirección (https://…)") },
                        singleLine = true,
                        enabled = editable && !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SignInPage(busy: Boolean, actions: AccountActions, snackbar: SnackbarHostState?, onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    // Sin rememberSaveable: la contraseña no debe acabar en el estado guardado de la actividad.
    var password by remember { mutableStateOf("") }
    FgScreen(
        "Entrar o crear cuenta", onBack = onBack, snackbar = snackbar,
        bottomBar = {
            BottomAction {
                PrimaryButton("Iniciar sesión", { actions.login(email, password) }, Modifier.fillMaxWidth(), enabled = !busy)
                SecondaryButton("Crear cuenta", { actions.register(email, password) }, Modifier.fillMaxWidth(), enabled = !busy)
            }
        },
    ) {
        item {
            GroupStack {
                FgGroup {
                    FgCell {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Correo") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    FgCell { PasswordField("Contraseña (8 caracteres o más)", password, { password = it }) }
                }
                FgGroup { FgActionRow("Olvidé mi contraseña", { actions.forgotPassword(email) }, enabled = !busy) }
            }
        }
    }
}

/** Las personas que me eligieron como compañero (I3); si yo no tengo compañero, puedo pedirles que lo sean también (relación mutua). */
@Composable
internal fun HelpingCard(helping: List<HelpedInfo>, partner: PartnerState, busy: Boolean, actions: AccountActions) {
    GroupStack {
        FgNote(
            "Te eligieron como su compañero: te escriben por correo cuando quieren debilitar su protección y tú decides desde el enlace. " +
                "Si tú también quieres que alguien te ayude, puede ser una de ellas: así os cuidáis los dos.",
        )
        helping.forEach { person ->
            val status = buildList {
                if (person.mutual) add("Os ayudáis los dos")
                if (person.pending > 0) add(if (person.pending == 1) "1 solicitud espera tu respuesta en el correo." else "${person.pending} solicitudes esperan tu respuesta en el correo.")
            }.joinToString("\n").ifBlank { null }
            FgGroup {
                FgRow(person.email, subtitle = status)
                if (!person.mutual && partner == PartnerState.None) {
                    FgActionRow("Que también sea mi compañero", { actions.invitePartner(person.email) }, enabled = !busy)
                }
            }
        }
    }
}

/** El correo de un compañero nuevo: el primero, o uno más (hasta [PartnerRepository.MAX_PARTNERS]). */
@Composable
private fun InvitePage(partner: PartnerState, busy: Boolean, actions: AccountActions, snackbar: SnackbarHostState?, onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    FgScreen(
        "Invitar a un compañero", onBack = onBack, snackbar = snackbar,
        bottomBar = {
            BottomAction {
                PrimaryButton("Invitar", { actions.invitePartner(email); email = "" }, Modifier.fillMaxWidth(), enabled = !busy && email.isNotBlank())
            }
        },
    ) {
        item {
            FgGroup(
                footer = if (partner == PartnerState.None) "Le llega un correo con un enlace para aceptar."
                else "Hasta ${PartnerRepository.MAX_PARTNERS}. Cada cambio que pidas les llega a todos y basta con que uno lo apruebe; los avisos le llegan a cada uno según lo que haya elegido.",
            ) {
                FgCell {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(if (partner == PartnerState.None) "Correo de tu compañero" else "Correo de otro compañero") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PartnerCard(
    partner: PartnerState, notices: Boolean, parentalMode: Boolean, verified: Boolean, busy: Boolean, actions: AccountActions,
    partners: List<PartnerInfo>, onInvite: () -> Unit, onOpenPanel: () -> Unit,
) {
    GroupStack {
        when (partner) {
            PartnerState.None -> {
                FgNote(
                    "Una persona de confianza a la que le llega un aviso cuando intentas desactivar la protección, y que aprueba " +
                        "por correo los cambios que la debilitan. No necesita cuenta ni instalar nada.",
                )
                if (!verified) FgNote("Confirma antes tu correo para poder invitarle.")
                else FgGroup { FgRow("Invitar a un compañero", icon = FgIcons.Users, onClick = onInvite) }
            }
            is PartnerState.Invited -> {
                FgGroup(footer = "${partner.email} tiene que abrir el enlace del correo y aceptar (vale 7 días). Hasta entonces no recibe nada.") {
                    FgRow(partner.email, subtitle = "Invitación enviada")
                    FgActionRow("Comprobar de nuevo", actions.refreshPartner, enabled = !busy)
                    if (partners.size <= 1) FgActionRow("Cancelar la invitación", actions.removePartner, danger = true, enabled = !busy)
                }
            }
            is PartnerState.Active -> {
                FgGroup(
                    footer = "Recibe aviso cuando intentas apagar la protección o usas el pánico, y aprueba los cambios que la debilitan " +
                        "(elígelo en Aprobaciones). Cerrar la sesión también necesita su permiso.",
                ) { FgRow(partner.email, subtitle = "Compañero activo") }
                FgGroup {
                    FgSwitchRow(
                        "Avisarle de lo que pasa", notices, actions.setPartnerNotices,
                        subtitle = "Solo se envía qué ocurrió, cuándo y el nombre de la app o la web; nunca lo que ves en pantalla. Apagarlo lo tiene que aprobar él.",
                    )
                    FgSwitchRow(
                        "Modo parental", parentalMode, actions.setParentalMode,
                        subtitle = "Para un teléfono de un hijo o una hija: todo cambio que debilite la protección lo aprueba su padre o su madre, sin retardos ni frases. " +
                            "Quitar este modo también lo tiene que aprobar.",
                    )
                }
                FgGroup { FgRow("Qué se le avisa y qué se protege", onClick = onOpenPanel) }
            }
        }
        // Con compañero se pueden sumar más (hasta tres): una solicitud les llega a todos y basta con que uno la apruebe.
        if (partner != PartnerState.None) {
            if (partners.size > 1) {
                FgGroup(header = "Tus compañeros") {
                    partners.forEach { p ->
                        FgRow(
                            p.email, subtitle = if (p.accepted) "Activo" else "Invitación pendiente",
                            trailing = { TextButton({ actions.removePartnerById(p.id) }, enabled = !busy) { Text(if (p.accepted) "Pedir que me quite" else "Cancelar") } },
                        )
                    }
                }
            }
            if (verified && partners.size < PartnerRepository.MAX_PARTNERS) {
                FgGroup { FgRow("Añadir otro compañero", icon = FgIcons.Add, onClick = onInvite) }
            }
            if (partner is PartnerState.Active && partners.size <= 1) {
                FgGroup(footer = "Quitarle lo tiene que aprobar él por correo.") {
                    FgActionRow("Pedir que me quite", actions.removePartner, danger = true, enabled = !busy)
                }
            }
        }
    }
}
