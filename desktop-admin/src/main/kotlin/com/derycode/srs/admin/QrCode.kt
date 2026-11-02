package com.derycode.srs.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * One QR code every teacher scans to be connected to the school.
 *
 * Always drawn black-on-white — never themed — because phone cameras need
 * contrast and an inverted/dark QR is unreliable to scan.
 */
@Composable
fun InviteQr(code: String, size: Int = 172, caption: String = "") {
    if (code.isBlank()) return
    val matrix = remember(code, size) {
        runCatching {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            QRCodeWriter().encode(InvitePayload.encode(code), BarcodeFormat.QR_CODE, size, size, hints)
        }.getOrNull()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size.dp).background(Color.White, RoundedCornerShape(10.dp))
                .border(1.dp, Theme.LINE, RoundedCornerShape(10.dp)).padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (matrix == null) {
                Text("QR unavailable", color = Color(0xFF666666), fontSize = 12.sp)
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    val n = matrix.width
                    val cell = this.size.width / n
                    for (y in 0 until n) {
                        for (x in 0 until n) {
                            if (matrix.get(x, y)) {
                                drawRect(
                                    Color(0xFF101010),
                                    topLeft = Offset(x * cell, y * cell),
                                    size = Size(cell + 0.35f, cell + 0.35f)
                                )
                            }
                        }
                    }
                }
            }
        }
        if (caption.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(caption, color = Theme.MUTED, fontSize = 12.sp)
        }
    }
}

/** Shared payload format so the phone recognises our code even if it is printed or forwarded. */
object InvitePayload {
    private const val PREFIX = "SRS1:"

    fun encode(code: String): String = PREFIX + code.trim().uppercase()

    /** Accepts our payload, a bare code, a WhatsApp-forwarded code, or text containing one. */
    fun decode(raw: String): String {
        val s = raw.trim()
        val body = when {
            s.uppercase().startsWith(PREFIX) -> s.substring(PREFIX.length)
            s.contains("SRS1:") -> s.substring(s.indexOf("SRS1:") + PREFIX.length)
            else -> s
        }
        return body.uppercase().filter { it.isLetterOrDigit() }.take(24)
    }
}
