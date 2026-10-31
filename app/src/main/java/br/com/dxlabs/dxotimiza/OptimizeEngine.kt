package br.com.dxlabs.dxotimiza

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * Motor da v0.1.
 *
 * Regras que não mudam:
 * - A origem nunca é alterada: o app só lê dela. O resultado vai para
 *   a pasta de destino, com o mesmo nome de arquivo e, se houver
 *   subpastas na origem, a mesma estrutura relativa.
 * - A resolução não muda: só a compressão JPEG muda.
 * - Se a versão recomprimida ficar maior ou igual à original, a
 *   original é copiada sem alteração para o destino e marcada como
 *   "mantida (sem ganho)". O app nunca entrega um arquivo maior.
 * - Um arquivo por vez, em sequência, para não estourar a memória
 *   com fotos grandes da câmera.
 *
 * Limite honesto da v0.1: a qualidade é fixa por modo (92 / 85 / 75).
 * Não há análise perceptual por foto como no JPEGmini, nem MozJPEG
 * via NDK — isso fica para a v0.2. O ganho vem de fotos que saíram
 * da câmera ou do editor em qualidade muito alta (95-100), onde
 * 85 costuma cortar bastante sem diferença visível.
 */
object OptimizeEngine {

    enum class Mode(val quality: Int, val label: String) {
        QUALIDADE(92, "Qualidade (92)"),
        EQUILIBRIO(85, "Equilíbrio (85)"),
        LEVE(75, "Leve (75)")
    }

    enum class Status { OTIMIZADO, MANTIDO, FALHOU }

    data class Item(
        val name: String,
        val relativePath: String,
        val originalBytes: Long,
        val newBytes: Long,
        val status: Status
    ) {
        val savedPercent: Double
            get() = if (originalBytes > 0) {
                (originalBytes - newBytes).toDouble() / originalBytes * 100.0
            } else 0.0
    }

    /** Lista os JPEGs da origem, com o caminho relativo a ela. */
    fun listJpegs(root: DocumentFile): List<Pair<DocumentFile, String>> {
        val result = mutableListOf<Pair<DocumentFile, String>>()
        collectJpegs(root, "", result)
        return result.sortedBy { it.second.lowercase() }
    }

    /**
     * Varredura recursiva que já carrega o caminho relativo junto.
     * DocumentFile não expõe caminho absoluto, então o relativo é
     * montado durante a descida, não reconstruído depois pela Uri.
     * Ignora pastas ocultas e pastas *_otimizado (ver SafTree).
     */
    private fun collectJpegs(
        dir: DocumentFile,
        prefix: String,
        out: MutableList<Pair<DocumentFile, String>>
    ) {
        for (child in SafTree.children(dir)) {
            val name = child.name ?: continue
            if (name.startsWith(".")) continue
            if (child.isDirectory) {
                if (name.endsWith("_otimizado", ignoreCase = true)) continue
                collectJpegs(child, prefix + name + "/", out)
            } else if (child.isFile && SafTree.isJpeg(name)) {
                out.add(child to (prefix + name))
            }
        }
    }

