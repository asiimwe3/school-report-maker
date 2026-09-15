package com.derycode.srs.admin

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File

/**
 * Excel bridge — the "Excel system":
 *  1. Schools keep student lists in Excel; [readToBulkText] turns a sheet into
 *     the same lines the bulk paste box understands (adm, name, sex, phone).
 *  2. [writeTemplate] hands out a ready-to-fill template.
 *  3. [writeStudentsWorkbook] exports the student register to a real .xlsx
 *     for portals, printing or sharing.
 */
object ExcelBridge {

    data class ReadResult(val text: String = "", val count: Int = 0, val error: String? = null)

    // ── 1. upload: sheet → bulk-paste text ──────────────────────────────────

    fun readToBulkText(file: File): ReadResult {
        val rows: List<List<String>> = when (file.extension.lowercase()) {
            "xlsx", "xls" -> readWorkbook(file) ?: return ReadResult(error = "Could not open ${file.name}. Is it a real Excel file?")
            "csv", "txt" -> readCsv(file)
            else -> return ReadResult(error = "Unsupported file type — use Excel (.xlsx / .xls) or CSV (.csv / .txt).")
        }
        val clean = rows.filter { it.any { c -> c.isNotBlank() } }
        if (clean.isEmpty()) return ReadResult(error = "The file is empty.")
        val header = clean.first().map { it.trim().lowercase() }
        val hasHeader = header.any { h ->
            h.contains("adm") || h.contains("name") || h.contains("sex") || h.contains("gender") ||
                h.contains("first") || h.contains("surname") || h.contains("phone") || h.contains("contact")
        }
        val dataRows = if (hasHeader) clean.drop(1) else clean
        if (dataRows.isEmpty()) return ReadResult(error = "Only a header row found — no student rows below it.")
        val lines = if (hasHeader) mapWithHeader(header, dataRows)
        else dataRows.map { r -> r.joinToString(", ") { it.trim() } }
            .filter { l -> l.replace(",", " ").isNotBlank() }
        if (lines.isEmpty()) return ReadResult(error = "No readable student rows found.")
        return ReadResult(text = lines.joinToString("\n"), count = lines.size)
    }

    /** Headers like: Admission No | Name | Sex | Guardian Phone — or First/Middle/Last split. */
    private fun mapWithHeader(header: List<String>, rows: List<List<String>>): List<String> {
        fun findIdx(exact: List<String> = emptyList(), contains: List<String> = emptyList()): Int? {
            header.indexOfFirst { it.trim() in exact }.takeIf { it >= 0 }?.let { return it }
            header.indexOfFirst { h -> contains.any { h.contains(it) } }.takeIf { it >= 0 }?.let { return it }
            return null
        }
        val admIdx = findIdx(contains = listOf("adm", "admission", "reg no", "registration"))
        val fullIdx = findIdx(exact = listOf("name", "student", "full name", "student name"), contains = listOf("full name", "student name"))
        val firstIdx = findIdx(contains = listOf("first", "given"))
        val middleIdx = findIdx(contains = listOf("middle", "other name"))
        val lastIdx = findIdx(contains = listOf("last", "surname", "family"))
        val sexIdx = findIdx(contains = listOf("sex", "gender"))
        val phoneIdx = findIdx(contains = listOf("phone", "contact", "parent", "guardian", "tel"))
        return rows.map { r ->
            fun cell(i: Int?) = i?.let { r.getOrNull(it) }?.trim() ?: ""
            val adm = cell(admIdx)
            val name = cell(fullIdx).ifBlank {
                listOf(cell(firstIdx), cell(middleIdx), cell(lastIdx)).filter { it.isNotBlank() }.joinToString(" ")
            }
            if (adm.isBlank() && name.isBlank()) return@map ""
            listOfNotNull(
                adm.ifBlank { null },
                name.ifBlank { null },
                cell(sexIdx).ifBlank { null },
                cell(phoneIdx).ifBlank { null }
            ).joinToString(", ")
        }.filter { it.isNotBlank() }
    }

    private fun readWorkbook(file: File): List<List<String>>? = try {
        file.inputStream().use { inp ->
            WorkbookFactory.create(inp).use { wb ->
                val sheet = wb.getSheetAt(0)
                sheet.map { row ->
                    row.map { c ->
                        when (c.cellType) {
                            CellType.STRING -> c.stringCellValue.trim()
                            CellType.NUMERIC -> {
                                val v = c.numericCellValue
                                if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()
                            }
                            CellType.BOOLEAN -> c.booleanCellValue.toString()
                            else -> ""
                        }
                    }
                }
            }
        }
    } catch (e: Exception) { null }

    private fun readCsv(file: File): List<List<String>> =
        file.readText(charset("UTF-8")).removePrefix("\uFEFF").lines()
            .filter { it.isNotBlank() }
            .map { splitDelimited(it) }

    private fun splitDelimited(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') { sb.append('"'); i++ } else inQuotes = false
                } else sb.append(ch)
            } else when (ch) {
                '"' -> inQuotes = true
                ',', ';', '\t' -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(ch)
            }
            i++
        }
        out.add(sb.toString())
        return out.map { it.trim() }
    }

    // ── 2. template: ready-to-fill starter sheet ────────────────────────────

    fun writeTemplate(file: File) {
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("Students")
            val hRow = sheet.createRow(0)
            listOf("Admission No", "Name", "Sex (M/F)", "Guardian Phone").forEachIndexed { i, h -> hRow.createCell(i).setCellValue(h) }
            listOf(listOf("TRC1127", "ABARUHANGA EDMON", "M", "0772 123 456"),
                   listOf("TRC1154", "AINOMUGISHA RONAH", "F", "0782 456 789")).forEachIndexed { i, ex ->
                val row = sheet.createRow(i + 1)
                ex.forEachIndexed { j, v -> row.createCell(j).setCellValue(v) }
            }
            listOf(0, 1, 2, 3).forEach { sheet.setColumnWidth(it, 18 * 256) }
            file.outputStream().use { wb.write(it) }
        }
    }

    // ── 3. export: student register → .xlsx ─────────────────────────────────

    data class ExportRow(
        val adm: String, val first: String, val middle: String, val last: String,
        val sex: String, val className: String, val phone: String, val status: String
    )

    fun writeStudentsWorkbook(rows: List<ExportRow>, file: File) {
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("Students")
            sheet.createFreezePane(0, 1)
            val headers = listOf("Admission No", "First Name", "Middle Name", "Last Name", "Sex", "Class", "Guardian Phone", "Status")
            val hRow = sheet.createRow(0)
            headers.forEachIndexed { i, h -> hRow.createCell(i).setCellValue(h) }
            rows.forEachIndexed { i, r ->
                val row = sheet.createRow(i + 1)
                listOf(r.adm, r.first, r.middle, r.last, r.sex, r.className, r.phone, r.status)
                    .forEachIndexed { j, v -> row.createCell(j).setCellValue(v) }
            }
            headers.indices.forEach { sheet.autoSizeColumn(it) }
            file.outputStream().use { wb.write(it) }
        }
    }
}
