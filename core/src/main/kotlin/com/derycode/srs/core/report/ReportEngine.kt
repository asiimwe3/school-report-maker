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

        val subjectsById = data.subjects.associateBy { it.id }
        // Follow the result's own order (compulsory subjects first, then electives)
        // instead of the order the subjects happen to sit in school setup.
        val subjectRows = result.subjectResults.mapNotNull { r -> subjectsById[r.subjectId]?.let { s -> s to r } }
            .joinToString("") { (s, r) ->
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

        // Compulsory subjects first, then the electives — the order completed in
        // ResultEngine.classResults, kept by every renderer.
        val subjectsInOrder = result.subjectResults.mapNotNull { r -> data.subjects.firstOrNull { it.id == r.subjectId }?.let { it to r } }
        val rows = subjectsInOrder.joinToString("") { (subj, r) ->
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

        // The grade next to the average score, read from the scheme the result was computed with.
        val averageGrade = data.gradingSchemes.firstOrNull { it.id == result.schemeId }
            ?.boundaries
            ?.firstOrNull { result.averagePercent >= it.minScore && result.averagePercent <= it.maxScore + 0.999 }
            ?.grade ?: ""

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

        val blue = "#0B5CAD"
        val blueDark = "#0A4C8F"
        val blueSoft = "#DCEBFA"
        val blueLine = "#7FB0E0"
        val css = """
            @page { size: A4 portrait; margin: 7mm; }
            * { box-sizing: border-box; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
            body { margin: 0; font-family: "Segoe UI", Arial, Helvetica, sans-serif; color: $blueDark; }
            .card { width: 100%; border: 2px solid $blue; }
            .head { display: flex; align-items: center; gap: 10px; padding: 8px 12px 6px 12px; }
            .logo { width: 84px; height: 84px; display: flex; align-items: center; justify-content: center; font-size: 40px; }
            .logo img { width: 80px; height: 80px; object-fit: contain; }
            .school { flex: 1; text-align: center; }
            .school h1 { margin: 0; font-size: 26px; font-weight: 800; letter-spacing: .4px; text-transform: uppercase; color: $blue; line-height: 1.05; }
            .school .type { font-size: 17px; font-weight: 800; text-transform: uppercase; color: $blue; margin-top: 1px; }
            .school .motto { font-size: 12.5px; font-style: italic; color: $blueDark; margin-top: 3px; }
            .school .contact { font-size: 10px; color: $blueDark; margin-top: 3px; line-height: 1.45; }
            .termbox { width: 150px; text-align: center; border-left: 2px solid $blue; padding: 4px 0 4px 10px; color: $blue; }
            .termbox .t { font-size: 15px; font-weight: 800; }
            .termbox .r { font-size: 15px; font-weight: 800; }
            .termbox .y { display: inline-block; margin-top: 5px; background: $blue; color: #fff; font-size: 17px; font-weight: 800; padding: 3px 22px; border-radius: 6px; }
            table { width: 100%; border-collapse: collapse; }
            .bar { background: $blue; color: #fff; font-size: 11.5px; font-weight: 800; letter-spacing: .8px; padding: 5px 12px; text-transform: uppercase; }
            .bar.center { text-align: center; }
            .infowrap { display: flex; gap: 14px; padding: 8px 14px 8px 14px; border-bottom: 1px solid $blueLine; }
            .photo { width: 84px; height: 98px; border: 1.5px solid $blue; background: $blueSoft; display: flex; align-items: center; justify-content: center; overflow: hidden; }
            .photo img { width: 100%; height: 100%; object-fit: cover; }
            .photo svg { width: 62px; height: 62px; fill: $blue; }
            .fields { flex: 1; }
            .fields .row { display: flex; align-items: flex-end; font-size: 11px; margin-top: 5px; }
            .fields .k { width: 112px; font-weight: 800; text-transform: uppercase; color: $blue; letter-spacing: .3px; }
            .fields .c { width: 14px; font-weight: 800; }
            .fields .v { flex: 1; border-bottom: 1px solid $blue; padding: 0 4px 1px 4px; min-height: 15px; font-weight: 700; color: #0b2a4a; }
            .marks { margin-top: 0; }
            .marks th, .marks td { border: 1px solid $blueLine; padding: 3px 4px; font-size: 9.6px; text-align: center; color: #0b2a4a; }
            .marks th { background: $blueSoft; color: $blue; font-size: 8.4px; font-weight: 800; letter-spacing: .3px; line-height: 1.2; padding: 6px 3px; }
            .marks td { height: 20px; }
            .marks td.subj { text-align: left; font-weight: 700; color: $blue; font-size: 9.2px; }
            .marks td.cmt { text-align: left; font-size: 8.6px; }
            .marks td.total, .marks td.grade { font-weight: 800; }
            .avgrow td { background: $blueSoft; font-weight: 800; color: $blue; }
            .two { display: flex; gap: 10px; padding: 8px 10px 8px 10px; }
            .box { flex: 1; border: 1.5px solid $blue; }
            .box .label { background: $blueSoft; color: $blue; font-size: 10.5px; font-weight: 800; padding: 4px 8px; border-bottom: 1px solid $blueLine; }
            .box .label b { color: #0b2a4a; margin-left: 4px; }
            .box .body { padding: 6px 8px; font-size: 10px; min-height: 48px; line-height: 1.45; color: #0b2a4a; }
            .box .sigline { padding: 2px 8px 8px 8px; font-size: 10px; font-weight: 800; color: $blue; display: flex; align-items: flex-end; gap: 6px; }
            .box .sigline span { flex: 1; border-bottom: 1px solid $blue; height: 14px; }
            .dates { margin: 8px 10px 8px 10px; border: 1.5px solid $blue; }
            .dates td { border: 1px solid $blueLine; text-align: center; font-size: 10.5px; padding: 5px 6px; color: #0b2a4a; }
            .dates td.h { background: $blueSoft; color: $blue; font-weight: 800; }
            .dates td.v { height: 28px; font-weight: 700; }
            .sigs { display: flex; gap: 22px; align-items: flex-end; padding: 8px 14px 6px 14px; }
            .sig { flex: 1; text-align: center; font-size: 10px; font-weight: 800; color: $blue; }
            .sig .line { border-bottom: 1px solid $blue; height: 24px; margin-bottom: 3px; }
            .sig small { display: block; color: #0b2a4a; font-weight: 600; font-size: 9px; }
            .stampwrap { width: 120px; text-align: center; font-size: 9px; font-weight: 800; color: $blue; }
            .stamp { width: 70px; height: 70px; margin: 0 auto 3px auto; border: 1.2px dashed $blue; border-radius: 50%; }
            .foot { background: $blue; color: #fff; text-align: center; font-size: 11.5px; font-style: italic; padding: 6px 4px; margin-top: 6px; }
            .key { font-size: 8px; color: #46688c; margin: 4px 10px 3px 10px; text-align: center; }
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
              <div class="motto">${if (school.motto.isBlank()) "" else escape(school.motto)}</div>
              <div class="contact">${escape(contact)}</div>
            </div>
            <div class="termbox">
              <div class="t">TERM ${term?.number ?: ""}</div>
              <div class="r">SUMMARY REPORT</div>
              <div class="y">${termPair?.first?.year ?: ""}</div>
            </div>
          </div>

          <div class="bar">Student information</div>
          <div class="infowrap">
            <div class="photo">${if (!student.photoPath.isNullOrBlank()) """<img src="file://${student.photoPath}"/>""" else """<svg viewBox="0 0 24 24"><circle cx="12" cy="7.5" r="4.6"/><path d="M2.5 23c0-5.4 4.2-9 9.5-9s9.5 3.6 9.5 9z"/></svg>"""}</div>
            <div class="fields">
              <div class="row"><div class="k">Student name</div><div class="c">:</div><div class="v">${escape(student.fullName)}</div></div>
              <div class="row"><div class="k">Admission no.</div><div class="c">:</div><div class="v">${escape(student.admissionNo)}</div></div>
              <div class="row"><div class="k">Class</div><div class="c">:</div><div class="v">${escape(cls?.name ?: studentClass(data, student.id))}</div></div>
              <div class="row"><div class="k">Stream</div><div class="c">:</div><div class="v">${escape(cls?.stream ?: "")}</div></div>
              <div class="row"><div class="k">Result</div><div class="c">:</div><div class="v">${escape(overall)}</div></div>
            </div>
          </div>

          <table class="marks">
            <thead>
              <tr>
                <th style="width:21%">SUBJECT</th><th>A1</th><th>A2</th><th>A3</th>
                <th>FRNTV<br/>SCORE<br/>(20%)</th><th>EXAM<br/>SCORE<br/>(80%)</th>
                <th>TOTAL<br/>SCORE<br/>(100%)</th><th>GRADE</th>
                <th style="width:22%">TEACHER COMMENT</th><th>TEACHER<br/>INITIALS</th>
              </tr>
            </thead>
            <tbody>
              $rows
              <tr class="avgrow">
                <td class="subj" colspan="6" style="text-align:center">AVERAGE SCORE</td>
                <td>${fmt(result.averagePercent)}</td><td>${averageGrade}</td><td></td><td></td>
              </tr>
            </tbody>
          </table>

          <div class="bar center" style="margin-top:8px">Comments</div>
          <div class="two">
            <div class="box">
              <div class="label">Class Teacher:<b>${escape(classTeacher?.name ?: "")}</b></div>
              <div class="body">${if (classComment.isBlank()) "&nbsp;" else escape(classComment)}</div>
              <div class="sigline">Signature:<span></span></div>
            </div>
            <div class="box">
              <div class="label">Head Teacher Remarks:<b>${escape(head?.name ?: school.headTeacher)}</b></div>
              <div class="body">${if (headComment.isBlank()) "&nbsp;" else escape(headComment)}</div>
              <div class="sigline">Signature:<span></span></div>
            </div>
          </div>

          <div class="bar center">Fees and term dates</div>
          <table class="dates">
            <tr><td class="h">Current Term Closing Date</td><td class="h">Next Term Opening Date</td></tr>
            <tr><td class="v">${escape(term?.endDate ?: "")}</td><td class="v">${escape(term?.nextTermBegins ?: "")}</td></tr>
          </table>

          <div class="sigs">
            <div class="sig"><div class="line"></div>Class Teacher</div>
            <div class="sig"><div class="line"></div>Head Teacher</div>
            <div class="stampwrap"><div class="stamp"></div>School Stamp</div>
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
