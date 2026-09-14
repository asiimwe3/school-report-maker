package com.derycode.srs.core.importer

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.nio.file.Path

/**
 * Bulk class-list import from Excel (.xlsx) — v2.2.3.
 *
 * Convention: one SHEET per class. The sheet's name should match (or closely
 * resemble) an existing class in the school, e.g. "Primary 1", "P1", "Senior 2 East".
 * First row of every sheet is a header (ignored — matched loosely by position,
 * not by header text, so schools don't need to type exact column names).
 * Column order: Admission No, First Name, Middle Name, Last Name, Sex, Date of Birth, Guardian Phone.
 * Only Admission No, First Name and Last Name are required — the rest can be left blank.
 */
object ExcelImport {

    data class Row(
        val admissionNo: String,
        val firstName: String,
        val middleName: String,
        val lastName: String,
        val sex: String,
        val dateOfBirth: String,
        val guardianPhone: String
    )

    data class SheetResult(val sheetName: String, val rows: List<Row>)

    /** Normalizes a class label for fuzzy matching: lowercase, strip spaces/punctuation. */
    fun normalize(s: String): String = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    fun readClassLists(path: Path): List<SheetResult> {
        val results = mutableListOf<SheetResult>()
        WorkbookFactory.create(path.toFile()).use { wb ->
            for (i in 0 until wb.numberOfSheets) {
                val sheet = wb.getSheetAt(i)
                val rows = mutableListOf<Row>()
                for (r in 1..sheet.lastRowNum) {   // row 0 assumed header
                    val row = sheet.getRow(r) ?: continue
                    fun cell(c: Int): String {
                        val cl = row.getCell(c) ?: return ""
                        return when (cl.cellType) {
                            CellType.NUMERIC -> {
                                val d = cl.numericCellValue
                                if (d == Math.floor(d)) d.toLong().toString() else d.toString()
                            }
                            CellType.BOOLEAN -> cl.booleanCellValue.toString()
                            CellType.FORMULA -> try { cl.stringCellValue } catch (_: Exception) { "" }
                            else -> cl.toString()
                        }.trim()
                    }
                    val admissionNo = cell(0)
                    val firstName = cell(1)
                    if (admissionNo.isBlank() && firstName.isBlank()) continue   // skip blank rows
                    rows += Row(
                        admissionNo = admissionNo,
                        firstName = firstName,
                        middleName = cell(2),
                        lastName = cell(3),
                        sex = cell(4).ifBlank { "M" }.take(1).uppercase(),
                        dateOfBirth = cell(5),
                        guardianPhone = cell(6)
                    )
                }
                if (rows.isNotEmpty()) results += SheetResult(sheet.sheetName, rows)
            }
        }
        return results
    }
}
