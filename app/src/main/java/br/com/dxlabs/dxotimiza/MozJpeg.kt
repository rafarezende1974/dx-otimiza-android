package br.com.dxlabs.dxotimiza

import android.graphics.Bitmap

/**
 * Ponte para o MozJPEG nativo (v0.2).
 *
 * Se a biblioteca não carregar (ABI diferente, build sem NDK, .so
 * ausente), [available] fica false e o OptimizeEngine usa o motor
 * Android da v0.1, sem quebrar o lote. O app nunca depende do
 * nativo para funcionar; o nativo só dá arquivo menor.
 */
object MozJpeg {

    var available: Boolean = false
        private set

    init {
        try {
            System.loadLibrary("jpgotimiza")
            available = true
        } catch (e: UnsatisfiedLinkError) {
            available = false
        } catch (e: Exception) {
            available = false
        }
    }

    private external fun nativeCompressArgb(
        argb: IntArray,
        width: Int,
        height: Int,
        quality: Int,
        trellis: Boolean
    ): ByteArray?

    /** Comprime um Bitmap via MozJPEG. Null = falhou, usar fallback. */
    fun compress(bitmap: Bitmap, quality: Int, trellis: Boolean = true): ByteArray? {
        if (!available) return null
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val argb = IntArray(width * height)
            bitmap.getPixels(argb, 0, width, 0, 0, width, height)
            nativeCompressArgb(argb, width, height, quality, trellis)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}
