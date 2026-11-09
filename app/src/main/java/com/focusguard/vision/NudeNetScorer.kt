package com.focusguard.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.focusguard.decision.ImageWeights
import com.focusguard.decision.ModelSpec
import com.focusguard.decision.NudeNetOutput
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

/**
 * NudeNet 320n (YOLOv8n, licencia AGPL-3.0) convertido a TensorFlow Lite con pesos de 8 bits (3,2 MB). El archivo no va en git: se pone en
 * `app/modelos/models/` antes de compilar ([available] es falso si no está, y entonces solo puntúa GantMan).
 *
 * Entrada: imagen RGB de 320×320, con la pantalla escalada sin deformarla y rellena de negro por abajo y a la derecha, valores de 0 a 1 y los canales
 * primero (`[1, 3, 320, 320]`). Salida: `[1, 22, 2100]`, o sea 4 de caja y 18 de clase por cada una de las 2100 posiciones; se guarda la mayor puntuación de
 * cada clase en toda la imagen ([NudeNetOutput]).
 */
class NudeNetScorer(private val context: Context) : AutoCloseable {

    /** El mismo modelo da los dos niveles: lo explícito (partes íntimas expuestas) y lo sugerente (partes íntimas cubiertas). */
    val explicitSpec = ModelSpec("$ID.explicit", stage = 1, weight = ImageWeights.NUDENET_EXPLICIT)
    val suggestiveSpec = ModelSpec("$ID.suggestive", stage = 1, weight = ImageWeights.NUDENET_SUGGESTIVE)

    private var interpreter: Interpreter? = null
    private val input: ByteBuffer = ByteBuffer.allocateDirect(3 * SIZE * SIZE * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
    private val output = Array(1) { Array(ROWS) { FloatArray(POSITIONS) } }
    private val pixels = IntArray(SIZE * SIZE)

    fun available(): Boolean = runCatching { context.assets.openFd(ASSET).close() }.isSuccess

    /** La mayor puntuación de cada una de las 18 clases en [letterbox] (320×320), o null si el modelo no se pudo cargar o la salida no tiene la forma esperada. */
    @Synchronized
    fun scores(letterbox: Bitmap): List<Double>? {
        if (letterbox.width != SIZE || letterbox.height != SIZE) return null
        val model = interpreter ?: load() ?: return null
        letterbox.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        input.rewind()
        // Los canales primero: todos los rojos, luego todos los verdes y luego todos los azules.
        for (shift in CHANNEL_SHIFTS) for (p in pixels) input.putFloat(((p shr shift) and 0xFF) / 255f)
        input.rewind()
        return runCatching {
            model.run(input, output)
            List(NudeNetOutput.CLASS_COUNT) { c ->
                var best = 0f
                for (v in output[0][BOX_VALUES + c]) if (v > best) best = v
                best.coerceIn(0f, 1f).toDouble()
            }.takeIf { NudeNetOutput.explicitProbability(it) != null }
        }.onFailure { Log.w(TAG, "no se pudo puntuar: ${it.message}") }.getOrNull()
    }

    private fun load(): Interpreter? = runCatching {
        val buffer = context.assets.openFd(ASSET).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
        Interpreter(buffer, Interpreter.Options().setNumThreads(THREADS)).also { model ->
            // Si el modelo no tiene la forma que se espera, mejor no usarlo que puntuar con una entrada equivocada.
            val shape = model.getInputTensor(0).shape().toList()
            val type = model.getInputTensor(0).dataType()
            val outShape = model.getOutputTensor(0).shape().toList()
            if (shape != listOf(1, 3, SIZE, SIZE) || type != DataType.FLOAT32 || outShape != listOf(1, ROWS, POSITIONS)) {
                Log.w(TAG, "modelo inesperado: entrada $shape $type, salida $outShape")
                model.close()
                return@runCatching null
            }
            interpreter = model
        }
    }.onFailure { Log.w(TAG, "no se pudo cargar el modelo: ${it.message}") }.getOrNull()

    @Synchronized
    override fun close() {
        interpreter?.close()
        interpreter = null
    }

    companion object {
        const val ID = "nudenet"
        const val SIZE = 320
        private const val ASSET = "models/nudenet_320n_w8a32.tflite"
        private const val BOX_VALUES = 4
        private const val ROWS = BOX_VALUES + NudeNetOutput.CLASS_COUNT
        private const val POSITIONS = 2100
        private const val THREADS = 2
        private val CHANNEL_SHIFTS = intArrayOf(16, 8, 0)
        private const val TAG = "FGimg"
    }
}
