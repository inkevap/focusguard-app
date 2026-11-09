package com.focusguard.ui.components

import com.focusguard.ui.icons.FgIcons
import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.focusguard.ui.theme.LocalBrandColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.focusguard.decision.Approvals

private const val MAX_PIN_LENGTH = 8

/** Si [onClick] no es nulo, toda la tarjeta es pulsable. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val inner: @Composable ColumnScope.() -> Unit = { Column(Modifier.padding(contentPadding), content = content) }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, border = border, content = inner)
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, border = border, content = inner)
    }
}

/** Con [icon], el título lleva delante su insignia (un icono de la marca sobre un fondo suave). */
@Composable
fun SectionTitle(text: String, icon: ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        icon?.let { IconBadge(it, size = 32.dp) }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Un icono dentro de un recuadro redondeado del color de la marca; [container] y [content] cambian de papel (brasa, peligro). */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier.size(size).background(container, RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.58f))
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) =
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 4.dp, bottom = 8.dp),
    )

/** Interruptor grande: más fácil de acertar y de ver que el estándar. */
@Composable
fun LargeSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier.padding(horizontal = 6.dp).scale(1.25f),
    )
}

/** Toda la fila es el área de toque y el lector de pantalla la lee como un interruptor. */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            SectionTitle(title)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LargeSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun SwitchCard(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    AppCard(modifier) { SwitchRow(title, subtitle, checked, onCheckedChange, enabled = enabled) }
}

/** La barra de arriba de las pantallas interiores: con [onBack] lleva la flecha de volver; sin ella no hay forma de salir desde aquí. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FgTopBar(title: String, onBack: (() -> Unit)? = null, backDescription: String = "Volver", actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(FgIcons.Back, contentDescription = backDescription) } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** El «?» de la barra de arriba: abre la ficha de Ayuda de la pantalla. */
@Composable
fun HelpAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(FgIcons.Info, contentDescription = "Ayuda de esta pantalla") }
}

/** El encabezado de las pantallas principales; con [icon] lleva la insignia de la sección delante. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.statusBarsPadding().padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon?.let { IconBadge(it, size = 48.dp) }
        Column {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Barra de progreso redondeada, sin el punto que Material pone al final de la pista. */
@Composable
fun FgProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    LinearProgressIndicator(progress = { progress().takeUnless { it.isNaN() }?.coerceIn(0f, 1f) ?: 0f }, modifier = modifier.height(8.dp), color = color, trackColor = trackColor, strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {})
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

enum class StatusKind { Active, Neutral, Warning }

@Composable
fun StatusCard(
    title: String,
    body: String,
    icon: ImageVector,
    kind: StatusKind,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val brand = LocalBrandColors.current
    val (content, background) = when (kind) {
        StatusKind.Active -> brand.onBrand to brand.gradient
        StatusKind.Neutral -> scheme.onSecondaryContainer to SolidColor(scheme.secondaryContainer)
        StatusKind.Warning -> scheme.onTertiaryContainer to SolidColor(scheme.tertiaryContainer)
    }
    val shape = MaterialTheme.shapes.large
    Box(modifier.fillMaxWidth().clip(shape).background(background)) {
        // La marca de agua del escudo solo adorna el estado activo y no cuenta para el tamaño de la tarjeta.
        if (kind == StatusKind.Active) {
            Box(Modifier.matchParentSize()) {
                Icon(
                    FgIcons.Shield,
                    contentDescription = null,
                    tint = content.copy(alpha = 0.10f),
                    modifier = Modifier.align(Alignment.CenterEnd).offset(x = 40.dp).requiredSize(140.dp),
                )
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BadgeRow(icon, title, body, content, titleStyle = MaterialTheme.typography.titleLarge, badgeSize = 48.dp, badgeContainer = content.copy(alpha = 0.16f), badgeContent = content)
            if (actionLabel != null && onAction != null) PrimaryButton(actionLabel, onAction)
        }
    }
}

/** Insignia, título y explicación en una fila: lo comparten las tarjetas de estado, la racha, «Ayúdame» y las herramientas de Inicio. */
@Composable
fun BadgeRow(
    icon: ImageVector,
    title: String,
    body: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    bodyColor: Color = textColor,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    badgeSize: Dp = 40.dp,
    badgeContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    badgeContent: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconBadge(icon, size = badgeSize, container = badgeContainer, content = badgeContent)
        Column(Modifier.weight(1f)) {
            Text(title, style = titleStyle, color = textColor)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = bodyColor)
        }
        trailing?.invoke()
    }
}

