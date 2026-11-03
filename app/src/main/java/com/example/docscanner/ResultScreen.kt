package com.example.docscanner

import android.graphics.Bitmap
import android.graphics.Matrix
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ResultScreen(input: Bitmap, onNew: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var base by remember(input) { mutableStateOf(input) }
    var filter by remember { mutableStateOf(ScanFilter.MAGIC) }
    var result by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(base, filter) {
        result = withContext(Dispatchers.Default) { Scanner.applyFilter(base, filter) }
    }

    fun save(pdf: Boolean) {
        val bmp = result ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (pdf) Storage.savePdf(ctx, bmp) else Storage.saveJpeg(ctx, bmp)
            }
            Toast.makeText(
                ctx,
                if (ok) (if (pdf) "Saved to Downloads/DocScanner" else "Saved to Pictures/DocScanner")
                else "Save failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            val r = result
            if (r != null) {
                Image(
                    bitmap = r.asImageBitmap(),
                    contentDescription = "Scanned document",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator()
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScanFilter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    base = Bitmap.createBitmap(
                        base, 0, 0, base.width, base.height,
                        Matrix().apply { postRotate(90f) }, true
                    )
                }
            ) { Text("Rotate") }
            Button(modifier = Modifier.weight(1f), enabled = result != null, onClick = { save(false) }) { Text("JPG") }
            Button(modifier = Modifier.weight(1f), enabled = result != null, onClick = { save(true) }) { Text("PDF") }
            TextButton(modifier = Modifier.weight(1f), onClick = onNew) { Text("New") }
        }
    }
}
