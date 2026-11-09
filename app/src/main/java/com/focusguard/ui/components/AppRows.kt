package com.focusguard.ui.components

import com.focusguard.ui.icons.FgIcons
import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.focusguard.appLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Iconos de apps ya cargados: una lista de cientos de apps no los vuelve a pedir al sistema al hacer scroll. */
private object AppIcons {
    private val cache = LruCache<String, ImageBitmap>(200)

    fun cached(packageName: String): ImageBitmap? = cache.get(packageName)

    /** El icono de la app, o `null` si no es un paquete instalado (un dominio, una app ya desinstalada). */
    suspend fun load(context: Context, packageName: String, sizePx: Int): ImageBitmap? = withContext(Dispatchers.Default) {
        cached(packageName) ?: runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).asImageBitmap()
        }.getOrNull()?.also { cache.put(packageName, it) }
    }
}

/** El icono de una app. Mientras carga, o si no hay (no es una app instalada), queda un cuadrado neutro del mismo tamaño para que las filas no salten. */
@Composable
fun AppIcon(packageName: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val sizePx = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val icon by produceState(initialValue = packageName?.let { AppIcons.cached(it) }, packageName) {
        if (value == null && packageName != null) value = AppIcons.load(context, packageName, sizePx)
    }
    val shown = icon
    if (shown != null) {
        // Decorativo: el nombre de la app va al lado y es lo que lee el lector de pantalla.
        Image(bitmap = shown, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)))
    }
}

/** Nombre y icono de una app, con un hueco a la derecha para lo que haga falta (un botón de quitar, un contador). */
@Composable
fun AppRow(packageName: String?, label: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(packageName)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        trailing()
    }
}

/** Una app de una lista de elegir: toda la fila es el área de toque (mínimo 56 dp). */
@Composable
fun AppCheckRow(packageName: String, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        AppIcon(packageName)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Una app de una lista con un botón de quitar de 48 dp. */
@Composable
fun RemovableAppRow(packageName: String?, label: String, removeDescription: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    AppRow(packageName, label, modifier) {
        IconButton(onClick = onRemove) { Icon(FgIcons.Delete, contentDescription = removeDescription) }
    }
}

/** Los nombres de unas apps. Cuesta dos llamadas al sistema por app, así que solo se rehace si cambia la lista. */
@Composable
fun rememberAppLabels(packages: Collection<String>): Map<String, String> {
    val context = LocalContext.current
    return remember(packages) { packages.associateWith { context.appLabel(it) } }
}

/** Una fila con la etiqueta a la izquierda y el valor a la derecha, con el icono de la app si [packageName] es una. */
@Composable
fun ValueRow(label: String, value: String, packageName: String? = null) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (packageName != null) AppIcon(packageName, size = 32.dp)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
