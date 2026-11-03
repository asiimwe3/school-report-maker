package com.derycode.srs.core

import com.derycode.srs.core.model.*
import com.derycode.srs.core.report.ReportEngine
import com.derycode.srs.core.results.ResultEngine
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The summary card must carry the school's own layout: A1–A3 assessments,
 * the 20% formative and 80% exam split, totals, per-subject teacher comment
 * and initials, both comments, fees and term dates.
 */
class StandardReportTest {

    private fun sample(): SchoolData {
        val school = School(name = "Tooro Royal College", motto = "Together for a Brighter Future",
            phone = "+256 700 000 000", email = "info@trc.ac.ug", address = "P.O. Box 123, Fort Portal",
            district = "Kabarole", colorPrimary = "#1F4E79")
        val year = AcademicYear(id = "y1", year = "2026", currentTermId = "t1", terms = listOf(
            Term(id = "t1", yearId = "y1", number = 2, endDate = "2026-08-14", nextTermBegins = "2026-09-12")))
        val cls = SchoolClass(id = "c1", name = "Senior 1", stream = "A", level = Level.O_LEVEL)
        val student = Student(id = "s1", admissionNo = "TRC/001", firstName = "Amina", lastName = "Kembabazi")
        val subject = Subject(id = "sub1", name = "Mathematics", code = "MTC", level = Level.O_LEVEL)
        val teacher = Teacher(id = "te1", name = "Francis Mwesige", role = Role.TEACHER)
        val comps = listOf(
            AssessmentComponent(id = "comp-ca", code = "CA", name = "Coursework", weightPercent = 20),
            AssessmentComponent(id = "comp-eot", code = "EOT", name = "End of term", weightPercent = 80)
        )
        val marks = listOf(
            Mark(id = "m1", studentId = "s1", subjectId = "sub1", termId = "t1", componentId = "comp-ca", score = 16.0, maxScore = 20),
            Mark(id = "m2", studentId = "s1", subjectId = "sub1", termId = "t1", componentId = "comp-eot", score = 64.0, maxScore = 80)
        )
        val scheme = GradingScheme(id = "g1", name = "UCE", level = Level.O_LEVEL,
            aggregateRule = AggregateRule.UCE_INDICATOR, caWeightPercent = 20, examWeightPercent = 80,
            boundaries = listOf(GradeBoundary(minScore = 80.0, maxScore = 100.0, grade = "A", label = "Outstanding"),
                                GradeBoundary(minScore = 60.0, maxScore = 79.9, grade = "B", label = "Good"),
                                GradeBoundary(minScore = 0.0, maxScore = 59.9, grade = "F", label = "Needs support")))
        return SchoolData(
            school = school,
            classes = listOf(cls),
            students = listOf(student),
            subjects = listOf(subject),
            teachers = listOf(teacher),
            components = comps,
            marks = marks,
            gradingSchemes = listOf(scheme),
            academicYears = listOf(year),
            enrollments = listOf(Enrollment(id = "e1", studentId = "s1", classId = "c1", academicYearId = "y1")),
            assignments = listOf(TeacherAssignment(id = "a1", teacherId = "te1", subjectId = "sub1", classId = "c1")),
            comments = listOf(
                Comment(id = "cm1", studentId = "s1", termId = "t1", type = CommentType.CLASS_TEACHER, text = "A steady term."),
                Comment(id = "cm2", studentId = "s1", termId = "t1", type = CommentType.HEAD_TEACHER, text = "Keep pushing."),
                Comment(id = "cm3", studentId = "s1", termId = "t1", subjectId = "sub1", type = CommentType.SUBJECT, text = "Improve algebra.")
            )
        )
    }

