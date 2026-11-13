package com.focusguard.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.ui.icons.FgIcons

/*
 * Lista agrupada: la base de todas las pantallas. Una pantalla es un fondo liso con grupos (tarjetas sin borde) de filas cortas;
 * lo que no cabe en una fila se abre en una subpantalla. Una decisión por pantalla.
 */

/** El color de las tarjetas sobre el fondo de la pantalla: en claro el más claro, en oscuro un escalón por encima del fondo. */
val ColorScheme.card: Color get() = if (background.luminance() > 0.5f) surface else surfaceContainerHigh

/** El relleno gris de chips, botones secundarios e interruptores apagados. */
val ColorScheme.fill: Color get() = surfaceContainerHighest

val GroupShape = RoundedCornerShape(14.dp)
private val RowPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)

/**
 * El armazón de una pantalla o subpantalla: barra de arriba (o título grande si [large]) y una lista de grupos.
 * Con [onBack] la barra lleva la flecha de volver.
 */
@Composable
fun FgScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    large: Boolean = false,
    snackbar: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { if (!large) FgTopBar(title, onBack = onBack, actions = actions) },
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (large) item(key = "fg-large-title") { LargeTitle(title, actions = actions) }
            content()
        }
    }
}

/** El título grande de las cinco pantallas principales, con sus acciones a la derecha. */
@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().statusBarsPadding().padding(top = 20.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}

/** Una acción de texto en la barra de arriba («Guardar», «Nuevo»). */
@Composable
fun FgTextAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

/**
 * Un grupo de filas dentro de una tarjeta, con rótulo arriba y nota debajo opcionales. Las filas ([FgRow] y las demás de este archivo)
 * se separan solas con una línea fina; la última no la enseña.
 */
@Composable
fun FgGroup(modifier: Modifier = Modifier, header: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        header?.let { GroupHeader(it) }
        Column(
            Modifier.fillMaxWidth().clip(GroupShape).background(MaterialTheme.colorScheme.card)
                // Cada fila dibuja su línea abajo; recortar un píxel esconde la de la última.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, (placeable.height - 1.dp.roundToPx()).coerceAtLeast(0)) { placeable.place(0, 0) }
                },
            content = content,
        )
        footer?.let { FgNote(it, Modifier.padding(horizontal = 16.dp)) }
    }
}

@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.6.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp).semantics { heading() },
    )
}

/** Texto de apoyo, en gris, bajo un grupo o suelto en la pantalla. */
@Composable
fun FgNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** La línea fina de abajo de una fila, alineada con su texto. */
@Composable
private fun Modifier.rowDivider(inset: Boolean): Modifier {
    val color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    return drawBehind {
        val y = size.height - 0.5.dp.toPx()
        drawLine(color, Offset((if (inset) 58.dp else 16.dp).toPx(), y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
}

/**
 * La fila de una lista agrupada: [icon] opcional, [title], [subtitle] debajo, [value] a la derecha y, si lleva a otra pantalla
 * ([onClick]), la flecha. [trailing] sustituye a la flecha por otro control.
 */
@Composable
fun FgRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = onClick != null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .rowDivider(inset = icon != null)
            .padding(RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon?.let { IconBadge(it, size = 30.dp, container = iconColor, content = scheme.surface) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) titleColor else scheme.onSurfaceVariant)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
        }
        value?.let {
            Text(
                it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.End,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp),
            )
        }
        trailing?.invoke(this)
        if (chevron && trailing == null) Icon(FgIcons.ChevronRight, contentDescription = null, tint = scheme.outline, modifier = Modifier.size(18.dp))
    }
}

/** Una fila que hace algo en vez de llevar a otra pantalla: el texto va en el color de la marca, o en rojo si [danger]. */
@Composable
fun FgActionRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, danger: Boolean = false, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    FgRow(title, modifier, subtitle = subtitle, titleColor = if (danger) scheme.error else scheme.primary, enabled = enabled, onClick = onClick, chevron = false)
}

/** Fila con interruptor: toda la fila es el área de toque. */
@Composable
fun FgSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    FgRow(
        title,
        modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        subtitle = subtitle, icon = icon, enabled = enabled,
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/** Fila de una lista en la que se eligen varias cosas: la marca va a la derecha. */
@Composable
fun FgCheckRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, value: String? = null, enabled: Boolean = true) {
    FgRow(
        title,
        modifier.toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        subtitle = subtitle, value = value, enabled = enabled,
        trailing = { CheckMark(checked) },
    )
}

/** Fila de una lista en la que se elige una sola cosa. */
@Composable
fun FgRadioRow(title: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    FgRow(
        title,
        modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        subtitle = subtitle,
        trailing = { CheckMark(selected) },
    )
}

/** Un grupo para elegir una opción entre varias (valor, título y explicación opcional). */
@Composable
fun <T> FgRadioGroup(options: List<Triple<T, String, String?>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, header: String? = null, footer: String? = null) {
    FgGroup(modifier, header = header, footer = footer) {
        options.forEach { (value, title, hint) -> FgRadioRow(title, selected == value, { onSelect(value) }, subtitle = hint) }
    }
}

/** La marca de elegido; ocupa su sitio aunque no se vea, para que las filas no bailen. */
@Composable
fun CheckMark(visible: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(22.dp), contentAlignment = Alignment.Center) {
        if (visible) Icon(FgIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    }
}

/** Contenido libre dentro de un grupo (un campo de texto, un selector, unos chips) con el mismo margen que las filas. */
@Composable
fun FgCell(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().rowDivider(inset = false).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/**
 * La tarjeta de cabecera de una pantalla: un dato grande ([number]) o un estado ([title]) con su explicación y una etiqueta opcional.
 * Con [accent] va sobre el color de la marca.
 */
@Composable
fun FgHero(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    number: String? = null,
    pill: String? = null,
    accent: Boolean = false,
    container: Color? = null,
    content: Color? = null,
    onClick: (() -> Unit)? = null,
    extra: @Composable (ColumnScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val background = container ?: if (accent) scheme.primary else scheme.card
    val foreground = content ?: if (accent) scheme.onPrimary else scheme.onSurface
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(background)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        number?.let { Text(it, style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp), color = foreground) }
        Text(title, style = MaterialTheme.typography.titleLarge, color = foreground)
        body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = foreground.copy(alpha = 0.85f)) }
        pill?.let {
            Text(
                it, style = MaterialTheme.typography.labelMedium, color = foreground,
                modifier = Modifier.padding(top = 6.dp).clip(CircleShape).background(foreground.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        extra?.invoke(this)
    }
}

/** Una acción principal como baldosa: icono arriba, título y una línea de apoyo. */
@Composable
fun FgTile(title: String, hint: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, iconColor: Color = MaterialTheme.colorScheme.primary) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(scheme.card).clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconBadge(icon, size = 32.dp, container = iconColor, content = scheme.surface)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 2.dp))
        Text(hint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

/**
 * Subpantallas dentro de una misma pantalla (comparten su ViewModel y su borrador): [page] nulo es la raíz. Volver atrás desde una
 * subpantalla regresa a la raíz en vez de salir de la pantalla.
 */
@Composable
fun <T : Any> SubPageHost(page: T?, onBack: () -> Unit, root: @Composable () -> Unit, sub: @Composable (T) -> Unit) {
    BackHandler(enabled = page != null, onBack = onBack)
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState != null
            (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
        },
        label = "subpantalla",
    ) { current -> if (current == null) root() else sub(current) }
}
