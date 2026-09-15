package com.derycode.srs.admin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExcelBridgeTest {

    @Test
    fun templateRoundTrip() {
        val f = File.createTempFile("tpl", ".xlsx")
        ExcelBridge.writeTemplate(f)
        val res = ExcelBridge.readToBulkText(f)
        assertEquals(null, res.error)
        assertEquals(2, res.count)
        assertTrue(res.text.contains("TRC1127, ABARUHANGA EDMON, M, 0772 123 456"), res.text)
        assertTrue(res.text.contains("TRC1154, AINOMUGISHA RONAH, F"), res.text)
    }

    @Test
    fun splitNameColumnsMapCorrectly() {
        val f = File.createTempFile("names", ".xlsx")
        org.apache.poi.xssf.usermodel.XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("Students")
            val h = sheet.createRow(0)
            listOf("Admission No", "First Name", "Middle Name", "Last Name", "Gender", "Parent Phone").forEachIndexed { i, v -> h.createCell(i).setCellValue(v) }
            val r1 = sheet.createRow(1)
            listOf("1127", "Edmon", "Kato", "Abaruhanga", "Male", "0772111222").forEachIndexed { i, v -> r1.createCell(i).setCellValue(v) }
            val r2 = sheet.createRow(2)
            listOf("1154", "Ronah", "", "Ainomugisha", "F").forEachIndexed { i, v -> r2.createCell(i).setCellValue(v) }
            f.outputStream().use { wb.write(it) }
        }
        val res = ExcelBridge.readToBulkText(f)
        assertEquals(null, res.error)
        assertEquals(2, res.count)
        assertTrue(res.text.lines()[0] == "1127, Edmon Kato Abaruhanga, Male, 0772111222", res.text)
        assertTrue(res.text.lines()[1] == "1154, Ronah Ainomugisha, F", res.text)
    }

    @Test
    fun numericAdmissionCellIsNotBroken() {
        val f = File.createTempFile("num", ".xlsx")
        org.apache.poi.xssf.usermodel.XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("S")
            val r = sheet.createRow(0)
            r.createCell(0).setCellValue(1127.0)   // numeric adm no.
            r.createCell(1).setCellValue("KATO JOHN")
            f.outputStream().use { wb.write(it) }
        }
        val res = ExcelBridge.readToBulkText(f)
        assertEquals(null, res.error)
        assertTrue(res.text.startsWith("1127, KATO JOHN"), res.text)
    }

    @Test
    fun csvWithQuotesAndSemicolons() {
        val f = File.createTempFile("list", ".csv")
        f.writeText("Admission No;Name;Sex\n1127;\"Abaruhanga, Edmon\";M\n1154;Ainomugisha Ronah;F\n")
        val res = ExcelBridge.readToBulkText(f)
        assertEquals(null, res.error)
        assertEquals(2, res.count)
        assertTrue(res.text.contains("1127, Abaruhanga, Edmon, M"), res.text)
    }

    @Test
    fun noHeaderSheetFallsBackToPositional() {
        val f = File.createTempFile("plain", ".csv")
        f.writeText("TRC1127, ABARUHANGA EDMON, M, 0772111222\nTRC1154, AINOMUGISHA RONAH, F\n")
        val res = ExcelBridge.readToBulkText(f)
        assertEquals(null, res.error)
        assertEquals(2, res.count)
        assertTrue(res.text.contains("TRC1127, ABARUHANGA EDMON, M, 0772111222"), res.text)
    }

    @Test
    fun exportWorkbookWritesAllColumns() {
        val f = File.createTempFile("export", ".xlsx")
        ExcelBridge.writeStudentsWorkbook(
            listOf(ExcelBridge.ExportRow("TRC1127", "Edmon", "", "Abaruhanga", "M", "Senior 1 East", "0772111222", "Active")),
            f
        )
        org.apache.poi.ss.usermodel.WorkbookFactory.create(f).use { wb ->
            val row = wb.getSheetAt(0).getRow(1)
            assertEquals("TRC1127", row.getCell(0).stringCellValue)
            assertEquals("Senior 1 East", row.getCell(5).stringCellValue)
            assertEquals("Active", row.getCell(7).stringCellValue)
        }
    }
}
