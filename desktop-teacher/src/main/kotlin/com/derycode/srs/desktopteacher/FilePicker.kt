package com.derycode.srs.desktopteacher

import java.io.File

/**
 * Native file dialogs, so a teacher picks a folder they already know instead of
 * typing a path. AWT dialogs are used because they look like the system's own.
 */
object FilePicker {

    fun open(title: String, extensions: List<String>): java.nio.file.Path? {
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
        dialog.isMultipleMode = false
        dialog.setFilenameFilter { _, name -> extensions.any { name.lowercase().endsWith(".$it") } }
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val f = dialog.file ?: return null
        return File(dir, f).toPath()
    }

    fun saveJson(suggested: String): java.nio.file.Path? {
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Save as", java.awt.FileDialog.SAVE)
        dialog.file = suggested
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val f = dialog.file ?: return null
        return File(dir, if (f.endsWith(".json")) f else "$f.json").toPath()
    }

    fun saveDocx(suggested: String): java.nio.file.Path? {
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Save the marksheet", java.awt.FileDialog.SAVE)
        dialog.file = suggested
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val f = dialog.file ?: return null
        return File(dir, if (f.endsWith(".docx")) f else "$f.docx").toPath()
    }

    fun revealInFolder(path: java.nio.file.Path) {
        try {
            val target = path.toFile()
            val os = System.getProperty("os.name").lowercase()
            when {
                os.contains("win") -> ProcessBuilder("explorer.exe", "/select,", target.absolutePath).start()
                os.contains("mac") -> ProcessBuilder("open", "-R", target.absolutePath).start()
                else -> ProcessBuilder("xdg-open", target.parent ?: ".").start()
            }
        } catch (_: Exception) { }
    }

    fun openUrl(url: String) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().browse(java.net.URI(url))
        } catch (_: Exception) { }
    }
}
