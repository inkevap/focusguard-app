package com.focusguard.lock

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.focusguard.MainActivity
import com.focusguard.R
import com.focusguard.model.BlockReason
import com.focusguard.ui.Routes

/**
 * Contenido de la pantalla de bloqueo. Es una vista clásica (no Compose) porque la usan
 * tanto la superposición del servicio de accesibilidad como [LockActivity] y la vista previa de Ajustes.
 */
class LockScreenView(
    context: Context,
    content: LockScreenContent,
    /** Ofrece el desbloqueo de emergencia; quien llama ya comprobó que se puede. */
    showEmergency: Boolean = false,
    /** Sin ella la pantalla no tiene botón de volver (el bloqueo de arranque se quita solo). */
    onDismiss: (() -> Unit)? = null,
    /** Ofrece reportar un bloqueo erróneo (B10); quien llama ya comprobó que se puede reportar. */
    onReport: (() -> Unit)? = null,
) : LinearLayout(context) {

    private val palette = content.palette

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(palette.background)
        val pad = (24 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)

        if (content.emoji.isNotBlank()) addView(text(content.emoji, 48f)) else addView(mark())
        addView(text(content.title ?: context.getString(R.string.lock_title), 30f, Typeface.BOLD).also { ViewCompat.setAccessibilityHeading(it, true) })
        addView(text(content.body, 18f))
        content.unlockLabel?.let { addView(text(it, 18f, Typeface.BOLD)) }
        content.motivation?.let { addView(text(it, 16f, Typeface.ITALIC)) }
        if (onDismiss != null) addView(button(R.string.lock_back, filled = true) { onDismiss() }, buttonParams(2 * pad))
        if (showEmergency) {
            addView(
                button(R.string.lock_emergency, filled = false) {
                    onDismiss?.invoke()
                    context.startActivity(MainActivity.intent(context, Routes.EMERGENCY))
                },
                buttonParams(pad / 2),
            )
        }
        if (onReport != null) {
            addView(
                button(R.string.lock_report, filled = false) { onReport() },
                buttonParams(pad / 2),
            )
        }
        // Absorbe los toques para que no lleguen a la app bloqueada.
        isClickable = true
        isFocusable = true
    }

    /** El escudo de la marca con el color del texto de la paleta; lo decorativo no se anuncia. */
    private fun mark() = ImageView(context).apply {
        setImageResource(R.drawable.ic_lock_mark)
        setColorFilter(palette.text)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun buttonParams(topMargin: Int) =
        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { this.topMargin = topMargin }

    /** Botón de 56 dp: relleno con el color del texto la acción principal, solo contorno la secundaria. */
    private fun button(label: Int, filled: Boolean, onClick: () -> Unit) = Button(context).apply {
        val density = resources.displayMetrics.density
        text = context.getString(label)
        textSize = 16f
        isAllCaps = false
        setTextColor(if (filled) palette.background else palette.text)
        background = GradientDrawable().apply {
            if (filled) setColor(palette.text) else { setColor(Color.TRANSPARENT); setStroke((2 * density).toInt(), palette.text) }
            cornerRadius = 28 * density
        }
        minimumHeight = (56 * density).toInt()
        minimumWidth = (200 * density).toInt()
        setOnClickListener { onClick() }
    }

    private fun text(value: String, sizeSp: Float, style: Int = Typeface.NORMAL) = TextView(context).apply {
        text = value
        textSize = sizeSp
        setTextColor(palette.text)
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.15f)
        setTypeface(typeface, style)
        val gap = (12 * resources.displayMetrics.density).toInt()
        setPadding(0, gap, 0, gap)
    }

    companion object {
        /** El mismo motivo con la lista de perfiles que bloquean; solo los motivos de un perfil lo tienen. */
        fun byRes(reason: BlockReason): Int? = when (reason) {
            BlockReason.PROFILE_APP -> R.string.lock_reason_app_by
            BlockReason.PROFILE_WEB -> R.string.lock_reason_web_by
            BlockReason.NOT_ALLOWED -> R.string.lock_reason_not_allowed_by
            BlockReason.LIMIT_REACHED -> R.string.lock_reason_limit_by
            else -> null
        }

        fun reasonRes(reason: BlockReason): Int = when (reason) {
            BlockReason.PROFILE_APP -> R.string.lock_reason_app
            BlockReason.PROFILE_WEB -> R.string.lock_reason_web
            BlockReason.NOT_ALLOWED -> R.string.lock_reason_not_allowed
            BlockReason.LIMIT_REACHED -> R.string.lock_reason_limit
            BlockReason.STRICT_MODE -> R.string.lock_reason_strict
            BlockReason.CLOCK_PROTECTED -> R.string.lock_reason_clock
            BlockReason.SUB_APP -> R.string.lock_reason_subapp
            BlockReason.NEW_APP -> R.string.lock_reason_new_app
            BlockReason.EMBEDDED_BROWSER -> R.string.lock_reason_embedded_browser
            BlockReason.ADULT_CONTENT -> R.string.lock_reason_adult
            BlockReason.IMAGE_CONTENT -> R.string.lock_reason_image
            BlockReason.ADMIN_PROTECTED -> R.string.lock_reason_admin
        }
    }
}
