package com.example.petseg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Executa o modelo de segmentação (.tflite, FP32) e devolve a imagem
 * original com a máscara sobreposta.
 *
 * Entrada do modelo : [1, 128, 128, 3] float32, normalizada para [0, 1]
 * Saída do modelo   : [1, 128, 128, 3] float32 (probabilidades por classe)
 * Classes           : 0 = pet | 1 = fundo | 2 = borda
 */
class Segmenter(context: Context, modelName: String = "model.tflite") {

    private val interpreter: Interpreter

    init {
        val options = Interpreter.Options().apply { setNumThreads(4) }
        interpreter = Interpreter(loadModelFile(context, modelName), options)
    }

    /** Roda a inferência e retorna [original + máscara sobreposta]. */
    fun segment(original: Bitmap): Bitmap {
        // 1) Redimensiona para a entrada da rede
        val resized = Bitmap.createScaledBitmap(original, INPUT_SIZE, INPUT_SIZE, true)

        // 2) Monta o buffer de entrada (float32, normalizado /255)
        val input = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (p in pixels) {
            input.putFloat(Color.red(p) / 255f)
            input.putFloat(Color.green(p) / 255f)
            input.putFloat(Color.blue(p) / 255f)
        }
        input.rewind()

        // 3) Inferência
        val output = Array(1) { Array(INPUT_SIZE) { Array(INPUT_SIZE) { FloatArray(NUM_CLASSES) } } }
        interpreter.run(input, output)

        // 4) Argmax por pixel -> máscara colorida (128x128, com transparência)
        val mask = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val maskPixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val scores = output[0][y][x]
                var best = 0
                for (c in 1 until NUM_CLASSES) if (scores[c] > scores[best]) best = c
                maskPixels[y * INPUT_SIZE + x] = CLASS_COLORS[best]
            }
        }
        mask.setPixels(maskPixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // 5) Sobrepõe a máscara (escalada) sobre a imagem original
        val overlay = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(overlay)
        canvas.drawBitmap(
            mask,
            Rect(0, 0, INPUT_SIZE, INPUT_SIZE),
            Rect(0, 0, overlay.width, overlay.height),
            null
        )
        return overlay
    }

    fun close() = interpreter.close()

    private fun loadModelFile(context: Context, name: String): MappedByteBuffer {
        val afd = context.assets.openFd(name)
        FileInputStream(afd.fileDescriptor).use { fis ->
            return fis.channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )
        }
    }

    companion object {
        const val INPUT_SIZE = 128
        const val NUM_CLASSES = 3

        // ARGB com alpha: pet (vermelho), fundo (transparente), borda (azul)
        private val CLASS_COLORS = intArrayOf(
            Color.argb(130, 255, 0, 0),   // 0 = pet
            Color.argb(0, 0, 0, 0),       // 1 = fundo (sem overlay)
            Color.argb(130, 0, 90, 255)   // 2 = borda
        )
    }
}
