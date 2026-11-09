package com.focusguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.BrandHero
import com.focusguard.ui.components.CheckRow
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.RadioGroup
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionTitle

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

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Paso ${step + 1} de $ONBOARDING_STEPS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (step) {
            0 -> {
                item {
                    BrandHero(
                        "Bienvenido a FocusGuard",
                        "Te ayuda a no caer en lo que ya sabes que te quita tiempo. En un par de minutos dejamos listo un primer bloqueo; luego lo puedes cambiar todo.",
                    )
                }
                item {
                    AppCard {
                        SectionTitle("Qué hace y qué no")
                        Hint("Bloquea las apps y las webs que tú elijas, a las horas que elijas, y te frena cuando quieres saltarte tus propias reglas.")
                        Hint("No bloquea las llamadas ni las emergencias. Lo que ves en pantalla y tu uso del teléfono se quedan en el teléfono: no se envían a ningún sitio.")
                    }
                }
            }
            1 -> item {
                AppCard {
                    SectionTitle("¿Qué quieres conseguir?")
                    Hint("Elige lo más parecido; en los pasos siguientes puedes ajustarlo.")
                    RadioGroup(
                        options = OnboardingGoal.entries.map { Triple(it, it.label, it.hint) },
                        selected = goal,
                        onSelect = { picked ->
                            picked?.let {
                                goal = it
                                categories = it.categoryIds
                                whenToBlock = it.whenToBlock
                            }
                        },
                    )
                }
            }
            2 -> item {
                AppCard {
                    SectionTitle("¿Qué quieres cuidar?")
                    Hint("Al terminar se añadirán al perfil las apps que tienes instaladas de cada tipo y sus webs; las verás y podrás quitarlas.")
                    Onboarding.SUGGESTED_CATEGORIES.forEach { category ->
                        CheckRow(
                            title = category.label,
                            hint = null,
                            checked = category.id in categories,
                            onCheckedChange = { categories = if (it) categories + category.id else categories - category.id },
                        )
                    }
                }
            }
            3 -> item {
                AppCard {
                    SectionTitle("¿Cuándo?")
                    RadioGroup(
                        options = OnboardingWhen.entries.map { Triple(it, it.label, it.hint) },
                        selected = whenToBlock,
                        onSelect = { whenToBlock = it },
                    )
                    Hint("Podrás elegir otras horas, y varias franjas, al editar el perfil.")
                }
            }
            4 -> item {
                AppCard {
                    SectionTitle("Contenido adulto")
                    Hint("Ya viene bloqueado. FocusGuard filtra las webs de contenido adulto, también las de manga para adultos, y el filtro DNS lo extiende a todo el teléfono: una app o un navegador que no conoce el bloqueo tampoco las carga.")
                    Hint("Apagarlo después pide aprobación, y se vuelve a encender solo pasado un tiempo. Lo gestionas en Protección.")
                }
            }
            5 -> item {
                AppCard {
                    SectionTitle("Un compañero (opcional)")
                    Hint("Es una persona de confianza que recibe avisos y aprueba tus cambios desde su correo, con un botón: por ejemplo, si quieres apagar un bloqueo.")
                    Hint("Para invitarle necesitas una cuenta. Puedes hacerlo cuando quieras en Ajustes → Cuenta y compañero; no hace falta ahora.")
                }
            }
            6 -> item {
                AppCard {
                    SectionTitle("Modo estricto y emergencia")
                    Hint("El modo estricto impide editar tus perfiles mientras está activo. Para salir hay que cumplir lo que tú elijas: esperar, cargar el teléfono, un PIN o la aprobación de tu compañero.")
                    Hint("Te recomendamos activarlo después de la primera semana, cuando ya sepas qué reglas quieres proteger.")
                    Hint("Si de verdad necesitas algo con un bloqueo activo, el desbloqueo de emergencia da un respiro de unos minutos, con un tope por semana.")
                }
            }
            7 -> {
                item { Hint("Android pide que actives tú estos permisos. Cada botón abre su pantalla de ajustes y, al volver, se comprueba solo.") }
                permissionCards(Permission.entries.filter { it.required }, granted, onGrant)
            }
            else -> item {
                AppCard {
                    SectionTitle("Pruébalo")
                    Hint("Así se verá cuando algo esté bloqueado. Si no te gusta, podrás cambiar el mensaje y el color en Ajustes. Al terminar, abre una de las apps que elegiste: debería aparecer esta pantalla.")
                    SecondaryButton("Ver el bloqueo", onTestLock)
                }
            }
        }
        item {
            val last = step == ONBOARDING_STEPS - 1
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
    }
}