    @Test
    fun standardCardCarriesTheSchoolLayout() {
        val data = sample()
        val result = ResultEngine.classResults(data, "c1", "t1", "g1").first()
        val html = ReportEngine.studentReport(data, result, TemplateLayout.SUMMARY)

        // the split is real: 16/20 = 80% formative, 64/80 = 80% exam
        val subj = result.subjectResults.first()
        assertTrue(subj.formativePercent > 79.0 && subj.formativePercent < 81.0, "formative was ${subj.formativePercent}")
        assertTrue(subj.examPercent > 79.0 && subj.examPercent < 81.0, "exam was ${subj.examPercent}")

        listOf("FRNTV", "20%", "80%", "A1", "A2", "A3", "TEACHER COMMENT", "TEACHER", "AVERAGE SCORE",
               "Class Teacher", "Head Teacher Remarks", "Current Term Closing Date", "Next Term Opening Date", "SUMMARY REPORT", "STUDENT NAME".lowercase().let { "Student name" },
               "Improve algebra.", "2026-08-14", "2026-09-12", "School Stamp", "Together for a Brighter Future",
               "MATHEMATICS").forEach {
            assertTrue(html.contains(it), "summary card missing: $it")
        }
        // initials of the subject teacher: F.M. -> FM
        assertTrue(html.contains(">FM<"), "teacher initials missing")

        // keep a copy so the layout can be eyeballed after a build
        java.io.File("build/standard-card-preview.html").writeText(html)
    }
}

/**
 * Every card lists the compulsory subjects first, then the electives, no matter
 * how the subjects were entered in the school setup.
 */
class SubjectOrderTest {
    @Test
    fun compulsorySubjectsComeBeforeOptionalOnes() {
        val subjects = listOf(
            Subject(id = "s-mus", name = "Music", code = "MUS", compulsory = false),
            Subject(id = "s-eng", name = "English", code = "ENG", compulsory = true),
            Subject(id = "s-art", name = "Fine Art", code = "ART", compulsory = false),
            Subject(id = "s-mat", name = "Mathematics", code = "MTC", compulsory = true)
        )
        val data = SchoolData(
            school = School(name = "Test SS"),
            classes = listOf(SchoolClass(id = "c1", name = "Senior 1", level = Level.O_LEVEL)),
            students = listOf(Student(id = "st1", admissionNo = "1", firstName = "A", lastName = "B")),
            subjects = subjects,
            components = listOf(AssessmentComponent(id = "ca", code = "CA", name = "CA", weightPercent = 100)),
            marks = subjects.map { Mark(id = "m-${it.id}", studentId = "st1", subjectId = it.id, termId = "t1", componentId = "ca", score = 70.0, maxScore = 100) },
            gradingSchemes = listOf(GradingScheme(id = "g1", name = "UCE", level = Level.O_LEVEL,
                caWeightPercent = 100, examWeightPercent = 0,
                boundaries = listOf(GradeBoundary(minScore = 0.0, maxScore = 100.0, grade = "B", label = "Good")))),
            academicYears = listOf(AcademicYear(id = "y1", year = "2026", currentTermId = "t1",
                terms = listOf(Term(id = "t1", yearId = "y1", number = 1)))),
            enrollments = listOf(Enrollment(id = "e1", studentId = "st1", classId = "c1", academicYearId = "y1"))
        )
        val order = ResultEngine.classResults(data, "c1", "t1", "g1").first()
            .subjectResults.map { it.subjectId }
        assertTrue(order == listOf("s-eng", "s-mat", "s-mus", "s-art"), "order was $order")
    }
}

