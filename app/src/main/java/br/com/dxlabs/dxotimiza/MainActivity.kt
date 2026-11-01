package br.com.dxlabs.dxotimiza

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
    private lateinit var txtEngine: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnOptimize: Button
    private lateinit var btnAnalyze: Button
    private lateinit var rdoQualidade: RadioButton
    private lateinit var rdoLeve: RadioButton
    private lateinit var rdoAndroid: RadioButton
    private lateinit var chkExif: CheckBox
    private lateinit var chkNoGps: CheckBox

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
        txtEngine = findViewById(R.id.txtEngine)
        progress = findViewById(R.id.progress)
        btnOptimize = findViewById(R.id.btnOptimize)
        btnAnalyze = findViewById(R.id.btnAnalyze)
        rdoQualidade = findViewById(R.id.rdoQualidade)
        rdoLeve = findViewById(R.id.rdoLeve)
        rdoAndroid = findViewById(R.id.rdoAndroid)
        chkExif = findViewById(R.id.chkExif)
        chkNoGps = findViewById(R.id.chkNoGps)

        val prefs = getSharedPreferences("dxotimiza", MODE_PRIVATE)
        prefs.getString("source_uri", null)?.let { sourceUri = Uri.parse(it) }
        prefs.getString("dest_uri", null)?.let { destUri = Uri.parse(it) }
        updateLabels()
        txtEngine.text = if (MozJpeg.available) {
            "Motor: MozJPEG nativo disponível neste aparelho."
        } else {
            "Motor: MozJPEG indisponível — o lote usará o encoder Android."
        }
        if (sourceUri != null) analyze()

        findViewById<Button>(R.id.btnSource).setOnClickListener { pickSource.launch(null) }
        findViewById<Button>(R.id.btnDest).setOnClickListener { pickDest.launch(null) }
        btnAnalyze.setOnClickListener { analyze() }
        btnOptimize.setOnClickListener { optimize() }
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
        return when {
            rdoQualidade.isChecked -> OptimizeEngine.Mode.QUALIDADE
            rdoLeve.isChecked -> OptimizeEngine.Mode.LEVE
            else -> OptimizeEngine.Mode.EQUILIBRIO
        }
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
            withContext(Dispatchers.Main) {
                txtStatus.text = "Análise: ${items.size} JPGs, " +
                    SafTree.formatBytes(total) + " no total."
                txtSummary.text = ""
                progress.progress = 0
                btnOptimize.isEnabled = items.isNotEmpty()
            }
        }
    }

    private fun optimize() {
        if (running) return
        val sourceRoot = sourceUri?.let { SafTree.root(this, it) }
        val destRoot = destUri?.let { SafTree.root(this, it) }
        if (sourceRoot == null || destRoot == null) {
            Toast.makeText(this, "Escolha a origem e o destino primeiro.", Toast.LENGTH_SHORT).show()
            return
        }
        if (sourceRoot.uri == destRoot.uri) {
            Toast.makeText(
                this,
                "Destino igual à origem: escolha outra pasta, para a original não se misturar com a otimizada.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val mode = selectedMode()
        val keepExif = chkExif.isChecked
        val useMozJpeg = !rdoAndroid.isChecked
        val removeGps = chkNoGps.isChecked

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
                    mode.quality, keepExif, removeGps, relative, useMozJpeg
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
            val motorLabel = if (!useMozJpeg) "Android"
                else if (MozJpeg.available) "MozJPEG"
                else "Android (fallback, MozJPEG indisponível)"
            val summaryText = "JPG Otimiza 0.2.0 — modo ${mode.label} — motor $motorLabel\n" +
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
