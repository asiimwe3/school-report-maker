package com.derycode.srs.admin

import com.derycode.srs.core.model.TimetableSheet
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * Import a picture of the printed timetable.
 *
 * The sheet is downscaled to a readable size and stored inside the school setup,
 * so every teacher's phone shows the same picture after pulling setup — nothing is
 * retyped and no period can be transcribed wrongly.
 */
internal object TimetableSheetTool {

    private const val MAX_WIDTH = 1500
    private const val JPEG_QUALITY = 0.85f

    fun import(file: File): Pair<TimetableSheet?, String> {
        val img: BufferedImage = try {
            ImageIO.read(file) ?: return null to "That file is not a picture the app can read. Use a JPG or PNG photo of the timetable."
        } catch (e: Exception) {
            return null to "Could not open ${file.name}: ${e.message?.take(80) ?: "unknown error"}"
        }
        val scaled = if (img.width > MAX_WIDTH) {
            val ratio = MAX_WIDTH.toDouble() / img.width
            BufferedImage(MAX_WIDTH, (img.height * ratio).roundToInt(), BufferedImage.TYPE_INT_RGB).also { out ->
                out.createGraphics().apply {
                    drawImage(img.getScaledInstance(MAX_WIDTH, (img.height * ratio).roundToInt(), java.awt.Image.SCALE_SMOOTH), 0, 0, null)
                    dispose()
                }
            }
        } else {
            BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB).also { out ->
                out.createGraphics().apply { drawImage(img, 0, 0, null); dispose() }
            }
        }
        val bytes = ByteArrayOutputStream().use { bos ->
            // JPEG has no alpha; write through ImageWriter to control quality
            val writer = ImageIO.getImageWritersByFormatName("jpg").next()
            ImageIO.createImageOutputStream(bos).use { ios ->
                writer.output = ios
                val param = writer.defaultWriteParam.apply {
                    compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = JPEG_QUALITY
                }
                writer.write(null, javax.imageio.IIOImage(scaled, null, null), param)
            }
            bos.toByteArray()
        }
        if (bytes.size > 3_000_000) return null to "That picture is too large even after shrinking (${bytes.size / 1024} KB). Try a smaller photo."
        val sheet = TimetableSheet(
            fileName = file.name,
            imageBase64 = Base64.getEncoder().encodeToString(bytes),
            importedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
            note = ""
        )
        return sheet to "Imported ${file.name} — ${scaled.width}×${scaled.height}, ${bytes.size / 1024} KB. Teachers get it when they pull school setup."
    }

    fun decodeBase64(b64: String): ByteArray? = try {
        if (b64.isBlank()) null else Base64.getDecoder().decode(b64)
    } catch (_: Exception) { null }
}
