package com.focusguard.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.decision.BrowserPrep
import com.focusguard.decision.BrowserPrep.Choice
import com.focusguard.decision.BrowserPrep.Result
import com.focusguard.decision.BrowserPrep.UiText

/**
 * El asistente «Preparar navegadores»: abre cada navegador instalado en `chrome://flags/#incognito-screenshot`, pone la opción en Enabled y toca
 * «Reiniciar». Lo maneja el servicio de accesibilidad con pasos cortos en [handler]; qué tocar lo decide [BrowserPrep]. Mientras trabaja en un
 * navegador, [currentPackage] dice cuál es, para que el bloqueo de páginas flags lo deje pasar.
 */
class BrowserPrepRunner(
    private val service: AccessibilityService,
    private val handler: Handler,
    private val addressBar: (String) -> AccessibilityNodeInfo?,
    private val onProgress: (Map<String, Result>) -> Unit,
    private val onFinish: (Map<String, Result>) -> Unit,
) {

    private enum class Step { LAUNCH, OPEN, FIND, PICK, CONFIRM, RELAUNCH }

    private var queue: List<String> = emptyList()
    private val results = linkedMapOf<String, Result>()
    private var step = Step.LAUNCH
    private var stepStartMs = 0L
    private var attempts = 0

    /** El navegador que se está preparando, o null si el asistente está parado. */
    var currentPackage: String? = null
        private set

    val running get() = currentPackage != null

    private val tick = Runnable { advance() }

    fun start(installed: List<String>) {
        if (running) return
        queue = installed
        results.clear()
        next()
    }

    fun cancel() {
        handler.removeCallbacks(tick)
        currentPackage = null
    }

    private fun next() {
        val pkg = queue.firstOrNull { it !in results }
        if (pkg == null) {
            currentPackage = null
            onFinish(results.toMap())
            return
        }
        currentPackage = pkg
        go(Step.LAUNCH)
        val intent = service.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) return finish(Result.FAILED)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { service.startActivity(intent) }.onFailure { return finish(Result.FAILED) }
        schedule(LAUNCH_WAIT_MS)
    }

    private fun go(next: Step) {
        step = next
        stepStartMs = System.currentTimeMillis()
        attempts = 0
    }

    private fun schedule(delayMs: Long = POLL_MS) {
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, delayMs)
    }

    private fun finish(result: Result) {
        val pkg = currentPackage ?: return
        Log.i(TAG, "$pkg: $result (paso $step)")
        results[pkg] = result
        onProgress(results.toMap())
        handler.removeCallbacks(tick)
        // Tras reiniciar, el navegador tarda en volver: se espera antes de abrir el siguiente.
        handler.postDelayed({ next() }, if (result == Result.DONE) AFTER_RELAUNCH_MS else 300L)
    }

    private fun advance() {
        val pkg = currentPackage ?: return
        if (System.currentTimeMillis() - stepStartMs > STEP_TIMEOUT_MS) {
            logScreen(pkg, "tiempo agotado en $step")
            return finish(Result.FAILED)
        }
        attempts++
        when (step) {
            Step.LAUNCH -> if (inFront(pkg)) go(Step.OPEN)
            Step.OPEN -> if (openFlags(pkg)) go(Step.FIND)
            Step.FIND -> {
                val nodes = screen(pkg)
                val select = BrowserPrep.findSelect(nodes.map { it.first })
                if (select == null) {
                    if (attempts % 6 == 0) logScreen(pkg, "sin la opción")
                } else when (BrowserPrep.choiceOf(nodes[select].first.text)) {
                    Choice.ENABLED -> return finish(Result.ALREADY)
                    // Chrome no abre la lista con la acción de tocar (solo la enfoca): hace falta un toque de verdad en su sitio.
                    else -> if (tap(nodes[select].second)) go(Step.PICK)
                }
            }
            Step.PICK -> {
                // La lista es otra ventana del navegador: se busca solo en las que no tienen la página de la opción, para no tocar
                // el «Default» de otra opción de la página.
                val lists = windowTexts(pkg).filter { win -> win.none { it.first.text.contains("#${BrowserPrep.FLAG}", ignoreCase = true) } }
                val nodes = lists.flatten()
                val option = BrowserPrep.findOption(nodes.map { it.first }, Choice.ENABLED)
                if (option != null && click(nodes[option].second)) go(Step.CONFIRM)
                else if (attempts % 4 == 0) Log.i(TAG, "$pkg sin lista: ${windowTexts(pkg).map { w -> w.take(8).joinToString(",") { it.first.text.take(15) } }}")
            }
            Step.CONFIRM -> {
                val nodes = screen(pkg).map { it.first }
                val select = BrowserPrep.findSelect(nodes)
                if (select != null && BrowserPrep.choiceOf(nodes[select].text) == Choice.ENABLED) go(Step.RELAUNCH)
            }
            Step.RELAUNCH -> {
                val nodes = screen(pkg)
                val button = BrowserPrep.findRelaunch(nodes.map { it.first })
                if (button != null && click(nodes[button].second)) return finish(Result.DONE)
            }
        }
        schedule()
    }

    /** El navegador está delante: su ventana es la activa (la raíz activa a veces tarda en cambiar tras abrirlo). */
    private fun inFront(pkg: String): Boolean =
        service.rootInActiveWindow?.packageName?.toString() == pkg ||
            service.windows.any { it.isActive && it.root?.packageName?.toString() == pkg }


    /** Escribe la dirección de la opción en la barra y la envía. Si la barra aún no está (página de inicio), toca la caja de búsqueda. */
    private fun openFlags(pkg: String): Boolean {
        val bar = addressBar(pkg)
        if (bar == null || !bar.isEditable) {
            val root = service.rootInActiveWindow ?: return false
            SEARCH_BOXES.firstNotNullOfOrNull { root.findAccessibilityNodeInfosByViewId("$pkg:id/$it")?.firstOrNull() }?.let(::click)
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        bar.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val text = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, BrowserPrep.FLAGS_URL) }
        if (!bar.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text)) return false
        return bar.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    /** Todos los textos de las ventanas del navegador (la página y, si está abierta, la lista desplegable) en orden de lectura. */
    private fun screen(pkg: String): List<Pair<UiText, AccessibilityNodeInfo>> = windowTexts(pkg).flatten()

    /** Lo mismo, ventana por ventana. */
    private fun windowTexts(pkg: String): List<List<Pair<UiText, AccessibilityNodeInfo>>> {
        val roots = service.windows.mapNotNull { it.root }.filter { it.packageName?.toString() == pkg }
            .ifEmpty { listOfNotNull(service.rootInActiveWindow) }
        return roots.map { root -> mutableListOf<Pair<UiText, AccessibilityNodeInfo>>().also { collect(root, false, it, 0) } }
    }

    private fun collect(node: AccessibilityNodeInfo, clickableAbove: Boolean, out: MutableList<Pair<UiText, AccessibilityNodeInfo>>, depth: Int) {
        if (depth > MAX_DEPTH) return
        val clickable = clickableAbove || node.isClickable
        val text = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) out += UiText(text, clickable) to node
        for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, clickable, out, depth + 1) }
    }

    /** Un toque en el centro de la vista, como el de un dedo. */
    private fun tap(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect().also(node::getBoundsInScreen)
        if (bounds.isEmpty) return false
        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, TAP_MS)).build()
        return service.dispatchGesture(gesture, null, null)
    }

    /** Toca la vista o, si ella no se puede tocar, el antecesor más cercano que sí. */
    private fun click(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops < 6) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            current = current.parent
            hops++
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** Para afinar en el teléfono: solo textos de la página de experimentos del navegador, nunca de otras apps. */
    private fun logScreen(pkg: String, why: String) {
        val roots = service.windows.mapNotNull { it.root }.filter { it.packageName?.toString() == pkg }
        val nodes = roots.flatMap { root -> mutableListOf<Pair<UiText, AccessibilityNodeInfo>>().also { collect(root, false, it, 0) } }.map { it.first }
        Log.i(TAG, "$pkg $why: " + nodes.take(60).joinToString(" | ") { (if (it.clickable) "*" else "") + it.text.take(40) })
    }

    companion object {
        /** La acción que manda la pantalla de ajustes para empezar; la recibe el servicio, solo dentro de la app. */
        const val ACTION_START = "com.focusguard.action.PREPARE_BROWSERS"
        private const val TAG = "FGprep"
        private const val POLL_MS = 500L
        private const val LAUNCH_WAIT_MS = 1_200L
        private const val STEP_TIMEOUT_MS = 12_000L
        private const val AFTER_RELAUNCH_MS = 4_000L
        private const val MAX_DEPTH = 40
        private const val TAP_MS = 60L
        private val SEARCH_BOXES = listOf("search_box_text", "search_box", "url_bar")
    }
}
