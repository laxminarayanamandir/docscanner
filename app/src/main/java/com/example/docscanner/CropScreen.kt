package com.example.docscanner

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

@Composable
fun CropScreen(bitmap: Bitmap, onDone: (Bitmap) -> Unit, onBack: () -> Unit) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val pts = remember(bitmap) { mutableStateListOf<Offset>() }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(bitmap) {
        val found = withContext(Dispatchers.Default) { Scanner.detectCorners(bitmap) }
        pts.clear()
        pts.addAll(found.map { Offset(it.x, it.y) })
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
            val vw = constraints.maxWidth.toFloat()
            val vh = constraints.maxHeight.toFloat()
            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            val scale = min(vw / bw, vh / bh)
            val offX = (vw - bw * scale) / 2f
            val offY = (vh - bh * scale) / 2f
            fun toView(p: Offset) = Offset(p.x * scale + offX, p.y * scale + offY)

            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(scale, offX, offY) {
                        var idx = -1
                        detectDragGestures(
                            onDragStart = { start ->
                                val i = pts.indices.minByOrNull { (toView(pts[it]) - start).getDistance() } ?: -1
                                idx = if (i >= 0 && (toView(pts[i]) - start).getDistance() < 120f) i else -1
                            },
                            onDragEnd = { idx = -1 },
                            onDragCancel = { idx = -1 }
                        ) { change, drag ->
                            change.consume()
                            if (idx >= 0) {
                                val p = pts[idx]
                                pts[idx] = Offset(
                                    (p.x + drag.x / scale).coerceIn(0f, bw),
                                    (p.y + drag.y / scale).coerceIn(0f, bh)
                                )
                            }
                        }
                    }
            ) {
                if (pts.size == 4) {
                    val v = pts.map { toView(it) }
                    val path = Path().apply {
                        moveTo(v[0].x, v[0].y)
                        v.drop(1).forEach { lineTo(it.x, it.y) }
                        close()
                    }
                    drawPath(path, Color(0x332196F3))
                    drawPath(path, Color(0xFF2196F3), style = Stroke(width = 4f))
                    v.forEach {
                        drawCircle(Color.White, 20f, it)
                        drawCircle(Color(0xFF2196F3), 14f, it)
                    }
                }
            }
        }

        Text(
            "Drag the corners to fit the document",
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Retake") }
            Button(
                enabled = pts.size == 4 && !working,
                modifier = Modifier.weight(1f),
                onClick = {
                    working = true
                    scope.launch {
                        val corners = pts.map { PointF(it.x, it.y) }
                        val out = withContext(Dispatchers.Default) { Scanner.warp(bitmap, corners) }
                        working = false
                        onDone(out)
                    }
                }
            ) { Text(if (working) "Processing…" else "Continue") }
        }
    }
}
