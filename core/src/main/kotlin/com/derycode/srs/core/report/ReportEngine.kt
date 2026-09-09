package com.derycode.srs.core.report

import com.derycode.srs.core.grading.Grading
import com.derycode.srs.core.grading.round1
import com.derycode.srs.core.model.SchoolData
import com.derycode.srs.core.model.Student

/**
 * Renders report cards as self-contained HTML — the same template output
 * works on desktop (print to PDF) and Android (render/print via WebView).
 */
class ReportEngine(private val data: SchoolData) {

    fun subjectRows(student: Student, term: Int): String {
        val perSubject = data.marks
            .filter { it.studentId == student.id && it.term == term }
            .groupBy { it.subjectId }

        val compulsory = data.subjects.sortedBy { it.name }
        return compulsory.joinToString("") { subject ->
            val entries = perSubject[subject.id].orEmpty()
            val bot = entries.firstOrNull { it.assessment == "BOT" }?.score ?: 0.0
            val mid = entries.firstOrNull { it.assessment == "MID" }?.score ?: 0.0
            val eot = entries.firstOrNull { it.assessment == "EOT" }?.score ?: 0.0
            val total = bot + mid + eot
            val score = if (entries.isEmpty()) 0.0 else total / 3.0
            """
            <tr>
              <td class="sub">${subject.name}</td>
              <td>${fmt(bot)}</td><td>${fmt(mid)}</td><td>${fmt(eot)}</td>
              <td class="tot">${fmt(total)}</td><td class="tot">${fmt(score)}</td>
              <td>${Grading.grade(score)}</td><td>${Grading.remark(score)}</td>
            </tr>
            """.trimIndent()
        }
    }

    fun reportCard(student: Student, term: Int): String {
        val klass = data.classes.firstOrNull { it.id == student.classId }
        val avgs = data.students
            .filter { it.classId == student.classId && it.active }
            .map { Grading.termAverage(data.marks, it.id, term) to it }
        val myAvg = Grading.termAverage(data.marks, student.id, term)
        val position = avgs.sortedByDescending { it.first }.indexOfFirst { it.second.id == student.id } + 1
        val subjectPoints = data.subjects.mapNotNull { s ->
            val entries = data.marks.filter { it.studentId == student.id && it.term == term && it.subjectId == s.id }
            if (entries.isEmpty()) null else Grading.points(entries.sumOf { it.score } / entries.size)
        }
        val aggregate = subjectPoints.sum()
        val division = Grading.division(aggregate)

        return """
        <!DOCTYPE html>
        <html lang="en"><head><meta charset="utf-8">
        <title>Report — ${student.firstName} ${student.lastName}</title>
        <style>
          body { font-family: 'Times New Roman', serif; margin: 24px; color: #111; }
          .head { text-align:center; border-bottom: 3px double #111; padding-bottom: 8px; }
          .head h1 { margin: 0; font-size: 22px; letter-spacing: 1px; }
          .head .motto { font-style: italic; color: #444; }
          .meta { display:flex; justify-content:space-between; margin: 12px 0; font-size: 14px; }
          table { width: 100%; border-collapse: collapse; font-size: 13px; }
          th, td { border: 1px solid #444; padding: 5px 7px; text-align: center; }
          td.sub, th.sub { text-align: left; }
          th { background: #efefef; }
          .tot { font-weight: bold; }
          .summary { margin-top: 12px; font-size: 14px; }
          .sign { margin-top: 40px; display: flex; justify-content: space-between; font-size: 14px; }
          .sign div { width: 40%; border-top: 1px solid #111; text-align: center; padding-top: 4px; }
          @media print { body { margin: 8mm; } }
        </style></head>
        <body>
          <div class="head">
            <h1>${data.school.name}</h1>
            <div>${data.school.address}</div>
            <div class="motto">${data.school.motto}</div>
            <div style="margin-top:6px"><b>TERMINAL REPORT CARD — TERM ${term}</b></div>
          </div>
          <div class="meta">
            <div><b>Name:</b> ${student.firstName} ${student.lastName}<br/>
                 <b>Class:</b> ${klass?.name ?: "-"} ${klass?.stream ?: ""}<br/>
                 <b>Student No:</b> ${student.studentNo}</div>
            <div style="text-align:right"><b>Guardian:</b> ${student.guardianName}<br/>
                 <b>Tel:</b> ${student.guardianPhone}<br/>
                 <b>Enrolment:</b> ${avgs.size}</div>
          </div>
          <table>
            <tr><th class="sub">Subject</th><th>BOT</th><th>MID</th><th>EOT</th>
                <th>Total (300)</th><th>Avg (100)</th><th>Grade</th><th>Remark</th></tr>
            ${subjectRows(student, term)}
          </table>
          <div class="summary">
            <b>Average:</b> ${fmt(myAvg)}% &nbsp; <b>Position:</b> $position of ${avgs.size} &nbsp;
            <b>Aggregate:</b> $aggregate &nbsp; <b>Division:</b> $division &nbsp;
            <b>Class Teacher's remark:</b> ${if (myAvg >= 50) "Promising performance. Keep it up." else "Must work harder next term."}
          </div>
          <div class="sign">
            <div>Class Teacher</div><div>Head Teacher</div><div>Parent / Guardian</div>
          </div>
        </body></html>
        """.trimIndent()
    }

    /** Batch-render every active student in a class — the "fast" admin path. */
    fun reportCards(classId: String, term: Int): List<Pair<Student, String>> =
        data.students.filter { it.classId == classId && it.active }
            .map { it to reportCard(it, term) }

    private fun fmt(d: Double) = if (d == d.toLong().toDouble()) d.toLong().toString() else d.round1().toString()
}