@Composable
fun MissingPermissionsCard(count: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    StatusCard(
        title = if (count == 1) "Falta 1 permiso" else "Faltan $count permisos",
        body = "Sin ellos FocusGuard no puede bloquear nada.",
        icon = FgIcons.Warning,
        kind = StatusKind.Warning,
        modifier = modifier,
        actionLabel = "Dar permisos",
        onAction = onOpen,
    )
}

@Composable
fun EmptyState(title: String, body: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector = FgIcons.ShieldFocus) {
    AppCard(modifier) {
        SectionTitle(title, icon)
        Hint(body)
        PrimaryButton(actionLabel, onAction)
    }
}

/** La cabecera de bienvenida: el escudo de la marca sobre el degradado, con el nombre y una frase. */
@Composable
fun BrandHero(title: String, body: String, modifier: Modifier = Modifier, icon: ImageVector = FgIcons.ShieldFocus) {
    val brand = LocalBrandColors.current
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(brand.gradient).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = brand.onBrand, modifier = Modifier.size(64.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = brand.onBrand)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = brand.onBrand)
    }
}

/** Una herramienta de Inicio como fila: insignia, título, explicación y flecha. */
@Composable
fun ActionRow(title: String, hint: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BadgeRow(
        icon, title, hint, MaterialTheme.colorScheme.onSurface,
        bodyColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        trailing = { Icon(FgIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline) },
    )
}

/** Una acción principal de Inicio como baldosa: la insignia arriba y el texto debajo. */
@Composable
fun ActionTile(title: String, hint: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    AppCard(modifier, onClick = onClick, containerColor = scheme.primaryContainer) {
        IconBadge(icon, container = scheme.primary, content = scheme.onPrimary)
        Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onPrimaryContainer, modifier = Modifier.padding(top = 10.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) { Text(text) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) { Text(text) }
}

/** Toda la fila es el área de toque (mínimo 56 dp). */
@Composable
fun CheckRow(title: String, hint: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Toda la fila es el área de toque (mínimo 56 dp). */
@Composable
fun RadioRow(title: String, hint: String, selected: Boolean, modifier: Modifier = Modifier, onSelect: () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun <T> RadioGroup(options: List<Triple<T, String, String>>, selected: T, onSelect: (T) -> Unit) {
    options.forEach { (value, title, hint) -> RadioRow(title, hint, selected == value) { onSelect(value) } }
}

/** Opciones cortas en fila que se parten en varias líneas si no caben. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipChoices(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

/** Con [minimum] igual a cero se admite el cero. */
@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, minimum: Int = 1) {
    val invalid = value.toIntOrNull()?.let { it >= minimum } != true
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        singleLine = true,
        isError = invalid,
        supportingText = { if (invalid) Text(if (minimum >= 1) "Escribe un número mayor que cero" else "Escribe un número") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun PinField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, error: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(MAX_PIN_LENGTH)) },
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Muestra [message] como aviso pasajero y avisa con [onShown] para que el ViewModel lo olvide. */
@Composable
fun rememberMessageSnackbar(message: String?, onShown: () -> Unit): SnackbarHostState {
    val state = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            state.showSnackbar(it)
            onShown()
        }
    }
    return state
}

/** Sin capturas ni grabación de pantalla mientras esta pantalla está abierta (frases a mano, desbloqueo de emergencia). */
@Composable
fun SecureWindowEffect() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/** Campo para escribir a mano una frase: ignora los cambios que añaden mucho texto de golpe (pegar). */
@Composable
fun HandTypedField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (Approvals.isTypedChange(value, it)) onChange(it) },
        label = { Text("Texto") },
        keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Text),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Fila de texto con un botón de borrar de 48 dp. */
@Composable
fun RemovableRow(text: String, removeDescription: String, onRemove: () -> Unit) {
    Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = onRemove) { Icon(FgIcons.Delete, contentDescription = removeDescription) }
    }
}

/** Campo de una contraseña o frase: oculta lo escrito y no autocorrige. */
@Composable
fun PasswordField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Barras sencillas (una por valor, de la altura de su valor respecto al mayor) con etiquetas debajo de algunas. Es un dibujo: el lector
 * de pantalla lee solo [description] con el total, no cada barra.
 */
@Composable
fun MiniBars(values: List<Int>, labels: Map<Int, String>, description: String, modifier: Modifier = Modifier, barHeight: Dp = 56.dp) {
    val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$description: ${values.sum()} en total" }) {
        Row(Modifier.fillMaxWidth().height(barHeight), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            values.forEach { value ->
                Box(
                    Modifier.weight(1f).fillMaxHeight(if (value == 0) 0.04f else 0.04f + 0.96f * value / max)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(if (value == 0) track else color),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            values.indices.forEach { index ->
                Text(labels[index].orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
            }
        }
    }
}
