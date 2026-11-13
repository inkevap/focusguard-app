package com.focusguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.decision.Onboarding
import com.focusguard.decision.OnboardingGoal
import com.focusguard.decision.OnboardingWhen
import com.focusguard.permissions.Permission
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgCheckRow
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgRadioGroup
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.icons.FgIcons
import com.focusguard.ui.profiles.BottomAction

internal const val ONBOARDING_STEPS = 9

/**
 * Asistente de la primera vez (O3): qué hace la app y qué no, qué quieres conseguir, qué cuidar, cuándo, contenido adulto, compañero,
 * modo estricto, permisos y una prueba del bloqueo. Cada paso cabe en una pantalla; se puede volver atrás, saltarlo todo y repetirlo
 * desde Ajustes. El primer perfil queda creado y se puede cambiar después. El asistente no apaga ninguna protección: lo único que
 * hace es crear un perfil, así que repetirlo no se salta las aprobaciones.
 */
@Composable
fun OnboardingScreen(
    granted: Map<Permission, Boolean>,
    onGrant: (Permission) -> Unit,
    onTestLock: () -> Unit,
    onFinish: (categories: Set<String>, whenToBlock: OnboardingWhen) -> Unit,
    onSkip: () -> Unit,
    startStep: Int = 0,
) {
    var step by rememberSaveable { mutableIntStateOf(startStep) }
    var goal by rememberSaveable { mutableStateOf<OnboardingGoal?>(null) }
    var categories by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var whenToBlock by rememberSaveable { mutableStateOf(OnboardingWhen.WORK_HOURS) }
    val last = step == ONBOARDING_STEPS - 1

    FgScreen(
        title = stepTitle(step),
        large = true,
        bottomBar = {
            BottomAction {
                PrimaryButton(
                    text = if (last) "Terminar" else if (step == 0) "Empezar" else "Siguiente",
                    onClick = { if (last) onFinish(categories, whenToBlock) else step++ },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (step > 0) TextButton(onClick = { step-- }) { Text("Atrás") } else Spacer(Modifier)
                    TextButton(onClick = onSkip) { Text("Saltar") }
                }
            }
        },
    ) {
        item { FgNote("Paso ${step + 1} de $ONBOARDING_STEPS", Modifier.padding(horizontal = 4.dp)) }
        when (step) {
            0 -> {
                item {
                    Text(
                        "Te ayuda a no caer en lo que ya sabes que te quita tiempo. En un par de minutos dejamos listo un primer bloqueo; luego lo puedes cambiar todo.",
                        style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                item {
                    InfoGroup(
                        "Qué hace y qué no",
                        "Bloquea las apps y las webs que tú elijas, a las horas que elijas, y te frena cuando quieres saltarte tus propias reglas.",
                        "No bloquea las llamadas ni las emergencias. Lo que ves en pantalla y tu uso del teléfono se quedan en el teléfono: no se envían a ningún sitio.",
                    )
                }
            }
            1 -> item {
                FgRadioGroup<OnboardingGoal?>(
                    options = OnboardingGoal.entries.map { Triple<OnboardingGoal?, String, String?>(it, it.label, it.hint) },
                    selected = goal,
                    onSelect = { picked ->
                        picked?.let {
                            goal = it
                            categories = it.categoryIds
                            whenToBlock = it.whenToBlock
                        }
                    },
                    footer = "Elige lo más parecido; en los pasos siguientes puedes ajustarlo.",
                )
            }
            2 -> item {
                FgGroup(footer = "Al terminar se añadirán al perfil las apps que tienes instaladas de cada tipo y sus webs; las verás y podrás quitarlas.") {
                    Onboarding.SUGGESTED_CATEGORIES.forEach { category ->
                        FgCheckRow(
                            title = category.label,
                            checked = category.id in categories,
                            onCheckedChange = { categories = if (it) categories + category.id else categories - category.id },
                        )
                    }
                }
            }
            3 -> item {
                FgRadioGroup(
                    options = OnboardingWhen.entries.map { Triple<OnboardingWhen, String, String?>(it, it.label, it.hint) },
                    selected = whenToBlock,
                    onSelect = { whenToBlock = it },
                    footer = "Podrás elegir otras horas, y varias franjas, al editar el perfil.",
                )
            }
            4 -> item {
                InfoGroup(
                    null,
                    "Ya viene bloqueado. FocusGuard filtra las webs de contenido adulto, también las de manga para adultos, y el filtro DNS lo extiende a todo el teléfono: una app o un navegador que no conoce el bloqueo tampoco las carga.",
                    "Apagarlo después pide aprobación, y se vuelve a encender solo pasado un tiempo. Lo gestionas en Protección.",
                )
            }
            5 -> item {
                InfoGroup(
                    null,
                    "Es una persona de confianza que recibe avisos y aprueba tus cambios desde su correo, con un botón: por ejemplo, si quieres apagar un bloqueo.",
                    "Para invitarle necesitas una cuenta. Puedes hacerlo cuando quieras en Ajustes → Cuenta; no hace falta ahora.",
                )
            }
            6 -> item {
                InfoGroup(
                    null,
                    "El modo estricto impide editar tus perfiles mientras está activo. Para salir hay que cumplir lo que tú elijas: esperar, cargar el teléfono, un PIN o la aprobación de tu compañero.",
                    "Te recomendamos activarlo después de la primera semana, cuando ya sepas qué reglas quieres proteger.",
                    "Si de verdad necesitas algo con un bloqueo activo, el desbloqueo de emergencia da un respiro de unos minutos, con un tope por semana.",
                )
            }
            7 -> item { PermissionRows(Permission.entries.filter { it.required }, granted, onGrant) }
            else -> {
                item {
                    InfoGroup(
                        null,
                        "Así se verá cuando algo esté bloqueado. Si no te gusta, podrás cambiar el mensaje y el color en Ajustes. Al terminar, abre una de las apps que elegiste: debería aparecer esta pantalla.",
                    )
                }
                item { FgGroup { FgActionRow("Ver el bloqueo", onTestLock) } }
            }
        }
    }
}

private fun stepTitle(step: Int) = when (step) {
    0 -> "Bienvenido a FocusGuard"
    1 -> "¿Qué quieres conseguir?"
    2 -> "¿Qué quieres cuidar?"
    3 -> "¿Cuándo?"
    4 -> "Contenido adulto"
    5 -> "Un compañero (opcional)"
    6 -> "Modo estricto y emergencia"
    7 -> "Permisos"
    else -> "Pruébalo"
}

/** Un grupo de párrafos cortos. */
@Composable
private fun InfoGroup(header: String?, vararg paragraphs: String) {
    FgGroup(header = header) {
        paragraphs.forEach { FgCell { Text(it, style = MaterialTheme.typography.bodyMedium) } }
    }
}

/** Android pide que actives tú estos permisos: cada fila abre su pantalla de ajustes y, al volver, se comprueba sola. */
@Composable
private fun PermissionRows(permissions: List<Permission>, granted: Map<Permission, Boolean>, onGrant: (Permission) -> Unit) {
    FgGroup(footer = "Android pide que actives tú estos permisos. Cada botón abre su pantalla de ajustes y, al volver, se comprueba solo.") {
        permissions.forEach { permission ->
            val info = permission.info()
            val ok = granted[permission] == true
            FgRow(
                info.title, subtitle = info.reason,
                icon = if (ok) FgIcons.CheckCircle else FgIcons.Warning,
                iconColor = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                value = if (ok) "Listo" else null,
                trailing = if (ok) null else {
                    { TextButton(onClick = { onGrant(permission) }) { Text(info.action) } }
                },
            )
        }
    }
}
