package com.derycode.srs.teacher

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.nio.file.Path

/**
 * Paper has to leave the phone.
 *
 * A teacher in a staff room does not own a printer. What they can do is send the
 * Word file to the school clerk on WhatsApp, or drop it on a flash disk at the
 * cybercafé down the road. Both start here: build the .docx with the same core
 * writer the console uses, then hand it to whatever the phone can share.
 */
object ShareAsWord {

    /** Writes [build] into the app's share folder and returns the file. */
    private fun build(context: Context, fileName: String, build: (Path) -> Path): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val target = File(dir, fileName)
        if (target.exists()) target.delete()
        build(target.toPath())
        return target
    }

    private fun uriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Offers the whole phone's app list: WhatsApp, email, Bluetooth, a printer app
     * or "save to Files". Returns false when the phone has nothing that can take it.
     */
    fun share(context: Context, fileName: String, build: (Path) -> Path): Boolean = runCatching {
        val file = build(context, fileName, build)
        val uri = uriFor(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Send ${file.name}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        true
    }.getOrDefault(false)

    /** Lets the teacher keep the file on the phone itself (Downloads) when there is no printer app. */
    fun save(context: Context, fileName: String, build: (Path) -> Path): Boolean = runCatching {
        val file = build(context, fileName, build)
        val uri = uriFor(context, file)
        val create = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            putExtra(Intent.EXTRA_TITLE, file.name)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        context.startActivity(create.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        true
    }.getOrDefault(false)

    /** A file name a clerk can recognise: no spaces, no slashes, always unique enough. */
    fun safe(parts: String): String =
        parts.trim().replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifBlank { "report" }
}
