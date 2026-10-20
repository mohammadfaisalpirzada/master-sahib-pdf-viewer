package com.themastersahib.pdfviewer.ui

import android.graphics.Bitmap
import android.graphics.Paint as APaint
import android.graphics.Path as APath
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.themastersahib.pdfviewer.ImageSaver
import com.themastersahib.pdfviewer.core.FileExport
import com.themastersahib.pdfviewer.core.PdfBuilder
import com.themastersahib.pdfviewer.core.PdfTools
import com.themastersahib.pdfviewer.core.PointXY
import com.themastersahib.pdfviewer.core.StrokePath
import com.themastersahib.pdfviewer.core.TextStamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private enum class AnnotTool { PEN, HIGHLIGHTER, ERASER, TEXT, RECTANGLE, CIRCLE }

private class Stroke(
    val points: MutableList<Offset>,
    val color: Color,
    val width: Float,
    val alpha: Float
)

private data class PlacedText(
    val text: String,
    val pos: Offset,
    val color: Color
)

/**
 * Full-page annotation screen with the top edit toolbar (Pen, Highlighter, Eraser,
 * Text Box 'T', Rectangle, Circle, Undo, Clear) and color palette.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationScreen(
    page: Bitmap,
    onBack: () -> Unit,
    sourceUri: Uri? = null,
    sourcePage: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val img = remember(page) { page.asImageBitmap() }
    val strokes = remember { mutableListOf<Stroke>() }
    val texts = remember { mutableListOf<PlacedText>() }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var pendingTextPos by remember { mutableStateOf<Offset?>(null) }
    var pendingTextInput by remember { mutableStateOf("") }

    var tick by remember { mutableIntStateOf(0) }
    var tool by remember { mutableStateOf(AnnotTool.PEN) }
    var color by remember { mutableStateOf(Color.Red) }
    var surface by remember { mutableStateOf(IntSize.Zero) }
    var busy by remember { mutableStateOf(false) }

    fun shapePoints(kind: AnnotTool, start: Offset, end: Offset): MutableList<Offset> {
        val pts = mutableListOf<Offset>()
        if (kind == AnnotTool.RECTANGLE) {
            pts.add(Offset(start.x, start.y))
            pts.add(Offset(end.x, start.y))
            pts.add(Offset(end.x, end.y))
            pts.add(Offset(start.x, end.y))
            pts.add(Offset(start.x, start.y))
        } else if (kind == AnnotTool.CIRCLE) {
            val cx = (start.x + end.x) / 2f
            val cy = (start.y + end.y) / 2f
            val rx = kotlin.math.abs(end.x - start.x) / 2f
            val ry = kotlin.math.abs(end.y - start.y) / 2f
            val steps = 36
            for (i in 0..steps) {
                val a = (2.0 * Math.PI * i) / steps
                pts.add(Offset(cx + (rx * cos(a)).toFloat(), cy + (ry * sin(a)).toFloat()))
            }
        }
        return pts
    }

    fun flattenAndSave(asPdf: Boolean) {
        if (surface.width == 0) return
        busy = true
        val snapshot = strokes.toList()
        val textSnap = texts.toList()
        val scale = page.width.toFloat() / surface.width
        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                val out = page.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = android.graphics.Canvas(out)
                snapshot.forEach { s ->
                    val paint = APaint(APaint.ANTI_ALIAS_FLAG).apply {
                        style = APaint.Style.STROKE
                        strokeCap = APaint.Cap.ROUND
                        strokeJoin = APaint.Join.ROUND
                        strokeWidth = s.width * scale
                        this.color = s.color.toArgb()
                        alpha = (s.alpha * 255).toInt()
                    }
                    val p = APath()
                    s.points.forEachIndexed { i, pt ->
                        val x = pt.x * scale; val y = pt.y * scale
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    canvas.drawPath(p, paint)
                }
                textSnap.forEach { t ->
                    val tp = APaint(APaint.ANTI_ALIAS_FLAG).apply {
                        this.color = t.color.toArgb()
                        textSize = 42f * scale
                        isFakeBoldText = true
                    }
                    canvas.drawText(t.text, t.pos.x * scale, t.pos.y * scale, tp)
                }
                if (asPdf) {
                    val f = File(context.cacheDir, "annotated_${System.currentTimeMillis()}.pdf")
                    PdfBuilder.build(listOf(out), f)
                    if (FileExport.savePdfToDownloads(context, f, f.name) != null)
                        "Saved PDF to Downloads ▸ Master Sahib PDF" else "PDF save failed"
                } else {
                    if (ImageSaver.saveToGallery(context, out, "annotated_${System.currentTimeMillis()}") != null)
                        "Saved image to Gallery" else "Save failed"
                }
            }
            busy = false
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    fun saveVectorPdf() {
        val uri = sourceUri ?: return
        if (surface.width == 0) return
        busy = true
        val snapshot = strokes.toList()
        val textSnap = texts.toList()
        val sw = surface.width.toFloat(); val sh = surface.height.toFloat()
        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                val paths = snapshot.map { s ->
                    val argb = s.color.toArgb()
                    StrokePath(
                        points = s.points.map { PointXY(it.x, it.y) },
                        r = (argb shr 16) and 0xFF, g = (argb shr 8) and 0xFF, b = argb and 0xFF,
                        width = s.width, alpha = s.alpha
                    )
                }
                val stamps = textSnap.map { t ->
                    val argb = t.color.toArgb()
                    TextStamp(
                        text = t.text,
                        normX = (t.pos.x / sw).coerceIn(0f, 1f),
                        normY = (t.pos.y / sh).coerceIn(0f, 1f),
                        r = (argb shr 16) and 0xFF, g = (argb shr 8) and 0xFF, b = argb and 0xFF
                    )
                }
                val f = PdfTools.annotateIntoPdf(context, uri, sourcePage, paths, sw, sh, stamps)
                if (FileExport.savePdfToDownloads(context, f, "annotated_${System.currentTimeMillis()}.pdf") != null)
                    "Saved PDF (text kept) to Downloads ▸ Master Sahib PDF" else "Save failed"
            }
            busy = false
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Edit & Annotate") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    actions = {
                        IconButton(
                            enabled = strokes.isNotEmpty() || texts.isNotEmpty(),
                            onClick = {
                                if (strokes.isNotEmpty()) strokes.removeLastOrNull()
                                else if (texts.isNotEmpty()) texts.removeLastOrNull()
                                tick++
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
                        }
                        IconButton(
                            enabled = strokes.isNotEmpty() || texts.isNotEmpty(),
                            onClick = { strokes.clear(); texts.clear(); tick++ }
                        ) {
                            Icon(Icons.Filled.Delete, "Clear")
                        }
                    }
                )
                // Top Edit Bar matching screenshot
                Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = tool == AnnotTool.PEN,
                            onClick = { tool = AnnotTool.PEN },
                            leadingIcon = { Icon(Icons.Filled.Edit, null, modifier = Modifier.size(18.dp)) },
                            label = { Text("Pen") }
                        )
                        FilterChip(
                            selected = tool == AnnotTool.HIGHLIGHTER,
                            onClick = { tool = AnnotTool.HIGHLIGHTER },
                            leadingIcon = { Icon(Icons.Filled.BorderColor, null, modifier = Modifier.size(18.dp)) },
                            label = { Text("Highlight") }
                        )
                        FilterChip(
                            selected = tool == AnnotTool.ERASER,
                            onClick = { tool = AnnotTool.ERASER },
                            leadingIcon = { Icon(Icons.Filled.AutoFixNormal, null, modifier = Modifier.size(18.dp)) },
                            label = { Text("Eraser") }
                        )
                        FilterChip(
                            selected = tool == AnnotTool.TEXT,
                            onClick = { tool = AnnotTool.TEXT },
                            leadingIcon = { Icon(Icons.Filled.Title, null, modifier = Modifier.size(18.dp)) },
                            label = { Text("Text (T)") }
                        )
                        FilterChip(
                            selected = tool == AnnotTool.RECTANGLE,
                            onClick = { tool = AnnotTool.RECTANGLE },
                            leadingIcon = { Icon(Icons.Filled.CropSquare, null, modifier = Modifier.size(18.dp)) },
                            label = { Text("Box") }
                        )
                        FilterChip(
                            selected = tool == AnnotTool.CIRCLE,
                            onClick = { tool = AnnotTool.CIRCLE },
                            leadingIcon = { Icon(Icons.Filled.RadioButtonUnchecked, null, modifier = Modifier.size(18.dp)) },
                            label = { Text("Circle") }
                        )
                    }
                }
            }
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ColorDot(Color.Red, color == Color.Red) { color = Color.Red }
                    ColorDot(Color.Blue, color == Color.Blue) { color = Color.Blue }
                    ColorDot(Color.Black, color == Color.Black) { color = Color.Black }
                    ColorDot(Color(0xFF15803D), color == Color(0xFF15803D)) { color = Color(0xFF15803D) }
                    ColorDot(Color.Yellow, color == Color.Yellow) { color = Color.Yellow }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = !busy, onClick = { flattenAndSave(false) }) { Text("Save Image") }
                    Button(enabled = !busy, onClick = { flattenAndSave(true) }) { Text("Save PDF") }
                    if (sourceUri != null) {
                        Button(enabled = !busy, onClick = { saveVectorPdf() }) { Text("Save PDF (keep text)") }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val aspect = page.width.toFloat() / page.height.toFloat()
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .background(Color.White)
                    .onSizeChanged { surface = it }
                    .pointerInput(tool, color) {
                        if (tool == AnnotTool.TEXT) {
                            detectTapGestures { pos ->
                                pendingTextPos = pos
                                pendingTextInput = ""
                            }
                        } else {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    when (tool) {
                                        AnnotTool.PEN, AnnotTool.HIGHLIGHTER -> {
                                            val hi = tool == AnnotTool.HIGHLIGHTER
                                            strokes.add(
                                                Stroke(
                                                    points = mutableListOf(pos),
                                                    color = color,
                                                    width = if (hi) 34f else 6f,
                                                    alpha = if (hi) 0.35f else 1f
                                                )
                                            )
                                        }
                                        AnnotTool.ERASER -> {
                                            strokes.removeAll { s ->
                                                s.points.any { pt -> hypot(pt.x - pos.x, pt.y - pos.y) < 36f }
                                            }
                                            texts.removeAll { t ->
                                                hypot(t.pos.x - pos.x, t.pos.y - pos.y) < 48f
                                            }
                                        }
                                        AnnotTool.RECTANGLE, AnnotTool.CIRCLE -> {
                                            dragStart = pos
                                            dragCurrent = pos
                                        }
                                        else -> Unit
                                    }
                                    tick++
                                },
                                onDrag = { change, _ ->
                                    when (tool) {
                                        AnnotTool.PEN, AnnotTool.HIGHLIGHTER -> {
                                            strokes.lastOrNull()?.points?.add(change.position)
                                        }
                                        AnnotTool.ERASER -> {
                                            val pos = change.position
                                            strokes.removeAll { s ->
                                                s.points.any { pt -> hypot(pt.x - pos.x, pt.y - pos.y) < 36f }
                                            }
                                        }
                                        AnnotTool.RECTANGLE, AnnotTool.CIRCLE -> {
                                            dragCurrent = change.position
                                        }
                                        else -> Unit
                                    }
                                    tick++
                                },
                                onDragEnd = {
                                    val s = dragStart
                                    val e = dragCurrent
                                    if (s != null && e != null && (tool == AnnotTool.RECTANGLE || tool == AnnotTool.CIRCLE)) {
                                        strokes.add(
                                            Stroke(
                                                points = shapePoints(tool, s, e),
                                                color = color,
                                                width = 5f,
                                                alpha = 1f
                                            )
                                        )
                                    }
                                    dragStart = null
                                    dragCurrent = null
                                    tick++
                                }
                            )
                        }
                    }
            ) {
                @Suppress("UNUSED_EXPRESSION") tick
                drawImage(img, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                strokes.forEach { s ->
                    if (s.points.size > 1) {
                        val path = Path().apply {
                            moveTo(s.points.first().x, s.points.first().y)
                            s.points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = path,
                            color = s.color.copy(alpha = s.alpha),
                            style = DrawStroke(width = s.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
                val ds = dragStart
                val dc = dragCurrent
                if (ds != null && dc != null && (tool == AnnotTool.RECTANGLE || tool == AnnotTool.CIRCLE)) {
                    val previewPts = shapePoints(tool, ds, dc)
                    if (previewPts.size > 1) {
                        val path = Path().apply {
                            moveTo(previewPts.first().x, previewPts.first().y)
                            previewPts.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = path,
                            color = color,
                            style = DrawStroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
                texts.forEach { t ->
                    val tp = APaint(APaint.ANTI_ALIAS_FLAG).apply {
                        this.color = t.color.toArgb()
                        textSize = 40f
                        isFakeBoldText = true
                    }
                    drawContext.canvas.nativeCanvas.drawText(t.text, t.pos.x, t.pos.y, tp)
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }

    if (pendingTextPos != null) {
        AlertDialog(
            onDismissRequest = { pendingTextPos = null },
            title = { Text("Add Text to Page") },
            text = {
                OutlinedTextField(
                    value = pendingTextInput,
                    onValueChange = { pendingTextInput = it },
                    label = { Text("Enter text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = pendingTextPos
                    if (p != null && pendingTextInput.isNotBlank()) {
                        texts.add(PlacedText(pendingTextInput.trim(), p, color))
                        tick++
                    }
                    pendingTextPos = null
                }) { Text("Place Text") }
            },
            dismissButton = {
                TextButton(onClick = { pendingTextPos = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ColorDot(c: Color, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = c,
        border = if (selected) BorderStroke(3.dp, Color.DarkGray) else null,
        modifier = Modifier.size(32.dp)
    ) {}
}
