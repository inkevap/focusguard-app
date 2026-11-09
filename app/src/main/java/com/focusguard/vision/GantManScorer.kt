package com.focusguard.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.focusguard.decision.GantManOutput
import com.focusguard.decision.ImageWeights
import com.focusguard.decision.ModelSpec
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

/**
 * El modelo de GantMan (MobileNetV2 de 224 px, MIT) en TensorFlow Lite. El archivo no va en git: se pone en `app/modelos/models/` antes de compilar
 * ([available] es falso si no está). Entrada: imagen RGB de 224×224 con los valores entre 0 y 1. Salida: cinco probabilidades ([GantManOutput]).
 */
class GantManScorer(private val context: Context) : AutoCloseable {

    /** El mismo modelo da los dos niveles: lo explícito (porno y hentai) y lo sugerente (la clase «sexy»), con una sola pasada. */
    val explicitSpec = ModelSpec("$ID.explicit", stage = 1, weight = ImageWeights.GANTMAN_EXPLICIT)
    val suggestiveSpec = ModelSpec("$ID.suggestive", stage = 1, weight = ImageWeights.GANTMAN_SUGGESTIVE)

    private var interpreter: Interpreter? = null
    private val input: ByteBuffer = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
    private val output = Array(1) { FloatArray(GantManOutput.CLASS_COUNT) }
    private val pixels = IntArray(SIZE * SIZE)

    /** ¿Está el modelo entre los archivos de la app? */
    fun available(): Boolean = runCatching { context.assets.openFd(ASSET).close() }.isSuccess

    /** Las cinco probabilidades de [GantManOutput] para [bitmap], o null si el modelo no se pudo cargar o su salida no tiene la forma esperada. */
    @Synchronized
    fun scores(bitmap: Bitmap): List<Double>? {
        val model = interpreter ?: load() ?: return null
        val scaled = if (bitmap.width == SIZE && bitmap.height == SIZE) bitmap else Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true)
        try {
            scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
        input.rewind()
        for (p in pixels) {
            input.putFloat(((p shr 16) and 0xFF) / 255f)
            input.putFloat(((p shr 8) and 0xFF) / 255f)
            input.putFloat((p and 0xFF) / 255f)
        }
        input.rewind()
        return runCatching {
            model.run(input, output)
            output[0].map { it.toDouble() }.takeIf { GantManOutput.explicitProbability(it) != null }
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
            if (shape != listOf(1, SIZE, SIZE, 3) || type != DataType.FLOAT32 || outShape != listOf(1, GantManOutput.CLASS_COUNT)) {
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
        const val ID = "gantman"
        private const val ASSET = "models/gantman_mobilenetv2_224.tflite"
        private const val SIZE = 224
        private const val THREADS = 2
        private const val TAG = "FGimg"
    }
}
