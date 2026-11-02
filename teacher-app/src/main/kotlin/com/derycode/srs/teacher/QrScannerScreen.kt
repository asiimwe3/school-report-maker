package com.derycode.srs.teacher

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * One-code connect: the head teacher shows the school QR once, every teacher
 * scans it, and the phone joins that school automatically.
 */
@Composable
fun QrScannerScreen(onCode: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var problem by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) problem = "Camera permission is needed to scan the school code. You can still type the code by hand."
    }
    LaunchedEffect(granted) { if (!granted) askPermission.launch(Manifest.permission.CAMERA) }

    Column(Modifier.fillMaxSize().background(BG)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(CARD)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = NAVY)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Scan school code", color = NAVY, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Point at the QR on the head teacher's screen", color = MUTED, fontSize = 13.sp)
            }
        }

        if (granted) {
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(18.dp)).background(Color.Black)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            try {
                                val provider = providerFuture.get()
                                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { image ->
                                    if (!done) {
                                        val text = QrDecoder.decode(image)
                                        if (text != null) {
                                            done = true
                                            onCode(ExtractInvite.decode(text))
                                        }
                                    }
                                }
                                provider.unbindAll()
                                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                            } catch (e: Exception) {
                                problem = "Camera could not start on this phone (${e.javaClass.simpleName}). Type the code by hand instead."
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    }
                )
                // viewfinder frame
                Box(
                    Modifier.align(Alignment.Center).size(240.dp)
                        .border(2.dp, Color.White, RoundedCornerShape(16.dp))
                )
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = BLUE, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(problem.ifBlank { "Hold steady — it connects automatically once it reads the code." },
                    color = if (problem.isBlank()) MUTED else RED, fontSize = 13.sp)
            }
        } else {
            CardBox {
                Text("Camera permission needed", color = NAVY, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(problem.ifBlank { "Allow camera access to scan the school code." }, color = MUTED, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Button(onClick = { askPermission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                    androidx.compose.material3.Button(
                        onClick = onClose,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CARD_ALT, contentColor = NAVY)
                    ) { Text("Type code instead") }
                }
            }
        }
    }
}

/** Decodes a QR straight from the camera frame — pure offline ZXing, no Play Services. */
private object QrDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )
        )
    }

    fun decode(image: ImageProxy): String? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val data = ByteArray(rowStride * image.height)
        buffer.get(data)
        return try {
            val source = PlanarYUVLuminanceSource(data, rowStride, image.height, 0, 0, image.width, image.height, false)
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: Exception) {
            null
        } finally {
            image.close()
        }
    }
}

/** Turns a scanned string into the invite code the cloud expects. */
object ExtractInvite {
    fun decode(raw: String): String {
        val s = raw.trim()
        val body = when {
            s.uppercase().startsWith("SRS1:") -> s.substring(5)
            s.contains("SRS1:") -> s.substring(s.indexOf("SRS1:") + 5)
            else -> s
        }
        return body.uppercase().filter { it.isLetterOrDigit() }.take(24)
    }
}
