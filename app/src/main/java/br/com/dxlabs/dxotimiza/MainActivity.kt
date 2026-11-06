package br.com.dxlabs.dxotimiza

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private var sourceUri: Uri? = null
    private var destUri: Uri? = null
    private var listed: List<Pair<DocumentFile, String>> = emptyList()
    private var running = false
    private var analyzing = false
    private var includeSubfolders = true

    private lateinit var txtSource: TextView
    private lateinit var txtDest: TextView
    private lateinit var txtStatus: TextView
    private lateinit var txtCurrent: TextView
    private lateinit var txtSummary: TextView
    private lateinit var txtSavedTotal: TextView
    private lateinit var txtSavedDetail: TextView
    private lateinit var txtPreviewHint: TextView
    private lateinit var txtOptionsSummary: TextView
    private lateinit var txtPercent: TextView
    private lateinit var imgPreview: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var btnOptimize: Button
    private lateinit var btnAnalyze: Button
    private lateinit var btnOptions: Button
    private lateinit var btnDest: Button

    // Inline controls map to the existing optimizer options.
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
            updateOptionsSummary()
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
            updateControls()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindScreen()

        val prefs = getSharedPreferences("dxotimiza", MODE_PRIVATE)
        prefs.getString("source_uri", null)?.let { sourceUri = Uri.parse(it) }
        prefs.getString("dest_uri", null)?.let { destUri = Uri.parse(it) }
        selectedMode = OptimizeEngine.Mode.values().firstOrNull {
            it.name == prefs.getString("mode", null)
        } ?: OptimizeEngine.Mode.EQUILIBRIO
        keepExifState = prefs.getBoolean("keep_exif", true)
        removeGpsState = prefs.getBoolean("remove_gps", false)
        includeSubfolders = prefs.getBoolean("subfolders", true)
        bindInlineOptions()
        updateLabels()
        updateOptionsSummary()
        btnOptimize.isEnabled = false
        if (sourceUri != null) analyze()
    }

    private fun bindScreen() {
        setContentView(R.layout.activity_main)
        val mainScroll = findViewById<android.view.View>(R.id.mainScroll)
        ViewCompat.setOnApplyWindowInsetsListener(mainScroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        txtSource = findViewById(R.id.txtSource)
        txtDest = findViewById(R.id.txtDest)
        txtStatus = findViewById(R.id.txtStatus)
        txtCurrent = findViewById(R.id.txtCurrent)
        txtSummary = findViewById(R.id.txtSummary)
        txtSavedTotal = findViewById(R.id.txtSavedTotal)
        txtSavedDetail = findViewById(R.id.txtSavedDetail)
        txtPreviewHint = findViewById(R.id.txtPreviewHint)
        txtOptionsSummary = findViewById(R.id.txtOptionsSummary)
        txtPercent = findViewById(R.id.txtPercent)
        imgPreview = findViewById(R.id.imgPreview)
        progress = findViewById(R.id.progress)
        btnOptimize = findViewById(R.id.btnOptimize)
        btnAnalyze = findViewById(R.id.btnAnalyze)
        btnOptions = findViewById(R.id.btnOptions)
        btnDest = findViewById(R.id.btnDest)

        findViewById<Button>(R.id.btnSource).setOnClickListener { pickSource.launch(null) }
        findViewById<Button>(R.id.btnDest).setOnClickListener { pickDest.launch(null) }
        btnAnalyze.setOnClickListener { analyze() }
        btnOptimize.setOnClickListener { optimize() }
        btnOptions.setOnClickListener { showOptionsMenu() }

        val modeGroup = findViewById<RadioGroup>(R.id.modeGroup)
        modeGroup?.check(if (overwriteState) R.id.modeOverwrite else R.id.modeOther)
        modeGroup?.setOnCheckedChangeListener { _, checkedId ->
            val newOverwrite = checkedId == R.id.modeOverwrite
            if (newOverwrite != overwriteState) {
                overwriteState = newOverwrite
                updateOptionsSummary()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // The manifest handles resizing in place: reinflate the width-qualified layout
        // while preserving the active batch, its preview and actual displayed metrics.
        val textIds = listOf(
            R.id.txtSavedTotal, R.id.txtSavedDetail, R.id.txtFileCount,
            R.id.txtInputSize, R.id.txtStatus, R.id.txtCurrent, R.id.txtSummary,
            R.id.txtPreviewHint, R.id.txtPercent
        )
        val texts = textIds.mapNotNull { id ->
            findViewById<TextView>(id)?.let { id to it.text }
        }.toMap()
        val preview = imgPreview.drawable
        val percent = progress.progress
        bindScreen()
        bindInlineOptions()
        updateOptionsSummary()
        texts.forEach { (id, text) -> findViewById<TextView>(id)?.text = text }
        imgPreview.setImageDrawable(preview)
        progress.isIndeterminate = analyzing
        progress.progress = percent
    }

    private fun formatPresetText(number: String, label: String): CharSequence {
        val ssb = SpannableStringBuilder()
        val startNum = ssb.length
        ssb.append(number)
        ssb.setSpan(RelativeSizeSpan(1.75f), startNum, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.setSpan(StyleSpan(Typeface.BOLD), startNum, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.append("\n")
        val startLabel = ssb.length
        ssb.append(label)
        ssb.setSpan(RelativeSizeSpan(0.85f), startLabel, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return ssb
    }

    private fun updateOptionsSummary() {
        val exif = if (keepExifState) {
            if (removeGpsState) "EXIF mantido, sem GPS" else "EXIF mantido"
        } else {
            "sem EXIF"
        }
        val sub = if (includeSubfolders) "com subpastas" else "sem subpastas"
        val dest = if (overwriteState) "Substituir originais" else "Outra pasta"
        txtOptionsSummary.text = "${selectedMode.label} • $exif • $sub • $dest"

        val modeGroup = findViewById<RadioGroup>(R.id.modeGroup)
        modeGroup?.check(if (overwriteState) R.id.modeOverwrite else R.id.modeOther)

        val destRow = findViewById<android.view.View>(R.id.layoutDestRow)
        if (overwriteState) {
            destRow?.alpha = 0.35f
            txtDest.text = "Destino: mesma pasta dos originais"
            btnDest.isEnabled = false
            findViewById<TextView>(R.id.txtProtection)?.text =
                "Atenção: os originais serão substituídos após confirmação."
        } else {
            destRow?.alpha = 1.0f
            btnDest.isEnabled = !running && !analyzing
            updateLabels()
            findViewById<TextView>(R.id.txtProtection)?.text =
                "Os originais serão preservados em outra pasta."
        }

        getSharedPreferences("dxotimiza", MODE_PRIVATE).edit()
            .putString("mode", selectedMode.name)
            .putBoolean("keep_exif", keepExifState)
            .putBoolean("remove_gps", removeGpsState)
            .putBoolean("subfolders", includeSubfolders).apply()

        updateControls()
    }

    private fun bindInlineOptions() {
        val presets = findViewById<RadioGroup>(R.id.presetGroup)
        presets.check(when (selectedMode) {
            OptimizeEngine.Mode.QUALIDADE -> R.id.preset92
            OptimizeEngine.Mode.LEVE -> R.id.preset75
            else -> R.id.preset85
        })

        findViewById<RadioButton>(R.id.preset92)?.text = formatPresetText("92", "Qualidade")
        findViewById<RadioButton>(R.id.preset85)?.text = formatPresetText("85", "Equilíbrio")
        findViewById<RadioButton>(R.id.preset75)?.text = formatPresetText("75", "Leve")

        presets.setOnCheckedChangeListener { _, checked ->
            selectedMode = when (checked) {
                R.id.preset92 -> OptimizeEngine.Mode.QUALIDADE
                R.id.preset75 -> OptimizeEngine.Mode.LEVE
                else -> OptimizeEngine.Mode.EQUILIBRIO
            }
            updateOptionsSummary()
        }
    }

    private fun updateControls() {
        val available = !running && !analyzing
        listOf(
            R.id.btnSource, R.id.btnAnalyze, R.id.btnOptions,
            R.id.preset92, R.id.preset85, R.id.preset75,
            R.id.modeOther, R.id.modeOverwrite
        ).forEach { id ->
            findViewById<android.view.View>(id)?.isEnabled = available
        }
        btnDest.isEnabled = available && !overwriteState
        btnOptimize.isEnabled = available && listed.isNotEmpty() &&
            (overwriteState || destUri != null)
    }

    private fun showOptionsMenu() {
        if (running || analyzing) return
        val popup = PopupMenu(this, btnOptions)
        popup.menu.add(0, 1, 0, "Manter EXIF").apply {
            isCheckable = true
            isChecked = keepExifState
        }
        popup.menu.add(0, 2, 1, "Remover GPS").apply {
            isCheckable = true
            isChecked = removeGpsState
            isEnabled = keepExifState
        }
        popup.menu.add(0, 3, 2, "Incluir subpastas").apply {
            isCheckable = true
            isChecked = includeSubfolders
        }
        popup.menu.add(0, 4, 3, "Modo: " + if (overwriteState) "Substituir originais" else "Outra pasta (cópia)")

        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "2.0.0"
        popup.menu.add(0, 5, 4, "Versão $versionName")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    keepExifState = !keepExifState
                    updateOptionsSummary()
                    true
                }
                2 -> {
                    removeGpsState = !removeGpsState
                    updateOptionsSummary()
                    true
                }
                3 -> {
                    includeSubfolders = !includeSubfolders
                    updateOptionsSummary()
                    if (sourceUri != null) analyze()
                    true
                }
                4 -> {
                    overwriteState = !overwriteState
                    updateOptionsSummary()
                    true
                }
                5 -> {
                    Toast.makeText(this, "JPG Diet v$versionName", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
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

    private fun sameDocument(first: Uri, second: Uri): Boolean {
        if (first == second) return true
        if (first.authority != second.authority) return false
        return runCatching {
            android.provider.DocumentsContract.getDocumentId(first) ==
                android.provider.DocumentsContract.getDocumentId(second)
        }.getOrDefault(false)
    }

    private fun selectedMode(): OptimizeEngine.Mode {
        return selectedMode
    }

    private fun analyze() {
        if (running || analyzing) return
        val uri = sourceUri
        if (uri == null) {
            txtStatus.text = "Escolha a pasta de origem primeiro."
            return
        }
        analyzing = true
        listed = emptyList()
        updateControls()
        txtStatus.text = "Analisando pasta…"
        txtPercent.text = "—"
        findViewById<TextView>(R.id.txtFileCount).text = "—"
        findViewById<TextView>(R.id.txtInputSize).text = "—"
        progress.isIndeterminate = true
        val recursive = includeSubfolders
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val root = SafTree.root(this@MainActivity, uri)
                if (root == null) {
                    withContext(Dispatchers.Main) {
                        txtStatus.text = "Não consegui abrir a pasta de origem. Escolha de novo."
                    }
                    return@launch
                }
                val items = OptimizeEngine.listJpegs(root, recursive)
                val total = items.sumOf { it.first.length() }

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
                    txtPercent.text = "0%"
                    listed = items
                    findViewById<TextView>(R.id.txtFileCount).text = items.size.toString()
                    findViewById<TextView>(R.id.txtInputSize).text = SafTree.formatBytes(total)
                    txtSummary.text = ""
                    if (preview != null) {
                        imgPreview.setImageBitmap(preview)
                        txtPreviewHint.text = "Primeira foto: ${items.first().second}"
                    } else {
                        imgPreview.setImageDrawable(null)
                        txtPreviewHint.text = "Nenhuma foto para prévia nesta pasta"
                    }
                    progress.progress = 0
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    listed = emptyList()
                    txtStatus.text = "Falha na análise. Selecione a pasta novamente."
                }
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                    analyzing = false
                    progress.isIndeterminate = false
                    updateControls()
                }
            }
        }
    }

    private fun optimize(confirmedOverwrite: Boolean = false) {
        if (running || analyzing) return
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
                    "arquivos sem ganho ficam intactos. Mantenha uma cópia de segurança."
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
        if (!overwriteState && sameDocument(sourceRoot.uri, destRoot.uri)) {
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
        val overwrite = overwriteState
        val recursive = includeSubfolders

        running = true
        updateControls()
        progress.progress = 0
        txtPercent.text = "0%"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        txtStatus.text = "Otimizando em ${mode.label}… mantenha o app aberto."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val items = OptimizeEngine.listJpegs(sourceRoot, recursive)
                val results = mutableListOf<OptimizeEngine.Item>()
                var done = 0
                for ((file, relative) in items) {
                    ensureActive()
                    // Recria a estrutura relativa dentro do destino.
                    var parent = destRoot
                    val parts = relative.split("/").dropLast(1)
                    if (parts.isNotEmpty()) {
                        parent = SafTree.dirPath(destRoot, parts)
                            ?: throw java.io.IOException("Não foi possível criar a subpasta de destino")
                    }
                    val result = OptimizeEngine.optimizeOne(
                        this@MainActivity, file, parent,
                        mode.quality, keepExif, removeGps, relative, overwrite
                    )
                    results.add(result)
                    done++
                    val current = done
                    val pct = if (items.isNotEmpty()) current * 100 / items.size else 100
                    withContext(Dispatchers.Main) {
                        progress.progress = pct
                        txtPercent.text = "$pct%"
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
                val destLabel = if (overwrite) "substituir originais" else "outra pasta"
                val summaryText = "JPG Diet 2.0.0 — modo ${mode.label} — motor $motorLabel — destino $destLabel\n" +
                    "Arquivos: ${results.size} (otimizados: $optimized, mantidos sem ganho: $kept, falhas: $failed)\n" +
                    "Antes: ${SafTree.formatBytes(originalTotal)}\n" +
                    "Depois: ${SafTree.formatBytes(newTotal)}\n" +
                    "Economia: ${"%.1f".format(saved)}%\n" +
                    (if (overwrite) "Modo de substituição de originais. " else "Originais não alterados. ") +
                    "Detalhe por arquivo em relatorio-otimizacao.csv."
                SafTree.writeText(this@MainActivity, destRoot, "resumo-otimizacao.txt", summaryText)

                withContext(Dispatchers.Main) {
                    progress.progress = 100
                    txtPercent.text = "100%"
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    txtStatus.text = "Lote interrompido. Confira o destino antes de tentar novamente."
                }
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                    running = false
                    updateControls()
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    private fun csvCell(value: String): String {
        return if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
    }
}