/** The printed card itself must follow that order, not the school setup order. */
class CardSubjectOrderTest {
    @Test
    fun cardRowsListCompulsorySubjectsFirst() {
        val subjects = listOf(
            Subject(id = "s-mus", name = "Music", code = "MUS", compulsory = false),
            Subject(id = "s-eng", name = "English", code = "ENG", compulsory = true),
            Subject(id = "s-mat", name = "Mathematics", code = "MTC", compulsory = true)
        )
        val data = SchoolData(
            school = School(name = "Test SS"),
            classes = listOf(SchoolClass(id = "c1", name = "Senior 1", level = Level.O_LEVEL)),
            students = listOf(Student(id = "st1", admissionNo = "1", firstName = "A", lastName = "B")),
            subjects = subjects,
            components = listOf(AssessmentComponent(id = "ca", code = "CA", name = "CA", weightPercent = 100)),
            marks = subjects.map { Mark(id = "m-${it.id}", studentId = "st1", subjectId = it.id, termId = "t1", componentId = "ca", score = 70.0, maxScore = 100) },
            gradingSchemes = listOf(GradingScheme(id = "g1", name = "UCE", level = Level.O_LEVEL,
                caWeightPercent = 100, examWeightPercent = 0,
                boundaries = listOf(GradeBoundary(minScore = 0.0, maxScore = 100.0, grade = "B", label = "Good")))),
            academicYears = listOf(AcademicYear(id = "y1", year = "2026", currentTermId = "t1",
                terms = listOf(Term(id = "t1", yearId = "y1", number = 1)))),
            enrollments = listOf(Enrollment(id = "e1", studentId = "st1", classId = "c1", academicYearId = "y1"))
        )
        val result = ResultEngine.classResults(data, "c1", "t1", "g1").first()
        for (layout in listOf(TemplateLayout.SUMMARY, TemplateLayout.CLASSIC)) {
            val html = ReportEngine.studentReport(data, result, layout)
            val lower = html.lowercase()
            val eng = lower.indexOf("english")
            val mtc = lower.indexOf("mathematics")
            val mus = lower.indexOf("music")
            assertTrue(eng in 0 until mtc && mtc < mus, "$layout order: ENG=$eng MTC=$mtc MUS=$mus")
        }
    }
}


/** The blue Standard card is the default design, and a school's own later choice is respected. */
class StandardIsDefaultTest {
    private fun bare(templates: List<ReportTemplate>, applied: Boolean = false) =
        SchoolData(school = School(name = "Test SS"), templates = templates,
            settings = AppSettings(standardDefaultApplied = applied))

    private fun defaultLayout(d: SchoolData) = d.templates.single { it.isDefault && !it.archived }.layout

    @Test
    fun aSchoolOnClassicIsMovedToStandardOnce() {
        val before = bare(listOf(
            ReportTemplate(id = "tpl-classic", name = "Classic", layout = TemplateLayout.CLASSIC, isDefault = true),
            ReportTemplate(id = "tpl-modern", name = "Modern", layout = TemplateLayout.MODERN)))
        val repoClass = Class.forName("com.derycode.srs.core.store.SchoolRepository")
        val m = repoClass.declaredMethods.first { it.name.startsWith("withStandardAsDefault") }
        m.isAccessible = true
        val dummy = com.derycode.srs.core.store.SchoolRepository(com.derycode.srs.core.store.JsonStore(),
            java.nio.file.Files.createTempDirectory("srs").resolve("d.json"), deviceId = "t")
        val after = m.invoke(dummy, before) as SchoolData
        assertTrue(defaultLayout(after) == TemplateLayout.SUMMARY, "default was ${defaultLayout(after)}")
        assertTrue(after.templates.size == 3, "Standard should be added, not replace the others")
        assertTrue(after.settings.standardDefaultApplied)

        // the school now picks Modern: running the migration again must leave that alone
        val chose = after.copy(templates = after.templates.map { it.copy(isDefault = it.layout == TemplateLayout.MODERN) })
        val again = m.invoke(dummy, chose) as SchoolData
        assertTrue(defaultLayout(again) == TemplateLayout.MODERN, "school's own choice was overridden")
    }

    @Test
    fun freshSchoolSeedsStandardAsTheDefault() {
        val repo = com.derycode.srs.core.store.SchoolRepository(com.derycode.srs.core.store.JsonStore(),
            java.nio.file.Files.createTempDirectory("srs").resolve("d.json"), deviceId = "t")
        repo.seedIfNeeded(emptyList(), emptyList())
        val d = repo.data
        assertTrue(defaultLayout(d) == TemplateLayout.SUMMARY, "fresh default was ${defaultLayout(d)}")
        assertTrue(d.templates.first().name == "Standard")
    }
}


