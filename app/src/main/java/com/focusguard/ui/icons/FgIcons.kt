package com.focusguard.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Iconos propios de FocusGuard: trazo de 1,8 con extremos redondos sobre una cuadrícula de 24, todos con el mismo peso para que
 * convivan. Se tiñen con `Icon(tint = …)`. El escudo con el anillo se repite a mano en `ic_launcher_fg.xml`, `ic_launcher_mono.xml` y
 * `ic_lock_mark.xml` (la pantalla de bloqueo y el lanzador no pueden usar un `ImageVector`): si cambia aquí, cambia allí.
 */
object FgIcons {
    private const val STROKE = 1.8f

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
        arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
        close()
    }

    /** Un icono de líneas; [dots] son puntos rellenos (los centros, los puntos de exclamación). */
    private fun outline(name: String, autoMirror: Boolean = false, dots: (PathBuilder.() -> Unit)? = null, lines: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = autoMirror).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round, pathBuilder = lines,
            )
            dots?.let { path(fill = SolidColor(Color.Black), pathBuilder = it) }
        }.build()

    private fun PathBuilder.shield() {
        moveTo(12f, 3f); lineTo(19f, 6f); verticalLineTo(11f)
        curveTo(19f, 15.6f, 16f, 19.6f, 12f, 21f)
        curveTo(8f, 19.6f, 5f, 15.6f, 5f, 11f)
        verticalLineTo(6f); close()
    }

    val Shield: ImageVector by lazy { outline("Shield") { shield() } }

    /** El escudo con el anillo de enfoque: la marca de la app. */
    val ShieldFocus: ImageVector by lazy {
        outline("ShieldFocus", dots = { circle(12f, 11.5f, 1.4f) }) { shield(); circle(12f, 11.5f, 3.6f) }
    }

    val Home: ImageVector by lazy {
        outline("Home") {
            moveTo(4f, 11f); lineTo(12f, 4f); lineTo(20f, 11f); verticalLineTo(19f)
            arcToRelative(1f, 1f, 0f, false, true, -1f, 1f); horizontalLineTo(5f)
            arcToRelative(1f, 1f, 0f, false, true, -1f, -1f); close()
            moveTo(10f, 20f); verticalLineTo(14f); horizontalLineTo(14f); verticalLineTo(20f)
        }
    }

    val Chart: ImageVector by lazy {
        outline("Chart") {
            moveTo(5f, 20f); verticalLineTo(11f)
            moveTo(12f, 20f); verticalLineTo(4f)
            moveTo(19f, 20f); verticalLineTo(14f)
        }
    }

    val Lock: ImageVector by lazy {
        outline("Lock", dots = { circle(12f, 15.5f, 1.3f) }) {
            moveTo(6f, 11f); horizontalLineTo(18f)
            arcToRelative(1f, 1f, 0f, false, true, 1f, 1f); verticalLineTo(19f)
            arcToRelative(1f, 1f, 0f, false, true, -1f, 1f); horizontalLineTo(6f)
            arcToRelative(1f, 1f, 0f, false, true, -1f, -1f); verticalLineTo(12f)
            arcToRelative(1f, 1f, 0f, false, true, 1f, -1f); close()
            moveTo(8f, 11f); verticalLineTo(8f); arcToRelative(4f, 4f, 0f, false, true, 8f, 0f); verticalLineTo(11f)
        }
    }

    /** Dos reguladores: ajustes. */
    val Sliders: ImageVector by lazy {
        outline("Sliders") {
            moveTo(4f, 7f); horizontalLineTo(10.5f); moveTo(15.5f, 7f); horizontalLineTo(20f)
            circle(13f, 7f, 2.5f)
            moveTo(4f, 17f); horizontalLineTo(8.5f); moveTo(13.5f, 17f); horizontalLineTo(20f)
            circle(11f, 17f, 2.5f)
        }
    }

    val Timer: ImageVector by lazy {
        outline("Timer") {
            circle(12f, 13.5f, 7.5f)
            moveTo(12f, 13.5f); lineTo(14.8f, 10.7f)
            moveTo(9.5f, 3f); horizontalLineTo(14.5f)
        }
    }

    val Flame: ImageVector by lazy {
        outline("Flame") {
            moveTo(12f, 3f)
            curveTo(12.6f, 6.2f, 17.5f, 8.6f, 17.5f, 14f)
            curveTo(17.5f, 17.6f, 15.1f, 20.5f, 12f, 20.5f)
            curveTo(8.9f, 20.5f, 6.5f, 17.8f, 6.5f, 14.6f)
            curveTo(6.5f, 12.8f, 7.3f, 11.4f, 8.5f, 10.4f)
            curveTo(8.6f, 12f, 9.4f, 12.8f, 10.3f, 13f)
            curveTo(10.1f, 9.6f, 10.7f, 5.6f, 12f, 3f)
            close()
        }
    }

    /** Dos soplos: la respiración del modo impulsos. */
    val Wind: ImageVector by lazy {
        outline("Wind") {
            moveTo(3f, 9f); horizontalLineTo(13.5f)
            arcToRelative(2.5f, 2.5f, 0f, true, false, -2.5f, -2.5f)
            moveTo(3f, 15f); horizontalLineTo(17f)
            arcToRelative(2.5f, 2.5f, 0f, true, true, -2.5f, 2.5f)
            moveTo(3f, 12f); horizontalLineTo(8f)
        }
    }

    val Book: ImageVector by lazy {
        outline("Book") {
            moveTo(6f, 4f); horizontalLineTo(18f); verticalLineTo(20f); horizontalLineTo(8f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f); close()
            moveTo(6f, 18f); arcToRelative(2f, 2f, 0f, false, true, 2f, -2f); horizontalLineTo(18f)
            moveTo(10f, 8f); horizontalLineTo(14f)
        }
    }

    val Users: ImageVector by lazy {
        outline("Users") {
            circle(9f, 8.5f, 3f)
            moveTo(3.5f, 19.5f); arcToRelative(5.5f, 5.5f, 0f, false, true, 11f, 0f)
            moveTo(15.5f, 5.7f); arcToRelative(3f, 3f, 0f, false, true, 0f, 5.6f)
            moveTo(16.5f, 14f); arcToRelative(5.5f, 5.5f, 0f, false, true, 4f, 5.5f)
        }
    }

    /** El salvavidas de «Ayúdame». */
    val Lifebuoy: ImageVector by lazy {
        outline("Lifebuoy") {
            circle(12f, 12f, 9f); circle(12f, 12f, 3.8f)
            moveTo(5.6f, 5.6f); lineTo(9.3f, 9.3f)
            moveTo(18.4f, 5.6f); lineTo(14.7f, 9.3f)
            moveTo(5.6f, 18.4f); lineTo(9.3f, 14.7f)
            moveTo(18.4f, 18.4f); lineTo(14.7f, 14.7f)
        }
    }

    val Target: ImageVector by lazy {
        outline("Target", dots = { circle(12f, 12f, 1.4f) }) { circle(12f, 12f, 9f); circle(12f, 12f, 5f) }
    }

    /** Un círculo tachado: bloquear. */
    val Block: ImageVector by lazy {
        outline("Block") { circle(12f, 12f, 9f); moveTo(5.6f, 5.6f); lineTo(18.4f, 18.4f) }
    }

    val Check: ImageVector by lazy { outline("Check") { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) } }

    val CheckCircle: ImageVector by lazy {
        outline("CheckCircle") { circle(12f, 12f, 9f); moveTo(8f, 12.3f); lineTo(11f, 15.3f); lineTo(16.2f, 9.5f) }
    }

    val Info: ImageVector by lazy {
        outline("Info", dots = { circle(12f, 8f, 1.2f) }) { circle(12f, 12f, 9f); moveTo(12f, 11f); verticalLineTo(16.5f) }
    }

    val Warning: ImageVector by lazy {
        outline("Warning", dots = { circle(12f, 16.5f, 1.2f) }) {
            moveTo(12f, 4f); lineTo(21f, 19.5f); horizontalLineTo(3f); close()
            moveTo(12f, 10f); verticalLineTo(14f)
        }
    }

    val Add: ImageVector by lazy { outline("Add") { moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f) } }

    val Back: ImageVector by lazy {
        outline("Back", autoMirror = true) { moveTo(19f, 12f); horizontalLineTo(5f); moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f) }
    }

    val ChevronRight: ImageVector by lazy { outline("ChevronRight", autoMirror = true) { moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f) } }

    val ChevronLeft: ImageVector by lazy { outline("ChevronLeft", autoMirror = true) { moveTo(15f, 6f); lineTo(9f, 12f); lineTo(15f, 18f) } }

    val Delete: ImageVector by lazy {
        outline("Delete") {
            moveTo(4f, 7f); horizontalLineTo(20f)
            moveTo(9f, 7f); verticalLineTo(4.5f); horizontalLineTo(15f); verticalLineTo(7f)
            moveTo(6.5f, 7f); lineTo(7.4f, 19f); arcToRelative(1.5f, 1.5f, 0f, false, false, 1.5f, 1.4f); horizontalLineTo(15.1f)
            arcToRelative(1.5f, 1.5f, 0f, false, false, 1.5f, -1.4f); lineTo(17.5f, 7f)
        }
    }

    val Apps: ImageVector by lazy {
        outline("Apps") {
            moveTo(5f, 5f); horizontalLineTo(10f); verticalLineTo(10f); horizontalLineTo(5f); close()
            moveTo(14f, 5f); horizontalLineTo(19f); verticalLineTo(10f); horizontalLineTo(14f); close()
            moveTo(5f, 14f); horizontalLineTo(10f); verticalLineTo(19f); horizontalLineTo(5f); close()
            moveTo(14f, 14f); horizontalLineTo(19f); verticalLineTo(19f); horizontalLineTo(14f); close()
        }
    }

    val Globe: ImageVector by lazy {
        outline("Globe") {
            circle(12f, 12f, 9f)
            moveTo(3f, 12f); horizontalLineTo(21f)
            moveTo(12f, 3f); curveTo(8.5f, 6.5f, 8.5f, 17.5f, 12f, 21f)
            moveTo(12f, 3f); curveTo(15.5f, 6.5f, 15.5f, 17.5f, 12f, 21f)
        }
    }

    val Bell: ImageVector by lazy {
        outline("Bell") {
            moveTo(6f, 16f); verticalLineTo(11f); arcToRelative(6f, 6f, 0f, false, true, 12f, 0f); verticalLineTo(16f)
            lineTo(19.5f, 18f); horizontalLineTo(4.5f); close()
            moveTo(10f, 21f); horizontalLineTo(14f)
        }
    }

    /** Una hoja: tranquilidad, el ánimo y la recuperación. */
    val Leaf: ImageVector by lazy {
        outline("Leaf") {
            moveTo(5f, 19f)
            curveTo(5f, 11f, 10f, 5f, 19f, 5f)
            curveTo(19f, 14f, 13f, 19f, 5f, 19f)
            close()
            moveTo(5f, 19f); lineTo(13f, 11f)
        }
    }
}
