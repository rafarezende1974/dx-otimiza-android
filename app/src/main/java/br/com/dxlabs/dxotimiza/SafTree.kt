package br.com.dxlabs.dxotimiza

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.io.OutputStream

/**
 * Helpers para pastas escolhidas pelo Storage Access Framework (SAF).
 *
 * No Android não dá para escrever direto em um caminho absoluto do SD
 * ou do pendrive: o usuário escolhe a pasta uma vez pelo seletor do
 * sistema, o app guarda a permissão persistente, e todo acesso passa
 * por DocumentFile. É por isso que não existe "caminho" aqui, só Uri.
 * Mesmo padrão do Editor Assistente Android.
 */
object SafTree {

    fun root(context: Context, uri: Uri): DocumentFile? {
        return DocumentFile.fromTreeUri(context, uri)
    }

    fun displayName(file: DocumentFile): String {
        return file.name ?: file.uri.toString()
    }

    /** Encontra ou cria uma subpasta. Retorna null se não conseguir. */
    fun dir(parent: DocumentFile, name: String): DocumentFile? {
        val existing = parent.findFile(name)
        if (existing != null && existing.isDirectory) return existing
        if (existing != null) return null
        return parent.createDirectory(name)
    }

    /** Caminha por uma lista de subpastas, criando as que faltam. */
    fun dirPath(parent: DocumentFile, parts: List<String>): DocumentFile? {
        var current = parent
        for (part in parts) {
            current = dir(current, part) ?: return null
        }
        return current
    }

    fun children(file: DocumentFile): List<DocumentFile> {
        return try {
            file.listFiles().toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Varredura recursiva. Ignora pastas ocultas (começam com ponto) e
     * pastas de saída do próprio app (terminam em _otimizado), para o
     * caso de o destino ficar dentro da origem: sem isso, uma segunda
     * rodada reprocessaria os arquivos já otimizados.
     */
    fun walkFiles(root: DocumentFile, out: MutableList<DocumentFile>) {
        for (child in children(root)) {
            val name = child.name ?: continue
            if (name.startsWith(".")) continue
            if (child.isDirectory) {
                if (name.endsWith("_otimizado", ignoreCase = true)) continue
                walkFiles(child, out)
            } else if (child.isFile) {
                out.add(child)
            }
        }
    }

    fun isJpeg(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
    }

    fun mimeFor(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".txt") -> "text/plain"
            lower.endsWith(".csv") -> "text/csv"
            else -> "application/octet-stream"
        }
    }

    /** Copia um arquivo via streams. Retorna os bytes copiados, ou -1. */
    fun copyStream(context: Context, source: DocumentFile, dest: DocumentFile): Long {
        var input: InputStream? = null
        var output: OutputStream? = null
        var total = 0L
        try {
            input = context.contentResolver.openInputStream(source.uri) ?: return -1
            output = context.contentResolver.openOutputStream(dest.uri) ?: return -1
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                total += read
            }
            output.flush()
        } finally {
            try { output?.close() } catch (ignored: Exception) { }
            try { input?.close() } catch (ignored: Exception) { }
        }
        return total
    }

    /** Cria (ou recria) um arquivo dentro de [parent] e escreve bytes. */
    fun writeBytes(context: Context, parent: DocumentFile, name: String, bytes: ByteArray): DocumentFile? {
        return try {
            val existing = parent.findFile(name)
            if (existing != null) existing.delete()
            val created = parent.createFile(mimeFor(name), name) ?: return null
            context.contentResolver.openOutputStream(created.uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: return null
            created
        } catch (e: Exception) {
            null
        }
    }

    /** Reescreve um arquivo de texto inteiro (relatório é pequeno). */
    fun writeText(context: Context, parent: DocumentFile, name: String, text: String): Boolean {
        return writeBytes(context, parent, name, text.toByteArray(Charsets.UTF_8)) != null
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
