package br.com.dxlabs.dxotimiza

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private var sourceUri: Uri? = null
    private var destUri: Uri? = null
    private var listed: List<Pair<DocumentFile, String>> = emptyList()
    private var running = false

    private lateinit var txtSource: TextView
    private lateinit var txtDest: TextView
    private lateinit var txtStatus: TextView
    private lateinit var txtCurrent: TextView
    private lateinit var txtSummary: TextView
    private lateinit var txtSavedTotal: TextView
    private lateinit var txtSavedDetail: TextView
    private lateinit var txtPreviewHint: TextView
    private lateinit var txtOptionsSummary: TextView
    private lateinit var imgPreview: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var btnOptimize: Button
    private lateinit var btnAnalyze: Button
    private lateinit var btnOptions: Button
    private lateinit var btnDest: Button

    // v0.4: opções vivem no menu "Opções", não na tela principal.
    private var selectedMode: OptimizeEngine.Mode = OptimizeEngine.Mode.EQUILIBRIO
    private var keepExifState: Boolean = true
    private var removeGpsState: Boolean = false
    // v0.5: false = outra pasta (padrão, seguro); true = substituir.
    private var overwriteState: Boolean = false

    private val pickSource = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            takePermission(uri)
            sourceUri = uri
            saveUris()
            updateLabels()
            analyze()
        }
    }

    private val pickDest = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            takePermission(uri)
            destUri = uri
            saveUris()
            updateLabels()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtSource = findViewById(R.id.txtSource)
        txtDest = findViewById(R.id.txtDest)
        txtStatus = findViewById(R.id.txtStatus)
        txtCurrent = findViewById(R.id.txtCurrent)
        txtSummary = findViewById(R.id.txtSummary)
        txtSavedTotal = findViewById(R.id.txtSavedTotal)
        txtSavedDetail = findViewById(R.id.txtSavedDetail)
        txtPreviewHint = findViewById(R.id.txtPreviewHint)
        txtOptionsSummary = findViewById(R.id.txtOptionsSummary)
        imgPreview = findViewById(R.id.imgPreview)
        progress = findViewById(R.id.progress)
        btnOptimize = findViewById(R.id.btnOptimize)
        btnAnalyze = findViewById(R.id.btnAnalyze)
        btnOptions = findViewById(R.id.btnOptions)
        btnDest = findViewById(R.id.btnDest)

        val prefs = getSharedPreferences("dxotimiza", MODE_PRIVATE)
        prefs.getString("source_uri", null)?.let { sourceUri = Uri.parse(it) }
        prefs.getString("dest_uri", null)?.let { destUri = Uri.parse(it) }
        updateLabels()
        updateOptionsSummary()
        if (sourceUri != null) analyze()

        findViewById<Button>(R.id.btnSource).setOnClickListener { pickSource.launch(null) }
        findViewById<Button>(R.id.btnDest).setOnClickListener { pickDest.launch(null) }
        btnAnalyze.setOnClickListener { analyze() }
        btnOptimize.setOnClickListener { optimize() }
        btnOptions.setOnClickListener { showOptionsDialog() }
    }

    private fun updateOptionsSummary() {
        val exif = if (keepExifState) {
            if (removeGpsState) "EXIF mantido, sem GPS" else "EXIF mantido"
        } else {
            "sem EXIF"
        }
        val dest = if (overwriteState) "Substituir originais" else "Outra pasta"
        txtOptionsSummary.text = "${selectedMode.label} • $exif • $dest"
        if (overwriteState) {
            txtDest.text = "Destino: mesma pasta — substituir originais"
            btnDest.isEnabled = false
        } else {
            btnDest.isEnabled = true
            updateLabels()
        }
    }

    private fun showOptionsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_options, null)
        val rdoQualidade = view.findViewById<RadioButton>(R.id.rdoQualidadeDialog)
        val rdoEquilibrio = view.findViewById<RadioButton>(R.id.rdoEquilibrioDialog)
        val rdoLeve = view.findViewById<RadioButton>(R.id.rdoLeveDialog)
        val chkExif = view.findViewById<CheckBox>(R.id.chkExifDialog)
        val chkNoGps = view.findViewById<CheckBox>(R.id.chkNoGpsDialog)
        val rdoDestOther = view.findViewById<RadioButton>(R.id.rdoDestOtherDialog)
        val rdoDestOverwrite = view.findViewById<RadioButton>(R.id.rdoDestOverwriteDialog)
        when (selectedMode) {
            OptimizeEngine.Mode.QUALIDADE -> rdoQualidade.isChecked = true
            OptimizeEngine.Mode.LEVE -> rdoLeve.isChecked = true
            else -> rdoEquilibrio.isChecked = true
        }
        chkExif.isChecked = keepExifState
        chkNoGps.isChecked = removeGpsState
        if (overwriteState) rdoDestOverwrite.isChecked = true else rdoDestOther.isChecked = true
        AlertDialog.Builder(this)
            .setTitle("Opções")
            .setView(view)
            .setPositiveButton("Salvar") { _, _ ->
                selectedMode = when {
                    rdoQualidade.isChecked -> OptimizeEngine.Mode.QUALIDADE
                    rdoLeve.isChecked -> OptimizeEngine.Mode.LEVE
                    else -> OptimizeEngine.Mode.EQUILIBRIO
                }
                keepExifState = chkExif.isChecked
                removeGpsState = chkNoGps.isChecked
                overwriteState = rdoDestOverwrite.isChecked
                updateOptionsSummary()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun takePermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (ignored: Exception) {
            // Permissão não persistente: o app continua funcionando nesta sessão.
        }
    }

    private fun saveUris() {
        val prefs = getSharedPreferences("dxotimiza", MODE_PRIVATE)
        prefs.edit()
            .putString("source_uri", sourceUri?.toString())
            .putString("dest_uri", destUri?.toString())
            .apply()
    }

    private fun updateLabels() {
        val sourceRoot = sourceUri?.let { SafTree.root(this, it) }
        val destRoot = destUri?.let { SafTree.root(this, it) }
        txtSource.text = if (sourceRoot != null) {
            "Origem: " + SafTree.displayName(sourceRoot)
        } else {
            "Origem: nenhuma pasta escolhida"
        }
        txtDest.text = if (destRoot != null) {
            "Destino: " + SafTree.displayName(destRoot)
        } else {
            "Destino: nenhuma pasta escolhida"
        }
    }

    private fun selectedMode(): OptimizeEngine.Mode {
        return selectedMode
    }

    private fun analyze() {
        val uri = sourceUri
        if (uri == null) {
            txtStatus.text = "Escolha a pasta de origem primeiro."
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val root = SafTree.root(this@MainActivity, uri)
            if (root == null) {
                withContext(Dispatchers.Main) {
                    txtStatus.text = "Não consegui abrir a pasta de origem. Escolha de novo."
                }
                return@launch
            }
            val items = OptimizeEngine.listJpegs(root)
            val total = items.sumOf { it.first.length() }
            listed = items
            // Prévia do visual v0.3: miniatura da primeira foto, sem
            // carregar o arquivo inteiro (amostra para ~800px de largura).
            val preview = try {
                val first = items.firstOrNull()?.first
                if (first == null) null else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(first.uri)?.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }
                    var sample = 1
                    while (bounds.outWidth / sample > 800 && bounds.outHeight / sample > 800) sample *= 2
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    contentResolver.openInputStream(first.uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                }
            } catch (ignored: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                txtStatus.text = "Análise: ${items.size} JPGs, " +
                    SafTree.formatBytes(total) + " no total."
                txtSummary.text = ""
                txtSavedTotal.text = "0 MB"
                txtSavedDetail.text = "${items.size} JPGs • " +
                    SafTree.formatBytes(total) + " no total"
                if (preview != null) {
                    imgPreview.setImageBitmap(preview)
                    txtPreviewHint.text = "Primeira foto: ${items.first().second}"
                } else {
                    imgPreview.setImageDrawable(null)
                    txtPreviewHint.text = "Nenhuma foto para prévia nesta pasta"
                }
                progress.progress = 0
                btnOptimize.isEnabled = items.isNotEmpty()
            }
        }
    }

    private fun optimize(confirmedOverwrite: Boolean = false) {
        if (running) return
        val sourceRoot = sourceUri?.let { SafTree.root(this, it) }
        if (sourceRoot == null) {
            Toast.makeText(this, "Escolha a pasta de origem primeiro.", Toast.LENGTH_SHORT).show()
            return
        }
        // v0.5: em modo substituir, o destino é a própria origem e
        // o botão de destino fica desabilitado. Confirmação obrigatória,
        // porque substituir é irreversível.
        if (overwriteState && !confirmedOverwrite) {
            AlertDialog.Builder(this)
                .setTitle("Substituir os originais?")
                .setMessage(
                    "Os arquivos otimizados vão substituir os originais " +
                    "na mesma pasta. Isso é irreversível. Só arquivos que " +
                    "ficarem menores serão substituídos; os demais e as " +
                    "falhas ficam intactos."
                )
                .setPositiveButton("Substituir mesmo assim") { _, _ ->
                    optimize(confirmedOverwrite = true)
                }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }
        val destRoot = if (overwriteState) {
            sourceRoot
        } else {
            destUri?.let { SafTree.root(this, it) }
        }
        if (destRoot == null) {
            Toast.makeText(this, "Escolha a origem e o destino primeiro.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!overwriteState && sourceRoot.uri == destRoot.uri) {
            Toast.makeText(
                this,
                "Destino igual à origem: escolha outra pasta, para a original não se misturar com a otimizada.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val mode = selectedMode()
        val keepExif = keepExifState
        val removeGps = removeGpsState

        running = true
        btnOptimize.isEnabled = false
        btnAnalyze.isEnabled = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        txtStatus.text = "Otimizando em ${mode.label}… mantenha o app aberto."

        lifecycleScope.launch(Dispatchers.IO) {
            val items = OptimizeEngine.listJpegs(sourceRoot)
            val results = mutableListOf<OptimizeEngine.Item>()
            var done = 0
            for ((file, relative) in items) {
                // Recria a estrutura relativa dentro do destino.
                var parent = destRoot
                val parts = relative.split("/").dropLast(1)
                if (parts.isNotEmpty()) {
                    parent = SafTree.dirPath(destRoot, parts) ?: destRoot
                }
                val result = OptimizeEngine.optimizeOne(
                    this@MainActivity, file, parent,
                    mode.quality, keepExif, removeGps, relative, overwriteState
                )
                results.add(result)
                done++
                val current = done
                withContext(Dispatchers.Main) {
                    progress.progress = if (items.isNotEmpty()) current * 100 / items.size else 100
                    txtCurrent.text = "$current/${items.size}: ${file.name}"
                    txtStatus.text = "Otimizando… $current de ${items.size}"
                }
            }

            val originalTotal = results.sumOf { it.originalBytes }
            val newTotal = results.sumOf { it.newBytes }
            val optimized = results.count { it.status == OptimizeEngine.Status.OTIMIZADO }
            val kept = results.count { it.status == OptimizeEngine.Status.MANTIDO }
            val failed = results.count { it.status == OptimizeEngine.Status.FALHOU }
            val saved = if (originalTotal > 0) {
                (originalTotal - newTotal).toDouble() / originalTotal * 100.0
            } else 0.0

            // Relatório dentro do destino, para conferir depois.
            val csv = buildString {
                append("arquivo;original_bytes;novo_bytes;economia_pct;status\n")
                for (r in results) {
                    append(csvCell(r.relativePath)).append(";")
                    append(r.originalBytes).append(";")
                    append(r.newBytes).append(";")
                    append("%.1f".format(r.savedPercent)).append(";")
                    append(r.status.name).append("\n")
                }
            }
            SafTree.writeText(this@MainActivity, destRoot, "relatorio-otimizacao.csv", csv)
            val motorLabel = "Android"
            val destLabel = if (overwriteState) "substituir originais" else "outra pasta"
            val summaryText = "JPG Diet 2.0.0 — modo ${mode.label} — motor $motorLabel — destino $destLabel\n" +
                "Arquivos: ${results.size} (otimizados: $optimized, mantidos sem ganho: $kept, falhas: $failed)\n" +
                "Antes: ${SafTree.formatBytes(originalTotal)}\n" +
                "Depois: ${SafTree.formatBytes(newTotal)}\n" +
                "Economia: ${"%.1f".format(saved)}%\n" +
                "Originais não alterados. Detalhe por arquivo em relatorio-otimizacao.csv."
            SafTree.writeText(this@MainActivity, destRoot, "resumo-otimizacao.txt", summaryText)

            withContext(Dispatchers.Main) {
                running = false
                btnOptimize.isEnabled = true
                btnAnalyze.isEnabled = true
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                txtSavedTotal.text = SafTree.formatBytes(
                    (originalTotal - newTotal).coerceAtLeast(0)
                )
                txtSavedDetail.text = "${results.size} arquivos • " +
                    "economia ${"%.1f".format(saved)}%"
                txtStatus.text = "Pronto: ${results.size} arquivos."
                txtCurrent.text = ""
                txtSummary.text = "Motor: $motorLabel. Antes: ${SafTree.formatBytes(originalTotal)} → " +
                    "Depois: ${SafTree.formatBytes(newTotal)} " +
                    "(economia ${"%.1f".format(saved)}%). " +
                    "Otimizados: $optimized, mantidos: $kept, falhas: $failed. " +
                    "Relatório em relatorio-otimizacao.csv, no destino."
                Toast.makeText(this@MainActivity, "Lote concluído.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun csvCell(value: String): String {
        return if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
    }
}
