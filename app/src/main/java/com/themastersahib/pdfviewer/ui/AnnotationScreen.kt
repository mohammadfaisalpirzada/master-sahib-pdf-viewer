package com.themastersahib.pdfviewer.ui

import android.graphics.Bitmap
import android.graphics.Paint as APaint
import android.graphics.Path as APath
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.themastersahib.pdfviewer.ImageSaver
import com.themastersahib.pdfviewer.core.FileExport
import com.themastersahib.pdfviewer.core.PdfBuilder
import com.themastersahib.pdfviewer.core.PdfTools
import com.themastersahib.pdfviewer.core.PointXY
import com.themastersahib.pdfviewer.core.StrokePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Tool { PEN, HIGHLIGHTER }

private class Stroke(
    val points: MutableList<Offset>,
    val color: Color,
    val width: Float,   // surface px
    val alpha: Float
)

/**
 * Annotate one page: PEN (opaque, thin — also for signatures) and
 * HIGHLIGHTER (semi-transparent, thick). On save, strokes are flattened onto
 * the page bitmap and exported as an image or a 1-page PDF.
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
    var tick by remember { mutableIntStateOf(0) }           // bump to force redraw
    var tool by remember { mutableStateOf(Tool.PEN) }
    var color by remember { mutableStateOf(Color.Red) }
    var surface by remember { mutableStateOf(IntSize.Zero) }
    var busy by remember { mutableStateOf(false) }

    fun flattenAndSave(asPdf: Boolean) {
        if (surface.width == 0) return
        busy = true
        val snapshot = strokes.toList()
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
                val f = PdfTools.annotateIntoPdf(context, uri, sourcePage, paths, sw, sh)
                if (FileExport.savePdfToDownloads(context, f, "annotated_${System.currentTimeMillis()}.pdf") != null)
                    "Saved PDF (text kept) to Downloads ▸ Master Sahib PDF" else "Save failed"
            }
            busy = false
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Annotate") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(enabled = strokes.isNotEmpty(), onClick = { strokes.removeLastOrNull(); tick++ }) {
                        Icon(Icons.Filled.Undo, "Undo")
                    }
                    IconButton(enabled = strokes.isNotEmpty(), onClick = { strokes.clear(); tick++ }) {
                        Icon(Icons.Filled.Delete, "Clear")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(tool == Tool.PEN, { tool = Tool.PEN }, { Text("Pen") })
                    FilterChip(tool == Tool.HIGHLIGHTER, { tool = Tool.HIGHLIGHTER }, { Text("Highlighter") })
                    Spacer(Modifier.width(4.dp))
                    ColorDot(Color.Red, color == Color.Red) { color = Color.Red }
                    ColorDot(Color.Blue, color == Color.Blue) { color = Color.Blue }
                    ColorDot(Color.Black, color == Color.Black) { color = Color.Black }
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
                        detectDragGestures(
                            onDragStart = { pos ->
                                val hi = tool == Tool.HIGHLIGHTER
                                strokes.add(
                                    Stroke(
                                        points = mutableListOf(pos),
                                        color = color,
                                        width = if (hi) 34f else 6f,
                                        alpha = if (hi) 0.35f else 1f
                                    )
                                )
                                tick++
                            },
                            onDrag = { change, _ ->
                                strokes.lastOrNull()?.points?.add(change.position)
                                tick++
                            }
                        )
                    }
            ) {
                @Suppress("UNUSED_EXPRESSION") tick // read so drawing invalidates on each change
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
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
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
