package br.com.dxlabs.dxotimiza

/**
 * Stub da v0.4: o MozJPEG foi removido porque deixava o lote muito
 * lento na Tab (trellis). Este arquivo fica no repo só porque o push
 * via API não apaga arquivos; nada mais o chama, ele nunca carrega
 * biblioteca nativa e [compress] sempre retorna null. Os arquivos
 * em src/main/cpp também ficam órfãos, sem compilar, porque o
 * externalNativeBuild foi removido do build.gradle.kts.
 */
object MozJpeg {

    val available: Boolean = false

    /** Sempre null: sem motor nativo na v0.4. */
    fun compress(bitmap: android.graphics.Bitmap, quality: Int, trellis: Boolean = true): ByteArray? {
        return null
    }
}
