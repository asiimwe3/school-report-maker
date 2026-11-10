package com.derycode.srs.core

import com.derycode.srs.core.model.AuditEntry
import com.derycode.srs.core.model.School
import com.derycode.srs.core.model.SchoolData
import com.derycode.srs.core.model.Student
import com.derycode.srs.core.report.DocxReport
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class PrintoutsTest {

    private val student = Student(id = "s1", admissionNo = "1042", firstName = "Esther", lastName = "Atuhaire")
    private val data = SchoolData(
        school = School(name = "Kyenjojo High School", phone = "0772000111"),
        students = listOf(student),
        auditLog = listOf(
            AuditEntry(id = "a1", action = "MARK_ENTERED", entity = "Mark", entityId = "s1|math|t1|a1",
                newValue = "47", timestamp = 1_700_000_000_000, device = "console")
        )
    )
    private fun tmp(name: String) = Files.createTempFile(name, ".docx")

    @Test fun `statement of fees prints the class and the totals`() {
        val f = DocxReport.writeFeeStatement(tmp("fees"), data, "Senior 4 West", "Term 2 2026", "UGX",
            listOf(listOf("Esther Atuhaire", "250000", "100000", "150000", "Due")),
            listOf("250000", "100000", "150000"))
        val bytes = Files.readAllBytes(f)
        assertTrue(bytes.size > 800, "a real document is written")
        assertTrue(bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte(), "it is a real docx (zip)")
    }

    @Test fun `an official receipt prints the amount and the balance`() {
        val f = DocxReport.writeFeeReceipt(tmp("receipt"), data, student, "Senior 4 West", "Term 2 2026",
            "RCP-000123", 100000.0, "MTN MoMo", "MP2401", 250000.0, 100000.0, "UGX", "Bursar")
        val bytes = Files.readAllBytes(f)
        assertTrue(bytes.size > 800)
        assertTrue(bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte(), "it is a real docx (zip)")
    }

    @Test fun `audit log exports as csv with a header and the row`() {
        val f = Files.createTempFile("audit", ".csv")
        DocxReport.writeAuditCsv(f, data)
        val text = Files.readString(f)
        assertTrue(text.startsWith("timestamp,date,action,entity"), "header first")
        assertTrue(text.contains("MARK_ENTERED"), "the entry is there")
        assertTrue(text.contains("console"), "device column filled")
    }
}
