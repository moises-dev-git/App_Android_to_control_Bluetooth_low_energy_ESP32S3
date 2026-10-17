package com.example.esp32app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.esp32app.ui.theme.*
import com.example.esp32app.vision.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tela de visão: mostra a imagem da captura HDMI, testa OCR e busca de imagens,
 * e permite recortar modelos de imagem para usar em macros (WAIT_IMAGE, CLICK_IMAGE...).
 */
class VisionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.init(this)
        setContent {
            ESP32AppTheme {
                VisionScreenContent(onBack = { finish() })
            }
        }
    }
}

/** Retângulo destacado sobre a imagem, em coordenadas normalizadas. */
private data class Highlight(val match: ScreenMatch, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionScreenContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { TemplateStore(context) }
    val matcher = remember { ImageMatcher(store) }

    val status = HdmiCapture.statusState.value
    val message = HdmiCapture.messageState.value

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var frozenFrame by remember { mutableStateOf<Frame?>(null) }
    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selection by remember { mutableStateOf<ScreenMatch?>(null) }
    var templateName by remember { mutableStateOf("") }

    var highlights by remember { mutableStateOf<List<Highlight>>(emptyList()) }
    var queryText by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var templates by remember { mutableStateOf(store.list()) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) HdmiCapture.start(context)
        else Toast.makeText(context, "A captura HDMI precisa da permissão de câmera", Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(Unit) {
        if (HdmiCapture.hasCameraPermission(context)) HdmiCapture.start(context)
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Preview ao vivo em baixa taxa (só para visualização; a análise usa o frame bruto)
    LaunchedEffect(frozenFrame) {
        if (frozenFrame != null) return@LaunchedEffect
        var lastFrame: Frame? = null
        while (true) {
            val frame = HdmiCapture.latestFrame
            if (frame != null && frame !== lastFrame) {
                lastFrame = frame
                previewBitmap = withContext(Dispatchers.Default) { frame.toBitmap(70) }
            }
            delay(250)
        }
    }

    fun runAnalysis(block: suspend (Frame) -> Unit) {
        val frame = frozenFrame ?: HdmiCapture.latestFrame
        if (frame == null) {
            Toast.makeText(context, "Sem imagem da captura HDMI", Toast.LENGTH_SHORT).show()
            return
        }
        busy = true
        scope.launch {
            try {
                block(frame)
            } catch (e: Exception) {
                resultText = "Erro: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Visão da Tela (HDMI)", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = message,
                color = if (status == HdmiCapture.Status.STREAMING) ConnectedGreen else TextSecondary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            if (status == HdmiCapture.Status.ERROR || status == HdmiCapture.Status.STOPPED) {
                OutlinedButton(onClick = {
                    HdmiCapture.stop()
                    if (HdmiCapture.hasCameraPermission(context)) HdmiCapture.start(context)
                    else permissionLauncher.launch(Manifest.permission.CAMERA)
                }) { Text("Tentar novamente") }
            }

            // --- IMAGEM (ao vivo ou congelada para recorte) ---
            val shown = frozenBitmap ?: previewBitmap
            if (shown != null) {
                FrameView(
                    bitmap = shown,
                    highlights = highlights,
                    selection = if (frozenFrame != null) selection else null,
                    onSelectionChange = if (frozenFrame != null) { sel -> selection = sel } else null
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(DarkSurface, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Sem imagem", color = TextSecondary)
                }
            }

            // --- TESTE DE OCR ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Testar reconhecimento de texto", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Texto a procurar (ex: Salvar)", color = TextSecondary) }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !busy && queryText.isNotBlank(),
                            onClick = {
                                runAnalysis { frame ->
                                    val result = OcrEngine.recognize(frame)
                                    val match = TextMatcher.find(result, queryText)
                                    highlights = match?.let { listOf(Highlight(it, ConnectedGreen)) } ?: emptyList()
                                    resultText = if (match != null) {
                                        val (x, y) = match.toHidAbsolute(ScreenMappingStore.load(context))
                                        "Encontrado. Clique iria para ABS:$x,$y"
                                    } else {
                                        "\"$queryText\" não encontrado na tela"
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = CyanOnPrimary)
                        ) { Text("Procurar") }
                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                runAnalysis { frame ->
                                    val result = OcrEngine.recognize(frame)
                                    highlights = result.lines.map { line ->
                                        Highlight(
                                            ScreenMatch(
                                                line.box.left.toFloat() / result.frameWidth,
                                                line.box.top.toFloat() / result.frameHeight,
                                                line.box.right.toFloat() / result.frameWidth,
                                                line.box.bottom.toFloat() / result.frameHeight
                                            ),
                                            CyanPrimary
                                        )
                                    }
                                    resultText = if (result.lines.isEmpty()) "Nenhum texto reconhecido"
                                    else result.lines.joinToString("\n") { it.text }
                                }
                            }
                        ) { Text("Ler tudo") }
                    }
                    if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CyanPrimary)
                    if (resultText.isNotEmpty()) {
                        Text(resultText, color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }

            // --- NOVO MODELO DE IMAGEM ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Novo modelo de imagem", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    if (frozenFrame == null) {
                        Text(
                            "Congele a imagem e arraste o dedo sobre ela para marcar o ícone/botão.",
                            color = TextSecondary, fontSize = 12.sp
                        )
                        Button(
                            enabled = previewBitmap != null,
                            onClick = {
                                val frame = HdmiCapture.latestFrame ?: return@Button
                                scope.launch {
                                    frozenBitmap = withContext(Dispatchers.Default) { frame.toBitmap(95) }
                                    frozenFrame = frame
                                    selection = null
                                    highlights = emptyList()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = CyanOnPrimary)
                        ) {
                            Icon(Icons.Default.PauseCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Congelar imagem")
                        }
                    } else {
                        OutlinedTextField(
                            value = templateName,
                            onValueChange = { templateName = it.filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' }.take(40) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Nome (ex: botao_ok)", color = TextSecondary) }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = selection != null && TemplateStore.isValidName(templateName),
                                onClick = {
                                    val frame = frozenFrame
                                    val bitmap = frozenBitmap
                                    val sel = selection
                                    if (frame != null && bitmap != null && sel != null) {
                                        val crop = cropNormalized(bitmap, sel)
                                        if (crop == null) {
                                            Toast.makeText(context, "Seleção muito pequena", Toast.LENGTH_SHORT).show()
                                        } else {
                                            store.save(templateName, crop, frame.width, frame.height)
                                            templates = store.list()
                                            Toast.makeText(context, "Modelo \"$templateName\" salvo", Toast.LENGTH_SHORT).show()
                                            templateName = ""
                                            frozenFrame = null
                                            frozenBitmap = null
                                            selection = null
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = CyanOnPrimary)
                            ) { Text("Salvar modelo") }
                            OutlinedButton(onClick = {
                                frozenFrame = null
                                frozenBitmap = null
                                selection = null
                            }) { Text("Voltar ao vivo") }
                        }
                    }
                }
            }

            // --- MODELOS SALVOS ---
            Text("Modelos de imagem salvos", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
            if (templates.isEmpty()) {
                Text("Nenhum modelo salvo.", color = TextSecondary, fontSize = 13.sp)
            }
            templates.forEach { info ->
                TemplateRow(
                    info = info,
                    store = store,
                    enabled = !busy,
                    onTest = {
                        runAnalysis { frame ->
                            val result = withContext(Dispatchers.Default) { matcher.find(frame, info.name) }
                            val score = "%.2f".format(result.bestScore)
                            highlights = result.match?.let { listOf(Highlight(it, ConnectedGreen)) } ?: emptyList()
                            resultText = if (result.match != null) {
                                "\"${info.name}\" encontrado (similaridade $score)"
                            } else {
                                "\"${info.name}\" não encontrado (melhor similaridade $score, mínimo ${ImageMatcher.DEFAULT_THRESHOLD})"
                            }
                        }
                    },
                    onCopy = {
                        copyToClipboard(context, "CLICK_IMAGE:${info.name}")
                        Toast.makeText(context, "Copiado: CLICK_IMAGE:${info.name}", Toast.LENGTH_SHORT).show()
                    },
                    onDelete = {
                        store.delete(info.name)
                        templates = store.list()
                    }
                )
            }
        }
    }
}

/**
 * Imagem com destaques e, opcionalmente, seleção de retângulo por arrasto.
 * O Box tem a mesma proporção do frame, então coordenadas normalizadas mapeiam direto.
 */
@Composable
private fun FrameView(
    bitmap: Bitmap,
    highlights: List<Highlight>,
    selection: ScreenMatch?,
    onSelectionChange: ((ScreenMatch) -> Unit)?
) {
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    val selectionColor = WarningYellow
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(bitmap.width.toFloat() / bitmap.height)
            .background(Color.Black, RoundedCornerShape(8.dp))
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Imagem da captura HDMI",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (onSelectionChange != null) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> dragStart = offset },
                                onDrag = { change, _ ->
                                    val end = change.position
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    onSelectionChange(
                                        ScreenMatch(
                                            left = (minOf(dragStart.x, end.x) / w).coerceIn(0f, 1f),
                                            top = (minOf(dragStart.y, end.y) / h).coerceIn(0f, 1f),
                                            right = (maxOf(dragStart.x, end.x) / w).coerceIn(0f, 1f),
                                            bottom = (maxOf(dragStart.y, end.y) / h).coerceIn(0f, 1f)
                                        )
                                    )
                                }
                            )
                        }
                    } else Modifier
                )
        ) {
            fun drawMatch(m: ScreenMatch, color: Color) {
                drawRect(
                    color = color,
                    topLeft = Offset(m.left * size.width, m.top * size.height),
                    size = Size((m.right - m.left) * size.width, (m.bottom - m.top) * size.height),
                    style = Stroke(width = 3f)
                )
            }
            highlights.forEach { drawMatch(it.match, it.color) }
            selection?.let { drawMatch(it, selectionColor) }
        }
    }
}

@Composable
private fun TemplateRow(
    info: TemplateInfo,
    store: TemplateStore,
    enabled: Boolean,
    onTest: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val thumb = remember(info.imageFile.lastModified()) { store.loadBitmap(info) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = info.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(info.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                if (thumb != null) {
                    Text("${thumb.width}x${thumb.height} px", color = TextSecondary, fontSize = 11.sp)
                }
            }
            IconButton(onClick = onTest, enabled = enabled) {
                Icon(Icons.Default.Search, contentDescription = "Testar", tint = CyanPrimary)
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar comando", tint = CyanPrimary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun cropNormalized(bitmap: Bitmap, sel: ScreenMatch): Bitmap? {
    val left = (sel.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
    val top = (sel.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
    val right = (sel.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
    val bottom = (sel.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
    if (right - left < 8 || bottom - top < 8) return null
    return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("macro", text))
}
