package com.derycode.srs.core.report

import com.derycode.srs.core.model.CommentType
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
        if (layout == TemplateLayout.SUMMARY) return summaryReport(data, result)
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
            .filter { it.studentId == student.id && it.termId == result.termId && it.type == CommentType.CLASS_TEACHER }
            .joinToString("<br/>") { escape(it.text) }
        val headComments = data.comments
            .filter { it.studentId == student.id && it.termId == result.termId && it.type == CommentType.HEAD_TEACHER }
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
                <div class="body">${if (headComments.isBlank()) "&nbsp;" else headComments}</div>
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

    /**
     * The school's summary card: formative assessments (A1–A3), the 20% / 80%
     * split, totals, per-subject teacher comment and initials, both sets of
     * comments, fees and term dates, three signature blocks and the school stamp.
     */
    fun summaryReport(data: SchoolData, result: TermResult): String {
        val student = data.students.firstOrNull { it.id == result.studentId }
            ?: return "<html><body><p>Student not found.</p></body></html>"
        val school = data.school
        val termPair = data.academicYears.asSequence()
            .flatMap { y -> y.terms.asSequence().map { t -> y to t } }
            .firstOrNull { it.second.id == result.termId }
        val term = termPair?.second
        val cls = data.classes.firstOrNull { c ->
            data.enrollments.any { e -> e.studentId == student.id && e.classId == c.id }
        }

        fun initialsOf(teacherName: String) = teacherName.split(' ', '.', '-')
            .filter { it.isNotBlank() }.map { it.first().uppercaseChar() }.take(3).joinToString("")

        fun teacherFor(subjectId: String): String {
            val t = data.teachers.firstOrNull { te ->
                data.assignments.any { a -> a.teacherId == te.id && a.subjectId == subjectId && a.classId == cls?.id }
            }
            return t?.let { initialsOf(it.name) } ?: ""
        }

        fun subjectComment(subjectId: String): String =
            data.comments.firstOrNull {
                it.studentId == result.studentId && it.termId == result.termId &&
                    it.subjectId == subjectId && it.type == CommentType.SUBJECT
            }?.text ?: ""

        val subjectsInOrder = data.subjects.filter { s -> result.subjectResults.any { it.subjectId == s.id } }
        val rows = subjectsInOrder.joinToString("") { subj ->
            val r = result.subjectResults.first { it.subjectId == subj.id }
            fun dash(v: Double) = if (v < 0) "" else fmt(v)
            val a = r.assessments
            """
            <tr>
              <td class="subj">${escape(subj.name.uppercase())}</td>
              <td>${a.getOrElse(0) { -1.0 }.let { if (it < 0) "" else fmt(it) }}</td>
              <td>${a.getOrElse(1) { -1.0 }.let { if (it < 0) "" else fmt(it) }}</td>
              <td>${a.getOrElse(2) { -1.0 }.let { if (it < 0) "" else fmt(it) }}</td>
              <td>${dash(r.formativePercent)}</td>
              <td>${dash(r.examPercent)}</td>
              <td class="total">${fmt(r.percentage)}</td>
              <td class="grade">${r.grade}</td>
              <td class="cmt">${escape(subjectComment(subj.id))}</td>
              <td>${teacherFor(subj.id)}</td>
            </tr>
            """.trimIndent()
        }

        val overall = buildString {
            result.aggregate?.let { append("Aggregate $it") }
            result.division?.let { if (isNotEmpty()) append(" — "); append(it) }
            result.uceResult?.let { if (isNotEmpty()) append(" — "); append(it) }
            result.uacePoints?.let { if (isNotEmpty()) append(" — "); append("Points $it") }
            if (isEmpty()) append("Average ${fmt(result.averagePercent)}%")
        }

        val classComment = data.comments
            .filter { it.studentId == student.id && it.termId == result.termId && it.type == CommentType.CLASS_TEACHER }
            .joinToString(" ") { it.text }
        val headComment = data.comments
            .filter { it.studentId == student.id && it.termId == result.termId && it.type == CommentType.HEAD_TEACHER }
            .joinToString(" ") { it.text }
        val head = data.teachers.firstOrNull { it.role == com.derycode.srs.core.model.Role.HEAD_TEACHER }
        val classTeacher = cls?.classTeacherId?.let { id -> data.teachers.firstOrNull { it.id == id } }

        val css = """
            @page { size: A4 portrait; margin: 9mm; }
            * { box-sizing: border-box; }
            body { margin: 0; font-family: "Times New Roman", Georgia, serif; color: #111; }
            .card { width: 100%; }
            .head { display: flex; align-items: flex-start; gap: 10px; border-bottom: 2.5px solid #111; padding-bottom: 6px; }
            .logo { width: 74px; height: 74px; display: flex; align-items: center; justify-content: center; font-size: 34px; }
            .logo img { width: 70px; height: 70px; object-fit: contain; }
            .school { flex: 1; text-align: center; }
            .school h1 { margin: 0; font-size: 25px; letter-spacing: .6px; text-transform: uppercase; }
            .school .type { font-size: 11px; letter-spacing: 2.4px; text-transform: uppercase; }
            .school .motto { font-size: 10.5px; font-style: italic; color: #333; margin-top: 1px; }
            .school .contact { font-size: 9.5px; color: #222; margin-top: 3px; line-height: 1.35; }
            .termbox { width: 108px; text-align: center; border: 1.5px solid #111; padding: 5px 4px; font-size: 10px; }
            .termbox b { display: block; font-size: 13px; letter-spacing: .5px; }
            table { width: 100%; border-collapse: collapse; }
            .info { margin-top: 8px; border: 1.5px solid #111; }
            .info td { border: 1px solid #999; padding: 3px 6px; font-size: 10.5px; }
            .bar { background: #111; color: #fff; font-size: 10px; letter-spacing: 1.6px; padding: 3px 8px; text-transform: uppercase; }
            .marks { margin-top: 8px; }
            .marks th, .marks td { border: 1px solid #444; padding: 3px 4px; font-size: 9.5px; text-align: center; }
            .marks th { background: #111; color: #fff; font-size: 8px; letter-spacing: .4px; line-height: 1.15; }
            .marks td.subj { text-align: left; font-weight: bold; }
            .marks td.cmt { text-align: left; font-size: 8.6px; }
            .marks td.total { font-weight: bold; }
            .marks td.grade { font-weight: bold; }
            .avgrow td { background: #f0f0f0; font-weight: bold; }
            .two { display: flex; gap: 8px; margin-top: 8px; }
            .box { flex: 1; border: 1.5px solid #111; }
            .box .label { font-size: 9px; font-weight: bold; letter-spacing: 1.2px; padding: 3px 6px; border-bottom: 1px solid #999; text-transform: uppercase; }
            .box .body { padding: 5px 6px; font-size: 9.5px; min-height: 52px; line-height: 1.4; }
            .sigs { display: flex; gap: 8px; margin-top: 10px; }
            .sig { flex: 1; text-align: center; font-size: 9px; }
            .sig .line { border-bottom: 1px solid #111; height: 22px; margin-bottom: 3px; }
            .stamp { width: 84px; height: 84px; border: 1.5px dashed #777; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 8px; color: #777; text-align: center; }
            .foot { text-align: center; font-size: 9.5px; font-style: italic; margin-top: 8px; border-top: 1px solid #111; padding-top: 4px; }
            .key { font-size: 8.5px; color: #333; margin-top: 4px; }
        """.trimIndent()

        val contact = listOfNotNull(
            school.phone.takeIf { it.isNotBlank() }?.let { "Tel: $it" },
            school.phone2.takeIf { it.isNotBlank() },
            school.email.takeIf { it.isNotBlank() },
            school.address.takeIf { it.isNotBlank() },
            school.district.takeIf { it.isNotBlank() }
        ).joinToString(" · ")

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head><meta charset="UTF-8"/><title>${escape(school.name)} — ${escape(student.fullName)}</title>
        <style>$css</style></head>
        <body>
        <div class="card">
          <div class="head">
            <div class="logo">${if (school.logoPath != null) """<img src="file://${school.logoPath}"/>""" else "🎓"}</div>
            <div class="school">
              <h1>${escape(school.name)}</h1>
              <div class="type">${escape(if (school.schoolType.isBlank()) "Secondary School" else school.schoolType)}</div>
              <div class="motto">${if (school.motto.isBlank()) "" else "“${escape(school.motto)}”"}</div>
              <div class="contact">${escape(contact)}</div>
            </div>
            <div class="termbox">
              <b>TERM ${term?.number ?: ""}</b>
              <div>TERMLY REPORT CARD</div>
              <div><b>${termPair?.first?.year ?: ""}</b></div>
            </div>
          </div>

          <div class="bar" style="margin-top:8px">Student information</div>
          <table class="info">
            <tr>
              <td><b>STUDENT NAME:</b> ${escape(student.fullName)}</td>
              <td><b>ADMISSION NO.:</b> ${escape(student.admissionNo)}</td>
            </tr>
            <tr>
              <td><b>CLASS:</b> ${escape(cls?.name ?: studentClass(data, student.id))}</td>
              <td><b>STREAM:</b> ${escape(cls?.stream ?: "")}</td>
            </tr>
            <tr>
              <td><b>RESULT:</b> ${escape(overall)}</td>
              <td><b>POSITION:</b> ${result.classPosition} of ${data.enrollments.count { it.classId == cls?.id }.coerceAtLeast(result.classPosition)}</td>
            </tr>
          </table>

          <table class="marks">
            <thead>
              <tr>
                <th style="width:23%">SUBJECT</th><th>A1</th><th>A2</th><th>A3</th>
                <th>FRNTV<br/>SCORE<br/>(20%)</th><th>EXAM<br/>SCORE<br/>(80%)</th>
                <th>TOTAL<br/>SCORE<br/>(100%)</th><th>GRADE</th>
                <th style="width:22%">TEACHER COMMENT</th><th>TEACHER<br/>INITIALS</th>
              </tr>
            </thead>
            <tbody>
              $rows
              <tr class="avgrow">
                <td class="subj">AVERAGE SCORE</td><td></td><td></td><td></td><td></td><td></td>
                <td>${fmt(result.averagePercent)}</td><td></td><td></td><td></td>
              </tr>
            </tbody>
          </table>

          <div class="bar" style="margin-top:8px">Comments</div>
          <div class="two">
            <div class="box">
              <div class="label">Class Teacher</div>
              <div class="body">${if (classComment.isBlank()) "&nbsp;" else escape(classComment)}</div>
              <div class="body" style="min-height:0;padding-top:0">Signature: ______________</div>
            </div>
            <div class="box">
              <div class="label">Head Teacher Remarks</div>
              <div class="body">${if (headComment.isBlank()) "&nbsp;" else escape(headComment)}</div>
              <div class="body" style="min-height:0;padding-top:0">Signature: ______________</div>
            </div>
          </div>

          <div class="bar" style="margin-top:8px">Fees and term dates</div>
          <table class="info">
            <tr>
              <td><b>CURRENT TERM CLOSING DATE:</b> ${escape(term?.endDate ?: "")}</td>
              <td><b>NEXT TERM OPENING DATE:</b> ${escape(term?.nextTermBegins ?: "")}</td>
            </tr>
          </table>

          <div class="sigs">
            <div class="sig"><div class="line"></div>Class Teacher<br/><b>${escape(classTeacher?.name ?: "")}</b></div>
            <div class="sig"><div class="line"></div>Head Teacher<br/><b>${escape(head?.name ?: school.headTeacher)}</b></div>
            <div class="stamp">SCHOOL<br/>STAMP</div>
          </div>

          <div class="foot">${escape(school.motto.ifBlank { "Together for a Brighter Future" })}</div>
          <div class="key">${gradingKey(data, result)}</div>
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
        @page { size: A4 portrait; margin: 10mm; }
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
        /* ── A4 fit: never spill onto a second sheet ── */
        .marks, .student-info { page-break-inside: avoid; }
        .marks tr, .student-info tr { page-break-inside: avoid; page-break-after: auto; }
        .header, .summary, .comments, .signatures { page-break-inside: avoid; }
        @media print {
            html, body { width: 210mm; height: 297mm; background: #fff; padding: 0; margin: 0; }
            body { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
            .card { border: none; max-width: none; width: 100%; padding: 0; border-radius: 0;
                    box-shadow: none; page-break-after: avoid; }
            .page-break:last-child { page-break-after: auto; }
        }
        """.trimIndent()
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun fmt(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else String.format("%.1f", d)
}
