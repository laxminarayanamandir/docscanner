package com.example.docscanner

import android.graphics.Bitmap
import android.graphics.Matrix
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors
import kotlin.math.max

@Composable
fun CameraScreen(onCaptured: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    val worker = remember { Executors.newSingleThreadExecutor() }
    var busy by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        Text(
            "Place the document on a flat, contrasting surface",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp)
                .background(Color(0x99000000), CircleShape)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .size(76.dp)
                .clip(CircleShape)
                .background(if (busy) Color.Gray else Color.White)
                .border(4.dp, Color(0xFF888888), CircleShape)
                .clickable(enabled = !busy) {
                    busy = true
                    imageCapture.takePicture(worker, object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bmp = runCatching { prepare(image) }.getOrNull()
                            image.close()
                            ContextCompat.getMainExecutor(context).execute {
                                busy = false
                                if (bmp != null) onCaptured(bmp)
                                else Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            ContextCompat.getMainExecutor(context).execute {
                                busy = false
                                Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    })
                }
        )
    }
}

/** Rotates the captured frame upright and caps its size to keep memory sane. */
private fun prepare(image: ImageProxy): Bitmap {
    val raw = image.toBitmap()
    val m = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
    val maxSide = 3000f
    val k = maxSide / max(raw.width, raw.height)
    if (k < 1f) m.postScale(k, k)
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
}
