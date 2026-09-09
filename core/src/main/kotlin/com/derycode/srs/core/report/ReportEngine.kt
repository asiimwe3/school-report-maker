package com.derycode.srs.core.report

import com.derycode.srs.core.model.School
import com.derycode.srs.core.model.SchoolData
import com.derycode.srs.core.model.Student
import com.derycode.srs.core.model.TemplateLayout
import com.derycode.srs.core.model.TermResult

/**
 * HTML report engine (Sections 25–29). Templates are data-driven; the four
 * starter layouts below are just CSS/structure presets a school can pick and
 * customize. Print to PDF from any browser — no internet required.
 */
object ReportEngine {

    /** One student's full report card as standalone HTML. */
    fun studentReport(
        data: SchoolData,
        result: TermResult,
        layout: TemplateLayout = TemplateLayout.CLASSIC
    ): String {
        val student = data.students.firstOrNull { it.id == result.studentId }
            ?: return "<html><body><p>Student not found.</p></body></html>"
        val school: School = data.school
        val term = data.academicYears.asSequence()
            .flatMap { y -> y.terms.asSequence().map { t -> y to t } }
            .firstOrNull { it.second.id == result.termId }

        val subjectRows = data.subjects.filter { s -> result.subjectResults.any { it.subjectId == s.id } }
            .joinToString("") { s ->
                val r = result.subjectResults.first { it.subjectId == s.id }
                """
                <tr>
                    <td class="name">${escape(s.name)}</td>
                    <td>${r.total}</td>
                    <td>${fmt(r.percentage)}%</td>
                    <td class="grade">${r.grade}</td>
                    <td>${if (r.points > 0) r.points.toString() else ""}</td>
                    <td>${if (r.remark.isBlank()) "&nbsp;" else escape(r.remark)}</td>
                </tr>
                """.trimIndent()
            }

        val overall = buildString {
            result.aggregate?.let { append("Aggregate $it — ") }
            result.division?.let { append(it) }
            result.uceResult?.let { append(it) }
            result.uacePoints?.let { append("Points $it") }
            if (isEmpty()) append("Average ${fmt(result.averagePercent)}%")
        }

        val classComments = data.comments
            .filter { it.studentId == student.id && it.termId == result.termId }
            .joinToString("<br/>") { escape(it.text) }

        val head = data.teachers.firstOrNull { it.role == com.derycode.srs.core.model.Role.HEAD_TEACHER }
        val classTeacher = data.classes.firstOrNull { c ->
            data.enrollments.any { e -> e.studentId == student.id }
        }?.let { cls -> data.teachers.firstOrNull { it.id == cls.classTeacherId } }

        val css = cssFor(layout, school)
        val title = "${school.name} — ${student.fullName}"
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
        <meta charset="UTF-8"/>
        <title>${escape(title)}</title>
        <style>$css</style>
        </head>
        <body>
        <div class="card">
            <div class="header">
                <div class="logo">${if (school.logoPath != null) """<img src="file://${school.logoPath}"/>""" else "🎓"}</div>
                <div class="school">
                    <h1>${escape(school.name)}</h1>
                    <div class="motto">${if (school.motto.isBlank()) "" else "“${escape(school.motto)}”"}</div>
                    <div class="addr">${escape(school.address)}${if (school.phone.isNotBlank()) " · Tel: ${escape(school.phone)}" else ""}</div>
                </div>
                <div class="stamp">${if (school.stampPath != null) """<img src="file://${school.stampPath}"/>""" else ""}</div>
            </div>

            <div class="report-title">STUDENT TERMINAL REPORT — ${term?.second?.let { "Term ${it.number} ${term.first.year}" } ?: ""}</div>

            <table class="student-info">
                <tr><td><b>Name:</b> ${escape(student.fullName)}</td>
                    <td><b>Adm No:</b> ${escape(student.admissionNo)}</td>
                    <td><b>Sex:</b> ${escape(student.sex)}</td></tr>
                <tr><td><b>Class:</b> ${escape(studentClass(data, student.id))}</td>
                    <td><b>Position:</b> ${result.classPosition}</td>
                    <td><b>Guardian:</b> ${escape(student.guardianName)}</td></tr>
            </table>

            <table class="marks">
                <thead>
                <tr><th>Subject</th><th>Total</th><th>%</th><th>Grade</th><th>Points</th><th>Remark</th></tr>
                </thead>
                <tbody>$subjectRows</tbody>
            </table>

            <div class="summary">
                <div><b>Overall:</b> $overall</div>
                <div><b>Average:</b> ${fmt(result.averagePercent)}%</div>
            </div>

            <div class="comments">
                <div class="label">Class Teacher's Comments</div>
                <div class="body">${if (classComments.isBlank()) "&nbsp;" else classComments}</div>
            </div>
            <div class="comments">
                <div class="label">Head Teacher's Comments</div>
                <div class="body">&nbsp;</div>
            </div>

            <div class="signatures">
                <div class="sig"><div class="line"></div>Class Teacher<br/><b>${classTeacher?.let { escape(it.name) } ?: ""}</b></div>
                <div class="sig"><div class="line"></div>Head Teacher<br/><b>${head?.let { escape(it.name) } ?: escape(school.headTeacher)}</b></div>
                <div class="sig"><div class="line"></div>Date</div>
            </div>

            <div class="grading-key">
                ${gradingKey(data, result)}
            </div>
        </div>
        <script>window.onload = () => { if (location.search.includes("print=1")) window.print(); };</script>
        </body>
        </html>
        """.trimIndent()
    }

    /** Whole class into one printable document (batch — Section 28). */
    fun classReports(data: SchoolData, results: List<TermResult>, layout: TemplateLayout): String =
        results.joinToString("\n<div class='page-break'></div>\n") { studentReport(data, it, layout) }

    /** Grading key rendered from the scheme the report was computed with (Section 27). */
    private fun gradingKey(data: SchoolData, result: TermResult): String {
        val scheme = data.gradingSchemes.firstOrNull { it.id == result.schemeId }
            ?: return "Printed by DeryCode School Report Maker"
        val key = scheme.boundaries.sortedByDescending { it.minScore }
            .joinToString(" · ") { "${it.grade} ${it.minScore.toInt()}–${it.maxScore.toInt()}%" }
        return "Grading: $key — Printed by DeryCode School Report Maker"
    }

    private fun studentClass(data: SchoolData, studentId: String): String {
        val e = data.enrollments.lastOrNull { it.studentId == studentId } ?: return "—"
        val c = data.classes.firstOrNull { it.id == e.classId } ?: return "—"
        return if (c.stream.isBlank()) c.name else "${c.name} ${c.stream}"
    }

    private fun cssFor(layout: TemplateLayout, school: School): String {
        val accent = school.colorPrimary
        val ink = school.colorSecondary
        return """
        @page { size: A4; margin: 12mm; }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Segoe UI', Arial, sans-serif; }
        body { background: #f3f4f6; padding: 16px; }
        .card { max-width: 800px; margin: 0 auto; background: #fff; padding: 26px 30px;
                border: ${if (layout == TemplateLayout.PLAIN) "1px solid #ccc" else "3px double $accent"};
                border-radius: ${if (layout == TemplateLayout.MODERN) "14px" else "2px"}; }
        .header { display: flex; align-items: center; justify-content: space-between; border-bottom: 2px solid $accent; padding-bottom: 12px; }
        .logo img { height: ${if (layout == TemplateLayout.COMPACT) "48px" else "70px"}; }
        .logo { font-size: 40px; }
        .school { text-align: center; flex: 1; }
        .school h1 { font-size: 21px; color: $ink; letter-spacing: .5px; }
        .motto { font-style: italic; color: #555; font-size: 12px; margin: 2px 0; }
        .addr { font-size: 11px; color: #777; }
        .stamp img { height: 60px; }
        .report-title { text-align: center; font-weight: bold; font-size: 13px; letter-spacing: 2px; color: $accent; margin: 14px 0 8px; }
        .student-info { width: 100%; border-collapse: collapse; margin-bottom: 10px; font-size: 12px; }
        .student-info td { padding: 3px 6px; border: 1px solid #ddd; background: #fafafa; }
        .marks { width: 100%; border-collapse: collapse; font-size: 12px; }
        .marks th { background: $accent; color: #fff; padding: 6px; text-align: left; font-size: 11.5px; }
        .marks td { padding: 5px 6px; border: 1px solid #ddd; }
        .marks tr:nth-child(even) td { background: #f7f8fa; }
        .grade { font-weight: bold; color: $accent; }
        .summary { display: flex; gap: 30px; font-size: 13px; margin: 10px 0; font-weight: 600; }
        .comments { margin: 8px 0; }
        .comments .label { font-size: 11px; font-weight: bold; color: #444; margin-bottom: 2px; }
        .comments .body { border: 1px solid #ccc; min-height: 46px; padding: 6px 8px; font-size: 12px; }
        .signatures { display: flex; justify-content: space-between; margin-top: 26px; font-size: 11px; color: #333; text-align: center; }
        .sig .line { border-bottom: 1px solid #444; width: 170px; height: 34px; margin-bottom: 3px; }
        .grading-key { margin-top: 16px; font-size: 9px; color: #999; text-align: center; }
        .page-break { page-break-after: always; }
        @media print { body { background: #fff; padding: 0; } .card { border: none; max-width: none; } }
        """.trimIndent()
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun fmt(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else String.format("%.1f", d)
}