    /**
     * Otimiza um arquivo. [destParent] já é a pasta de destino (ou a
     * subpasta correspondente à estrutura relativa). Retorna o Item.
     */
    fun optimizeOne(
        context: Context,
        source: DocumentFile,
        destParent: DocumentFile,
        quality: Int,
        keepExif: Boolean,
        removeGps: Boolean,
        relativePath: String = ""
    ): Item {
        val name = source.name ?: "foto.jpg"
        val originalBytes = source.length()
        try {
            // 1) Decodifica. Se não for um JPEG legível, falha sem tocar em nada.
            val bitmap = context.contentResolver.openInputStream(source.uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return Item(name, relativePath, originalBytes, originalBytes, Status.FALHOU)

            // 2) Lê a orientação EXIF antes de qualquer coisa e já gira
            // os pixels, para a saída sair com orientação normal (1) e
            // nenhum visualizador girar a foto duas vezes.
            var orientation = ExifInterface.ORIENTATION_NORMAL
            var originalExif: ExifInterface? = null
            if (keepExif) {
                try {
                    context.contentResolver.openInputStream(source.uri)?.use { input ->
                        originalExif = ExifInterface(input)
                        orientation = originalExif?.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        ) ?: ExifInterface.ORIENTATION_NORMAL
                    }
                } catch (ignored: Exception) {
                    originalExif = null
                }
            }
            val rotated = rotateIfNeeded(bitmap, orientation)
            if (rotated !== bitmap) bitmap.recycle()

            // 3) Recomprime para um arquivo temporário no cache do app
            // (nunca direto no destino, para não deixar arquivo parcial).
            val temp = File(context.cacheDir, "dxotimiza_tmp.jpg")
            try {
                FileOutputStream(temp).use { out ->
                    rotated.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    out.flush()
                }
            } finally {
                rotated.recycle()
            }
            if (!temp.exists() || temp.length() == 0L) {
                temp.delete()
                return Item(name, relativePath, originalBytes, originalBytes, Status.FALHOU)
            }

            // 4) Se não ganhou nada, copia a original sem alteração.
            if (temp.length() >= originalBytes) {
                temp.delete()
                val dest = createDestFile(context, destParent, name) ?: return Item(
                    name, "", originalBytes, originalBytes, Status.FALHOU
                )
                val copied = SafTree.copyStream(context, source, dest)
                if (copied < 0) return Item(name, relativePath, originalBytes, originalBytes, Status.FALHOU)
                return Item(name, relativePath, originalBytes, copied, Status.MANTIDO)
            }

            // 5) Copia o EXIF da original para a recomprimida (sem GPS,
            // se pedido, e com orientação normal, pois já giramos).
            if (keepExif && originalExif != null) {
                try {
                    val outExif = ExifInterface(temp.absolutePath)
                    copyExif(originalExif!!, outExif, removeGps)
                    outExif.setAttribute(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL.toString()
                    )
                    outExif.saveAttributes()
                } catch (ignored: Exception) {
                    // Sem EXIF, a foto otimizada continua válida.
                }
            }

            // 6) Publica o temporário no destino.
            val dest = createDestFile(context, destParent, name) ?: run {
                temp.delete()
                return Item(name, relativePath, originalBytes, originalBytes, Status.FALHOU)
            }
            val published = context.contentResolver.openOutputStream(dest.uri)?.use { out ->
                temp.inputStream().use { input -> input.copyTo(out) }
                temp.length()
            } ?: -1L
            temp.delete()
            if (published < 0) return Item(name, relativePath, originalBytes, originalBytes, Status.FALHOU)
            return Item(name, relativePath, originalBytes, published, Status.OTIMIZADO)
        } catch (e: Exception) {
            return Item(name, relativePath, originalBytes, originalBytes, Status.FALHOU)
        }
    }

    private fun createDestFile(
        context: Context,
        parent: DocumentFile,
        name: String
    ): DocumentFile? {
        return try {
            val existing = parent.findFile(name)
            if (existing != null) existing.delete()
            parent.createFile("image/jpeg", name)
        } catch (e: Exception) {
            null
        }
    }

    private fun rotateIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }

    /** Tags copiadas da original. GPS só vai se [removeGps] for falso. */
    private fun copyExif(from: ExifInterface, to: ExifInterface, removeGps: Boolean) {
        val tags = mutableListOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_ISO_SPEED_RATINGS,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_IMAGE_WIDTH,
            ExifInterface.TAG_IMAGE_LENGTH,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_USER_COMMENT
        )
        if (!removeGps) {
            tags.add(ExifInterface.TAG_GPS_LATITUDE)
            tags.add(ExifInterface.TAG_GPS_LATITUDE_REF)
            tags.add(ExifInterface.TAG_GPS_LONGITUDE)
            tags.add(ExifInterface.TAG_GPS_LONGITUDE_REF)
            tags.add(ExifInterface.TAG_GPS_ALTITUDE)
            tags.add(ExifInterface.TAG_GPS_ALTITUDE_REF)
        }
        for (tag in tags) {
            val value = from.getAttribute(tag) ?: continue
            to.setAttribute(tag, value)
        }
    }
}