/** A full 14-subject Tooro Royal College card must still fit one A4 page and look like the template. */
class FullCardPreviewTest {
    @Test
    fun fourteenSubjectCardIsWrittenForVisualCheck() {
        val names = listOf("English","Mathematics","Biology","Physics","Chemistry","Geography","C.R.E.","Art and Design",
            "Agriculture","P.E.","I.C.T","Handwriting","Entrepreneurship","Rutooro")
        val subjects = names.mapIndexed { i, n -> Subject(id = "s$i", name = n, code = "S$i", level = Level.O_LEVEL, compulsory = i < 8) }
        val data = SchoolData(
            school = School(name = "Tooro Royal College", schoolType = "Secondary School", motto = "Determination makes a difference",
                phone = "0784504690", email = "tooroyalcollege@gmail.com", address = "P.O. Box 123, Kyenjojo", headTeacher = "Tumusiime David"),
            classes = listOf(SchoolClass(id = "c1", name = "Senior 1", stream = "A", level = Level.O_LEVEL, classTeacherId = "t1")),
            students = listOf(Student(id = "st1", admissionNo = "TRC1012", firstName = "Ruhanga", middleName = "Ayebale", lastName = "John Paul")),
            subjects = subjects,
            teachers = listOf(Teacher(id = "t1", name = "Kabakurasi Aida", role = Role.CLASS_TEACHER),
                              Teacher(id = "t2", name = "Tumusiime David", role = Role.HEAD_TEACHER)),
            components = listOf(AssessmentComponent(id = "ca", code = "CA", name = "A1", weightPercent = 20),
                                AssessmentComponent(id = "eot", code = "EOT", name = "Exam", weightPercent = 80)),
            marks = subjects.flatMap { sj -> listOf(
                Mark(id = "a-${sj.id}", studentId = "st1", subjectId = sj.id, termId = "t1", componentId = "ca", score = 14.0, maxScore = 20),
                Mark(id = "b-${sj.id}", studentId = "st1", subjectId = sj.id, termId = "t1", componentId = "eot", score = 52.0, maxScore = 80)) },
            gradingSchemes = listOf(GradingScheme(id = "g1", name = "UCE", level = Level.O_LEVEL, caWeightPercent = 20, examWeightPercent = 80,
                boundaries = listOf(GradeBoundary(80.0, 100.0, "A"), GradeBoundary(70.0, 79.9, "B"), GradeBoundary(60.0, 69.9, "C"),
                                    GradeBoundary(50.0, 59.9, "D"), GradeBoundary(0.0, 49.9, "E")))),
            academicYears = listOf(AcademicYear(id = "y1", year = "2026", currentTermId = "t1",
                terms = listOf(Term(id = "t1", yearId = "y1", number = 2, endDate = "14/08/2026", nextTermBegins = "14/09/2026")))),
            enrollments = listOf(Enrollment(id = "e1", studentId = "st1", classId = "c1", academicYearId = "y1")),
            comments = listOf(
                Comment(id = "k1", studentId = "st1", termId = "t1", type = CommentType.CLASS_TEACHER, text = "Has achieved only a basic level of competency and needs support to apply knowledge in real-life situations."),
                Comment(id = "k2", studentId = "st1", termId = "t1", type = CommentType.HEAD_TEACHER, text = "You have shown some effort but are performing below your capability, so closer follow-up, better time management, and support from home will be essential next term."),
                Comment(id = "k3", studentId = "st1", termId = "t1", subjectId = "s0", type = CommentType.SUBJECT, text = "Satisfactory Performance"))
        )
        val result = ResultEngine.classResults(data, "c1", "t1", "g1").first()
        val html = ReportEngine.studentReport(data, result, TemplateLayout.SUMMARY)
        assertTrue(html.contains("ENTREPRENEURSHIP") && html.contains("RUTOORO"))
        java.io.File("build/full-card-preview.html").writeText(html)
    }
}
